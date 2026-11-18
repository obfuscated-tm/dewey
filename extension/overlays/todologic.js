// The pure logic of a "To Do" panel row (dates, sort, type tag), split out of
// todorows.js so it loads without ui.js: the iOS app's engine
// (engine/engine.js) bundles it. No DOM, no chrome.*. todorows.js re-exports
// everything here.

// ── Pure date helpers ────────────────────────────────────────────────────

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
export const calendarDays = (a, b) => Math.round((startOfDay(a) - startOfDay(b)) / 86_400_000);

/**
 * "12 days late", "1 day late", or "late" when it was due earlier today: the
 * iOS app's words (ToDoView's dueText). days is calendar days overdue, ≥ 0.
 */
export function formatLate(days) {
  const n = Math.max(0, Math.round(days || 0));
  return n === 0 ? 'late' : `${n} day${n === 1 ? '' : 's'} late`;
}
/** The old name, kept for its importers. */
export const formatOverdue = formatLate;

// ── Late, owed, the class stamp and the countdowns (the iOS app's TodoLogic) ──
// `kind` is matstate.js's rowState() kind; `exam` whether it's a test.

const DONE_KINDS = new Set(['submitted', 'graded', 'removed']);

/**
 * Whether a row says late (red): due in the past and not handed in. A test is
 * late only when it's a Schoology quiz not yet studied for (TodoLogic.showsLate);
 * work ticked Done but not turned in stays late until it is. Pure.
 */
export function showsLate({ overdue, kind, exam, quiz }) {
  if (!overdue || DONE_KINDS.has(kind)) return false;
  if (exam) return !!quiz && kind !== 'ready';
  return true;
}

/** Whether the row is still owed: not handed in, and a test not studied yet. Pure. */
export function isOwed({ kind, exam }) {
  if (DONE_KINDS.has(kind)) return false;
  return exam ? kind !== 'ready' : true;
}

/**
 * The one stamp a class heading carries: 'late' when any row there shows late,
 * else 'dueToday' when owed work is due later today, else null (TodoLogic.cardStamp).
 * rows: [{ due, late, owed }]. Pure.
 */
export function cardStamp(rows, now = new Date()) {
  const at = (r) => (r.due ? new Date(r.due) : null);
  if (rows.some((r) => r.late && at(r) && at(r) < now)) return 'late';
  if (rows.some((r) => r.owed && at(r) && at(r) >= now && calendarDays(at(r), now) === 0)) return 'dueToday';
  return null;
}

