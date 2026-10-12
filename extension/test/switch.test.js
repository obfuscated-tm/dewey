// node --test test/   (from extension/)
//
// overlays/switch.js's pure logic (the course id parsed out of the page's
// path for the Archive button) and overlays/dialog.js's (the frame-height
// clamp for the neo-plan frame, the archive folder line). The cards
// themselves need a browser — covered by opening test/theme-page.html or
// test/course-page.html by hand, not by node --test.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { courseIdFromPath } from '../overlays/switch.js';
import { clampFrameHeight, folderText, DOWNLOADS_TEXT } from '../overlays/dialog.js';

test('clampFrameHeight: passes an ordinary height through', () => {
  assert.equal(clampFrameHeight(180), 180);
  assert.equal(clampFrameHeight('240'), 240);
});

test('clampFrameHeight: clamps to the sane range', () => {
  assert.equal(clampFrameHeight(1), 40);
  assert.equal(clampFrameHeight(0), 40);
  assert.equal(clampFrameHeight(-50), 40);
  assert.equal(clampFrameHeight(5000), 4000);
});

test('clampFrameHeight: rounds, and falls back to 120 for garbage', () => {
  assert.equal(clampFrameHeight(150.6), 151);
  assert.equal(clampFrameHeight(NaN), 120);
  assert.equal(clampFrameHeight(undefined), 120);
  assert.equal(clampFrameHeight('not a number'), 120);
  assert.equal(clampFrameHeight({}), 120);
});

test('courseIdFromPath: a course page\'s id', () => {
  assert.equal(courseIdFromPath('/course/12345'), '12345');
  assert.equal(courseIdFromPath('/course/12345/materials'), '12345');
  assert.equal(courseIdFromPath('/course/12345/student_grades'), '12345');
});

test('courseIdFromPath: anywhere else, null', () => {
  assert.equal(courseIdFromPath('/home'), null);
  assert.equal(courseIdFromPath('/grades/grades'), null);
  assert.equal(courseIdFromPath('/assignment/99'), null);
  assert.equal(courseIdFromPath(''), null);
  assert.equal(courseIdFromPath(undefined), null);
});

test('folderText: Downloads, a chosen folder, or one to allow again', () => {
  assert.equal(folderText({ custom: false }), DOWNLOADS_TEXT);
  assert.equal(folderText({ custom: true, name: 'School', granted: true }), 'School');
  assert.equal(folderText({ custom: true, name: 'School', granted: false }), 'School (needs allowing again)');
  assert.equal(folderText(null), 'Unknown (reload the extension)');
});
