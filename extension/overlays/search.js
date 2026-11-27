// Search all materials: a box at the top of the To Do sidebar on Home
// (overlays/todo.js) and above the Materials list on a course's Materials page
// (overlays/materials.js), that finds a file, page, folder or assignment by
// name across every class's whole Materials tree, the page's class first.
// docs/extension/CATCH-UP-SPEC.md ("Search all materials"), mockup
// docs/extension/design/search-archive.html.
//
// While there's a query the results cover the rest of the panel
// (`.panel.searching`; on Materials, `onSearching` hides Schoology's list too), grouped by class in Course Dashboard order, with the
// class of the page you're on first. Two lines each: the name, then where it
// is. "/" focuses the box, ↑↓ move, Enter opens, Esc (or ×) gives the To Do back.
//
// Data: the same walk as the MCP's find_material (mcp/src/materialwalk.js),
// done here with the page's own fetch through the shared limiter: each class's
// /course/{id}/materials, then every folder under it (breadth first, at most
// MAX_FOLDERS per class), parsed by reader/parse/mcp.js's materialsFull. A walk
// starts the first time the box gets focus, not on page load, and is kept in
// chrome.storage.local for WALK_TTL_MS so the next page doesn't walk again.

import { el } from './ui.js';
import { limitedFetch } from '../reader/ratelimit.js';
import { parseMcpKind } from '../reader/parse/mcp.js';

export const WALK_TTL_MS = 10 * 60 * 1000;
export const WALK_CACHE_KEY = 'materialsWalkCache';
const MAX_FOLDERS = 200;
const MAX_DEPTH = 6;
const CONCURRENCY = 3;
const MAX_SHOWN = 8; // per class, before "N more"

export const SEARCH_CSS = `
.sbox { display: flex; align-items: center; gap: 6px; height: 32px; margin: 0 0 12px; padding: 0 4px 0 8px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); }
.sbox:focus-within { border-color: var(--accent); }
.sbox svg { width: 14px; height: 14px; flex: none; fill: none; stroke: var(--dim); stroke-width: 2; stroke-linecap: round; }
.sbox input { flex: 1; min-width: 0; height: 30px; border: 0; outline: 0; background: none; padding: 0; font: 13px/18px var(--sans); color: var(--ink); }
.sbox input::placeholder { color: var(--faint); }
.sbox input::-webkit-search-cancel-button { display: none; }
.sbox .key { flex: none; font-size: 11px; line-height: 16px; color: var(--faint); border: 1px solid var(--line); border-radius: 4px; padding: 0 4px; margin-right: 4px; }
.sbox .clear { flex: none; width: 24px; height: 24px; border-radius: var(--radius-sm); color: var(--dim); font-size: 16px; line-height: 1; }
.sbox .clear:hover { background: var(--sunk); color: var(--ink); }
.sresults { display: none; }
.panel.searching > :not(.sbox):not(.sresults) { display: none; }
.panel.searching > .sresults { display: block; }
.scount { color: var(--dim); font-size: 12px; margin: -4px 0 8px; }
.sbusy { position: relative; height: 3px; margin: -6px 0 10px; border-radius: 2px; background: var(--line); overflow: hidden; }
.sbusy::after { content: ""; position: absolute; top: 0; bottom: 0; left: -40%; width: 40%; background: var(--accent); animation: np-sbusy 1.2s ease-in-out infinite; }
@keyframes np-sbusy { to { left: 100%; } }
@media (prefers-reduced-motion: reduce) { .sbusy::after { animation: none; left: 0; width: 100%; opacity: .35; } }
.sgroup + .sgroup { margin-top: 12px; }
.sgroup .ghead { display: flex; align-items: center; gap: 8px; padding: 2px 0 4px; border-bottom: 2px solid var(--cls, var(--line)); }
.sgroup .gname { font-weight: 600; }
.sgroup .dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.sres { display: grid; grid-template-columns: 16px minmax(0, 1fr); gap: 8px; padding: 6px; margin: 0 -6px; border-radius: var(--radius-sm); color: var(--ink); text-decoration: none; }
.sres:hover, .sres.sel { background: var(--accent-soft); }
.sres svg { width: 14px; height: 14px; margin-top: 2px; fill: none; stroke: var(--dim); stroke-width: 1.8; stroke-linejoin: round; stroke-linecap: round; }
.sres .t { display: block; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sres .t mark { background: none; color: var(--accent); font-weight: 600; }
.sres .p { display: block; color: var(--dim); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.smore { display: block; margin-top: 2px; color: var(--accent); font-size: 12px; font-weight: 600; }
.sempty { padding: 14px 0 6px; text-align: center; }
.sempty b { display: block; font-weight: 600; }
.sempty span { color: var(--dim); font-size: 12px; }
`;

