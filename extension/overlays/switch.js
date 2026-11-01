// The Overlay switch: a small on/off at the bottom left of every Schoology
// page the extension adds anything to (boot.js decides), and Alt+S. Off hides
// every element the extension adds, on every Schoology page, so Schoology
// looks untouched; the switch itself stays, reading "Schoology only".
// The setting lives in chrome.storage.local (ui.js keeps it).
//
// Beside it, while the overlay is on: an Archive button, then a gear.
//
// The Archive button opens the archiver's own card (overlays/archive.js, a
// dynamic import loaded on first click), built like the Settings card:
// dimmed page, inset card, ×/Esc/click-outside closes. The card is only a
// view — the job itself runs in the offscreen document, so closing it or
// navigating away never stops an archive in progress. A failed import (the
// worker gone after an extension reload) shows a quiet bubble above the
// button, "Reload the extension, then this page.", that hides itself after a
// few seconds.
//
// The gear opens the Settings card (overlays/settings.js, loaded on first
// click), and so does the extension's toolbar icon on a Schoology tab (a
// message from background.js). The first time the overlay shows, the Welcome
// card (overlays/intro.js) opens once by itself.

import { createHost, el, onClick, keepEvents, isOverlayOn, onOverlay, setOverlay } from './ui.js';

const RELOAD_HINT = 'Reload the extension, then this page.';

const GEAR_SVG = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
  + 'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"></circle>'
  + '<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>';

const ARCHIVE_SVG = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
  + 'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="4" rx="1"></rect>'
  + '<path d="M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8"></path><line x1="10" y1="12" x2="14" y2="12"></line></svg>';

const CSS = `
.wrap {
  position: fixed; left: 16px; bottom: 16px; z-index: 2147483000;
  display: inline-flex; align-items: center; gap: 8px;
}
.sw {
  display: inline-flex; align-items: center; gap: 8px;
  height: 28px; padding: 0 10px 0 6px; background: var(--surface);
  border: 1px solid var(--line); border-radius: var(--radius);
  font-size: 12px; color: var(--dim);
}
.sw:hover { border-color: var(--dim); color: var(--ink); }
.track {
  position: relative; width: 28px; height: 16px; border-radius: 8px; flex: none;
  background: var(--faint); /* not --line: off has to be visible too (WCAG non-text 3:1) */
}
.track::after {
  content: ""; position: absolute; top: 2px; left: 2px;
  width: 12px; height: 12px; border-radius: 50%; background: var(--surface);
}
.sw[aria-pressed="true"] { color: var(--accent); }
.sw[aria-pressed="true"] .track { background: var(--accent); }
.sw[aria-pressed="true"] .track::after { left: 14px; }
.gear, .arc {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; flex: none; background: var(--surface);
  border: 1px solid var(--line); border-radius: var(--radius); color: var(--dim);
}
.gear:hover, .arc:hover { border-color: var(--dim); color: var(--ink); }
.bubble {
  position: fixed; z-index: 2147483000; max-width: 220px;
  padding: 6px 10px; background: var(--surface);
  border: 1px solid var(--line); border-radius: var(--radius);
  font-size: 11px; color: var(--dim);
}
`;

/** Alt+S, but not while Owen is typing in one of Schoology's fields. */
function isToggleKey(e) {
  if (!e.altKey || e.ctrlKey || e.metaKey || e.shiftKey || e.code !== 'KeyS') return false;
  const t = e.target;
  return !(t && (t.isContentEditable || /^(input|textarea|select)$/i.test(t.localName || '')));
}

/** The course id on a course page (`/course/123…`), or null everywhere else. */
export function courseIdFromPath(pathname) {
  return (String(pathname || '').match(/^\/course\/(\d+)/) || [])[1] || null;
}

export function start({ doc = document, loc = location } = {}) {
  const { host, root } = createHost('np-overlay-switch', CSS, { keep: true });
  keepEvents(host);
  const wrap = el('div', 'wrap');

  const b = el('button', 'sw');
  b.title = 'Switch between the overlay and plain Schoology (Alt+S)';
  const label = el('span');
  b.append(el('span', 'track'), label);

  const gear = el('button', 'gear');
  gear.type = 'button';
  gear.title = 'Settings';
  gear.setAttribute('aria-label', 'Settings');
  gear.setAttribute('aria-haspopup', 'dialog');
  gear.innerHTML = GEAR_SVG;

  // Archive: its own button, between the switch and the gear, opening the
  // archiver's card (overlays/archive.js). Loaded lazily so a page that never
  // clicks it never pays for the Archiver's own dependencies.
  const courseId = courseIdFromPath(loc.pathname);
  const arc = el('button', 'arc');
  arc.type = 'button';
  const arcText = courseId ? 'Archive this course' : 'Archiver';
  arc.title = arcText;
  arc.setAttribute('aria-label', arcText);
  arc.innerHTML = ARCHIVE_SVG;

  const bubble = el('div', 'bubble', RELOAD_HINT);
  bubble.hidden = true;
  bubble.setAttribute('role', 'status');
  let bubbleTimer = 0;
  function showBubble(near = arc) {
    const r = near.getBoundingClientRect();
    bubble.style.left = `${Math.round(r.left)}px`;
    bubble.style.bottom = `${Math.round((doc.defaultView || globalThis).innerHeight - r.top + 8)}px`;
    bubble.hidden = false;
    clearTimeout(bubbleTimer);
    bubbleTimer = setTimeout(() => { bubble.hidden = true; }, 4000);
  }
  onClick(arc, async () => {
    bubble.hidden = true;
    try {
      const { start: startCard } = await import('./archive.js');
      await startCard({ doc, loc }).open();
    } catch { showBubble(); }
  });

  wrap.append(b, arc, gear);
  root.append(wrap, bubble);

  const draw = (on) => {
    b.setAttribute('aria-pressed', String(on));
    label.textContent = on ? 'Overlay' : 'Schoology only';
    gear.hidden = !on;
    arc.hidden = !on;
    if (!on) bubble.hidden = true;
  };
  draw(isOverlayOn());
  onOverlay(draw);
  onClick(b, () => setOverlay(!isOverlayOn()));

  // Settings: a card of its own, loaded on first open. A failed import (the
  // worker gone after an extension reload) shows the same bubble as Archive.
  async function openSettings() {
    bubble.hidden = true;
    try {
      const { start: startSettings } = await import('./settings.js');
      startSettings({ doc }).open();
    } catch { showBubble(gear); }
  }
  onClick(gear, openSettings);
  try {
    chrome.runtime.onMessage.addListener((msg, sender) => {
      if (sender.id !== chrome.runtime.id || msg?.type !== 'openSettings') return;
      if (!isOverlayOn()) setOverlay(true);
      openSettings();
    });
  } catch { /* tests: no chrome */ }

  // The Welcome card, once: the first time the overlay is on here.
  (async () => {
    if (!isOverlayOn()) return;
    try {
      const { start: startIntro, introSeen } = await import('./intro.js');
      if (await introSeen()) return;
      setTimeout(() => { if (isOverlayOn()) startIntro({ doc }).open(); }, 600);
    } catch { /* worker gone: next page */ }
  })();

  doc.addEventListener('keydown', (e) => {
    if (isToggleKey(e)) {
      e.preventDefault();
      e.stopPropagation();
      setOverlay(!isOverlayOn());
    }
  }, true);
  doc.body.append(host);
  return { host, root, button: b, arc, gear };
}

if (typeof chrome !== 'undefined' && chrome.runtime?.id && typeof location !== 'undefined' && !globalThis.__npNoAutoStart) start();
