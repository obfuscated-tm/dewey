// node --test test/   (from schoology_archiver_extension/)
//
// overlays/switch.js's pure logic: the frame-height clamp for the neo-plan
// settings iframe (docs/OVERLAY-UI.md, "Themes and settings") and the course
// id parsed out of the page's path for the Archive group. The popover itself
// (DOM, the settings menu, the frame, the outside-click closer) needs a
// browser — covered by opening test/theme-page.html or test/course-page.html
// by hand, not by node --test.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clampFrameHeight, courseIdFromPath } from '../overlays/switch.js';

test('clampFrameHeight: passes an ordinary height through', () => {
  assert.equal(clampFrameHeight(180), 180);
  assert.equal(clampFrameHeight('240'), 240);
});

test('clampFrameHeight: clamps to the sane range', () => {
  assert.equal(clampFrameHeight(1), 40);
  assert.equal(clampFrameHeight(0), 40);
  assert.equal(clampFrameHeight(-50), 40);
  assert.equal(clampFrameHeight(5000), 600);
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

// start() draws at once, and a page loading with the overlay already off
// closes the popover in that first draw: popOpen must exist by then, or the
// switch throws and never shows (no way back on but Alt+S).
test('start: popOpen is declared before the first draw', async () => {
  const { readFileSync } = await import('node:fs');
  const src = readFileSync(new URL('../overlays/switch.js', import.meta.url), 'utf8');
  const decl = src.indexOf('let popOpen');
  assert.ok(decl > 0 && decl < src.indexOf('draw(isOverlayOn())'));
});
