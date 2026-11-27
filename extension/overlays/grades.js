// The grades page, /course/{id}/student_grades (docs/extension/OVERLAY-UI.md §1), and
// each course on /grades/grades, inline
// in Schoology's own table. Schoology's rows, grades and buttons stay as they
// are; this only adds, and only what the page doesn't already show:
//   class card     a block of ours just above the class's table, sticky while
//                  that table scrolls by (it lets go at the next class): the
//                  grade large with its letter, "X.XX above [Goal A]" (the
//                  grade to aim for, A down to C-, saved per course; the A
//                  line until picked: every "A" below is that goal's letter),
//                  Reset N while anything is tried, and Graph (a step chart
//                  of the replayed term under the card; closed on
//                  /grades/grades). While scores are tried the big number is
//                  the what-if one, the real one before it ("93.22 →")
//   category rows  "A holds down to NN.N%", "→ NN.N%" when a tried score
//                  moves it, "+ Upcoming test" (or assignment: a row of our
//                  own under the category, name / worth / score / "need X
//                  for an A"); a weight box only when Schoology shows no (N%)
//   graded items   a small percent beside Schoology's score (Settings: on by
//                  default) and the impact tag; "not counted" on an item
//                  Schoology leaves out of the category (grademath.calibrate)
// Trying a score has no mode (gunn.one, not the iOS app): click any score,
// or a "—", and a box opens in its cell; typing moves every number at once.
// Enter or clicking away keeps it ("→ 12" in the accent, beside Schoology's
// own score), Esc puts the real one back. Beside the box, "Leave out" drops
// the item (Schoology's score struck through, "Undo" to count it again). A changed
// row gets a thin inset left bar. The right-hand column is a <td> of ours
// appended to every row; it, the card and our own rows vanish with the
// Overlay switch like every host. Schoology hides its Course Grade row, so
// the course-level additions sit on the current period's row then. Tried
// scores, left-out items and planned items live only in this page view;
// typed weights are saved per course, and so are the student's own marks on
// what counts: "Never counts" beside "Undo" on a left-out item; a click
// on "not counted" / "may not count" says it does count. A category whose items left out as marked
// give Schoology's percent gets a faint ✓; one that doesn't add up, a ≈.
//
// Only the current grading period (grademath.currentPeriod) is annotated.

import { parseGrades } from '../reader/parse/grades.js';
import {
  periodsFromRows, calibrate, currentPeriod, grade, impact, history, categoryFloor, needFor, letter, statedPct, ticks, A_CUTOFF, GOALS,
} from './grademath.js';
import {
  createHost, el, onOverlay, isOverlayOn, debounce, keepEvents, getSettings, onSettings,
} from './ui.js';
import { loadWeights, saveWeights, loadMarks, saveMarks, loadGoal, saveGoal, loadSeenGrades, saveSeenGrades } from './coursedata.js';
import { freshGrades } from './newgrades.js';
import { THEMES, themeOf, paint } from './theme.js';

const EPS = 0.005;

const CSS = `
.imp {
  display: inline-block; min-width: 44px; padding: 0 5px; border-radius: var(--radius-sm); text-align: center;
  font: 500 11px/16px var(--sans); font-variant-numeric: tabular-nums;
}
.imp.pos { color: var(--good); background: var(--good-soft); }
.imp.neg { color: var(--bad); background: var(--bad-soft); }
.pct { font-family: var(--sans); font-size: 11px; color: var(--faint); font-variant-numeric: tabular-nums; }
.acc { color: var(--accent); }
.note { font-size: 12px; color: var(--accent); white-space: nowrap; }
.note.warn { color: var(--bad); }
.note.quiet, .linkbtn.quiet { color: var(--faint); }
.num { font-family: var(--sans); font-variant-numeric: tabular-nums; font-weight: 500; }
.linkbtn { color: var(--accent); font-size: 12px; text-decoration: underline; text-underline-offset: 2px; }
input {
  font: 12px/16px var(--sans); font-variant-numeric: tabular-nums; color: var(--ink);
  padding: 1px 5px; margin: 0; border: 1px dashed var(--accent); border-radius: var(--radius-sm);
  background: var(--surface); text-align: right;
}
input::placeholder { color: var(--faint); opacity: 1; font-family: var(--sans); }
input:not(:placeholder-shown) { border-style: solid; background: var(--accent-soft); }
input[type=number] { -moz-appearance: textfield; }
input::-webkit-outer-spin-button, input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.wrap { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--dim); }
`;

const CELL_CSS = CSS + `
:host { display: block; padding: 0 8px; text-align: right; }
.note { white-space: normal; }
.num, .imp { white-space: nowrap; }
`;

const INLINE_CSS = CSS + `
.new { font: 600 11px/16px var(--sans); color: var(--accent); background: var(--accent-soft); border-radius: 999px; padding: 0 6px; white-space: nowrap; }
:host { display: inline-flex; align-items: center; gap: 6px; margin-left: 8px; vertical-align: middle; }
:host([hidden]) { display: none !important; }
.wi-pts { width: 44px; }
.wi-val {
  color: var(--accent); font: 500 12px/16px var(--sans); font-variant-numeric: tabular-nums;
  padding: 0 3px; border-radius: var(--radius-sm);
}
.wi-val:hover { background: var(--accent-soft); }
.wi-score { width: 56px; }
.w { width: 44px; }
`;

const ROW_CSS = CSS + `
:host { display: block; }
.hypo { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; font-size: 12px; color: var(--dim); }
.hypo input[type=text] { width: 160px; text-align: left; font-family: var(--sans); }
.hypo input[type=number] { width: 56px; }
.need b { font-weight: 500; font-variant-numeric: tabular-nums; }
.chart { position: relative; max-width: 640px; }
.chart svg { display: block; width: 100%; height: auto; overflow: visible; }
.chart-note { display: flex; flex-wrap: wrap; gap: 4px 20px; font-size: 12px; color: var(--dim); margin-top: 8px; }
.tip {
  position: absolute; pointer-events: none; transform: translate(-50%, -100%); margin-top: -10px;
  background: var(--ink); color: #fff; font-size: 12px; line-height: 1.4; padding: 6px 8px;
  border-radius: var(--radius-sm); white-space: nowrap;
}
.tip b { font-weight: 500; font-variant-numeric: tabular-nums; }
.tip .s { opacity: .7; }
`;

