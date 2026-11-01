// The shell the Settings and Welcome cards share: dimmed page, a centered
// card with a header bar, a scrolling body and a footer; ×, Esc and a click
// on the dimmed page close it; Tab stays inside. Same look as the Archive card
// (overlays/archive.js). Only BASE_CSS tokens, so a dark theme's page-wide
// invert still reads right (docs/extension/OVERLAY-UI.md, "Dark themes").
//
// Also the neo-plan frame both cards hold: the extension's own
// panel/neoplan.html in an <iframe>, so a Schoology page never sees the token.

import { createHost, el, onClick } from './ui.js';

export const DIALOG_CSS = `
.dim-page { position: fixed; inset: 0; z-index: 2147483001; background: rgba(31, 36, 41, 0.4); }
.card {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 2147483002;
  width: min(440px, calc(100vw - 32px)); max-height: calc(100vh - 64px);
  display: flex; flex-direction: column;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius);
  overflow: hidden;
}
.bar {
  display: flex; align-items: center; gap: 10px; flex: none;
  min-height: 48px; padding: 8px 8px 8px 14px; background: var(--sunk); border-bottom: 1px solid var(--line);
}
.bar .titles { display: flex; flex-direction: column; min-width: 0; margin-right: auto; gap: 1px; }
.bar .brand { font-family: var(--serif); font-weight: 700; font-size: 16px; }
.bar .sub { color: var(--dim); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.close {
  flex: none; width: 32px; height: 32px; border-radius: var(--radius);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 18px; line-height: 1; color: var(--dim);
}
.close:hover { color: var(--ink); background: var(--line); }
.body { flex: 1; min-height: 0; overflow: auto; padding: 14px; display: flex; flex-direction: column; gap: 16px; }
.body > * { flex: none; }
.foot { flex: none; padding: 12px; border-top: 1px solid var(--line); display: flex; gap: 8px; align-items: center; }
.primary, .secondary {
  min-height: 34px; padding: 7px 14px; border-radius: var(--radius); font-weight: 600; font-size: 13px;
}
.primary { background: var(--accent); color: var(--surface); border: 1px solid var(--accent); }
.secondary { background: var(--surface); color: var(--ink); border: 1px solid var(--line); }
.secondary:hover { border-color: var(--dim); }
.link {
  background: none; border: 0; padding: 0; font: inherit; font-weight: 600;
  color: var(--accent); text-decoration: none; cursor: pointer;
}
.link:hover, .link:focus-visible { text-decoration: underline; text-underline-offset: 2px; }
.section h4 {
  margin: 0 0 8px; font-size: 11px; font-weight: 600; color: var(--dim);
  text-transform: uppercase; letter-spacing: .03em;
}
.line { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 12px; font-size: 13px; }
.line .name { font-weight: 600; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.note { margin: 6px 0 0; font-size: 12px; color: var(--dim); }
.note.bad { color: var(--bad); }
/* Our own page: inverted along with the card under a dark theme (so it goes
   dark with it), unlike the page's real media, which theme.js re-inverts —
   the class outranks that rule's plain iframe selector. */
.npframe { display: block; width: 100%; border: 0; background: transparent; filter: none; }
@media (max-width: 600px) {
  .card { left: 8px; right: 8px; width: auto; transform: translateY(-50%); max-height: calc(100vh - 16px); }
}
`;

const FOCUSABLE_SEL = 'button:not([hidden]), a[href]:not([hidden]), input:not([hidden]), summary, iframe, [tabindex]:not([tabindex="-1"])';

/**
 * A closed dialog. `css` is added after DIALOG_CSS. Returns the pieces to fill
 * (body, foot, sub) and open()/close(); onClose(fn) runs after every close.
 */
