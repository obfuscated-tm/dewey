import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isQuizPageUrl } from '../sync/quiet.js';

const H = 'https://fuhsd.schoology.com';

test('quiz pages: taking, attempts, review, newer assessments', () => {
  for (const p of [
    '/assignment/8580019138/assessment',
    '/assignment/8580019138/assessment_view/2429939407',
    '/assignment/8580019138/assessment_view',
    '/course/41/assessments/2',
    '/course/41/assessments/2/take',
    '/assessment/5/start',
  ]) assert.equal(isQuizPageUrl(H + p), true, p);
});

test('not quiz pages', () => {
  for (const u of [
    `${H}/assignment/8580019138`,
    `${H}/course/41/materials`,
    `${H}/home`,
    'https://example.com/assessment',
    '',
    undefined,
  ]) assert.equal(isQuizPageUrl(u), false, String(u));
});

test('in a quiz: taking pages, and a newer quiz page until quizpage.js says it is only the start page', async () => {
  const { tabInQuiz, isQuizReviewUrl } = await import('../sync/quiet.js');
  const landingUrl = `${H}/course/41/assessments/2`;
  // An old-style quiz's attempts and answer review are history.
  assert.equal(tabInQuiz({ id: 1, url: `${H}/assignment/8580019138/assessment_view` }), false);
  assert.equal(tabInQuiz({ id: 1, url: `${H}/assignment/8580019138/assessment_view/2429939407` }), false);
  assert.equal(isQuizReviewUrl(`${H}/assignment/8580019138/assessment`), false);
  // The take tab and take/start URLs always count.
  assert.equal(tabInQuiz({ id: 1, url: `${H}/assignment/8580019138/assessment` }), true);
  assert.equal(tabInQuiz({ id: 1, url: `${landingUrl}/take` }, { landing: { 1: `${landingUrl}/take` } }), true);
  assert.equal(tabInQuiz({ id: 1, url: `${H}/assessment/5/start` }), true);
  // A newer quiz's page: counts until confirmed as the start page, for that tab at that URL.
  assert.equal(tabInQuiz({ id: 1, url: landingUrl }), true);
  assert.equal(tabInQuiz({ id: 1, url: landingUrl }, { landing: { 1: landingUrl } }), false);
  assert.equal(tabInQuiz({ id: 2, url: landingUrl }, { landing: { 1: landingUrl } }), true);
  assert.equal(tabInQuiz({ id: 1, url: `${landingUrl}#q1` }, { landing: { 1: landingUrl } }), true);
  // Navigating away from a confirmed start page: counts by where it's going.
  assert.equal(tabInQuiz({ id: 1, url: landingUrl, pendingUrl: `${landingUrl}/take` }, { landing: { 1: landingUrl } }), true);
  // The archiver's own review tabs never count; nor do other pages.
  assert.equal(tabInQuiz({ id: 9, url: landingUrl }, { ours: new Set([9]) }), false);
  assert.equal(tabInQuiz({ id: 1, url: `${H}/home` }), false);
});
