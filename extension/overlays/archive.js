// The Archiver's card: opened by the Archive button (overlays/switch.js) or a
// Materials page's "Sync now" (overlays/materials.js), built like
// launcher.js's neo-plan card — dimmed page, centered dialog, ×/Esc/click-
// outside closes. The job itself is not here: it runs in the offscreen
// document (background.js/offscreen/archive.js) so closing this card, or
// navigating away, never stops it. This card is only a view: it asks
// background.js for the job's state, keeps a port open for live pushes, and
// shows whatever course is actually running even if it isn't the one on this
// page — including in the Course row (a running job always wins there, even
// if the current page is a different course or no course at all).
//
// The top of the card is a library shelf (docs/extension/CATCH-UP-SPEC.md,
// "Archive card"; mockup docs/extension/design/search-archive.html, draft A):
// one upright book per class, in its class colour, taller with more in it,
// with its last-archived date on the spine and a fade as it gets older (never
// red: an old archive isn't late work). Classes on the Course Dashboard that
// haven't been archived are dashed outlines. The picked book (the page's
// class, or the one running) is pulled out and shown under the shelf; a
// running archive fills its book. Tapping another book picks that class.

import { createHost, el, onClick } from './ui.js';
import { onFolderChange } from './dialog.js';
import { classCourses, shortName, CARD_ORDER_KEY } from './todo.js';
import { OPTION_KEYS, DEFAULT_OPTIONS } from '../outputs/archive/state.js';

const OPTIONS_KEY = 'archiverOptions';
const OPT_LABELS = {
  google: 'Google Docs / Slides / Sheets linked in the course',
  submissions: 'My submissions, grades and teacher comments',
  quizzes: 'Quiz reviews (view only; never starts an attempt)',
  grades: 'Gradebook (grades.md)',
  updates: 'Announcements (updates.md)',
  redownloadAll: 'Re-check every page and re-download everything',
};