const ICONS = {
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  document: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
  page: '<rect x="5" y="4" width="14" height="16" rx="1"/><path d="M8 9h8M8 13h8M8 17h5"/>',
  assignment: '<rect x="5" y="4" width="14" height="17" rx="1"/><path d="M9 12l2 2 4-4"/>',
  quiz: '<circle cx="12" cy="12" r="8"/><path d="M10 10a2 2 0 1 1 3 1.7c-.6.4-1 .8-1 1.3M12 16h.01"/>',
  discussion: '<path d="M4 5h16v11H9l-5 4z"/>',
  folder: '<path d="M3 6h6l2 2h10v11H3z"/>',
  other: '<path d="M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1"/><path d="M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"/>',
};
function icon(kind) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.innerHTML = ICONS[kind] || ICONS.other; // our own constant markup, never page text
  return svg;
}

// ── Pure helpers (test/search.test.js) ──────────────────────────────────────

const words = (q) => String(q || '').toLowerCase().split(/\s+/).filter(Boolean);
const escape = (w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// A query word matches at the start of a word ("lab" finds "Labs", not "Syllabus"; "6" finds "Lab 6", not "2026").
const wordStart = (w) => new RegExp(`(^|[^\\p{L}\\p{N}])${escape(w)}`, 'u');
const has = (text, w) => wordStart(w).test(String(text || '').toLowerCase());

/** Every word of `query` starts a word in the row's title or its folder path. */
export function matchesQuery(row, query) {
  const w = words(query);
  if (!w.length) return false;
  const hay = `${row.title || ''} ${row.path || ''}`;
  return w.every((x) => has(hay, x));
}

/** The title split into [text, isMatch] runs, for <mark>ing the query's words. */
export function highlight(title, query) {
  const t = String(title || '');
  const w = words(query);
  if (!w.length) return [[t, false]];
  const lower = t.toLowerCase();
  const hit = new Array(t.length).fill(false);
  for (const x of w) {
    const re = new RegExp(`(^|[^\\p{L}\\p{N}])(${escape(x)})`, 'gu');
    for (const m of lower.matchAll(re)) { const at = m.index + m[1].length; hit.fill(true, at, at + x.length); }
  }
  const out = [];
  for (let i = 0; i < t.length;) {
    let j = i;
    while (j < t.length && hit[j] === hit[i]) j++;
    out.push([t.slice(i, j), hit[i]]);
    i = j;
  }
  return out;
}

/** Courses in dashboard order, with the page's own class moved to the front. */
export function orderCourses(courses, currentSection) {
  if (!currentSection) return courses.slice();
  const i = courses.findIndex((c) => String(c.section_id) === String(currentSection));
  if (i <= 0) return courses.slice();
  return [courses[i], ...courses.slice(0, i), ...courses.slice(i + 1)];
}

/**
 * Hits per course, in `courses`' order: [{ course, hits }] for courses with
 * at least one hit. A title match sorts before a folder-path-only match;
 * otherwise the walk's order (top of the tree first) stands.
 */
export function searchWalks(courses, walks, query) {
  const out = [];
  for (const c of courses) {
    const w = walks.get(String(c.section_id));
    if (!w?.rows) continue;
    const hits = w.rows.filter((r) => matchesQuery(r, query));
    if (!hits.length) continue;
    const inTitle = (r) => words(query).every((x) => has(r.title, x));
    hits.sort((a, b) => Number(inTitle(b)) - Number(inTitle(a)));
    out.push({ course: c, hits });
  }
  return out;
}

/** "Unit 4 / Labs" → "Unit 4 › Labs"; the top of the tree is "Materials". */
export function placeOf(row) {
  const path = String(row.path || '').split(' / ').filter(Boolean).join(' › ');
  return path || 'Materials';
}

// ── The walk ────────────────────────────────────────────────────────────────

async function listFolder(sectionId, folderId, fetchImpl) {
  const url = `/course/${sectionId}/materials${folderId ? `?f=${encodeURIComponent(folderId)}` : ''}`;
  const r = await fetchImpl(url, { credentials: 'include' });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  const parsed = parseMcpKind('materialsFull', await r.text(), r.url || url);
  if (parsed.login) throw new Error('signed out');
  return parsed.data || [];
}

/** One class's whole Materials tree: { rows: [{ kind, title, id, url, path }], truncated }. */
export async function walkCourse(sectionId, { fetchImpl = limitedFetch } = {}) {
  const rows = [];
  let folders = 0;
  let truncated = false;
  let level = [{ id: null, path: '', d: 1 }];
  let first = true;
  while (level.length) {
    const next = [];
    for (let i = 0; i < level.length; i += CONCURRENCY) {
      const batch = level.slice(i, i + CONCURRENCY);
      const results = await Promise.allSettled(batch.map((f) => listFolder(sectionId, f.id, fetchImpl)));
      results.forEach((r, j) => {
        const f = batch[j];
        if (r.status === 'rejected') { if (first) throw r.reason; return; }
        folders++;
        for (const row of r.value) {
          rows.push({ kind: row.kind, title: row.title, id: row.id, url: row.url, path: f.path });
          if (row.kind !== 'folder' || !row.id || f.d >= MAX_DEPTH) continue;
          if (folders + next.length >= MAX_FOLDERS) { truncated = true; continue; }
          next.push({ id: row.id, path: f.path ? `${f.path} / ${row.title}` : row.title, d: f.d + 1 });
        }
      });
      first = false;
    }
    level = next;
  }
  return { rows, truncated };
}

/**
 * The busy line. On a class page it names that class until it's read, then
 * "your other classes"; on Home, how many classes.
 */
export function lookingText(classes, walks, currentSection, groups = []) {
  const here = currentSection && classes.find((c) => String(c.section_id) === String(currentSection));
  if (!here) return `Looking through ${classes.length} ${classes.length === 1 ? 'class' : 'classes'}`;
  if (!walks.has(String(here.section_id))) return `Looking through ${here.name}`;
  const hereHits = groups.some((g) => String(g.course.section_id) === String(here.section_id));
  return hereHits ? 'Looking through your other classes' : `Nothing in ${here.name} · looking through your other classes`;
}

// ── The box ─────────────────────────────────────────────────────────────────

/**
 * One search per sidebar. `courses()` → [{ section_id, name, colorIndex }]
 * (classes only, dashboard order). The sidebar re-renders its panel often, so
 * it calls `attach(panel)` each time: the same box and results go back in at
 * the top and the bottom, with the query, results and focus kept.
 */
export function createSearch({
  doc = document, loc = location, courses = () => [], currentSection = null, currentName = null,
  walk = walkCourse, store = null, now = () => Date.now(), onSearching = () => {},
} = {}) {
  const box = el('label', 'sbox');
  box.append(icon('search'));
  const input = el('input');
  input.type = 'search';
  input.placeholder = 'Search all materials';
  input.setAttribute('aria-label', 'Search all materials');
  input.autocomplete = 'off';
  input.spellcheck = false;
  const key = el('span', 'key', '/');
  key.setAttribute('aria-hidden', 'true');
  const clear = el('button', 'clear', '×');
  clear.type = 'button';
  clear.setAttribute('aria-label', 'Clear search');
  clear.hidden = true;
  box.append(input, key, clear);

  const results = el('div', 'sresults');
  results.setAttribute('role', 'region');
  results.setAttribute('aria-label', 'Search results');
  results.setAttribute('aria-live', 'polite');

  let panel = null;
  let refocus = false;
  let wasOn = false;
  // The classes to look through, the page's first. A class page is searched even
  // when Home was never seen (no dashboard list yet).
  function list() {
    const all = orderCourses(courses(), currentSection);
    if (currentSection && !all.some((c) => String(c.section_id) === String(currentSection))) {
      all.unshift({ section_id: String(currentSection), name: currentName || 'This class', colorIndex: null });
    }
    return all;
  }
  const walks = new Map(); // section id → { rows } | { error }
  let walking = null;
  let sel = 0;
  let links = [];
  const expanded = new Set();

  async function loadCache() {
    if (!store) return;
    try {
      const c = await store.get(WALK_CACHE_KEY);
      if (!c || c.host !== loc.host || now() - c.at > WALK_TTL_MS) return;
      for (const [id, w] of Object.entries(c.walks || {})) if (!walks.has(id)) walks.set(id, w);
    } catch { /* no cache */ }
  }
  function saveCache() {
    if (!store) return;
    const out = {};
    for (const [id, w] of walks) if (w.rows) out[id] = w;
    store.set(WALK_CACHE_KEY, { at: now(), host: loc.host, walks: out })?.catch?.(() => {});
  }

  function startWalk() {
    if (walking) return walking;
    walking = (async () => {
      await loadCache();
      const todo = list().filter((c) => !walks.has(String(c.section_id)));
      draw();
      // One class at a time, the page's own first; the limiter paces requests.
      for (const c of todo) {
        try { walks.set(String(c.section_id), await walk(c.section_id)); } catch (e) { walks.set(String(c.section_id), { error: String(e?.message || e) }); }
        draw();
      }
      if (todo.length) saveCache();
    })().finally(() => { walking = null; draw(); });
    return walking;
  }

  function done() {
    return list().every((c) => walks.has(String(c.section_id)));
  }

  function draw() {
    const q = input.value.trim();
    const on = !!q;
    panel?.classList.toggle('searching', on);
    if (on !== wasOn) { wasOn = on; try { onSearching(on); } catch { /* host gone */ } }
    clear.hidden = !on;
    key.hidden = on;
    results.replaceChildren();
    links = [];
    if (!on) return;
    const classes = list();
    const groups = searchWalks(classes, walks, q);
    const found = groups.reduce((n, g) => n + g.hits.length, 0);
    const busy = !done();
    if (busy) {
      const b = el('div', 'sbusy');
      b.setAttribute('aria-hidden', 'true');
      results.append(b);
    }
    if (busy) results.append(el('div', 'scount', lookingText(classes, walks, currentSection, groups)));
    else if (found) results.append(el('div', 'scount', `${found} found in ${groups.length} ${groups.length === 1 ? 'class' : 'classes'}`));
    for (const { course, hits } of groups) {
      const g = el('div', 'sgroup');
      const head = el('div', 'ghead');
      head.style.setProperty('--cls', `var(--class-${course.colorIndex || 'none'})`);
      const dot = el('span', 'dot');
      dot.style.background = 'var(--cls)';
      head.append(dot, el('span', 'gname', course.name));
      g.append(head);
      const open = expanded.has(String(course.section_id));
      for (const r of open ? hits : hits.slice(0, MAX_SHOWN)) {
        const a = el('a', 'sres');
        if (r.url) a.href = r.url;
        a.append(icon(r.kind));
        const text = el('span');
        const t = el('span', 't');
        for (const [s, m] of highlight(r.title, q)) t.append(m ? el('mark', null, s) : doc.createTextNode(s));
        text.append(t, el('span', 'p', placeOf(r)));
        a.append(text);
        a.addEventListener('mousemove', () => { if (sel !== links.indexOf(a)) { sel = links.indexOf(a); mark(); } });
        g.append(a);
        links.push(a);
      }
      if (!open && hits.length > MAX_SHOWN) {
        const more = el('button', 'smore', `${hits.length - MAX_SHOWN} more`);
        more.type = 'button';
        more.addEventListener('click', () => { expanded.add(String(course.section_id)); draw(); });
        g.append(more);
      }
      results.append(g);
    }
    if (!busy && !found) {
      const e = el('div', 'sempty');
      e.append(el('b', null, 'Nothing called that'), el('span', null, `in ${classes.length} ${classes.length === 1 ? 'class’s' : 'classes’'} Materials`));
      results.append(e);
    }
    if (sel >= links.length) sel = 0;
    mark();
  }

  function mark() {
    links.forEach((a, i) => a.classList.toggle('sel', i === sel));
  }

  function reset() {
    input.value = '';
    sel = 0;
    expanded.clear();
    draw();
  }

  input.addEventListener('focus', () => { startWalk(); });
  input.addEventListener('input', () => { sel = 0; expanded.clear(); draw(); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { e.preventDefault(); if (input.value) reset(); else input.blur(); return; }
    if (!links.length) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); sel = (sel + 1) % links.length; mark(); links[sel].scrollIntoView?.({ block: 'nearest' }); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); sel = (sel - 1 + links.length) % links.length; mark(); links[sel].scrollIntoView?.({ block: 'nearest' }); }
    else if (e.key === 'Enter' && links[sel]?.href) { e.preventDefault(); if (e.metaKey || e.ctrlKey) window.open(links[sel].href, '_blank'); else loc.assign(links[sel].href); }
  });
  clear.addEventListener('click', (e) => { e.preventDefault(); reset(); input.focus(); });

  // "/" anywhere on the page (not while typing somewhere) jumps to the box.
  const onSlash = (e) => {
    if (e.key !== '/' || e.metaKey || e.ctrlKey || e.altKey) return;
    const t = e.composedPath?.()[0] || e.target;
    if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName || ''))) return;
    if (!input.isConnected || input.getClientRects().length === 0) return;
    e.preventDefault();
    input.focus();
  };
  try { doc.addEventListener('keydown', onSlash); } catch { /* tests */ }

  return {
    attach(p) {
      // Moving the box into a new panel drops its focus; mounted() puts it back.
      refocus = input.getRootNode?.()?.activeElement === input;
      panel = p;
      p.prepend(box);
      p.append(results);
      p.classList.toggle('searching', !!input.value.trim());
    },
    mounted() {
      if (!refocus) return;
      refocus = false;
      const at = input.selectionStart;
      input.focus({ preventScroll: true });
      try { input.setSelectionRange(at, at); } catch { /* type=search */ }
    },
    query: (q) => { input.value = q; draw(); return startWalk(); },
    stop: () => { try { doc.removeEventListener('keydown', onSlash); } catch { /* tests */ } },
  };
}