// The class card, sticky above the table, and the graph under it.
const CARD_CSS = ROW_CSS + `
:host { display: block; }
.card {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px 20px; padding: 10px 14px;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius);
  font-family: var(--sans); color: var(--ink); transition: background-color .15s, box-shadow .15s;
}
.card.stuck { box-shadow: 0 4px 12px rgba(31, 36, 41, .12); }
.card.changed { background: var(--accent-soft); }
.big { display: flex; align-items: baseline; gap: 8px; font-variant-numeric: tabular-nums; }
.big .was { color: var(--faint); font-size: 15px; line-height: 20px; }
.big .n { font-size: 28px; line-height: 32px; font-weight: 600; }
.big .n small { font-size: 18px; font-weight: 500; }
.big .let { font-size: 15px; line-height: 20px; font-weight: 600; color: var(--dim); }
.changed .big .n, .changed .big .let { color: var(--accent); }
.gap { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 13px; line-height: 18px; color: var(--dim); }
.gap .num { color: var(--ink); font-weight: 600; }
.gap .note { font-size: 13px; }
.end { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.pill {
  display: inline-flex; align-items: center; gap: 6px; height: 28px; padding: 0 12px; border-radius: 999px;
  border: 1px solid var(--line); background: var(--surface); color: var(--dim); font-size: 13px; font-weight: 500; white-space: nowrap;
}
.pill:hover { color: var(--ink); border-color: var(--faint); }
.pill.on, .pill.accent { border-color: var(--accent); color: var(--accent); }
.pill svg { display: block; }
select.goal {
  font: 500 12px/16px var(--sans); color: var(--dim); background: var(--surface); margin: 0;
  height: 26px; padding: 0 4px 0 8px; border: 1px solid var(--line); border-radius: 999px; cursor: pointer;
}
select.goal:hover { color: var(--ink); border-color: var(--faint); }
.chartwrap { padding: 4px 4px 8px; }
`;

// "N new" after a class's name on /grades/grades.
const PILL_CSS = CSS + `
.new { font: 600 11px/16px var(--sans); color: var(--accent); background: var(--accent-soft); border-radius: 999px; padding: 0 6px; white-space: nowrap; }
`;

// ── Small helpers ────────────────────────────────────────────────────────

const fmt = (v, d = 2) => (v == null ? '' : v.toFixed(d));
const signed = (d) => (d < 0 ? '−' : '+') + Math.abs(d).toFixed(2) + '%';
const num = (v) => { const n = parseFloat(v); return Number.isFinite(n) ? n : null; };
const dayNum = (day) => Date.UTC(+day.slice(0, 4), +day.slice(5, 7) - 1, +day.slice(8, 10)) / 864e5;
const dayLabel = (day) => new Date(dayNum(day) * 864e5).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const isShown = (node) => node.getClientRects().length > 0;

/** "90" → "90%"; "93.28" → "93.3%". */
function pctText(p) {
  const r = Math.round(p * 10) / 10;
  return `${Number.isInteger(r) ? r : r.toFixed(1)}%`;
}

function impTag(d, title) {
  if (d == null || Math.abs(d) < EPS) return null;
  const t = el('span', `imp ${d < 0 ? 'neg' : 'pos'}`, signed(d));
  if (title) t.title = title;
  return t;
}

function input(type, cls, label, placeholder) {
  const i = el('input', cls);
  i.type = type;
  if (type === 'number') { i.min = '0'; i.step = 'any'; i.inputMode = 'decimal'; }
  i.setAttribute('aria-label', label);
  if (placeholder) i.placeholder = placeholder;
  return i;
}

// ── The chart ────────────────────────────────────────────────────────────

/** History points with a day to plot at (undated ones plot at today). */
function plotPoints(hist) {
  const t = today();
  const pts = [];
  for (const p of hist) {
    if (!Number.isFinite(p.pct)) continue;
    const day = p.day || t;
    const last = pts[pts.length - 1];
    if (last && last.day === day) { last.pct = p.pct; last.items.push(...p.items); } else pts.push({ day, pct: p.pct, items: [...p.items] });
  }
  return pts;
}

