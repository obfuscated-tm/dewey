// node --test test/*.test.js   (from extension/)
//
// overlays/statusguide.js: which first tick earns the status tip, and its words.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tipKindFor, tipParts } from '../overlays/statusguide.js';

test('tipKindFor: done, studied or turned in earn a tip; going back does not', () => {
  assert.equal(tipKindFor('work', { work: 'ready', exam: false }), 'done');
  assert.equal(tipKindFor('work', { work: 'ready', exam: true }), 'studied');
  assert.equal(tipKindFor('turnIn'), 'turnedIn');
  assert.equal(tipKindFor('work', { work: 'todo' }), null);
  assert.equal(tipKindFor('putBack'), null);
});

test('tipParts: each tip says what comes next', () => {
  const text = (k) => tipParts(k).map(([t]) => t).join('');
  assert.match(text('done'), /Done is just for you\. Hand it in next: Submit .* Turn in for paper or a lab site\./);
  assert.match(text('studied'), /Tests are never turned in\./);
  assert.match(text('turnedIn'), /Dewey says Submitted only when Schoology does\./);
});