// A card that sizes to its content instead of stretching the viewport: a
// centered dialog with a fixed header and footer, and a body that scrolls
// internally once its content is taller than the available space. Only
// tokens from ui.js's BASE_CSS are used (never a hardcoded light-only
// colour) so the card still reads correctly once a dark theme inverts the
// whole page (docs/extension/OVERLAY-UI.md, "Dark themes").
const CSS = `
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
.body > * { flex: none; }
.body { flex: 1; overflow: auto; padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.row { display: flex; flex-wrap: wrap; align-items: baseline; column-gap: 8px; row-gap: 2px; }
.row .label {
  flex: none; min-width: 60px; font-size: 11px; color: var(--dim);
  text-transform: uppercase; letter-spacing: .03em;
}
.row .value { font-weight: 600; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.row .value.muted { font-weight: 400; color: var(--dim); }
.elsewhere { margin: -6px 0 0; font-size: 12px; color: var(--dim); }
.link {
  background: none; border: 0; padding: 0; font: inherit; font-weight: 600;
  color: var(--accent); text-decoration: none; cursor: pointer; min-height: 20px;
}
.link:hover, .link:focus-visible { text-decoration: underline; text-underline-offset: 2px; }
details { border: 1px solid var(--line); border-radius: var(--radius); }
details summary {
  list-style: none; cursor: pointer; display: flex; align-items: center; gap: 7px;
  min-height: 32px; padding: 7px 10px; font-weight: 600; font-size: 13px;
}
details summary::-webkit-details-marker { display: none; }
details summary::before {
  content: ""; flex: none; width: 0; height: 0;
  border-style: solid; border-width: 4px 0 4px 5px;
  border-color: transparent transparent transparent var(--dim);
  transition: transform .15s ease;
}
details[open] summary { border-bottom: 1px solid var(--line); }
details[open] summary::before { transform: rotate(90deg); }
fieldset { border: 0; padding: 10px; margin: 0; display: flex; flex-direction: column; gap: 8px; }
fieldset label { display: flex; gap: 8px; align-items: flex-start; font-weight: 400; font-size: 13px; cursor: pointer; }
fieldset input[type="checkbox"] { width: 16px; height: 16px; min-width: 16px; margin-top: 1px; accent-color: var(--accent); }
.warn-box { padding: 8px 10px; background: var(--bad-soft); border: 1px solid var(--bad); border-radius: var(--radius); }
.warn-box label { color: var(--bad); font-weight: 600; }
.warn-box input[type="checkbox"] { accent-color: var(--bad); }
.warn-hint { margin: 4px 0 0 24px; font-size: 12px; color: var(--bad); }
.empty p { margin: 0; color: var(--ink); font-size: 13px; }
.empty p + p { margin-top: 6px; color: var(--dim); font-size: 12px; }
.result { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 8px; font-size: 13px; font-weight: 600; }
.result.good { color: var(--good); }
.result.bad { color: var(--bad); }
.result.dim { color: var(--dim); }
.result .meta { font-weight: 400; color: var(--dim); font-size: 12px; }
.progress { position: relative; height: 4px; border-radius: 2px; background: var(--line); overflow: hidden; }
.progress .fill {
  position: absolute; top: 0; left: -40%; width: 40%; height: 100%;
  background: var(--accent); border-radius: 2px;
  animation: np-arc-indeterminate 1.2s ease-in-out infinite;
}
@media (prefers-reduced-motion: reduce) {
  .progress .fill { animation: none; left: 0; width: 100%; opacity: .35; }
}
@keyframes np-arc-indeterminate { 0% { left: -40%; } 100% { left: 100%; } }
.shelf { display: flex; align-items: flex-end; justify-content: center; gap: 4px; min-height: 120px; padding: 12px 12px 0; border-bottom: 6px solid #CFC6B6; overflow-x: auto; }
.spine {
  position: relative; flex: none; width: 46px; padding: 0 0 18px; border-radius: 3px 3px 1px 1px;
  background: var(--book); color: #fff; display: flex; align-items: center; justify-content: center; overflow: hidden;
  transition: transform .15s ease;
}
.spine::before, .spine::after { content: ""; position: absolute; left: 4px; right: 4px; height: 1.5px; background: rgba(255,255,255,.55); }
.spine::before { top: 7px; } .spine::after { bottom: 24px; }
.spine .name { position: relative; writing-mode: vertical-rl; transform: rotate(180deg); font-weight: 600; font-size: 12px; line-height: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-height: calc(100% - 20px); }
.spine .date { position: absolute; left: 0; right: 0; bottom: 5px; text-align: center; font-size: 11px; line-height: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.spine:hover { transform: translateY(-4px); }
.spine.sel { transform: translateY(-10px); outline: 2px solid var(--ink); outline-offset: 1px; }
.spine.ghost { padding: 0; background: none; border: 1.5px dashed var(--faint); color: var(--dim); }
.spine.ghost::before, .spine.ghost::after { display: none; }
.spine.filling { padding: 0 0 18px; background: color-mix(in srgb, var(--book) 30%, var(--surface)); color: var(--ink); border: 0; }
.spine .ink { display: none; }
.spine.filling .ink { display: block; position: absolute; left: 0; right: 0; bottom: 0; height: 45%; background: var(--book); animation: np-arc-fill 2.4s ease-in-out infinite alternate; }
@keyframes np-arc-fill { from { height: 20%; } to { height: 70%; } }
@media (prefers-reduced-motion: reduce) { .spine { transition: none; } .spine.filling .ink { animation: none; } }
.book { display: grid; grid-template-columns: 6px minmax(0, 1fr) auto; column-gap: 10px; align-items: center; }
.book .edge { grid-row: 1 / span 2; align-self: stretch; border-radius: 2px; background: var(--book); }
.book .n { font-family: var(--serif); font-weight: 700; font-size: 16px; line-height: 20px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book .m { grid-column: 2; color: var(--dim); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-variant-numeric: tabular-nums; }
.book .stamp { grid-column: 3; grid-row: 1 / span 2; color: var(--dim); font-size: 11px; line-height: 13px; font-weight: 800; text-align: center; border: 1.5px solid currentColor; border-radius: 3px; padding: 1px 5px; transform: rotate(-4deg); font-variant-numeric: tabular-nums; }
.log {
  margin: 8px 0 0; background: var(--sunk); border: 1px solid var(--line); border-radius: var(--radius);
  padding: 8px 10px; height: 200px; flex: none; overflow: auto;
  white-space: pre-wrap; overflow-wrap: anywhere;
  font-family: var(--mono); font-size: 12px; line-height: 16px;
}
.log div + div { margin-top: 2px; }
.log .warn { color: var(--bad); }
.log .error { color: var(--bad); font-weight: 600; }
.log .done { color: var(--good); font-weight: 600; }
.foot { flex: none; padding: 12px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 8px; }
.foot .hint { color: var(--bad); font-size: 12px; }
.foot .row2 { display: flex; gap: 8px; }
.primary, .stop, .secondary {
  flex: 1; min-height: 36px; padding: 8px 14px; border-radius: var(--radius); font-weight: 600; font-size: 13px;
}
.primary { background: var(--accent); color: var(--surface); border: 1px solid var(--accent); }
.primary:disabled { opacity: .5; cursor: default; }
.stop { background: var(--bad-soft); color: var(--bad); border: 1px solid var(--bad); }
.secondary { background: var(--surface); color: var(--ink); border: 1px solid var(--line); }
.secondary:hover { border-color: var(--dim); }
@media (max-width: 600px) {
  .card { left: 8px; right: 8px; width: auto; transform: translateY(-50%); max-height: calc(100vh - 16px); }
}
`;

