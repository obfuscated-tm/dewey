// node --test test/*.test.js   (from extension/)
//
// Search all materials (overlays/search.js): the match rule, the highlight
// runs, the page's class first, grouping and the walk over a Materials tree.
// The box itself is covered by opening test/home-page.html.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchesQuery, highlight, orderCourses, searchWalks, placeOf, walkCourse } from '../overlays/search.js';
import { classCourses } from '../overlays/todo.js';

test('matchesQuery: every word, in the title or the folder path, any case', () => {
  const row = { title: 'Lab 6 — Enzyme rates.pdf', path: 'Unit 4 / Labs' };
  assert.equal(matchesQuery(row, 'lab 6'), true);
  assert.equal(matchesQuery(row, 'RATES unit 4'), true);
  assert.equal(matchesQuery(row, 'lab 7'), false);
  assert.equal(matchesQuery(row, '   '), false);
});

test('matchesQuery: words match where a word starts, not in the middle', () => {
  assert.equal(matchesQuery({ title: 'Syllabus 2026-27', path: '' }, 'lab 6'), false);
  assert.equal(matchesQuery({ title: 'Labs', path: '' }, 'lab'), true);
  assert.equal(matchesQuery({ title: '(Lab) week-6', path: '' }, 'lab 6'), true);
  assert.equal(matchesQuery({ title: 'C++ notes', path: '' }, 'c++'), true);
});

test('highlight: marks each query word, merges neighbours, keeps the rest', () => {
  assert.deepEqual(highlight('Lab 6 rates', 'rates lab'), [['Lab', true], [' 6 ', false], ['rates', true]]);
  assert.deepEqual(highlight('Notes', ''), [['Notes', false]]);
  assert.deepEqual(highlight('Syllabus lab', 'lab'), [['Syllabus ', false], ['lab', true]]);
});

test('orderCourses: the page\'s class first, the rest in dashboard order', () => {
  const cs = [{ section_id: '1' }, { section_id: '2' }, { section_id: '3' }];
  assert.deepEqual(orderCourses(cs, '3').map((c) => c.section_id), ['3', '1', '2']);
  assert.deepEqual(orderCourses(cs, '9').map((c) => c.section_id), ['1', '2', '3']);
  assert.deepEqual(orderCourses(cs, null).map((c) => c.section_id), ['1', '2', '3']);
});

test('searchWalks: grouped by class in the given order, title matches before path-only ones, empty classes left out', () => {
  const cs = [{ section_id: '1', name: 'Bio' }, { section_id: '2', name: 'English' }, { section_id: '3', name: 'CS' }];
  const walks = new Map([
    ['1', { rows: [{ title: 'Syllabus', path: 'Lab notes' }, { title: 'Lab notes week 2', path: '' }] }],
    ['2', { rows: [{ title: 'Gatsby', path: '' }] }],
    ['3', { error: 'HTTP 500' }],
  ]);
  const out = searchWalks(cs, walks, 'lab notes');
  assert.equal(out.length, 1);
  assert.equal(out[0].course.name, 'Bio');
  assert.deepEqual(out[0].hits.map((h) => h.title), ['Lab notes week 2', 'Syllabus']);
});

test('placeOf: folder path with ›, the top is Materials', () => {
  assert.equal(placeOf({ path: 'Unit 4 / Labs' }), 'Unit 4 › Labs');
  assert.equal(placeOf({ path: '' }), 'Materials');
});

test('classCourses: classes only, coloured by place among classes, one per section', () => {
  const out = classCourses([
    { name: 'School Hub: Gr10', section_id: '9' },
    { name: 'Algebra 2 - 1202: ChenL p1', section_id: '1' },
    { name: 'AP Biology - 1101: RiveraM p2', section_id: '2' },
    { name: 'AP Biology - 1101: RiveraM p2', section_id: '2' },
  ]);
  assert.deepEqual(out, [
    { section_id: '1', name: 'Algebra 2', colorIndex: 1 },
    { section_id: '2', name: 'AP Biology', colorIndex: 2 },
  ]);
});

// A tiny Materials tree, served as Schoology's own folder markup.
function folderPage(rows) {
  const trs = rows.map((r) => r.folder
    ? `<tr id="f-${r.folder}" class="material-row-folder"><td class="folder-icon-wrapper"></td><td class="folder-title"><a href="/course/7/materials?f=${r.folder}">${r.title}</a></td></tr>`
    : `<tr id="n-${r.id}" class="dr type-document"><td class="item-info"><span class="item-title"><a href="/course/7/materials/gp/${r.id}">${r.title}</a></span></td></tr>`).join('');
  return `<html><head><title>Materials | Schoology</title></head><body><div id="center-top"></div><table id="folder-contents-table"><tbody>${trs}</tbody></table></body></html>`;
}

test('walkCourse: opens every folder, tags each row with its path', { skip: typeof DOMParser === 'undefined' && 'needs DOMParser (covered in test/home-page.html)' }, async () => {
  const pages = {
    '/course/7/materials': folderPage([{ folder: 11, title: 'Unit 1' }, { id: 1, title: 'Syllabus' }]),
    '/course/7/materials?f=11': folderPage([{ id: 2, title: 'Notes' }]),
  };
  const fetchImpl = async (url) => ({ ok: true, status: 200, url: `https://x.schoology.com${url}`, text: async () => pages[url] });
  const { rows } = await walkCourse('7', { fetchImpl });
  assert.deepEqual(rows.map((r) => [r.title, r.path]), [['Unit 1', ''], ['Syllabus', ''], ['Notes', 'Unit 1']]);
});
