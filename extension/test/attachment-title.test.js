// reader/parse/assignment.js fileTitle: the link text without the file name Schoology tucks after it.
import test from 'node:test';
import assert from 'node:assert/strict';
import { fileTitle } from '../reader/parse/assignment.js';

test('drops the repeated file name', () => {
  assert.equal(fileTitle('String Craft Classwork.pdfString_Craft_Classwork.pdf'), 'String Craft Classwork.pdf');
  assert.equal(fileTitle('Lab 3.1 handout.docxLab_3.1_handout.docx'), 'Lab 3.1 handout.docx');
});

test('leaves ordinary titles alone', () => {
  assert.equal(fileTitle('Lab 3.1 handout.pdf'), 'Lab 3.1 handout.pdf');
  assert.equal(fileTitle('Notes.pdf and answers.pdf'), 'Notes.pdf and answers.pdf');
  assert.equal(fileTitle('Rubric'), 'Rubric');
  assert.equal(fileTitle(''), '');
});
