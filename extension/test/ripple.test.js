// node --test test/*.test.js   (from extension/)
//
// overlays/ripple.js: the done ripple's timing, and that it stays still when
// it should. Its look is checked by hand on test/ripple-page.html.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RIPPLE_MS, rippleRings, rippleFrom } from '../overlays/ripple.js';

test('rippleRings: two rings, quick, fading out as they grow', () => {
  const rings = rippleRings();
  assert.equal(rings.length, 2);
  for (const { keyframes, timing } of rings) {
    assert.ok(timing.delay + timing.duration <= RIPPLE_MS && RIPPLE_MS <= 450);
    assert.equal(keyframes.at(-1).opacity, 0);
  }
});

test('rippleFrom: nothing plays with prefers-reduced-motion, or without a button', () => {
  const win = { matchMedia: () => ({ matches: true }) };
  assert.equal(rippleFrom({}, {}, { win }), null);
  assert.equal(rippleFrom(null, {}, { win: { matchMedia: () => ({ matches: false }) } }), null);
});
