// node --test test/*.test.js   (from extension/)
//
// The Archive card's shelf (overlays/archive.js): which books, in what order,
// how tall, how faded, and the age in words. The card itself is covered by
// opening test/archive-card.html.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shelfBooks, spineHeight, fadeFor, agoText } from '../overlays/archive.js';

const DAY = 86_400_000;
const NOW = Date.parse('2026-11-27T12:00:00Z');

const all = {
  todoCardOrder: [
    { name: 'School Hub: Gr10', section_id: '9' },
    { name: 'AP Biology - 1101: RiveraM p2', section_id: '1' },
    { name: 'English 11 - 1303: Park p3', section_id: '2' },
  ],
  'course:x.schoology.com:1': { courseName: 'AP Biology - 1101: RiveraM p2', files: { a: 1, b: 1 }, runs: [{ at: '2026-11-12T10:00:00Z' }] },
  'course:x.schoology.com:5': { courseName: 'Chemistry', files: { a: 1 }, runs: [{ at: '2026-06-01T10:00:00Z', stopped: true }] },
  'course:other.schoology.com:2': { courseName: 'English 11', files: { a: 1 }, runs: [{ at: '2026-11-01T10:00:00Z' }] },
};

test('shelfBooks: dashboard classes in order (archived or not), then old archives, then the page\'s class', () => {
  const books = shelfBooks({ all, host: 'X.schoology.com', pageCourseId: '7', pageCourseName: 'French 3 - 1404: Dupont' });
  assert.deepEqual(books.map((b) => [b.id, b.name, b.colorIndex, b.items, b.lastAt != null]), [
    ['1', 'AP Biology', 1, 2, true],
    ['2', 'English 11', 2, 0, false], // archived on another host only: not on this shelf
    ['5', 'Chemistry', null, 1, true],
    ['7', 'French 3', null, 0, false],
  ]);
  assert.equal(books[2].stopped, true);
});

test('shelfBooks: an empty store is an empty shelf; the page\'s class still gets a book', () => {
  assert.deepEqual(shelfBooks({ all: {}, host: 'x' }), []);
  assert.equal(shelfBooks({ all: {}, host: 'x', pageCourseId: '3' })[0].name, 'Course 3');
});

test('spineHeight: 96 empty, 160 for the fullest, in between by square root', () => {
  assert.equal(spineHeight(0, 100), 96);
  assert.equal(spineHeight(100, 100), 160);
  assert.equal(spineHeight(25, 100), 128);
});

test('fadeFor: nothing for two weeks, 35% by two months, never more', () => {
  assert.equal(fadeFor(null, NOW), 0);
  assert.equal(fadeFor(NOW - 10 * DAY, NOW), 0);
  assert.equal(fadeFor(NOW - 37 * DAY, NOW), 18);
  assert.equal(fadeFor(NOW - 60 * DAY, NOW), 35);
  assert.equal(fadeFor(NOW - 400 * DAY, NOW), 35);
});

test('agoText: plain words', () => {
  assert.equal(agoText(NOW - 3600_000, NOW), 'today');
  assert.equal(agoText(NOW - 1.5 * DAY, NOW), 'yesterday');
  assert.equal(agoText(NOW - 5 * DAY, NOW), '5 days ago');
  assert.equal(agoText(NOW - 24 * DAY, NOW), '3 weeks ago');
  assert.equal(agoText(NOW - 120 * DAY, NOW), '4 months ago');
});
