// The pure logic of a "To Do" panel row (dates, sort, type tag), split out of
// todorows.js so it loads without ui.js: the iOS app's engine
// (engine/engine.js) bundles it. No DOM, no chrome.*. todorows.js re-exports
// everything here.

// ── Pure date helpers ────────────────────────────────────────────────────

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
export const calendarDays = (a, b) => Math.round((startOfDay(a) - startOfDay(b)) / 86_400_000);

/** "12 d overdue" (days is always ≥ 0; the caller only calls this for overdue items). */
export function formatOverdue(days) {
  return `${Math.max(0, Math.round(days))} d overdue`;
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
 * calendar days overdue, for formatOverdue). Order is not meaningful yet —
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

