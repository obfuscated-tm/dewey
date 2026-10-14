// node --test test/*.test.js   (from extension/)
//
// overlays/quizpage.js: what the chip gets from a newer quiz's embedded data.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quizFacts } from '../overlays/quizpage.js';

const URL_ = 'https://fuhsd.schoology.com/course/8465375359/assessments/8604562267';

test('quizFacts: id, section and title from the quiz data; submitted = a finished attempt', () => {
  const init = { title: 'Grammaire 2.1 practice', gradeItemNid: 8604562267, sectionNid: 8465375359,
    submissions: [{ completed: true, status: '6' }] };
  assert.deepEqual(quizFacts(init, URL_), { id: '8604562267', section: '8465375359', title: 'Grammaire 2.1 practice', submitted: true });
  assert.equal(quizFacts({ submissions: [{ completed: false }] }, URL_).submitted, false);
  assert.equal(quizFacts({}, URL_).submitted, false);
});

test('quizFacts: falls back to the URL; not a quiz start page, null', () => {
  assert.deepEqual(quizFacts(null, URL_), { id: '8604562267', section: '8465375359', title: null, submitted: false });
  assert.equal(quizFacts({}, `${URL_}/take`), null);
  assert.equal(quizFacts({}, 'https://fuhsd.schoology.com/assignment/1'), null);
});

// ── quizInfo: the landing page's facts for the app ──────────────────────

import { quizInfo } from '../reader/parse/quiz.js';
import { isQuizTakingUrl } from '../reader/guard.js';
import { quizLandingHtml, DONE, OPEN } from './fixtures/quiz-landing.js';

test('quizInfo: a finished attempt', () => {
  const q = quizInfo(quizLandingHtml({ submissions: [DONE], score: 10, letter: 'A' }));
  assert.equal(q.title, 'Worksheet 2.5 Quiz');
  assert.equal(q.instructions, 'Answer every question. No notes.\nShow work & units.');
  assert.equal(q.attempts_allowed, 2);
  assert.equal(q.attempts_taken, 1);
  assert.equal(q.time_limit_min, null);
  assert.equal(q.opens_at, null);
  assert.equal(q.closes_at, null);
  assert.equal(q.overdue, false);
  assert.equal(q.score, 10);
  assert.equal(q.max_points, 10);
  assert.equal(q.letter, 'A');
  assert.equal(q.state, 'submitted');
  assert.equal(q.attempts.length, 1);
  assert.deepEqual({ ...q.attempts[0], at: undefined }, { n: 1, finished: true, at: undefined, minutes: 1, can_view: true });
  assert.match(q.attempts[0].at, /^2026-10-1[45]T/);
});

test('quizInfo: an open attempt is in_progress, and no letter without a score', () => {
  const q = quizInfo(quizLandingHtml({ submissions: [OPEN] }));
  assert.equal(q.state, 'in_progress');
  assert.equal(q.score, null);
  assert.equal(q.letter, null);
  assert.equal(q.attempts[0].finished, false);
  assert.equal(q.attempts[0].can_view, false);
});

test('quizInfo: not started, and nothing secret ever copied', () => {
  const q = quizInfo(quizLandingHtml({ overdue: true }));
  assert.equal(q.state, 'not_started');
  assert.equal(q.overdue, true);
  assert.doesNotMatch(JSON.stringify(q), /SECRET|csrf|eyJ|1978368/i);
  assert.equal(quizInfo('<html>no quiz</html>'), null);
});

test('the quiz-taking page is never requested', () => {
  const O = 'https://fuhsd.schoology.com';
  assert.equal(isQuizTakingUrl(`${O}/common-assessment-delivery/start/8610387962?action=onresume&submissionId=1`), true);
  assert.equal(isQuizTakingUrl(`${O}/common-assessment-delivery`), true);
  assert.equal(isQuizTakingUrl(`${O}/course/1/assessments/2`), false);
});