function sparkSvg(pts) {
  const w = 56, h = 16;
  const x0 = dayNum(pts[0].day), x1 = Math.max(dayNum(pts[pts.length - 1].day), x0 + 1);
  const lo = Math.min(...pts.map((p) => p.pct)), hi = Math.max(100, ...pts.map((p) => p.pct));
  const X = (d) => 2 + ((dayNum(d) - x0) / (x1 - x0)) * (w - 6);
  const Y = (v) => 2 + ((hi - v) / (hi - lo || 1)) * (h - 4);
  let d = `M${X(pts[0].day)},${Y(pts[0].pct)}`;
  for (const p of pts.slice(1)) d += ` H${X(p.day)} V${Y(p.pct)}`;
  const l = pts[pts.length - 1];
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><path d="${d}" fill="none" stroke="var(--accent)" stroke-width="1.5"/><circle cx="${X(l.day)}" cy="${Y(l.pct)}" r="2.5" fill="var(--accent)"/></svg>`;
}

/** The step chart into `box`, with a dashed line to `proj` when a what-if is set. */
function drawChart(box, pts, proj, goal = A_CUTOFF) {
  const w = 600, h = 200, m = { l: 36, r: 76, t: 12, b: 24 };
  if (!Number.isFinite(proj)) proj = null;
  const tNum = dayNum(today());
  const x0 = dayNum(pts[0].day);
  const xEnd = Math.max(dayNum(pts[pts.length - 1].day), tNum);
  const span = Math.max(xEnd - x0, 1);
  const x1 = proj != null ? xEnd + Math.max(3, span * 0.08) : xEnd + (xEnd === x0 ? 1 : 0);
  const vals = pts.map((p) => p.pct).concat(proj ?? []);
  const ymin = Math.min(goal - 1, Math.floor(Math.min(...vals)) - 1);
  const ymax = Math.max(100, Math.ceil(Math.max(...vals)));
  const X = (n) => m.l + ((n - x0) / (x1 - x0)) * (w - m.l - m.r);
  const Y = (v) => m.t + ((ymax - v) / (ymax - ymin)) * (h - m.t - m.b);
  const numFont = 'font-family="ov Instrument Sans, Instrument Sans, sans-serif" style="font-variant-numeric: tabular-nums"';
  const sans = 'font-family="ov Instrument Sans, Instrument Sans, sans-serif"';

  let g = '';
  for (const v of ticks(ymin, ymax)) {
    g += `<line x1="${m.l}" x2="${w - m.r}" y1="${Y(v)}" y2="${Y(v)}" stroke="var(--sunk)"/><text x="${m.l - 8}" y="${Y(v) + 4}" text-anchor="end" font-size="11" fill="var(--faint)" ${numFont}>${v}</text>`;
  }
  g += `<line x1="${m.l}" x2="${w - m.r}" y1="${Y(goal)}" y2="${Y(goal)}" stroke="var(--dim)" stroke-dasharray="3 3"/><text x="${w - m.r + 6}" y="${Y(goal) + 4}" font-size="11" fill="var(--dim)" ${sans}>${letter(goal)} cutoff</text>`;
  for (let i = 0; i < 4; i++) {
    const n = Math.round(x0 + ((xEnd - x0) * i) / 3);
    const label = new Date(n * 864e5).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
    g += `<text x="${X(n)}" y="${h - 6}" text-anchor="middle" font-size="11" fill="var(--faint)" ${numFont}>${label}</text>`;
    if (xEnd === x0) break;
  }
  let path = `M${X(dayNum(pts[0].day))},${Y(pts[0].pct)}`;
  for (const p of pts.slice(1)) path += ` H${X(dayNum(p.day))} V${Y(p.pct)}`;
  const lastX = X(xEnd), lp = pts[pts.length - 1];
  path += ` H${lastX}`;
  g += `<path d="${path} V${h - m.b} H${X(x0)} Z" fill="var(--accent)" fill-opacity=".07"/><path d="${path}" fill="none" stroke="var(--accent)" stroke-width="2" stroke-linejoin="round"/>`;
  g += `<circle cx="${lastX}" cy="${Y(lp.pct)}" r="4" fill="var(--accent)" stroke="var(--surface)" stroke-width="2"/><text x="${lastX + 8}" y="${Y(lp.pct) + (proj != null && proj > lp.pct ? 14 : -6)}" font-size="11" fill="var(--ink)" ${numFont}>${fmt(lp.pct)}</text>`;
  if (proj != null) {
    g += `<line x1="${lastX}" y1="${Y(lp.pct)}" x2="${X(x1)}" y2="${Y(proj)}" stroke="var(--accent)" stroke-width="2" stroke-dasharray="4 3"/><circle cx="${X(x1)}" cy="${Y(proj)}" r="4" fill="var(--surface)" stroke="var(--accent)" stroke-width="2"/><text x="${X(x1) + 8}" y="${Y(proj) + 4}" font-size="11" fill="var(--accent)" ${numFont}>${fmt(proj)}</text>`;
  }
  g += `<line class="xh" y1="${m.t}" y2="${h - m.b}" stroke="var(--dim)" visibility="hidden"/><circle class="xd" r="4" fill="var(--accent)" stroke="var(--surface)" stroke-width="2" visibility="hidden"/><rect class="hit" x="${m.l}" y="0" width="${Math.max(lastX - m.l, 1)}" height="${h}" fill="transparent"/>`;

  const chart = el('div', 'chart');
  chart.innerHTML = `<svg viewBox="0 0 ${w} ${h}" role="img">${g}</svg>`;
  const svg = chart.firstChild;
  svg.setAttribute('aria-label', `Course grade over the term, now ${fmt(lp.pct)}%`);
  const tip = el('div', 'tip');
  tip.hidden = true;
  chart.append(tip);

  const xh = svg.querySelector('.xh'), xd = svg.querySelector('.xd'), hit = svg.querySelector('.hit');
  hit.addEventListener('pointermove', (e) => {
    const r = svg.getBoundingClientRect();
    const sx = ((e.clientX - r.left) / r.width) * w;
    let best = pts[0];
    for (const p of pts) if (X(dayNum(p.day)) <= sx) best = p;
    const px = X(dayNum(best.day)), py = Y(best.pct);
    xh.setAttribute('x1', px); xh.setAttribute('x2', px); xh.setAttribute('visibility', 'visible');
    xd.setAttribute('cx', px); xd.setAttribute('cy', py); xd.setAttribute('visibility', 'visible');
    tip.hidden = false;
    tip.style.left = `${(px / w) * 100}%`;
    tip.style.top = `${(py / h) * r.height}px`;
    const more = best.items.length > 1 ? ` +${best.items.length - 1} more` : '';
    tip.replaceChildren(el('b', null, `${fmt(best.pct)}%`), ` · ${dayLabel(best.day)}`, el('br'), el('span', 's', best.items[best.items.length - 1] + more));
  });
  hit.addEventListener('pointerleave', () => {
    tip.hidden = true;
    xh.setAttribute('visibility', 'hidden');
    xd.setAttribute('visibility', 'hidden');
  });

  const lo = pts.reduce((a, b) => (b.pct < a.pct ? b : a));
  const note = el('div', 'chart-note');
  note.append(el('span', null, 'Replayed from graded items by due date'));
  const low = el('span', null, 'Lowest ');
  low.append(el('span', 'num', fmt(lo.pct)), ` on ${dayLabel(lo.day)}`);
  note.append(low);
  if (proj != null) note.append(el('span', 'acc', 'Dashed line is your what-if'));
  box.replaceChildren(chart, note);
}

// ── The page ─────────────────────────────────────────────────────────────

/**
 * One course's page, /course/{id}/student_grades → its overlay; or the
 * all-courses page, /grades/grades → [overlay], one per course with grades.
 * Each course there is a .gradebook-course, id s-js-gradebook-course-{id},
 * collapsed until clicked, its rows already in the page.
 */
export async function start({ doc = document, loc = location } = {}) {
  if (/^\/grades\/grades\/?$/.test(loc.pathname)) {
    const out = [];
    for (const box of doc.querySelectorAll('.gradebook-course')) {
      const courseId = (box.id.match(/(\d+)$/) || [])[1];
      const o = courseId && await mountCourse({ doc, root: box, courseId, all: true });
      if (o) out.push(o);
      if (o?.fresh.size) newPill(box, o);
    }
    return out;
  }
  const courseId = (loc.pathname.match(/^\/course\/(\d+)/) || [])[1] || 'page';
  return mountCourse({ doc, root: doc, courseId });
}

/** /grades/grades: "N new" after a class's name, until the class is opened. */
function newPill(box, o) {
  const title = box.querySelector('.gradebook-course-title');
  if (!title) return;
  const { host, root } = createHost('np-grade-new', PILL_CSS);
  host.style.cssText = 'display: inline-block; vertical-align: middle; margin-left: 8px;';
  const pill = el('span', 'new', `${o.fresh.size} new`);
  pill.title = 'Scores that are new or changed since you last opened this class here';
  root.append(pill);
  (title.querySelector('a') || title).append(host);
  title.addEventListener('click', () => { host.remove(); o.seen(); }, { once: true });
}

async function mountCourse({ doc, root: scope, courseId, all = false }) {
  const rows = parseGrades(scope, { els: true });
  if (!rows.length) return null;
  const marks = await loadMarks(courseId); // item id → 'out' | 'in': the student's own say on what counts
  const course = currentPeriod(periodsFromRows(rows, { marks }));
  if (!course) return null;

  // New grades: scores that appeared or changed since this course was last
  // looked at. A course's own page counts as looking; on /grades/grades,
  // opening the class does (start's newPill).
  const graded = course.categories.flatMap((c) => c.items).filter((it) => it.earned != null);
  const seenCopy = freshGrades(await loadSeenGrades(courseId), graded);
  const fresh = new Set(Object.keys(seenCopy.fresh));
  const seen = () => saveSeenGrades(courseId, { scores: seenCopy.scores, fresh: {} });
  if (all) saveSeenGrades(courseId, seenCopy); else seen();

  const typed = await loadWeights(courseId); // only for categories Schoology shows no (N%) for
  const whatIf = new Map(); // item id → { earned, possible } — ungraded rows, or a graded row's override
  const plans = new Map(); // category title → { name, possible, score } (null: none open)
  const dropped = new Set(); // item ids left out of the math, this page view only
  // A mark that sticks (saved per course): Schoology can count an item for
  // nothing and not say so, and only the student can know which.
  const setMark = (cat, it, v) => {
    if (v) marks[it.id] = v; else delete marks[it.id];
    saveMarks(courseId, { ...marks });
    dropped.delete(it.id);
    calibrate(cat, marks);
    update();
  };
  let goal = (await loadGoal(courseId)) ?? A_CUTOFF; // the grade to aim for, a percent
  const L = () => letter(goal);
  const aL = () => `${/^A/.test(L()) ? 'an' : 'a'} ${L()}`;
  let setGoal = () => {};
  let chartOpen = !all; // open on a class's own page, closed on /grades/grades (keeps it short); for this visit only

  const weights = () => {
    const w = {};
    for (const c of course.categories) if (c.weight == null && typed[c.title] != null) w[c.title] = typed[c.title];
    return w;
  };
  const extras = (except) => [...plans].filter(([t, p]) => t !== except && p && p.score != null && p.possible > 0)
    .map(([t, p]) => ({ category: t, earned: p.score, possible: p.possible }));
  const whatIfOpts = () => Object.fromEntries([...whatIf].filter(([, v]) => v.earned != null && v.possible > 0));
  const opts = (exceptPlan) => ({ weights: weights(), whatIf: whatIfOpts(), extra: extras(exceptPlan), drop: [...dropped] });
  const changeCount = () => Object.keys(whatIfOpts()).length + extras().length + dropped.size;
  const anyWhatIf = () => changeCount() > 0;

  // Schoology hides its "Course Grade" row (display: none) and shows the grade
  // in a box under the table; then the current period's row, at the top,
  // carries the course-level additions. The row's own display, not whether
  // it's on screen: on /grades/grades the whole course starts collapsed.
  const courseRow = [rows.find((r) => r.level === 'course'), course.src].find((r) => r?.el && getComputedStyle(r.el).display !== 'none')
    || rows.find((r) => r.level === 'course') || course.src;
  const updates = []; // () => void, run on every change
  const ourRows = []; // { tr, shown: () => bool }
  const wantTint = new Map(); // tr → has a what-if score
  const tinted = new Map(); // tr → the accent its bar is drawn in now ('' for none)
  const wantStrike = new Map(); // tr → its item is dropped
  const struckOn = new Map(); // tr → carries the strike-through now
  const resetters = []; // clear the what-if boxes on Reset

  // The right-hand column: a <td> of ours on every row of the table. Schoology
  // draws each row's rule and padding on the .td-content-wrapper inside every
  // cell, so ours has one too and lines up. The cells go with the Overlay
  // switch (syncRows), so an off page has no extra column.
  const cells = new Map(); // tr → shadow root
  const ourCells = [];
  for (const r of rows) {
    const tr = r.el;
    const td = doc.createElement('td');
    td.className = 'np-grade-col';
    td.style.width = '112px'; // "A holds down to" on one line, the number under it
    td.style.display = 'none';
    const wrap = doc.createElement('div');
    wrap.className = 'td-content-wrapper';
    wrap.style.justifyContent = 'flex-end';
    const { host, root } = createHost('np-grade-col', CELL_CSS);
    keepEvents(host);
    wrap.append(host);
    td.append(wrap);
    tr.append(td);
    cells.set(tr, root);
    ourCells.push(td);
  }
  const cell = (tr, ...kids) => { const root = cells.get(tr); root.replaceChildren(root.firstChild, ...kids.filter(Boolean)); };

  const inlineHosts = []; // for pinTitles' measuring
  function inline(where, tag, { wrap = false } = {}) {
    const { host, root } = createHost(tag, INLINE_CSS);
    keepEvents(host);
    inlineHosts.push(host);
    host.style.marginLeft = '8px'; // inline: Schoology's own margin reset beats :host
    const box = where.querySelector('.td-content-wrapper') || where;
    // The wrapper is a one-line flex box. With `wrap`, ours may drop to a
    // second line rather than squeeze Schoology's grade (not on item rows:
    // their empty .grade-wrapper would take a line of its own).
    if (wrap && getComputedStyle(box).display.includes('flex')) { box.style.flexWrap = 'wrap'; box.style.rowGap = '4px'; }
    box.append(host);
    return { host, root };
  }
  /** A row's indent: its title cell's reportSpacer padding (Schoology scopes that rule to its own cells). */
  const indentOf = (tr) => { const sp = tr?.querySelector('[class*="reportSpacer-"]'); return sp ? getComputedStyle(sp).paddingLeft : '0px'; };
  const gradeCell = (tr) => tr.querySelector('.grade-column') || tr.children[1] || tr;
  const titleCell = (tr) => tr.querySelector('th, .title-column') || tr.firstElementChild;

  /**
   * A <tr> of ours after `after`, one cell across the table, holding a host,
   * inside Schoology's own spacer and wrapper classes so it gets the same
   * indent (that of `indentLike`) and rule as the rows around it.
   */
  function ourRow(after, shown, indentLike) {
    const tr = doc.createElement('tr');
    tr.className = 'np-overlay-row';
    const td = doc.createElement('td');
    td.colSpan = after.children.length;
    const spacer = doc.createElement('div');
    spacer.style.paddingLeft = indentOf(indentLike);
    const wrap = doc.createElement('div');
    wrap.className = 'td-content-wrapper';
    const { host, root } = createHost('np-grade-row', ROW_CSS);
    host.style.flex = '1 1 auto'; // the wrapper is a flex box; let the chart have the width
    host.style.minWidth = '0';
    keepEvents(host);
    wrap.append(host);
    spacer.append(wrap);
    td.append(spacer);
    tr.append(td);
    after.after(tr);
    tr.style.display = 'none';
    ourRows.push({ tr, shown });
    return { tr, root };
  }

  // ── The class card ──
  // A block of ours just above the class's table, holding everything that
  // changes this class's numbers: the grade, "6.25 above [Goal A]", Reset,
  // Graph. It sticks under any fixed bar at the top of the window while the
  // table scrolls by, and lets go where the table's box ends (the next class
  // on /grades/grades). The graph opens in a second block under it, not
  // sticky. Schoology's course row gets nothing.
  const tableEl = courseRow.el.closest('table');
  const hist = plotPoints(history(course, { weights: weights() }));
  const { host: cardHost, root: cardRoot } = createHost('np-grade-card', CARD_CSS);
  keepEvents(cardHost);
  cardHost.style.cssText = 'display: none; position: sticky; top: 0; z-index: 50; margin: 0 0 8px;';
  const { host: chartHost, root: chartRoot } = createHost('np-grade-chart', CARD_CSS);
  keepEvents(chartHost);
  chartHost.style.cssText = 'display: none; margin: 0 0 8px;';
  (tableEl || courseRow.el).before(cardHost, chartHost);
  let stickTop = 0; // px: the bottom of a fixed bar at the top of the window, if Schoology has one
  {
    const card = el('div', 'card');
    const big = el('div', 'big');
    const gap = el('div', 'gap');
    const sum = el('span', 'sum');
    const end = el('div', 'end');

    // Goal: a plain <select>, drawn as a chip, inside the sentence it changes.
    const goalSel = el('select', 'goal');
    goalSel.setAttribute('aria-label', 'The grade you are aiming for in this class');
    goalSel.title = 'The grade you are aiming for in this class. The graph, "holds down to" and "need" use it.';
    for (const [name, min] of GOALS) {
      const o = el('option', null, `Goal ${name}`);
      o.value = String(min);
      goalSel.append(o);
    }
    goalSel.value = String(goal);
    setGoal = pct => {
      goal = pct;
      goalSel.value = String(pct);
      saveGoal(courseId, goal === A_CUTOFF ? null : goal);
      update();
    };
    goalSel.addEventListener('change', () => setGoal(Number(goalSel.value)));
    gap.append(sum, goalSel);

    const reset = el('button', 'pill accent');
    reset.type = 'button';
    reset.title = 'Put every score back as Schoology has it';
    reset.addEventListener('click', () => {
      whatIf.clear();
      dropped.clear();
      for (const p of plans.values()) if (p) p.score = null;
      for (const f of resetters) f();
      update();
    });
    end.append(reset);

    if (hist.length >= 2) {
      const graphBtn = el('button', 'pill');
      graphBtn.type = 'button';
      graphBtn.title = 'Your grade over the term';
      graphBtn.addEventListener('click', () => { chartOpen = !chartOpen; update(); });
      end.append(graphBtn);
      updates.push(() => {
        graphBtn.innerHTML = sparkSvg(plotPoints(history(course, { weights: weights() })));
        graphBtn.append(el('span', null, 'Graph'));
        graphBtn.classList.toggle('on', chartOpen);
        graphBtn.setAttribute('aria-expanded', String(chartOpen));
        if (chartOpen) {
          const box = el('div', 'chartwrap');
          drawChart(box, plotPoints(history(course, { weights: weights() })), anyWhatIf() ? grade(course, opts()).pct : null, goal);
          chartRoot.replaceChildren(chartRoot.firstChild, box);
        }
      });
    }
    card.append(big, gap, end);
    cardRoot.append(card);

    const shown = statedPct((course.src || courseRow).grade) || statedPct(courseRow.grade);
    updates.push(() => {
      const base = grade(course, { weights: weights() }).pct;
      const now = grade(course, opts()).pct;
      const changes = changeCount();
      const wi = changes > 0 && now != null;
      card.classList.toggle('changed', wi);
      reset.hidden = !changes;
      reset.textContent = `Reset ${changes}`;
      cell(courseRow.el);
      if (base == null) { big.replaceChildren(); return sum.replaceChildren(); }
      if (shown && Math.abs(base - shown.pct) > shown.tol + 1e-9) {
        big.replaceChildren();
        const n = el('span', 'note warn', `Doesn't match: ${fmt(base)}%`);
        n.title = `Schoology shows ${shown.pct}%. The overlay's math doesn't match it, so its numbers on this page are off. If a category has no weight, type it in beside the category's name.`;
        return sum.replaceChildren(n);
      }
      const v = wi ? now : base;
      const n = el('span', 'n', fmt(v));
      n.append(el('small', null, '%'));
      big.replaceChildren(...(wi ? [el('span', 'was', `${fmt(base)} →`)] : []), n, el('span', 'let', letter(v)));
      big.title = wi ? `With the scores you're trying. Schoology has ${fmt(base)}%.` : 'Your grade in this class';
      const d = v - goal;
      sum.replaceChildren(el('span', 'num', fmt(Math.abs(d))), d >= 0 ? ' above' : ' below');
      sum.title = `${wi ? 'What-if grade' : 'Course grade'} minus your goal (${goal}%)`;
    });

    // Stuck: the card has reached the top and the table is scrolling under it.
    const stuckNow = () => {
      if (cardHost.style.display === 'none' || !tableEl) return;
      const r = cardHost.getBoundingClientRect();
      card.classList.toggle('stuck', r.top <= stickTop + 1 && tableEl.getBoundingClientRect().top < r.bottom - 1);
    };
    let frame = 0;
    try {
      window.addEventListener('scroll', () => {
        if (frame) return;
        frame = requestAnimationFrame(() => { frame = 0; stuckNow(); });
      }, { passive: true });
    } catch { /* tests */ }
    updates.push(stuckNow);
  }

  /** The bottom of whatever fixed or sticky bar covers the top of the window (not ours), or 0. */
  function topBar() {
    let bottom = 0;
    try {
      for (const e of doc.elementsFromPoint(window.innerWidth / 2, 2)) {
        for (let n = e; n && n !== doc.documentElement; n = n.parentElement) {
          if (/^NP-/.test(n.tagName)) break;
          const pos = getComputedStyle(n).position;
          if (pos === 'fixed' || pos === 'sticky') { bottom = Math.max(bottom, n.getBoundingClientRect().bottom); break; }
        }
      }
    } catch { /* tests */ }
    return Math.max(0, Math.round(bottom));
  }

  // Click a score to try one: Schoology's score on an item row opens that
  // row's box. Only listeners and a hover underline (reverted on leave)
  // touch Schoology's cell. `can` says whether the row takes a score now.
  function tryScore(tr, start, can) {
    const gc = gradeCell(tr);
    const marks = () => [...gc.querySelectorAll('.awarded-grade, .max-grade, .no-grade')];
    const ours = (e) => /^NP-/.test(e.target.tagName || '');
    let lit = false;
    const light = (on) => {
      if (on === lit) return;
      lit = on;
      const t = themeOf(getSettings().theme) || THEMES.schoology;
      for (const m of marks()) m.style.boxShadow = on ? `inset 0 -1px 0 ${paint(t.accent, t)}` : '';
      gc.style.cursor = on ? 'pointer' : '';
      if (on) gc.setAttribute('title', 'Try a score'); else gc.removeAttribute('title');
    };
    gc.addEventListener('mouseover', (e) => light(isOverlayOn() && !ours(e) && can()));
    gc.addEventListener('mouseleave', () => light(false));
    gc.addEventListener('click', (e) => {
      if (!isOverlayOn() || ours(e) || !marks().length || !can()) return;
      light(false);
      start();
    });
  }

  // ── Categories, their items, and a planning row after each ──
  for (const cat of course.categories) {
    const ctr = cat.src.el;

    if (cat.weight == null) {
      const { root } = inline(titleCell(ctr), 'np-grade-weight');
      const w = input('number', 'w', `Weight of ${cat.title}, percent`, '?');
      if (typed[cat.title] != null) w.value = String(typed[cat.title]);
      w.addEventListener('input', () => {
        const v = num(w.value);
        if (v == null) delete typed[cat.title]; else typed[cat.title] = v;
        saveWeights(courseId, { ...typed });
        update();
      });
      const wrap = el('span', 'wrap', 'weight ');
      wrap.title = 'Schoology shows no weight for this category. Type it to weight the course grade by category; leave every one empty to use total points.';
      wrap.append(w, '%');
      root.append(wrap);
    }

    const { host: catProjHost, root: catProj } = inline(gradeCell(ctr), 'np-grade-proj');
    // Whether the items, left out as marked, give Schoology's own percent.
    const { host: addsHost, root: addsRoot } = inline(gradeCell(ctr), 'np-grade-adds');
    const addsMark = el('span', 'pct');
    addsRoot.append(addsMark);
    updates.push(() => {
      const marked = cat.items.some((it) => it.uncounted);
      addsHost.hidden = cat.adds == null || (cat.adds && !marked);
      addsMark.textContent = cat.adds ? '✓' : '≈';
      addsMark.title = cat.adds
        ? `Adds up to Schoology's ${cat.title} percent with the items marked not counted left out`
        : `Schoology's ${cat.title} percent doesn't add up from these scores, so it's scaled to match. Mark the items that don't count to fix it.`;
    });
    updates.push(() => {
      const base = grade(course, { weights: weights() }).categories.find((c) => c.title === cat.title)?.pct;
      const now = grade(course, opts()).categories.find((c) => c.title === cat.title)?.pct;
      catProjHost.hidden = now == null || (base != null && Math.abs(now - base) < EPS);
      if (!catProjHost.hidden) {
        const out = el('span', 'acc', '→ ');
        out.append(el('span', 'num', `${fmt(now, 1)}%`));
        catProj.replaceChildren(catProj.firstChild, out);
      }
      const floor = categoryFloor(course, cat.title, goal, opts());
      if (floor == null || now == null) return cell(ctr);
      const n = el('span', 'note');
      if (floor > 100) n.append(`${L()} out of reach here`);
      else if (floor > now + 1e-9) n.append(`${L()} needs `, el('span', 'num', `${fmt(floor, 1)}%`));
      else n.append(`${L()} holds down to `, el('span', 'num', `${fmt(Math.max(floor, 0), 1)}%`));
      n.title = `Lowest ${cat.title} can drop, the other categories unchanged, and keep ${aL()} (${goal}%)`;
      cell(ctr, n);
    });

    for (const it of cat.items) {
      const tr = it.src.el;

      if (it.earned != null) {
        // A percent beside Schoology's score; a click on the score opens a
        // box (blank keeps the real score) and "Leave out".
        const { host, root } = inline(gradeCell(tr), 'np-grade-pct');
        const pctSpan = el('span', 'pct');
        root.append(pctSpan);
        if (fresh.has(String(it.id))) {
          const tag = el('span', 'new', 'New');
          tag.title = 'New or changed since you last looked';
          root.append(tag);
        }

        // Left out of the category (Dewey's guess, or the student's mark).
        const same = cat.items.filter((x) => x.earned === it.earned && x.possible === it.possible);
        // The note is its own undo: click it to say the item does count
        // (or, on one you marked yourself, that it counts again).
        const uncountedNote = () => {
          const u = it.uncounted;
          const n = el('button', 'linkbtn quiet', u === 'sure' || u === 'mine' ? 'not counted' : 'may not count');
          n.type = 'button';
          n.title = (u === 'mine' ? 'You marked this as not counted'
            : u === 'sure' ? `Schoology's percent for ${cat.title} only adds up without this item, so it's left out of the math here too`
            : u === 'column' ? `Typed straight into the gradebook, and Schoology's percent for ${cat.title} is closer without these columns`
            : `Schoology's percent for ${cat.title} only adds up if ${same.filter((x) => x.uncounted).length} of the ${same.length} items scored ${it.earned} / ${it.possible} don't count. It doesn't say which; the totals are right either way`)
            + '. Click if it counts.';
          n.addEventListener('click', () => setMark(cat, it, u === 'mine' ? null : 'in'));
          return n;
        };

        // Trying a score: click Schoology's score (or the "→ 12" of one being
        // tried) and a box opens here; clicking away or Enter closes it and
        // keeps what's typed, Esc puts the real score back.
        const tried = el('button', 'wi-val');
        tried.type = 'button';
        tried.title = 'Change the score you are trying';
        pctSpan.before(tried);
        const score = input('number', 'wi-score', `Score to try for ${it.title}, out of ${it.possible}`, String(it.earned));
        const dropBtn = el('button', 'linkbtn quiet', 'Leave out');
        dropBtn.type = 'button';
        dropBtn.title = 'See your grade without this item';
        const editWrap = el('span', 'wrap');
        editWrap.append(score, dropBtn); // no "/ possible": Schoology's own score, just left of it, says so
        root.append(editWrap);

        let editing = false;
        const edit = (on) => {
          if (on === editing) return;
          editing = on;
          if (on) { const w = whatIf.get(it.id); score.value = w ? String(w.earned) : ''; }
          update();
          if (on) { score.focus(); score.select(); }
        };
        editWrap.addEventListener('focusout', (e) => { if (!editWrap.contains(e.relatedTarget)) edit(false); });
        const clear = () => { score.value = ''; whatIf.delete(it.id); };
        dropBtn.addEventListener('mousedown', (e) => e.preventDefault()); // keep the box's focus until the click lands
        dropBtn.addEventListener('click', () => { clear(); dropped.add(it.id); editing = false; update(); });
        const undo = el('button', 'linkbtn', 'Undo');
        undo.type = 'button';
        undo.title = 'Count it again';
        undo.addEventListener('click', () => { dropped.delete(it.id); update(); });
        const never = el('button', 'linkbtn quiet', 'Never counts');
        never.type = 'button';
        never.title = 'Schoology doesn\'t count this one: leave it out for good';
        never.addEventListener('click', () => setMark(cat, it, 'out'));
        const droppedCell = el('span', 'wrap');
        droppedCell.style.whiteSpace = 'nowrap';
        droppedCell.append(undo, ' ', never);
        tried.addEventListener('click', () => edit(true));
        score.addEventListener('input', () => {
          const earned = num(score.value);
          if (earned == null || earned === it.earned) whatIf.delete(it.id); else whatIf.set(it.id, { earned, possible: it.possible });
          update();
        });
        score.addEventListener('keydown', (e) => {
          if (e.key === 'Enter') score.blur();
          if (e.key === 'Escape') { clear(); score.blur(); update(); }
        });
        resetters.push(() => { score.value = ''; });
        tryScore(tr, () => edit(true), () => !editing && !dropped.has(it.id) && !it.uncounted);

        updates.push(() => {
          if (it.uncounted) {
            if (editing) { editing = false; clear(); }
            strike(tr, false);
            tint(tr, false);
            host.hidden = false;
            editWrap.hidden = true;
            tried.hidden = true;
            pctSpan.hidden = !getSettings().percent || !(it.possible > 0);
            if (it.possible > 0) pctSpan.textContent = pctText((it.earned / it.possible) * 100);
            return cell(tr, uncountedNote());
          }
          const isDropped = dropped.has(it.id);
          strike(tr, isDropped);
          host.hidden = isDropped;
          if (isDropped) {
            tint(tr, false);
            return cell(tr, droppedCell); // the struck-through score says it's left out
          }
          const wi = whatIf.get(it.id);
          const val = wi && wi.earned != null ? wi.earned : it.earned;
          const pct = it.possible > 0 ? (val / it.possible) * 100 : null;
          pctSpan.hidden = !getSettings().percent;
          pctSpan.textContent = pct == null ? '' : pctText(pct);
          editWrap.hidden = !editing;
          tried.hidden = editing || !wi;
          if (wi) tried.replaceChildren('→ ', el('span', 'num', String(wi.earned)));
          tint(tr, wi != null);
          // Untouched rows keep the impact as it really is; a row being
          // tried shows its impact under everything else tried right now.
          const impactOpts = wi != null ? opts() : { weights: weights(), drop: [...dropped] };
          cell(tr, impTag(impact(course, it.id, impactOpts), 'Course grade with this item minus without it'));
        });
        continue;
      }

      // A "—" row: nothing of ours until its "—" is clicked, then a score box
      // and, since the row doesn't say what it's out of, a points box.
      const { host, root } = inline(gradeCell(tr), 'np-grade-whatif');
      const tried = el('button', 'wi-val');
      tried.type = 'button';
      tried.title = 'Change the score you are trying';
      const score = input('number', 'wi-score', `Score to try for ${it.title}`, '—');
      const wrap = el('span', 'wrap');
      wrap.append(score, '/');
      let pts = null;
      if (it.possible != null) wrap.append(el('span', 'tnum', String(it.possible)));
      else { pts = input('number', 'wi-pts', `Points ${it.title} is out of`, 'pts'); wrap.append(pts); }
      root.append(tried, wrap);
      let editing = false;
      const edit = (on) => {
        if (on === editing) return;
        editing = on;
        update();
        if (on) { score.focus(); score.select(); }
      };
      wrap.addEventListener('focusout', (e) => { if (!wrap.contains(e.relatedTarget)) edit(false); });
      tried.addEventListener('click', () => edit(true));
      const read = () => {
        const earned = num(score.value), possible = pts ? num(pts.value) : it.possible;
        if (earned == null && possible == null) whatIf.delete(it.id); else whatIf.set(it.id, { earned, possible });
        update();
      };
      const restore = () => { score.value = ''; if (pts) pts.value = ''; whatIf.delete(it.id); update(); };
      score.addEventListener('input', read);
      pts?.addEventListener('input', read);
      for (const box of [score, pts].filter(Boolean)) {
        box.addEventListener('keydown', (e) => {
          if (e.key === 'Enter') box.blur();
          if (e.key === 'Escape') { restore(); box.blur(); }
        });
      }
      resetters.push(() => { score.value = ''; if (pts) pts.value = ''; });
      tryScore(tr, () => edit(true), () => !editing);
      updates.push(() => {
        const v = whatIf.get(it.id);
        const set = !!(v && v.earned != null && v.possible > 0);
        host.hidden = !editing && !set;
        wrap.hidden = !editing;
        tried.hidden = editing || !set;
        if (set) tried.replaceChildren('→ ', el('span', 'num', `${v.earned}/${v.possible}`));
        tint(tr, set);
        cell(tr, set ? impTag(impact(course, it.id, opts()), 'Course grade with this score minus without it') : null);
      });
    }

    // The planning row goes after the category's last row of items, and
    // exists (is `shown`) only while a plan for this category is open;
    // "+ Upcoming test" in the category's own row opens it.
    const last = cat.items.length ? cat.items[cat.items.length - 1].src.el : ctr;
    const lead = cat.items.length ? cat.items[0].src.el : ctr;
    const plan = ourRow(last, () => isShown(lead) && !!plans.get(cat.title), cat.items.length ? lead : ctr);
    plans.set(cat.title, null);
    const kind = /test|quiz|exam|assess/i.test(cat.title) ? 'test' : 'assignment';

    const { host: planBtnHost, root: planBtnRoot } = inline(titleCell(ctr), 'np-grade-plan');
    const planBtn = el('button', 'linkbtn', `+ Upcoming ${kind}`);
    planBtn.title = `Add a ${kind} that isn't in the gradebook yet, and see the score you need on it`;
    planBtnRoot.append(planBtn);
    planBtn.addEventListener('click', () => {
      plans.set(cat.title, { name: '', possible: kind === 'test' ? 50 : 10, score: null });
      drawPlan();
      update();
      plan.root.querySelector('input')?.focus();
    });

    let need = null;
    const drawPlan = () => {
      const p = plans.get(cat.title);
      if (!p) {
        need = null;
        return plan.root.replaceChildren(plan.root.firstChild);
      }
      const box = el('div', 'hypo');
      const name = input('text', null, `Upcoming ${kind} name`, 'name');
      name.value = p.name;
      name.addEventListener('input', () => { p.name = name.value; });
      const worth = input('number', null, 'Points it is worth');
      worth.value = String(p.possible);
      worth.addEventListener('input', () => { p.possible = num(worth.value); update(); });
      const sc = input('number', null, 'What-if score', '?');
      sc.addEventListener('input', () => { p.score = num(sc.value); update(); });
      resetters.push(() => { sc.value = ''; });
      need = el('span', 'need acc');
      const rm = el('button', 'linkbtn', 'Remove');
      rm.addEventListener('click', () => { plans.set(cat.title, null); drawPlan(); update(); });
      box.append(el('span', null, 'Upcoming'), name, el('span', null, 'worth'), worth, el('span', null, 'score'), sc, need, rm);
      plan.root.replaceChildren(plan.root.firstChild, box);
    };
    drawPlan();
    updates.push(() => {
      const p = plans.get(cat.title);
      planBtnHost.hidden = !!p;
      if (!p || !need) return;
      if (!(p.possible > 0)) return need.replaceChildren();
      const r = needFor(course, { category: cat.title, possible: p.possible, target: goal }, opts(cat.title));
      if (r.status === 'any') need.replaceChildren(`any score keeps ${aL()}`);
      else if (r.status === 'out') need.replaceChildren('out of reach with this alone');
      else need.replaceChildren('need ', el('b', null, `${Math.ceil(r.need * 100) / 100}/${p.possible}`), ` for ${aL()}`);
    });
  }

  // A row with a what-if score gets a thin inset left bar on its first cell,
  // on Schoology's own row, only while the overlay is on.
  function tint(tr, on) {
    wantTint.set(tr, on);
    const want = on && isOverlayOn();
    // Keyed by the theme's accent too, so a theme change repaints the bar.
    const t = themeOf(getSettings().theme) || THEMES.schoology;
    const accent = paint(t.accent, t);
    const now = want ? accent : '';
    if ((tinted.get(tr) || '') === now) return;
    tinted.set(tr, now);
    const first = tr.children[0];
    if (first) first.style.boxShadow = now ? `inset 3px 0 0 ${accent}` : '';
  }

  // A dropped item's own Schoology score, struck through and faded — reverted
  // on undrop or when the overlay switches off, same pattern as tint().
  function strike(tr, on) {
    wantStrike.set(tr, on);
    const want = on && isOverlayOn();
    if (!!struckOn.get(tr) === want) return;
    struckOn.set(tr, want);
    const gc = gradeCell(tr);
    gc.style.textDecoration = want ? 'line-through' : '';
    gc.style.opacity = want ? '.5' : '';
  }

  // Schoology gives its (mostly empty) comment column a percentage width, so
  // our column and the grade cell's additions would come out of the title
  // column and pull every grade left. While the overlay is on, the title
  // column is pinned to the width it has without them (measured once the table
  // is on screen, again after a resize) and the comment column goes to auto
  // (a percentage outranks a px width), so it gives up the room instead.
  let titleW = null; // px, or null: not measured yet
  const titleCells = rows.map((r) => r.el && titleCell(r.el)).filter(Boolean);
  const commentCells = rows.map((r) => r.el?.querySelector('.comment-column')).filter(Boolean);
  function pinTitles() {
    const on = isOverlayOn();
    if (on && titleW == null) {
      const probe = titleCells.find(isShown);
      if (!probe) return; // collapsed: measured when Schoology opens it
      const ours = [...ourCells, ...inlineHosts];
      const was = ours.map((n) => n.style.display);
      for (const n of ours) n.style.display = 'none';
      for (const c of [...titleCells, ...commentCells]) c.style.width = '';
      titleW = Math.round(probe.getBoundingClientRect().width);
      ours.forEach((n, i) => { n.style.display = was[i]; });
    }
    const w = on && titleW ? `${titleW}px` : '';
    for (const c of titleCells) if (c.style.width !== w) c.style.width = w;
    const cw = w ? 'auto' : '';
    for (const c of commentCells) if (c.style.width !== cw) c.style.width = cw;
  }
  try { window.addEventListener('resize', debounce(() => { titleW = null; pinTitles(); }, 150)); } catch { /* tests */ }

  // Our own rows and the card follow the overlay switch and Schoology's collapsing.
  function syncRows() {
    const col = isOverlayOn() ? '' : 'none';
    for (const td of ourCells) if (td.style.display !== col) td.style.display = col;
    pinTitles();
    const cardOn = isOverlayOn() && isShown(tableEl || courseRow.el);
    const cd = cardOn ? 'block' : 'none';
    if (cardHost.style.display !== cd) cardHost.style.display = cd;
    const chd = cardOn && chartOpen && chartRoot.childElementCount > 1 ? 'block' : 'none';
    if (chartHost.style.display !== chd) chartHost.style.display = chd;
    if (cardOn) {
      // As wide as the table, no wider; under Schoology's fixed bar, if it has one.
      const w = tableEl ? `${Math.round(tableEl.getBoundingClientRect().width)}px` : '';
      for (const h of [cardHost, chartHost]) if (h.style.maxWidth !== w) h.style.maxWidth = w;
      stickTop = topBar();
      const top = `${stickTop}px`;
      if (cardHost.style.top !== top) cardHost.style.top = top;
    }
    for (const r of ourRows) {
      const d = isOverlayOn() && r.shown() ? '' : 'none';
      if (r.tr.style.display !== d) r.tr.style.display = d;
    }
  }

  function update() {
    for (const f of updates) f();
    syncRows();
  }

  // Schoology's collapsing: rows in the table; on /grades/grades, the course too.
  const table = scope === doc ? courseRow.el.closest('table') || doc.body : scope;
  new MutationObserver(debounce(syncRows, 60)).observe(table, { attributes: true, attributeFilter: ['class', 'style'], subtree: true });
  onOverlay(() => {
    for (const [tr, on] of wantTint) tint(tr, on);
    for (const [tr, on] of wantStrike) strike(tr, on);
    update();
  });
  onSettings(() => update());
  update();
  return {
    update, whatIf, plans, dropped, setGoal: pct => setGoal(pct), fresh, seen,
  };
}

if (typeof chrome !== 'undefined' && chrome.runtime?.id && typeof location !== 'undefined' && !globalThis.__npNoAutoStart) start();
