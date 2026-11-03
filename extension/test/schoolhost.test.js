import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isSchoolHost } from '../util.js';

test('isSchoolHost: a district Schoology is one, any case', () => {
  for (const h of ['fuhsd.schoology.com', 'FUHSD.Schoology.com', 'my-district.schoology.com']) {
    assert.equal(isSchoolHost(h), true, h);
  }
});

test("isSchoolHost: Schoology's own sites and anything else aren't", () => {
  for (const h of [
    'developers.schoology.com', 'app.schoology.com', 'www.schoology.com', 'support.schoology.com',
    'api.schoology.com', 'files-cdn.schoology.com', 'schoology.com', 'a.b.schoology.com',
    'fuhsd.schoology.com.evil.com', 'evil.com', '', null, undefined,
  ]) {
    assert.equal(isSchoolHost(h), false, String(h));
  }
});
