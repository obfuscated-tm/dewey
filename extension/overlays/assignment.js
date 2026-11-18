// The assignment page (docs/extension/OVERLAY-UI.md §3), and submit detection.
// One addition, in a shadow root of its own; Schoology's page stays as it is:
//
//   card      at the top of Schoology's right column, above its Submit button
//             (under the title on a quiz's start page, or floating when the
//             page has neither). Styled like a To Do sidebar row:
//               head    the class, and the stamp: "Due Sep 23", "6 days late"
//                       (red), "Due today", "Submitted" / "Turned in" (green);
//                       a rule in the class colour, red while it's late
//               status  Missing · ○ To do / ● Done, not submitted (red while
//                       late) / Submitted / the score; then the full due date
//                       with "N days late", "5 h left" or a test's "in 3 days"
//               buttons ✓ Done (Studied) ↔ Not done, ↑ Turn in for work with
//                       no dropbox, Undo after a Turn in. Dropbox work is
//                       handed in only with Schoology's own Submit button,
//                       right under the card, and submit detection clears it.
//               grade   "A zero costs −N.NN" / "Moved your course grade ±N.NN ·
//                       Nth lowest of M in <category>", and "What if?", which
//                       opens "If I get [ ] / pts → NN.NN% ±delta" in place
//                       (never saved)
//               type    HW · Test · Task · CW, one tap to change it; ⋯ holds
//                       Remove from list
//             Not in neo-plan: "+ Add to list"; removed: "Removed · Add back".
//             The rules for late, the stamp and the countdowns are the To Do
//             sidebars' (overlays/todologic.js), which are the iOS app's.
//
// What it reads (overlays/coursedata.js, cached with the Materials page's):
// the course's gradebook.
//
// Submit detection: when this page shows the submission as made (on load, or
// after the submit dialog changes it), the service worker posts a one-item
// enrich. Reading only: nothing on the page is clicked or changed.

import { parseAssignmentPage, parseSubmissionStatus, assignmentIdOf } from '../reader/parse/sync.js';
import { clearedWord } from '../panel/today-format.js';
import { cleanText } from '../util.js';
import { np, pageUserId, createHost, el, classDot, onClick, debounce, failText, keepEvents } from './ui.js';
import { periodsFromRows, impact, grade as courseGrade } from './grademath.js';
import { openCourse, chromeStore, fetchDoc, loadWeights } from './coursedata.js';
import { rowState, scoreText } from './matstate.js';
import { MARK_CSS, iconBtn } from './marker.js';
import { rippleFrom } from './ripple.js';
import { formatDue, dueTail, showsLate, isOwed, calendarDays } from './todologic.js';
import {
  chipState, findItem, signed, zeroCost, whatIfGrade, rankIn, rankText, lateDays, lateText, pointsFromText, stampFor,
} from './asgstate.js';

const EPS = 0.005;