const courseIdOf = (pathname) => (String(pathname || '').match(/^\/course\/(\d+)/) || [])[1] || null;

// chrome.storage.local is readable straight from a content script (unlike
// storage.session). Wrapped so a harness page with no `chrome` at all (tests)
// gets an empty store instead of a ReferenceError.
const localStore = {
  get: (k) => (typeof chrome !== 'undefined' ? chrome.storage.local.get(k) : Promise.resolve({})),
  set: (o) => (typeof chrome !== 'undefined' ? chrome.storage.local.set(o) : Promise.resolve()),
};

// ── The shelf (pure; test/archive.test.js) ─────────────────────────────

const DAY = 86_400_000;

/**
 * The books, left to right: the Course Dashboard's classes in order (archived
 * or not), then classes archived on this host that aren't on the dashboard
 * any more, newest first. The page's course is added if it's on neither.
 * `all` is chrome.storage.local's whole contents (manifests under
 * `course:<host>:<id>`, the dashboard order under todo.js's CARD_ORDER_KEY).
 * → [{ id, name, colorIndex, items, lastAt, stopped }]; lastAt null = not archived.
 */
export function shelfBooks({ all = {}, host = '', pageCourseId = null, pageCourseName = null } = {}) {
  const prefix = `course:${String(host).toLowerCase()}:`;
  const archived = new Map();
  for (const [k, v] of Object.entries(all || {})) {
    if (!k.startsWith(prefix) || !v || typeof v !== 'object') continue;
    const id = k.slice(prefix.length);
    const runs = Array.isArray(v.runs) ? v.runs : [];
    const last = runs.at(-1);
    archived.set(id, {
      name: shortName(v.courseName || `Course ${id}`),
      items: Object.keys(v.files || {}).length,
      lastAt: last?.at ? Date.parse(last.at) || null : null,
      stopped: !!last?.stopped,
    });
  }
  const books = [];
  const seen = new Set();
  for (const c of classCourses(Array.isArray(all?.[CARD_ORDER_KEY]) ? all[CARD_ORDER_KEY] : [])) {
    const a = archived.get(c.section_id);
    books.push({ id: c.section_id, name: c.name, colorIndex: c.colorIndex, items: a?.items || 0, lastAt: a?.lastAt ?? null, stopped: !!a?.stopped });
    seen.add(c.section_id);
  }
  const rest = [...archived].filter(([id]) => !seen.has(id)).sort((x, y) => (y[1].lastAt || 0) - (x[1].lastAt || 0));
  for (const [id, a] of rest) { books.push({ id, colorIndex: null, ...a }); seen.add(id); }
  if (pageCourseId && !seen.has(String(pageCourseId))) {
    books.push({ id: String(pageCourseId), name: shortName(pageCourseName || `Course ${pageCourseId}`), colorIndex: null, items: 0, lastAt: null, stopped: false });
  }
  return books;
}

