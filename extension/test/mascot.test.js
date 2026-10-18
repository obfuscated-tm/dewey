// node --test test/*.test.js   (from extension/)
//
// Dewey's pictures are copies of brand/mascot/ (brand/sync-mascot.sh). A
// redraw lands in brand/mascot first; this fails until the copies follow.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { POSES, mascotSrc } from '../overlays/mascot.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BRAND = join(ROOT, '..', 'brand', 'mascot');
const IOS = join(ROOT, '..', 'ios', 'App', 'Assets.xcassets');
const assetName = (pose) => 'Dewey' + pose.split('-').map((w) => w[0].toUpperCase() + w.slice(1)).join('');

for (const pose of POSES) {
  for (const file of [`dewey-${pose}.svg`, `dewey-${pose}-dark.svg`]) {
    test(`${file} matches brand/mascot (run brand/sync-mascot.sh)`, () => {
      const want = readFileSync(join(BRAND, file), 'utf8');
      assert.equal(readFileSync(join(ROOT, 'overlays', 'mascot', file), 'utf8'), want, 'extension copy');
      assert.equal(readFileSync(join(IOS, `${assetName(pose)}.imageset`, file), 'utf8'), want, 'iOS asset copy');
    });
  }
}

test('mascotSrc picks the -dark file and is empty outside the extension', () => {
  assert.equal(mascotSrc('hello'), '');
  globalThis.chrome = { runtime: { getURL: (p) => `ext://x/${p}` } };
  try {
    assert.equal(mascotSrc('hello'), 'ext://x/overlays/mascot/dewey-hello.svg');
    assert.equal(mascotSrc('uh-oh', true), 'ext://x/overlays/mascot/dewey-uh-oh-dark.svg');
  } finally {
    delete globalThis.chrome;
  }
});
