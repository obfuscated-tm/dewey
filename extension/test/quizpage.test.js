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