/** Spine height in px: 96 for an empty book, up to 160 for the fullest on the shelf. */
export function spineHeight(items, maxItems) {
  if (!items || !maxItems) return 96;
  return Math.round(96 + 64 * Math.sqrt(Math.min(items, maxItems) / maxItems));
}

/** How far toward the paper an archive has faded (0–35%): none for two weeks, all of it by two months. */
export function fadeFor(lastAt, now = Date.now()) {
  if (!lastAt) return 0;
  const days = (now - lastAt) / DAY;
  return Math.round(Math.max(0, Math.min(1, (days - 14) / 46)) * 35);
}

/** "today", "yesterday", "5 days ago", "3 weeks ago", "4 months ago". */
export function agoText(lastAt, now = Date.now()) {
  const days = Math.floor((now - lastAt) / DAY);
  if (days < 1) return 'today';
  if (days < 2) return 'yesterday';
  if (days < 14) return `${days} days ago`;
  if (days < 63) return `${Math.floor(days / 7)} weeks ago`;
  return `${Math.max(2, Math.round(days / 30))} months ago`;
}

const shortDate = (t) => new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });

async function readOptions(storage) {
  const got = await storage.get(OPTIONS_KEY).catch(() => ({}));
  const saved = got?.[OPTIONS_KEY] || {};
  return { ...DEFAULT_OPTIONS, ...saved };
}

// Focusable elements inside the card, for the Tab trap. Hidden/disabled ones
// are excluded (a closed <details> already removes its own content from this
// list — no need to special-case the checkboxes).
const FOCUSABLE_SEL = 'button:not([hidden]), a[href]:not([hidden]), input:not([hidden]):not([type="hidden"]), summary, [tabindex]:not([tabindex="-1"])';

let singleton = null;

