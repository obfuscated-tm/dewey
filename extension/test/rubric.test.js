// node --test test/   (from extension/)
//
// reader/parse/rubric.js: the gradebook's rubric ids from the page settings,
// and the two /iapi/grades responses → criteria. Shapes are from World
// History's gradebook (2026-10-07); descriptions shortened.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { rubricArgs, rubricPaths, parseRubric } from '../reader/parse/rubric.js';

const SETTINGS = 'jQuery.extend(Drupal.settings, {"theme_js":{'
  + '"s_course_course_switcher_0":{"template":"s_course_course_switcher","module":[],"args":[]},'
  + '"s_grades_rubric_grading_launch_btn_1":{"template":"s_grades_rubric_grading_launch_btn","module":"gradingRubric","args":["55379888","3602079887",8538134342,"\\u003Cspan class=\\u0022grade-val\\u0022\\u003E10\\u003C\\/span\\u003E",0,0,false,10,false,false]},'
  + '"s_grades_rubric_grading_launch_btn_2":{"template":"s_grades_rubric_grading_launch_btn","module":"gradingRubric","args":["55379885","3602079887",8562611325,"",0,0,false,5,false,false]}'
  + '}});';

test('rubricArgs: tpl-id → rubric, enrollment and item ids', () => {
  const m = rubricArgs(SETTINGS);
  assert.equal(m.size, 2);
  assert.deepEqual(m.get('s_grades_rubric_grading_launch_btn_1'), { rubricId: '55379888', enrollmentId: '3602079887', itemId: '8538134342' });
  assert.equal(m.get('s_grades_rubric_grading_launch_btn_2').itemId, '8562611325');
});

test('rubricArgs: nothing to find', () => {
  assert.equal(rubricArgs('').size, 0);
  assert.equal(rubricArgs(undefined).size, 0);
});

test('rubricPaths: the popup\'s two GETs', () => {
  assert.deepEqual(rubricPaths({ rubricId: '55379888', enrollmentId: '3602079887', itemId: '8538134342' }), {
    rubric: '/iapi/grades/rubric/55379888',
    gradeInfo: '/iapi/grades/rubric_grade_info/55379888/8538134342/3602079887',
  });
});

const RUBRIC = {
  response_code: 200,
  body: {
    id: 55379888, title: 'Enlightenment Diagnostic Rubric', total_points: 10,
    rows: [
      { id: 146419686, title: 'Claim', description: '', max_points: 3, weight: 1,
        columns: [{ pts: 3, description: 'Eloquent' }, { pts: 2.55, description: 'Clear' }, { pts: 2.25, description: 'Unclear' }, { pts: 2.1, description: 'Confusing' }] },
      { id: 146419687, title: 'Evidence Selection', description: '', max_points: 3, weight: 1,
        columns: [{ pts: 3, description: 'Two specific' }, { pts: 2.55, description: 'Two cited' }] },
      { id: 146419688, title: 'Analysis', description: '', max_points: 4, weight: 1,
        columns: [{ pts: 4, description: 'Insightful' }, { pts: 3.4, description: 'Solid' }] },
    ],
  },
};

test('parseRubric: each criterion with its level, and the sum', () => {
  const info = { body: { gradeInfo: {
    146419686: { grade: '3', comment: '' },
    146419687: { grade: '2.55', comment: 'Cite page numbers' },
    146419688: { grade: '4', comment: '' },
  }, overrideGrade: null } };
  const r = parseRubric(RUBRIC, info);
  assert.equal(r.title, 'Enlightenment Diagnostic Rubric');
  assert.equal(r.total, 10);
  assert.ok(Math.abs(r.earned - 9.55) < 1e-9);
  assert.deepEqual(r.criteria[1], { title: 'Evidence Selection', max: 3, earned: 2.55, level: 'Two cited', comment: 'Cite page numbers' });
  assert.equal(r.criteria[2].level, 'Insightful');
});

test('parseRubric: ungraded rows, a grade between columns, an override', () => {
  const r = parseRubric(RUBRIC, { body: { gradeInfo: { 146419686: { grade: '2.8', comment: '' } }, overrideGrade: '7' } });
  assert.equal(r.criteria[0].earned, 2.8);
  assert.equal(r.criteria[0].level, null);
  assert.equal(r.criteria[1].earned, null);
  assert.equal(r.earned, 7);
});

test('parseRubric: nothing graded yet', () => {
  const r = parseRubric(RUBRIC, { body: { gradeInfo: [], overrideGrade: null } });
  assert.equal(r.earned, null);
  assert.equal(r.criteria.length, 3);
});
