// node --test test/*.test.js   (from extension/)
//
// reader/parse/account.js: the Username row of /settings/account (the shape
// of the live page, 2026-10-20, with a made-up number).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { accountUsername, studentNumber, parseAccount } from '../reader/parse/account.js';

const page = (username, email = 'jdoe123@student.fuhsd.org') => `
<form id="s-user-account-form">
  <div class="form-item" id="edit-user-username-wrapper">
     <label>Username<span role="tooltip" tabindex="0" class="infotip hide-qmark" aria-label="Either a username or email address is required."><span class="form-required">**</span><span class="infotip-content visually-hidden" role="tooltip">Either a username or email address is required.</span></span>: </label>
     <div class="form-text">${username}</div>
  </div>
  <div class="form-item" id="edit-user-primary-email-wrapper">
     <label>Primary Email: </label>
     <div class="form-text">${email}</div>
  </div>
</form>`;

test('accountUsername: the Username row, not the email row after it', () => {
  assert.equal(accountUsername(page('1234567')), '1234567');
  assert.equal(accountUsername(page('  1234567 \n')), '1234567');
  assert.equal(accountUsername(page('j.doe&amp;co')), 'j.doe&co');
  assert.equal(accountUsername(page('')), null);
  assert.equal(accountUsername('<html><body>Grades</body></html>'), null);
  assert.equal(accountUsername(null), null);
});

test('studentNumber: digits only', () => {
  assert.equal(studentNumber('1234567'), '1234567');
  assert.equal(studentNumber('123-4567'), '1234567');
  assert.equal(studentNumber('jdoe123'), null);
  assert.equal(studentNumber('jdoe123@student.fuhsd.org'), null);
  assert.equal(studentNumber('123'), null);
  assert.equal(studentNumber(''), null);
  assert.equal(studentNumber(null), null);
});

test('parseAccount: username and the student number it holds', () => {
  assert.deepEqual(parseAccount(page('1234567')), { username: '1234567', studentId: '1234567' });
  assert.deepEqual(parseAccount(page('jdoe')), { username: 'jdoe', studentId: null });
  assert.deepEqual(parseAccount('<html></html>'), { username: null, studentId: null });
});
