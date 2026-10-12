// node --test test/*.test.js   (from schoology_archiver_extension/)
//
// reader/parse/inbox.js's pure helpers (node has no DOMParser; the DOM parts
// were checked against live pages, docs/ios/FINDINGS.md).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { splitSubject, parseDayHeader, atTimeOfDay, edgeType, idAfter } from '../reader/parse/inbox.js';
import { SHOW_MORE_RE } from '../reader/parse/feed.js';

test('splitSubject: the [group] prefix Schoology adds to course and group messages', () => {
  assert.deepEqual(splitSubject('[AP Comp Sci A - 2350: McLeodT p2 T1] Lesson Slides 10-5-26'),
    { group: 'AP Comp Sci A - 2350: McLeodT p2 T1', title: 'Lesson Slides 10-5-26' });
  assert.deepEqual(splitSubject('  Question about the lab '), { group: null, title: 'Question about the lab' });
  assert.deepEqual(splitSubject('[CHS Hub: Gr10]'), { group: 'CHS Hub: Gr10', title: 'CHS Hub: Gr10' });
});

test('parseDayHeader + atTimeOfDay: a notification\'s day header and its own time', () => {
  const day = parseDayHeader('Saturday, October 10, 2026');
  assert.deepEqual(day, { y: 2026, m: 9, d: 10 });
  assert.equal(atTimeOfDay(day, 'Oct 10 at 7:11 am'), new Date(2026, 9, 10, 7, 11).toISOString());
  assert.equal(atTimeOfDay(day, 'Oct 10 at 12:05 pm'), new Date(2026, 9, 10, 12, 5).toISOString());
  assert.equal(atTimeOfDay(day, 'Oct 10 at 12:05 am'), new Date(2026, 9, 10, 0, 5).toISOString());
  assert.equal(atTimeOfDay(day, ''), new Date(2026, 9, 10).toISOString());
  assert.equal(parseDayHeader('Yesterday'), null);
  assert.equal(atTimeOfDay(null, '7:11 am'), null);
});

test('edgeType / idAfter / SHOW_MORE_RE', () => {
  assert.equal(edgeType('s-edge-type-course-materials-add'), 'material');
  assert.equal(edgeType('x s-edge-type-grade-add'), 'grade');
  assert.equal(edgeType('s-edge-type-dropbox-submit'), 'dropbox-submit');
  assert.equal(edgeType('nothing'), 'other');
  assert.equal(idAfter('/messages/view/1817060418', '/messages/view/'), '1817060418');
  assert.equal(idAfter('/course/8465375718/materials', '/course/'), '8465375718');
  assert.equal(idAfter('/user/', '/user/'), null);
  assert.ok(SHOW_MORE_RE.test('/update_post/8572299392/show_more/1af3ce6eb23db050a28c8536464433fa'));
  assert.ok(!SHOW_MORE_RE.test('/update_post/1/show_more/x/../../assignment/2'));
});