export function start({
  doc = document, loc = location,
  send = (m) => chrome.runtime.sendMessage(m),
  connect = () => chrome.runtime.connect({ name: 'archive-card' }),
  storage = localStore,
} = {}) {
  if (singleton) return singleton;

  const { host, root } = createHost('np-archive-card', CSS);
  const shade = el('div', 'dim-page');
  shade.hidden = true;
  const card = el('div', 'card');
  card.hidden = true;
  card.setAttribute('role', 'dialog');
  card.setAttribute('aria-modal', 'true');
  card.setAttribute('aria-labelledby', 'np-arc-title');

  const sub = el('span', 'sub');
  const titles = el('div', 'titles');
  titles.append(el('span', 'brand', 'Archive'), sub);
  titles.firstChild.id = 'np-arc-title';
  const close = el('button', 'close', '×');
  close.type = 'button';
  close.setAttribute('aria-label', 'Close');
  close.title = 'Close (Esc)';
  const bar = el('div', 'bar');
  bar.append(titles, close);

  const body = el('div', 'body');

  const shelf = el('div', 'shelf');
  shelf.setAttribute('role', 'group');
  shelf.setAttribute('aria-label', 'Archived classes');
  const book = el('div', 'book');
  const bookEdge = el('span', 'edge');
  const bookName = el('span', 'n');
  const bookMeta = el('span', 'm');
  bookMeta.setAttribute('role', 'status');
  const bookStamp = el('span', 'stamp');
  bookStamp.setAttribute('aria-hidden', 'true');
  book.append(bookEdge, bookName, bookMeta, bookStamp);
  const elsewhereHint = el('p', 'elsewhere');
  elsewhereHint.hidden = true;

  const folderRow = el('div', 'row');
  const folderValue = el('span', 'value');
  const changeFolder = el('button', 'link', 'Change');
  changeFolder.type = 'button';
  folderRow.append(el('span', 'label', 'Save to'), folderValue, changeFolder);

  const details = el('details');
  const summary = el('summary', null, 'Options');
  const fieldset = el('fieldset');
  const checks = {};
  for (const k of OPTION_KEYS) {
    const cb = doc.createElement('input');
    cb.type = 'checkbox';
    checks[k] = cb;
    if (k === 'redownloadAll') {
      const box = el('div', 'warn-box');
      const label = el('label');
      label.append(cb, doc.createTextNode(' ' + OPT_LABELS[k]));
      box.append(label, el('p', 'warn-hint', 'Downloads everything again, even files that have not changed. Only use this if something looks stale or missing.'));
      fieldset.append(box);
    } else {
      const label = el('label');
      label.append(cb, doc.createTextNode(' ' + OPT_LABELS[k]));
      fieldset.append(label);
    }
  }
  details.append(summary, fieldset);

  const emptyState = el('div', 'empty');
  emptyState.append(
    el('p', null, 'Open a course in Schoology, then press Archive to save its files, assignments and grades.'),
  );

  const resultBlock = el('div', 'result');
  resultBlock.hidden = true;
  const resultText = el('strong');
  const resultMeta = el('span', 'meta');
  resultBlock.append(resultText, resultMeta);


  const log = el('pre', 'log');
  log.hidden = true;
  log.setAttribute('role', 'log');
  log.setAttribute('aria-live', 'polite');
  let logJobKey = undefined;
  let logLen = 0;

  let books = [];
  let pickedId = null; // a book the student tapped; null = the page's (or the running) class

  body.append(shelf, book, elsewhereHint, resultBlock, details, folderRow, log, emptyState);

  const hint = el('p', 'hint');
  hint.hidden = true;
  const startBtn = el('button', 'primary', 'Archive this course');
  startBtn.type = 'button';
  const stopBtn = el('button', 'stop', '■ Stop now');
  stopBtn.type = 'button';
  const closeFootBtn = el('button', 'secondary', 'Close');
  closeFootBtn.type = 'button';
  const row2 = el('div', 'row2');
  row2.append(startBtn, stopBtn, closeFootBtn);
  const foot = el('div', 'foot');
  foot.append(hint, row2);

  card.append(bar, body, foot);
  root.append(shade, card);
  doc.body.append(host);

  let isOpen = false;
  let courseId = null;
  let jobState = null;
  let openerEl = null;

  function pageCourseName() {
    const name = (doc.title || '').split(' | ')[0];
    return name && !/^\d+$/.test(name) ? name : `Course ${courseId}`;
  }

  function renderLog(lines, jobKey) {
    if (jobKey !== logJobKey) {
      log.replaceChildren();
      logJobKey = jobKey;
      logLen = 0;
    }
    // Only the appended lines are added, so the aria-live region announces
    // what's new rather than re-reading the whole log on every push.
    for (let i = logLen; i < lines.length; i++) {
      const l = lines[i];
      log.append(el('div', l.level || null, l.text));
    }
    logLen = lines.length;
    log.scrollTop = log.scrollHeight;
  }

  // The class the card is about: a tapped book, else the running job's, else the page's.
  function targetId() {
    if (pickedId) return pickedId;
    if (jobState?.running && jobState.host === loc.host.toLowerCase()) return String(jobState.courseId);
    return courseId ? String(courseId) : null;
  }

  function drawShelf(target, runningHere) {
    const now = Date.now();
    const max = Math.max(0, ...books.map((b) => b.items));
    shelf.replaceChildren();
    for (const b of books) {
      const filling = runningHere && b.id === String(jobState.courseId);
      const archivedBook = b.lastAt != null;
      const spine = el('button', 'spine');
      spine.type = 'button';
      spine.classList.toggle('ghost', !archivedBook && !filling);
      spine.classList.toggle('filling', filling);
      spine.classList.toggle('sel', b.id === target);
      spine.setAttribute('aria-pressed', String(b.id === target));
      const colour = `var(--class-${b.colorIndex || 'none'})`;
      spine.style.setProperty('--book', fadeFor(b.lastAt, now) && !filling
        ? `color-mix(in srgb, ${colour} ${100 - fadeFor(b.lastAt, now)}%, var(--surface))` : colour);
      spine.style.height = `${spineHeight(b.items, max)}px`;
      spine.title = b.name;
      spine.setAttribute('aria-label', archivedBook ? `${b.name}, archived ${agoText(b.lastAt, now)}` : `${b.name}, not archived yet`);
      spine.append(el('span', 'ink'), el('span', 'name', b.name));
      if (archivedBook && !filling) spine.append(el('span', 'date', shortDate(b.lastAt)));
      onClick(spine, () => { pickedId = b.id; draw(); });
      shelf.append(spine);
    }
    shelf.hidden = !books.length;
  }

  function draw() {
    const running = !!jobState?.running;
    const finished = !!jobState && !running;
    const runningHere = running && jobState.host === loc.host.toLowerCase();
    const target = targetId();
    const b = books.find((x) => x.id === target) || null;
    const isJobCourse = !!jobState && target === String(jobState.courseId) && jobState.host === loc.host.toLowerCase();

    drawShelf(target, runningHere);

    elsewhereHint.hidden = !(running && !runningHere);
    if (running && !runningHere) elsewhereHint.textContent = `Running on ${jobState.host}.`;

    sub.textContent = running ? 'Archiving'
      : `${books.filter((x) => x.lastAt != null).length} of ${books.length} classes`;
    if (!books.length && !running) sub.textContent = '';

    // The picked book, under the shelf: two lines and the date stamp.
    book.hidden = !target;
    if (target) {
      const name = b?.name || (isJobCourse && jobState.courseName ? shortName(jobState.courseName) : (target === String(courseId) ? shortName(pageCourseName()) : `Course ${target}`));
      bookName.textContent = name;
      book.style.setProperty('--book', `var(--class-${b?.colorIndex || 'none'})`);
      if (running && isJobCourse) {
        bookMeta.textContent = [jobState.counts ? `${jobState.counts} items` : null, jobState.status || 'Archiving…'].filter(Boolean).join(' · ');
        bookStamp.hidden = true;
      } else if (b?.lastAt != null) {
        bookMeta.textContent = `${b.items} items · archived ${agoText(b.lastAt)}${b.stopped ? ' · stopped' : ''}`;
        const [mon, day] = shortDate(b.lastAt).split(' ');
        bookStamp.replaceChildren(doc.createTextNode(mon.toUpperCase()), el('br'), doc.createTextNode(day));
        bookStamp.hidden = false;
      } else {
        bookMeta.textContent = 'Not archived yet';
        bookStamp.hidden = true;
      }
    }

    resultBlock.hidden = !(finished && isJobCourse);
    if (finished && isJobCourse) {
      const kind = jobState.error ? 'bad' : jobState.stopped ? 'dim' : 'good';
      resultBlock.className = `result ${kind}`;
      const icon = jobState.error ? '✕' : jobState.stopped ? '■' : '✓';
      resultText.textContent = jobState.error ? `${icon} ${jobState.error}` : `${icon} ${jobState.stopped ? 'Stopped.' : (jobState.summary || 'Done.')}`;
      resultMeta.textContent = jobState.counts ? `${jobState.counts} items` : '';
    }

    details.hidden = running || !target;
    log.hidden = !((running && isJobCourse) || (finished && isJobCourse));
    if (!log.hidden) renderLog(jobState.log || [], jobState.jobId);

    emptyState.hidden = !!target || running || books.length > 0;

    // Footer: Stop while running; otherwise archive the picked class.
    stopBtn.hidden = !running;
    stopBtn.disabled = false;
    startBtn.hidden = running || !target;
    startBtn.disabled = false;
    startBtn.textContent = b?.lastAt != null || (finished && isJobCourse) ? 'Archive again' : 'Archive this course';
    closeFootBtn.hidden = !(finished && isJobCourse) || running;
    foot.hidden = !(running || target);
  }

  async function refreshFolder() {
    let r = null;
    try { r = await send({ type: 'archiveFolderInfo' }); } catch { /* worker gone */ }
    if (!r?.ok) { folderValue.textContent = 'Unknown (reload the extension)'; return; }
    folderValue.textContent = r.custom
      ? r.name + (r.granted ? '' : ' (permission lost — using Downloads)')
      : 'Downloads › Schoology Archive';
    changeFolder.textContent = r.custom && !r.granted ? 'Allow again' : 'Change';
  }

  async function refreshShelf() {
    const all = await storage.get(null).catch(() => ({}));
    books = shelfBooks({ all, host: loc.host, pageCourseId: courseId, pageCourseName: courseId ? pageCourseName() : null });
    draw();
  }

  function getFocusable() {
    return Array.from(root.querySelectorAll(FOCUSABLE_SEL)).filter((e) => !e.disabled && e.getClientRects().length > 0);
  }

  async function open() {
    courseId = courseIdOf(loc.pathname);
    pickedId = null;
    const saved = await readOptions(storage);
    for (const k of OPTION_KEYS) checks[k].checked = saved[k];
    draw();
    refreshFolder();
    refreshShelf();
    if (!isOpen) {
      isOpen = true;
      openerEl = doc.activeElement;
      shade.hidden = false;
      card.hidden = false;
      close.focus();
    }
  }

  function shut() {
    if (!isOpen) return;
    isOpen = false;
    shade.hidden = true;
    card.hidden = true;
    if (openerEl && typeof openerEl.focus === 'function') openerEl.focus();
    openerEl = null;
  }

  function currentOptions() {
    const out = {};
    for (const k of OPTION_KEYS) out[k] = checks[k].checked;
    return out;
  }

  async function startNow() {
    const id = targetId();
    if (!id) return;
    const options = currentOptions();
    storage.set({ [OPTIONS_KEY]: options }).catch(() => {});
    startBtn.disabled = true;
    hint.hidden = true;
    hint.textContent = '';
    let r = null;
    try { r = await send({ type: 'archiveStart', courseId: id, options }); } catch { /* worker gone */ }
    if (!r?.ok) {
      hint.textContent = r?.error === 'busy'
        ? 'An archive is already running. Stop it first.'
        : r?.error === 'quiz'
          ? 'A quiz is open in another tab. Finish it first.'
          : 'Reload the extension, then this page.';
      hint.hidden = false;
      draw();
    }
  }
  onClick(startBtn, startNow);
  onClick(stopBtn, async () => {
    stopBtn.disabled = true;
    try { await send({ type: 'archiveStop' }); } catch { /* worker gone */ }
  });
  onClick(closeFootBtn, shut);
  // Chooses in a small Dewey window (the picker is refused on this page);
  // it stamps FOLDER_STAMP when done, and the row redraws.
  onClick(changeFolder, async () => {
    try { await send({ type: 'archiveFolderPick' }); } catch { /* worker gone */ }
  });
  onFolderChange(() => { if (isOpen) refreshFolder(); });
  onClick(close, shut);
  onClick(shade, shut);
  shade.addEventListener('wheel', (e) => e.preventDefault(), { passive: false });
  doc.addEventListener('keydown', (e) => {
    if (!isOpen) return;
    if (e.key === 'Escape') { e.stopPropagation(); shut(); return; }
    if (e.key === 'Tab') {
      const items = getFocusable();
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      const activeInHost = root.activeElement;
      if (e.shiftKey) {
        if (activeInHost === first || !items.includes(activeInHost)) { e.preventDefault(); last.focus(); }
      } else if (activeInHost === last || !items.includes(activeInHost)) {
        e.preventDefault(); first.focus();
      }
    }
  }, true);

  // A live view onto the job wherever it's actually running: a port push,
  // not polling — the offscreen job keeps going whether or not this card, or
  // even this tab, is open.
  let port = null;
  function connectPort() {
    try {
      port = connect();
      port.onMessage.addListener((msg) => {
        if (msg?.type !== 'archiveState') return;
        const wasRunning = !!jobState?.running;
        jobState = msg.state;
        // A job that just ended wrote its manifest: the shelf's book changes.
        if (isOpen) { if (wasRunning && !jobState?.running) refreshShelf(); else draw(); }
      });
      port.onDisconnect.addListener(() => { port = null; setTimeout(connectPort, 1000); });
    } catch { /* no chrome: tests */ }
  }
  connectPort();

  singleton = { host, root, open, startNow, close: shut, get isOpen() { return isOpen; } };
  return singleton;
}

/** For tests: drop the cached card so a fresh start() builds a new one. */
export function resetForTests() { singleton = null; }