export function createDialog(id, { title, css = '', doc = document } = {}) {
  const { host, root } = createHost(id, DIALOG_CSS + css);
  const shade = el('div', 'dim-page');
  const card = el('div', 'card');
  shade.hidden = card.hidden = true;
  card.setAttribute('role', 'dialog');
  card.setAttribute('aria-modal', 'true');
  card.setAttribute('aria-labelledby', `${id}-title`);

  const brand = el('span', 'brand', title);
  brand.id = `${id}-title`;
  const sub = el('span', 'sub');
  sub.hidden = true;
  const titles = el('div', 'titles');
  titles.append(brand, sub);
  const x = el('button', 'close', '×');
  x.type = 'button';
  x.setAttribute('aria-label', 'Close');
  x.title = 'Close (Esc)';
  const bar = el('div', 'bar');
  bar.append(titles, x);

  const body = el('div', 'body');
  const foot = el('div', 'foot');
  card.append(bar, body, foot);
  root.append(shade, card);
  doc.body.append(host);

  let isOpen = false;
  let opener = null;
  const closers = new Set();

  function open() {
    if (isOpen) return;
    isOpen = true;
    opener = doc.activeElement;
    shade.hidden = card.hidden = false;
    x.focus();
  }
  function close() {
    if (!isOpen) return;
    isOpen = false;
    shade.hidden = card.hidden = true;
    if (opener && typeof opener.focus === 'function') opener.focus();
    opener = null;
    for (const fn of closers) fn();
  }
  onClick(x, close);
  onClick(shade, close);
  shade.addEventListener('wheel', (e) => e.preventDefault(), { passive: false });
  doc.addEventListener('keydown', (e) => {
    if (!isOpen) return;
    if (e.key === 'Escape') { e.stopPropagation(); close(); return; }
    if (e.key !== 'Tab') return;
    const items = [...root.querySelectorAll(FOCUSABLE_SEL)].filter((n) => !n.disabled && n.getClientRects().length > 0);
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    const at = root.activeElement;
    if (e.shiftKey ? (at === first || !items.includes(at)) : (at === last || !items.includes(at))) {
      e.preventDefault();
      (e.shiftKey ? last : first).focus();
    }
  }, true);

  return {
    host, root, body, foot,
    setSub(text) { sub.textContent = text || ''; sub.hidden = !text; },
    open, close,
    onClose(fn) { closers.add(fn); },
    get isOpen() { return isOpen; },
  };
}

/** A height the neo-plan frame posts us, clamped to something sane. */
export function clampFrameHeight(h) {
  const n = Math.round(Number(h));
  if (!Number.isFinite(n)) return 120;
  return Math.max(40, Math.min(4000, n)); // the frame grows to fit; only the card scrolls
}

/**
 * panel/neoplan.html in an iframe (`view`: 'settings' or 'connect'). The frame
 * posts its own height; only that window, from the extension's origin, is
 * listened to. Clicks inside a cross-origin frame never reach this document.
 */
export function neoplanFrame(view, { doc = document } = {}) {
  const frame = el('iframe', 'npframe');
  frame.title = 'neo-plan';
  frame.setAttribute('scrolling', 'no');
  frame.style.height = '120px';
  try { frame.src = chrome.runtime.getURL(`panel/neoplan.html?view=${view}`); } catch { /* tests: no chrome */ }
  (doc.defaultView || globalThis).addEventListener('message', (e) => {
    if (e.source !== frame.contentWindow || e.data?.type !== 'np-frame-height') return;
    let origin = null;
    try { origin = new URL(chrome.runtime.getURL('')).origin; } catch { return; }
    if (e.origin !== origin) return;
    frame.style.height = `${clampFrameHeight(e.data.height)}px`;
  });
  return frame;
}

// ── Where archives are saved ─────────────────────────────────────────────
// The folder lives in the extension's IndexedDB, and Chrome refuses the
// folder picker in a Schoology page or a frame on one: choosing opens a small
// Dewey window (panel/folder.html) that does just that. It writes
// FOLDER_STAMP when the folder changes, so every card here redraws.

export const FOLDER_STAMP = 'archiveFolderAt';
export const DOWNLOADS_TEXT = 'Downloads › Schoology Archive';

const send = (m) => chrome.runtime.sendMessage(m);

/** { custom, name, granted } or null (worker gone). */
export async function folderInfo() {
  try {
    const r = await send({ type: 'archiveFolderInfo' });
    return r?.ok ? r : null;
  } catch { return null; }
}

/** The folder in a few words, for a card's line. */
export function folderText(info) {
  if (!info) return 'Unknown (reload the extension)';
  if (!info.custom) return DOWNLOADS_TEXT;
  return info.granted ? info.name : `${info.name} (needs allowing again)`;
}

export async function pickFolder() {
  try { return (await send({ type: 'archiveFolderPick' }))?.ok === true; } catch { return false; }
}

export async function useDownloads() {
  try { return (await send({ type: 'archiveFolderClear' }))?.ok === true; } catch { return false; }
}

/** fn() whenever the saved folder changes (from any tab or the folder window). */
export function onFolderChange(fn) {
  try {
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area === 'local' && FOLDER_STAMP in changes) fn();
    });
  } catch { /* tests */ }
}