/** "5 h 12 min left" / "42 min left" in the last 12 hours before the due time, else ''. Pure. */
export function timeLeft(due, now = new Date()) {
  if (!due) return '';
  const ms = new Date(due) - now;
  if (!(ms > 0) || ms > 12 * 3_600_000) return '';
  const m = Math.ceil(ms / 60_000);
  return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min left` : `${m} min left`;
}

/** A test's countdown: "today", "tomorrow", "in 3 days"; '' once it's past (Reminders.testCountdown). Pure. */
export function testCountdown(due, now = new Date()) {
  if (!due) return '';
  const d = new Date(due);
  if (!(d > now)) return '';
  const days = calendarDays(d, now);
  return days === 0 ? 'today' : days === 1 ? 'tomorrow' : `in ${days} days`;
}

/**
 * The words after the due date on a sidebar row, and whether they're red:
 * "· 3 days late" (late), "· in 3 days" (a test still to study), "· 5 h 12 min
 * left" (owed work in its last 12 hours), or nothing. Pure.
 */
export function dueTail({ due, overdue, days, kind, exam, quiz }, now = new Date()) {
  if (showsLate({ overdue, kind, exam, quiz })) return { text: formatLate(days), late: true };
  if (exam && isOwed({ kind, exam })) {
    const c = testCountdown(due, now);
    if (c) return { text: c, late: false };
  }
  if (!exam && isOwed({ kind, exam })) {
    const left = timeLeft(due, now);
    if (left) return { text: left, late: false };
  }
  return { text: '', late: false };
}

function formatTime(d) {
  let h = d.getHours();
  const m = d.getMinutes();
  const ap = h >= 12 ? 'pm' : 'am';
  h %= 12;
  if (h === 0) h = 12;
  return `${h}:${String(m).padStart(2, '0')} ${ap}`;
}

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * "Tue Sep 29, 8:30 am": always the full specific date and time, no relative
 * words ("Today"/"Tomorrow") and never dropping the time far out — the grid
 * layout gives every row the room for it. "No due date" when there is none;
 * '' only for a due string that fails to parse.
 */
export function formatDue(due, now = new Date()) {
  if (!due) return 'No due date';
  const d = new Date(due);
  if (Number.isNaN(d.getTime())) return '';
  return `${WEEKDAYS[d.getDay()]} ${MONTHS[d.getMonth()]} ${d.getDate()}, ${formatTime(d)}`;
}

/**
 * overdue + upcoming events, each tagged `{ overdue, days? }` (days: how many
 * calendar days overdue, for formatLate). Order is not meaningful yet —
 * sortDueItems does that. Pure.
 */
export function annotateItems({ overdue = [], upcoming = [], now = new Date() } = {}) {
  const out = [];
  for (const e of overdue) {
    const days = e.due ? calendarDays(now, new Date(e.due)) : 0;
    out.push({ ...e, overdue: true, days });
  }
  for (const e of upcoming) out.push({ ...e, overdue: false });
  return out;
}

/** Overdue first (oldest due first), then upcoming (soonest first). Pure, non-mutating. */
export function sortDueItems(items) {
  return [...items].sort((a, b) => {
    if (a.overdue !== b.overdue) return a.overdue ? -1 : 1;
    if (a.overdue) return (b.days ?? 0) - (a.days ?? 0); // oldest (most days overdue) first
    return new Date(a.due || 0) - new Date(b.due || 0); // soonest first
  });
}

// ── The rotated type tag (column 1) ─────────────────────────────────────────

export const TYPE_LABEL = { exam: 'TEST', assignment: 'HW', classwork: 'CW', task: 'TASK', meeting: 'MEET' };

/**
 * The type tag's text and CSS class, from the Item marks.js knows about (or
 * null: not in neo-plan; or undefined: not known yet). Pure — item.type wins
 * even when marks.js's own rowState() says 'unknown' (a graded row still
 * knows its item, just not its open/done state).
 */
export function tagFor(item) {
  if (item) {
    const text = TYPE_LABEL[item.type] || '';
    if (!text) return { text: '', cls: '' };
    return { text, cls: item.removed ? 'tag-faint' : item.type === 'exam' ? 'tag-solid' : 'tag-outline' };
  }
  if (item === null) return { text: '', cls: 'tag-empty' }; // not in neo-plan: the dashed prompt to add
  return { text: '', cls: '' }; // unknown/still loading: no border
}

/** How far overdue an item can be and still be added to neo-plan by itself. */
export const AUTO_ADD_OVERDUE_DAYS = 14;

/**
 * Whether a To Do item goes into neo-plan without a click when it isn't there
 * yet: anything upcoming, and overdue work from the last two weeks (older
 * overdue work was most likely left out on purpose). A graded item is done
 * with. One already in neo-plan, or removed from it, is never added again
 * (marks.js only auto-adds a row neo-plan doesn't know at all). Pure.
 */
export function autoAddable(item) {
  if (item.graded) return false;
  return !item.overdue || (item.days ?? 0) <= AUTO_ADD_OVERDUE_DAYS;
}

/** A quiz links to /course/{c}/assessments/{id}: neo-plan files it as a test. Pure. */
export function typeFor(item) {
  return /\/assessments?\//.test(String(item.url || '')) ? 'exam' : undefined;
}