const CARD_CSS = MARK_CSS + `
:host { display: block; margin: 0 0 14px; }
:host(.inflow) { max-width: 360px; margin: 10px 0 14px; }
:host(.float) { position: fixed; right: 16px; bottom: 16px; z-index: 2147483000; margin: 0; width: 280px; }
.card {
  position: relative; display: grid; gap: 0; padding: 10px 12px 12px;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius);
}
.head { display: flex; align-items: center; gap: 6px; min-height: 26px; padding: 0 0 7px; border-bottom: 2px solid var(--cls, var(--line)); }
.head.late { border-bottom-color: var(--bad); }
.cls { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; min-width: 0; }
/* The stamp: the card's one strong detail. Tilted, outlined, never filled. */
.stamp {
  position: absolute; top: -10px; right: 8px; display: inline-grid; justify-items: center;
  padding: 3px 8px 4px; border: 2px solid currentColor; border-radius: 4px; background: var(--surface);
  color: var(--ink); transform: rotate(-6deg); white-space: nowrap;
  font-weight: 800; letter-spacing: .04em; text-transform: uppercase;
}
.stamp .s1 { font-size: 10px; line-height: 12px; }
.stamp .s2 { font-size: 15px; line-height: 18px; }
.stamp.late { color: var(--bad); }
.stamp.good { color: var(--good); }
.stamp.slam { animation: slam 300ms cubic-bezier(.2, .8, .3, 1); }
@keyframes slam { from { transform: rotate(-6deg) scale(1.8); opacity: 0; } to { transform: rotate(-6deg) scale(1); opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .stamp.slam { animation: none; } }
.lines { display: grid; gap: 3px; padding: 9px 0 0; }
.status { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; }
.st { font-weight: 500; }
.st.late { color: var(--bad); }
.st.late .circle { border-color: var(--bad); }
.st.done.late .circle { background: var(--bad); border-color: var(--bad); }
.due { color: var(--dim); font-variant-numeric: tabular-nums; }
.due span { white-space: nowrap; }
.due .over { color: var(--bad); font-weight: 600; }
.acts { display: flex; gap: 6px; padding: 10px 0 0; }
.acts .ib { flex: 1; justify-content: center; height: 26px; font-size: 12px; }
.acts .ib svg { width: 13px; height: 13px; }
.foot { display: grid; gap: 8px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line); }
.grade { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; color: var(--dim); }
.grade .rank { flex-basis: 100%; }
.head[hidden] + .lines { padding-top: 0; }
.row2 { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.imp { padding: 0 5px; border-radius: var(--radius-sm); font: 500 11px/16px var(--sans); font-variant-numeric: tabular-nums; }
.imp.pos { color: var(--good); background: var(--good-soft); }
.imp.neg { color: var(--bad); background: var(--bad-soft); }
.whatif { color: var(--accent); border-bottom: 1px dashed var(--accent); line-height: 16px; }
input {
  width: 48px; padding: 1px 5px; border: 1px solid var(--accent); border-radius: var(--radius-sm);
  background: var(--accent-soft); color: var(--ink); text-align: right;
  font: 13px/18px var(--sans); font-variant-numeric: tabular-nums;
}
input::placeholder { color: var(--accent); opacity: .7; }
input[type=number] { -moz-appearance: textfield; }
input::-webkit-outer-spin-button, input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
b.num { font-weight: 600; color: var(--ink); }
/* The type switch: HW · Test · Task · CW, the current one filled like its tag. */
.types { display: inline-flex; border: 1px solid var(--line); border-radius: var(--radius-sm); overflow: hidden; }
.types button { font: 500 11px/20px var(--sans); padding: 0 8px; color: var(--dim); }
.types button + button { border-left: 1px solid var(--line); }
.types button[aria-pressed="true"] { color: var(--ink); background: var(--sunk); }
.types button[aria-pressed="true"].test { color: var(--surface); background: var(--ink); }
.types button:disabled { cursor: default; }
.more { width: 26px; height: 22px; border-radius: var(--radius-sm); color: var(--dim); font-size: 16px; line-height: 1; letter-spacing: 1px; }
.more:hover, .more[aria-expanded="true"] { background: var(--sunk); }
.menu { display: flex; justify-content: flex-end; }
.err { padding: 8px 0 0; }
`;

// The type switch's choices, in neo-plan's order (lib/parse.ts TYPE_ORDER), with
// the To Do sidebars' short names. Meetings aren't offered: one is made as one.
export const TYPE_CHOICES = [['assignment', 'HW'], ['exam', 'Test'], ['task', 'Task'], ['classwork', 'CW']];

// ── Where things go on Schoology's page ──────────────────────────────────

// On fuhsd.schoology.com (2026-09-26): the title bar has its own, empty
// p.due-date beside "Grade: N/A" (.grading-grade); the due date that shows
// is .assignment-details > p.due-date, above .info-body (the instructions).
// The sidebar's blocks are in #right-column-inner.

/** Schoology's due date line (the one with text), the title if there's none. */
function chipAnchor(doc, page) {
  if (page.quiz) return page.anchor?.isConnected ? page.anchor : null; // overlays/quizpage.js placed it
  const details = doc.querySelector('.assignment-details');
  if (details && cleanText(details)) return details;
  const due = [...doc.querySelectorAll('p.due-date')].find((p) => cleanText(p));
  if (due) return due;
  return page.anchor?.isConnected ? page.anchor : doc.querySelector('#center-top .page-title, #center-top h2, h2.page-title');
}

