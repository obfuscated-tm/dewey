// node --test test/*.test.js   (from extension/; "test/" alone doesn't work)
//
// overlays/theme.js is pure (no chrome.* at the top level besides ui.js's
// storage reads, which fail over to the extension-less defaults in tests),
// so themeCss() and preInvert() are checked directly.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { THEMES, themeCss, hostThemeCss, themeOf, preInvert, paint, softSeen } from '../overlays/theme.js';
import { SETTINGS_DEFAULTS } from '../overlays/ui.js';

test('schoology: no page changes at all', () => {
  assert.equal(themeCss('schoology'), '');
  assert.equal(themeCss('not-a-theme'), '');
});

test('a light theme colours the header and doesn\'t invert the page', () => {
  const css = themeCss('ocean');
  assert.match(css, /#header > header/);
  assert.match(css, new RegExp(THEMES.ocean.header));
  assert.doesNotMatch(css, /html \{ filter/);
});

test('a dark theme inverts the page and re-inverts real media', () => {
  const css = themeCss('dark');
  assert.match(css, /filter: invert\(1\) hue-rotate\(180deg\)/);
  assert.match(css, /:not\(picture\) > img/);
  assert.match(css, /iframe/);
  // The header is written pre-inverted, not as the colour it displays as.
  assert.doesNotMatch(css, new RegExp(THEMES.dark.header));
  assert.match(css, new RegExp(preInvert(THEMES.dark.header)));
});

test('midnight is dark too, with a gradient header pre-inverted stop by stop', () => {
  const css = themeCss('midnight');
  assert.match(css, /filter: invert\(1\)/);
  for (const stop of THEMES.midnight.header) assert.match(css, new RegExp(preInvert(stop)));
  assert.match(css, /linear-gradient\(90deg/);
});

test('a dark page: canvas and body pre-inverted, and no light rubber-band past the edges', () => {
  const t = THEMES.dracula, css = themeCss('dracula');
  assert.match(css, new RegExp(`html \\{[^}]*background: ${preInvert(t.page)}[^}]*overscroll-behavior: none`));
  assert.match(css, new RegExp(`body \\{ background: ${preInvert(t.page)}`));
});

test('the header covers Schoology\'s wrapped buttons (Courses, Groups, Apps, user menu)', () => {
  for (const key of ['ocean', 'sunset', 'nord']) {
    const css = themeCss(key);
    assert.match(css, /#header, #header > header \{/);
    assert.match(css, /nav > ul > li > div > :is\(a, button\)/);
    assert.match(css, /svg/);
  }
  assert.match(themeCss('rainbow'), /text-shadow/);
});

test('light themes with a page colour tint the page and its canvas', () => {
  assert.match(themeCss('sakura'), new RegExp(`html, body \\{ background: ${THEMES.sakura.page}`));
});

test('preInvert round-trips: applying the filter to it gives back the original colour', () => {
  // The filter is self-inverse, so applying it to preInvert(hex) once more
  // reproduces hex — check by comparing preInvert(preInvert(hex)) to hex.
  for (const hex of ['#24272B', '#16305C', '#FFFFFF', '#000000', '#1D5EA8', '#E7EFF8']) {
    const back = preInvert(preInvert(hex));
    const [r1, g1, b1] = [hex.slice(1, 3), hex.slice(3, 5), hex.slice(5, 7)].map((h) => parseInt(h, 16));
    const [r2, g2, b2] = [back.slice(1, 3), back.slice(3, 5), back.slice(5, 7)].map((h) => parseInt(h, 16));
    assert.ok(Math.abs(r1 - r2) <= 2, `${hex} r: ${r1} vs ${r2}`);
    assert.ok(Math.abs(g1 - g2) <= 2, `${hex} g: ${g1} vs ${g2}`);
    assert.ok(Math.abs(b1 - b2) <= 2, `${hex} b: ${b1} vs ${b2}`);
  }
});

test('every theme has a name and an accent', () => {
  for (const [key, t] of Object.entries(THEMES)) {
    assert.equal(typeof t.name, 'string', key);
    assert.match(t.accent, /^#[0-9a-fA-F]{6}$/, key);
    assert.match(t.accentSoft, /^#[0-9a-fA-F]{6}$/, key);
  }
  assert.equal(THEMES.schoology.header, null);
});

test('schoology is the default; dew follows the computer: light or dark', () => {
  assert.equal(SETTINGS_DEFAULTS.theme, 'schoology');
  assert.equal(themeOf('dew', false), THEMES.dewLight);
  assert.equal(themeOf('dew', true), THEMES.dewDark);
  assert.equal(themeCss('dew', false), themeCss('dewLight'));
  assert.equal(themeCss('dew', true), themeCss('dewDark'));
  assert.match(themeCss('dew', true), /html \{ filter: invert/);
  assert.equal(themeOf('ocean', true), THEMES.ocean);
});

test('dew sets every overlay colour: the iOS paper-and-ink palette, pre-inverted when dark', () => {
  const light = hostThemeCss('dewLight');
  for (const v of ['#FFFDF8', '#1B1A17', '#0F6E56', '#A63A0B']) assert.match(light, new RegExp(v));
  assert.match(light, /--ink:/);
  const dark = hostThemeCss('dewDark');
  assert.match(dark, new RegExp(paint('#1B1A18', THEMES.dewDark)));
  assert.match(dark, new RegExp(paint('#5DCAA5', THEMES.dewDark)));
  assert.doesNotMatch(dark, /#EDEAE2/);
  // Other themes still change only the accent (and a dark one's surfaces).
  assert.doesNotMatch(hostThemeCss('ocean'), /--ink:/);
});

// Within a few steps per channel: the filter clamps the most saturated colours.
const near = (a, b, tol) => [1, 3, 5].every((i) => Math.abs(parseInt(a.slice(i, i + 2), 16) - parseInt(b.slice(i, i + 2), 16)) <= tol);

test('dew dark: a softer, warmer invert, so Schoology shows the iOS dark colours, not black and white', () => {
  const t = THEMES.dewDark;
  const css = themeCss('dewDark');
  assert.match(css, /html \{ filter: invert\(1\) contrast\(0\.87\) hue-rotate\(180deg\) sepia\(0\.05\)/);
  // Photos: the contrast undone too.
  assert.match(css, /img[^{]*\{ filter: hue-rotate\(180deg\) contrast\(1\.149\d*\) invert\(1\)/);
  assert.ok(near(softSeen('#FFFFFF', t.soft), '#121211', 2), 'white cards → the iOS background');
  assert.ok(near(softSeen('#000000', t.soft), '#EDEAE2', 12), 'black text → warm off-white');
  // Painted colours come back as themselves (ink, cards, the page, the header).
  for (const v of ['#1B1A18', '#121211', '#EDEAE2', '#A9A59B', '#2C2A27', '#04342C']) {
    assert.ok(near(softSeen(paint(v, t), t.soft), v, 2), v);
  }
  // Other dark themes keep the plain invert.
  assert.match(themeCss('dark'), /html \{ filter: invert\(1\) hue-rotate\(180deg\);/);
});