/** The points this is out of, as the page says it, or null (Schoology shows none until it's graded). */
function pagePoints(doc) {
  for (const n of [doc.querySelector('.grading-grade'), doc.querySelector('.assignment-details')]) {
    const p = n && pointsFromText(n.textContent);
    if (p) return p;
  }
  return null;
}

export function start({
  doc = document, loc = location, send = (m) => chrome.runtime.sendMessage(m), call = np,
  getDoc = fetchDoc, store = chromeStore(), now = () => Date.now(),
  // A newer quiz's start page (overlays/quizpage.js): { id, page, submitted }
  // from the quiz's own data. Its "submitted" is a finished attempt; it isn't
  // reported to the worker, which never reads quiz pages.
  quiz = null,
} = {}) {
  const id = quiz ? quiz.id : assignmentIdOf(loc.href);
  if (!id) return null;
  const page = quiz ? quiz.page : parseAssignmentPage(doc, loc.href);
  const section = page.section_id;

  const cardHost = createHost('np-asg-card', CARD_CSS);
  keepEvents(cardHost.host);
  const card = el('div', 'card');
  card.setAttribute('role', 'group');
  card.setAttribute('aria-label', 'Your status');
  cardHost.root.append(card);

  const state = {
    item: undefined, error: null, busy: false,
    submitted: false, late: undefined, submittedAt: null, dropbox: false,
    course: null, periods: [], weights: {},
    points: null, typedPoints: null, whatIf: false, menu: false,
  };

  // ── Placing ──
  function mount() {
    const rc = !page.quiz && doc.querySelector('#right-column-inner, #right-column');
    if (rc) {
      if (cardHost.host.parentNode !== rc || rc.firstElementChild !== cardHost.host) {
        cardHost.host.classList.remove('float', 'inflow');
        rc.prepend(cardHost.host);
      }
      return;
    }
    const anchor = chipAnchor(doc, page);
    if (anchor?.parentNode) {
      if (cardHost.host.previousElementSibling !== anchor) {
        cardHost.host.classList.remove('float');
        cardHost.host.classList.add('inflow');
        anchor.insertAdjacentElement('afterend', cardHost.host);
      }
    } else if (!cardHost.host.isConnected) {
      cardHost.host.classList.remove('inflow');
      cardHost.host.classList.add('float');
      doc.body.append(cardHost.host);
    }
  }

  // ── What the gradebook says about this assignment ──
  const found = () => findItem(state.periods, id);
  const possible = () => state.points ?? found()?.item.possible ?? state.typedPoints;
  const gradeHere = () => state.course?.gradeOf(id);

  // ── The facts the card is drawn from ──
  function facts() {
    const it = state.item;
    const ck = chipState(gradeHere(), it, state.submitted);
    const kind = ck === 'open' ? rowState(undefined, it).kind : ck; // missing / ready / todo, or the chip's
    const exam = it ? it.type === 'exam' : !!page.quiz;
    const due = page.due_at || it?.due?.at || null;
    const t = now();
    const dueMs = due ? Date.parse(due) : NaN;
    const overdue = !Number.isNaN(dueMs) && dueMs < t;
    const days = overdue ? calendarDays(new Date(t), new Date(dueMs)) : 0;
    const f = { kind, exam, quiz: !!page.quiz, due, overdue, days };
    // neo-plan hasn't said (or there's none): don't claim it's late or owed.
    if (kind === 'unknown') return { ...f, overdue: false, it, late: false, owed: false };
    return { ...f, it, late: showsLate(f), owed: isOwed(f) };
  }

  // ── The card ──
  const sections = {
    head: el('div', 'head'), lines: el('div', 'lines'), acts: el('div', 'acts'),
    foot: el('div', 'foot'), err: el('div', 'err'),
  };
  card.append(...Object.values(sections));
  let lastStamp = null; // the stamp's words last drawn: it slams only when they change

  function draw() {
    const f = facts();
    const { kind } = f;
    const t = new Date(now());
    drawHead(f, t);
    drawLines(f, t);
    const btns = buttons(f);
    sections.acts.replaceChildren(...btns);
    sections.acts.hidden = !btns.length;
    drawFoot(f);
    sections.err.replaceChildren();
    if (state.error) sections.err.append(el('span', state.error.quiet ? 'dim' : 'bad', state.error.text));
    sections.err.hidden = !state.error;
    // Nothing to say yet (neo-plan hasn't answered, no due date, no grade): no card.
    cardHost.host.hidden = kind === 'unknown' && !f.due && !sections.foot.childNodes.length && !state.error;
  }

  function drawHead(f, t) {
    const { it } = f;
    const head = sections.head;
    head.className = `head${f.late ? ' late' : ''}`;
    head.replaceChildren();
    const n = Number(it?.class?.colour_index);
    head.style.setProperty('--cls', it?.class ? `var(--class-${n >= 1 && n <= 8 ? n : 'none'})` : 'var(--line)');
    if (it?.class && f.kind !== 'none' && f.kind !== 'removed') {
      const c = el('span', 'cls');
      c.append(classDot(it.class), el('span', null, it.class.short_code || it.class.name || ''));
      head.append(c);
    }
    const word = state.submitted || !it ? 'Submitted' : clearedWord(it);
    const s = stampFor({ ...f, word }, t);
    head.hidden = !s && !head.childNodes.length;
    if (!s) { lastStamp = ''; return; }
    const stamp = el('span', `stamp ${s.tone}`.trim());
    if (s.s1) stamp.append(el('span', 's1', s.s1));
    stamp.append(el('span', 's2', s.s2));
    stamp.setAttribute('aria-label', [s.s2, s.s1].filter(Boolean).join(', '));
    const sig = `${s.s1}|${s.s2}`;
    if (lastStamp && sig !== lastStamp) stamp.classList.add('slam'); // feedback: only when it changes, never on load
    lastStamp = sig;
    head.append(stamp);
  }

  const circle = (exam) => { const c = el('span', exam ? 'circle sq' : 'circle'); c.setAttribute('aria-hidden', 'true'); return c; };
  function st(cls, exam, word) { const s = el('span', `st ${cls}`.trim()); s.append(circle(exam), el('span', null, word)); return s; }

  function drawLines(f, t) {
    const { it, kind, exam, late } = f;
    const status = el('div', 'status');
    // Missing is Schoology's word and stays until it's handed in; your own Done shows beside it.
    if (kind === 'missing') status.append(el('span', 'pill', 'Missing'), it.work === 'ready'
      ? st(`done${late ? ' late' : ''}`, exam, exam ? 'Studied' : state.dropbox ? 'Done, not submitted' : 'Done, not turned in')
      : st(late ? 'late' : '', exam, exam ? 'Not studied' : 'To do'));
    else if (kind === 'todo') status.append(st(exam ? 'exam' : '', exam, exam ? 'Not studied' : 'To do'));
    else if (kind === 'ready') status.append(st(`done${late ? ' late' : ''}`, exam, exam ? 'Studied' : state.dropbox ? 'Done, not submitted' : 'Done, not turned in'));
    else if (kind === 'submitted') {
      status.append(st('cleared', false, state.submitted || !it ? 'Submitted' : clearedWord(it)));
      const at = state.submittedAt ?? (it?.cleared_at ? Date.parse(it.cleared_at) : null);
      const d = lateDays(f.due, at);
      if (state.late === true || (state.late !== false && d > 0)) status.append(el('span', 'dim', lateText(d)));
    } else if (kind === 'graded') {
      const g = gradeHere();
      status.append(st('cleared', false, scoreText(g.earned, g.possible)));
    } else if (kind === 'removed') {
      status.append(el('span', 'removed', 'Removed'), linkBtn('Add back', 'Add back to your list', () => change('restore')));
    } else if (kind === 'none') {
      status.append(linkBtn('+ Add to list', 'Add to your list', add));
    }
    const lines = [status];
    if (f.due) {
      const due = el('div', 'due', formatDue(f.due, t));
      const tail = kind === 'submitted' || kind === 'graded' ? { text: '' } : dueTail(f, t);
      if (tail.text) due.append(' · ', el('span', tail.late ? 'over' : null, tail.text));
      lines.push(due);
    }
    sections.lines.replaceChildren(...lines.filter((n) => n.childNodes.length));
    sections.lines.hidden = !sections.lines.childNodes.length;
  }

  function buttons(f) {
    const { it, kind, exam } = f;
    if (!it || state.error?.offline) return [];
    const out = [];
    const done = kind === 'ready' || (kind === 'missing' && it.work === 'ready');
    if (!done && (kind === 'missing' || kind === 'todo')) {
      out.push(iconBtn('check', exam ? 'Studied' : 'Done', '', exam ? 'Mark studied' : 'Mark done', (e) => setWork('ready', e?.currentTarget), state.busy));
    } else if (done) {
      out.push(iconBtn('undo', exam ? 'Not studied' : 'Not done', '', exam ? 'Mark not studied' : 'Mark not done', () => setWork('todo'), state.busy));
    }
    // Turn in: work with no Schoology dropbox (paper, a lab site). Dropbox work
    // is handed in with Schoology's own Submit, right under the card.
    if (it.type === 'assignment' && !state.dropbox && !page.quiz && (kind === 'missing' || kind === 'todo' || kind === 'ready')) {
      out.push(iconBtn('turnIn', 'Turn in', 'go', 'Turn in', () => change('turnIn'), state.busy));
    } else if (kind === 'submitted' && it.cleared && it.cleared_by !== 'schoology' && !state.submitted) {
      out.push(iconBtn('undo', 'Undo', '', 'Undo turn in', () => change('putBack'), state.busy));
    }
    return out;
  }

  function linkBtn(text, label, fn, cls = 'linkbtn') {
    const b = el('button', cls, text);
    b.setAttribute('aria-label', label);
    b.disabled = state.busy;
    onClick(b, fn);
    return b;
  }

  // ── The foot: the grade, the what-if, the type ──
  const score = numInput('What-if score');
  score.placeholder = 'score';
  const pts = numInput('Points it is out of');
  pts.placeholder = 'pts';
  const wiOut = el('span', 'grade');
  const wiLine = el('div', 'grade');
  score.addEventListener('input', drawWhatIf);
  pts.addEventListener('input', () => { state.typedPoints = num(pts.value); drawWhatIf(); });
  for (const i of [score, pts]) {
    i.addEventListener('keydown', (e) => { if (e.key === 'Escape') { state.whatIf = false; draw(); } });
  }

  function numInput(label) {
    const i = el('input');
    i.type = 'number';
    i.min = '0';
    i.step = 'any';
    i.inputMode = 'decimal';
    i.setAttribute('aria-label', label);
    return i;
  }

  /** The course's grade is known, and this one isn't graded: a what-if can be worked out. */
  function canWhatIf() {
    const f = found();
    return !!f && !gradeHere() && courseGrade(f.course, { weights: state.weights }).pct != null;
  }

  let wiShape = '';
  function drawWhatIf() {
    const f = found();
    if (!f) return;
    // "/ 10" when the page or the gradebook says what it's out of, else a box for it.
    const shape = state.points ?? f.item.possible ?? 'typed';
    if (shape !== wiShape || !wiLine.childNodes.length) {
      wiShape = shape;
      wiLine.replaceChildren(document.createTextNode('If I get'), score, document.createTextNode('/'));
      wiLine.append(shape === 'typed' ? pts : el('span', 'num', String(shape)));
      wiLine.append(wiOut);
    }
    const r = whatIfGrade(f.course, id, num(score.value), possible(), state.weights);
    wiOut.replaceChildren();
    if (!r) return;
    wiOut.append(document.createTextNode('→'), el('b', 'num', `${r.pct.toFixed(2)}%`));
    if (r.delta != null && Math.abs(r.delta) >= EPS) wiOut.append(el('span', `imp num ${r.delta < 0 ? 'neg' : 'pos'}`, signed(r.delta)));
  }

  function gradeLine(f) {
    const { kind } = f;
    const line = el('div', 'grade');
    const fd = found();
    if (kind === 'graded') {
      if (!fd) return line;
      const d = impact(fd.course, id, { weights: state.weights });
      if (d != null) {
        if (Math.abs(d) < EPS) line.append('Didn’t move your course grade');
        else line.append('Moved your course grade', el('span', `imp num ${d < 0 ? 'neg' : 'pos'}`, signed(d)));
      }
      const r = rankIn(fd.category, id);
      if (r) line.append(el('span', 'rank', rankText(r, fd.category.title)));
      return line;
    }
    if (kind === 'removed' || kind === 'submitted' || !canWhatIf()) return line;
    if (kind === 'missing') {
      const z = zeroCost(fd.course, id, possible(), state.weights);
      if (z != null && Math.abs(z) >= EPS) line.append('A zero costs', el('span', 'imp num neg', signed(z)), '·');
    }
    const w = el('button', 'whatif', 'What if?');
    w.setAttribute('aria-expanded', 'false');
    onClick(w, () => { state.whatIf = true; draw(); setTimeout(() => score.focus()); });
    line.append(w);
    return line;
  }

  function drawFoot(f) {
    const { it, kind } = f;
    const foot = sections.foot;
    // While the what-if is open and still makes sense, leave its line (and the
    // focus in its boxes) alone: only its result is redrawn.
    const keepWi = state.whatIf && canWhatIf() && kind !== 'removed' && kind !== 'submitted';
    if (!keepWi) state.whatIf = false;
    const kids = [];
    if (keepWi) { drawWhatIf(); kids.push(wiLine); } else {
      const g = gradeLine(f);
      if (g.childNodes.length) kids.push(g);
    }
    const open = kind === 'missing' || kind === 'todo' || kind === 'ready';
    if (it && kind !== 'removed' && it.type !== 'meeting') {
      const row = el('div', 'row2');
      row.append(typeSwitch(it));
      if (open) {
        const more = el('button', 'more', '⋯');
        more.title = 'More';
        more.setAttribute('aria-label', 'More');
        more.setAttribute('aria-expanded', String(state.menu));
        onClick(more, () => { state.menu = !state.menu; draw(); });
        row.append(more);
      }
      kids.push(row);
      if (open && state.menu) {
        const m = el('div', 'menu');
        m.append(linkBtn('Remove from list', 'Remove from your list', () => { state.menu = false; change('remove'); }, 'linkbtn dim'));
        kids.push(m);
      }
    }
    // Keep the what-if line where it is: swap the rest around it.
    if (keepWi && foot.firstChild === wiLine) {
      while (wiLine.nextSibling) wiLine.nextSibling.remove();
      foot.append(...kids.slice(1));
    } else foot.replaceChildren(...kids);
    foot.hidden = !kids.length;
  }

  function typeSwitch(it) {
    const box = el('span', 'types');
    box.setAttribute('role', 'group');
    box.setAttribute('aria-label', 'Type');
    for (const [type, word] of TYPE_CHOICES) {
      const b = el('button', type === 'exam' ? 'test' : '', word);
      b.setAttribute('aria-pressed', String(it.type === type));
      b.disabled = state.busy;
      if (it.type !== type) onClick(b, () => setType(type));
      box.append(b);
    }
    return box;
  }

  // ── neo-plan: this assignment ──
  function fail(r) {
    if (r.status === 401) state.error = null; // no neo-plan: say nothing about it
    else state.error = { text: failText(r) };
  }

  async function load() {
    const r = await call('items', { schoology_ids: [id] });
    if (!r.ok) { fail(r); draw(); return; }
    state.error = null;
    state.item = (r.data?.items || []).find((x) => String(x.source_id) === id) || null;
    draw();
  }

  async function add() {
    if (state.busy) return;
    state.busy = true; state.error = null; draw();
    const r = await call('addItem', {
      item: { schoology_id: id, section_id: section, title: page.title, due_at: page.due_at, source_url: loc.origin + loc.pathname },
    });
    state.busy = false;
    if (r.ok && r.data) state.item = r.data; else fail(r);
    draw();
  }

  // Remove from neo-plan, or add it back: the item as it is after.
  async function change(op) {
    const it = state.item;
    if (state.busy || !it) return;
    state.busy = true; state.error = null; draw();
    const r = await call(op, { id: it.id });
    state.busy = false;
    if (r.ok && r.data) state.item = r.data; else fail(r);
    draw();
  }

  // HW ↔ Test ↔ Task ↔ CW. A switch neo-plan can't make says why ("Set a date first").
  async function setType(itemType) {
    const it = state.item;
    if (state.busy || !it) return;
    state.busy = true; state.error = null; draw();
    const r = await call('setType', { id: it.id, itemType });
    state.busy = false;
    if (r.ok && r.data) state.item = r.data; else fail(r);
    draw();
  }

  // Done ↔ not done (studied ↔ not studied): flips at once, back if neo-plan refuses.
  // Marking it done plays the To Do sidebars' done ripple from the button.
  async function setWork(work, button) {
    const it = state.item;
    if (state.busy || !it) return;
    if (work === 'ready' && button) rippleFrom(button, card);
    state.busy = true; state.error = null;
    state.item = { ...it, work };
    draw();
    const r = await call('work', { id: it.id, work });
    state.busy = false;
    if (r.ok && r.data) state.item = r.data;
    else { state.item = it; fail(r); }
    draw();
  }

  // ── Submit detection ──────────────────────────────────────────────────
  let reported = false;
  let checked = false; // the first look: a submission seen later was made in this view
  async function checkSubmitted() {
    if (quiz) {
      if (quiz.submitted && !state.submitted) { state.submitted = true; draw(); }
      return;
    }
    const st = parseSubmissionStatus(doc, loc.href);
    const first = !checked;
    checked = true;
    const dropbox = st.state === 'submitted' || st.state === 'not_submitted';
    if (dropbox !== state.dropbox) { state.dropbox = dropbox; draw(); }
    if (st.state !== 'submitted') return;
    if (typeof st.late === 'boolean') state.late = st.late;
    if (!state.submitted) {
      state.submitted = true;
      if (!first) state.submittedAt = now();
      draw();
    }
    if (reported) return;
    reported = true;
    let r = null;
    const uid = pageUserId(doc);
    try { r = await send({ type: 'submitted', schoology_id: id, ...(typeof st.late === 'boolean' ? { late: st.late } : {}), ...(uid ? { uid } : {}) }); } catch { /* worker asleep */ }
    if (!r?.ok) { reported = false; return; }
    if (!r.data?.already) load(); // neo-plan may have just cleared it
  }

  // ── The course: its gradebook ──
  async function readCourse() {
    if (!section) return;
    const course = await openCourse({ section, origin: loc.origin, store, getDoc, now });
    state.course = course;
    state.weights = await loadWeights(section);
    await course.readGrades();
    state.periods = periodsFromRows(course.rows || []);
    draw();
    await course.saveCache();
  }

  const onChange = debounce(() => { mount(); checkSubmitted(); }, 800);
  const observer = new MutationObserver((records) => {
    const ours = (n) => n.nodeType === 1 && /^np-/.test(n.localName);
    if (records.every((rec) => [...rec.addedNodes].every(ours) && [...rec.removedNodes].every(ours))) return;
    onChange();
  });
  observer.observe(doc.body, { childList: true, subtree: true });

  // The countdown and "N days late" move with the clock.
  const tick = setInterval(draw, 60_000);
  tick.unref?.();

  state.points = pagePoints(doc);
  mount();
  draw();
  checkSubmitted();
  const ready = Promise.all([load(), readCourse().catch((e) => console.debug('[neo-plan overlay]', e))]);
  return {
    host: cardHost.host, card, state, ready,
    checkSubmitted, observer, stop: () => { observer.disconnect(); clearInterval(tick); },
  };
}

const num = (v) => { const n = parseFloat(v); return Number.isFinite(n) ? n : null; };

if (typeof chrome !== 'undefined' && chrome.runtime?.id && typeof location !== 'undefined' && !globalThis.__npNoAutoStart) start();
