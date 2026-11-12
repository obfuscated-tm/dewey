// Grade math for the grades overlay (docs/extension/OVERLAY-UI.md, "Grade math"). Pure:
// no DOM, no chrome.*, no Date objects. Every percent is 0–100, unrounded;
// rounding is the caller's job.
//
// A course is { categories: [{ title, weight, items: [{ id, title, due,
// earned, possible }] }] }. `weight` is the category's percent from the
// gradebook, or null. `due` is 'YYYY-MM-DDTHH:MM' (wall-clock, no zone) or
// null. An item with earned === null has no grade ("—") and is left out, not
// counted as zero.
//
// Schoology lets a teacher make an item count for nothing (or half, or
// double) and shows a student no sign of it: the row reads "7 / 7" like any
// other. The only trace is the category's own percent, so periodsFromRows
// checks each category's sum against it (calibrate, below). An item with
// `uncounted` set is left out of all the math.
//
// Every function takes the same `opts`:
//   weights  { [category title]: percent } typed by Owen; beats the gradebook's
//   whatIf   { [item id]: earned | { earned, possible } }. On an ungraded
//            ("—") row this is the only source of a score, so possible must
//            come with it (a bare number, with no possible, is ignored). On a
//            graded row it overrides earned (and possible, if given) — the
//            real score stands in for whichever isn't given.
//   extra    [{ category, earned, possible }] planned items that don't exist yet
//   drop     item ids (array or Set) excluded from the math entirely, as if
//            they didn't exist — a real decision, not a hypothetical

/** The letter scale from the overlay demo. Not yet confirmed per course. */
export const SCALE = [['A', 93], ['A-', 90], ['B+', 87], ['B', 83], ['B-', 80], ['C+', 77], ['C', 73], ['C-', 70], ['D', 60], ['F', 0]];
export const A_CUTOFF = 93;
/** The grades a student can aim for (the iOS app's Goal.scale): A down to C-. */
export const GOALS = SCALE.filter(([, min]) => min >= 70);

export function letter(pct, scale = SCALE) {
  if (pct == null) return null;
  return (scale.find(([, min]) => pct >= min) || scale[scale.length - 1])[0];
}

// ── Reading Schoology's text ─────────────────────────────────────────────

/** "19.05 / 20" → { earned: 19.05, possible: 20 }; "—" or "" → { earned: null, possible: null }. */
export function parseScore(text) {
  const m = String(text || '').match(/(-?\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/);
  return m ? { earned: Number(m[1]), possible: Number(m[2]) } : { earned: null, possible: null };
}

/** "8/17/26 11:59pm" → '2026-08-17T23:59'; anything else → null. */
export function parseDue(text) {
  const m = String(text || '').match(/\b(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})\s+(\d{1,2}):(\d{2})\s*([ap])m\b/i);
  if (!m) return null;
  let h = Number(m[4]) % 12;
  if (m[6].toLowerCase() === 'p') h += 12;
  const y = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
  const p2 = (n) => String(n).padStart(2, '0');
  return `${y}-${p2(m[1])}-${p2(m[2])}T${p2(h)}:${m[5]}`;
}

/** A category row's text, "Labs & Homework — Category (25%)" → 25; no "(N%)" → null. */
export function weightFromText(text) {
  const all = [...String(text || '').matchAll(/\((\d+(?:\.\d+)?)\s*%\)/g)];
  return all.length ? Number(all[all.length - 1][1]) : null;
}

/** "A (97.67%)" → { pct: 97.67, tol: 0.005 }; "95.9%" → { pct: 95.9, tol: 0.05 }; no percent → null. */
export function statedPct(text) {
  const m = String(text || '').match(/(\d+(?:\.(\d+))?)\s*%/);
  return m ? { pct: Number(m[1]), tol: 0.5 * 10 ** -(m[2]?.length || 0) } : null;
}

/** Is `pct` what Schoology showed, rounded or cut off to the digits it shows? */
const agrees = (pct, s) => Math.abs(pct - s.pct) <= s.tol + 1e-9 || (pct > s.pct && pct - s.pct < 2 * s.tol);

/**
 * Makes a category's sum agree with the percent Schoology shows for it
 * (`cat.stated`). When they differ, some items don't count the way they read.
 * Looks for the fewest graded items (up to 4) that, left out, give Schoology's
 * percent, and marks them `uncounted`: 'sure' when only those items fit,
 * 'guess' when others with the same score would fit as well (the totals are
 * right either way). When no such set exists, or sets with different totals
 * fit, sets `cat.scale` instead: the factor that brings the category's earned
 * points to Schoology's percent.
 */
export function calibrate(cat) {
  const s = cat.stated;
  const graded = cat.items.filter((it) => it.earned != null && it.possible != null && (it.possible > 0 || it.earned !== 0));
  if (!s || !graded.length) return;
  let E = 0, P = 0;
  for (const it of graded) { E += it.earned; P += it.possible; }
  if (P <= 0 || agrees((E / P) * 100, s)) return;

  // Items with the same score are interchangeable: choose how many of each.
  const groups = new Map();
  for (const it of graded) {
    const k = `${it.earned}/${it.possible}`;
    if (!groups.has(k)) groups.set(k, { earned: it.earned, possible: it.possible, items: [] });
    groups.get(k).items.push(it);
  }
  const gs = [...groups.values()];
  let budget = 60000;
  for (let k = 1; k <= 4; k++) {
    const found = [];
    const pick = new Array(gs.length).fill(0);
    const walk = (i, left, e, p) => {
      if (budget-- <= 0) return;
      if (left === 0) {
        if (P - p > 0 && agrees(((E - e) / (P - p)) * 100, s)) found.push({ e, p, pick: [...pick] });
        return;
      }
      if (i >= gs.length) return;
      for (let n = Math.min(left, gs[i].items.length); n >= 0; n--) {
        pick[i] = n;
        walk(i + 1, left - n, e + n * gs[i].earned, p + n * gs[i].possible);
      }
      pick[i] = 0;
    };
    walk(0, k, 0, 0);
    if (budget <= 0) break;
    if (!found.length) continue;
    const f = found[0];
    if (!found.every((x) => Math.abs(x.e - f.e) < 1e-9 && Math.abs(x.p - f.p) < 1e-9)) break;
    gs.forEach((g, i) => {
      const sure = found.length === 1 && f.pick[i] === g.items.length;
      for (const it of g.items.slice(0, f.pick[i])) it.uncounted = sure ? 'sure' : 'guess';
    });
    return;
  }
  if (E > 0) cat.scale = ((s.pct / 100) * P) / E;
}

/**
 * parseGrades() rows → [{ title, categories }], one per grading period, in page
 * order. Category and item rows before any period row go in an untitled one.
 * Periods, categories and items keep the row they came from as `src`.
 */
export function periodsFromRows(rows) {
  const periods = [];
  let period = null;
  let category = null;
  for (const r of rows) {
    if (r.level === 'course') continue;
    if (r.level === 'period') {
      period = { title: r.title, categories: [], src: r };
      periods.push(period);
      category = null;
    } else if (r.level === 'category') {
      if (!period) periods.push((period = { title: '', categories: [] }));
      category = { title: r.title, weight: r.weight ?? weightFromText(r.title), stated: statedPct(r.grade), items: [], src: r };
      period.categories.push(category);
    } else if (r.level === 'item' && category) {
      const { earned, possible } = parseScore(r.grade);
      category.items.push({ id: r.id ?? `${category.title}/${r.title}/${r.due}`, title: r.title, due: parseDue(r.due), earned, possible, src: r });
    }
  }
  for (const p of periods) for (const c of p.categories) calibrate(c);
  return periods;
}

/** The last period with a graded item, or null. That's the one the course grade reads. */
export function currentPeriod(periods) {
  for (let i = periods.length - 1; i >= 0; i--) {
    if (periods[i].categories.some((c) => c.items.some((it) => it.earned != null))) return periods[i];
  }
  return null;
}

// ── The grade ────────────────────────────────────────────────────────────

/** The items that count, after drops, what-ifs and planned items, grouped by category title. */
function counted(course, { whatIf = {}, extra = [], drop } = {}) {
  const dropped = drop instanceof Set ? drop : new Set(drop || []);
  const byCat = new Map(course.categories.map((c) => [c.title, []]));
  for (const c of course.categories) {
    for (const it of c.items) {
      if (dropped.has(it.id) || it.uncounted) continue;
      const w = whatIf[it.id];
      if (w != null) {
        const ws = typeof w === 'object' ? w : { earned: w };
        const earned = ws.earned ?? it.earned;
        const possible = ws.possible ?? it.possible;
        if (earned != null && possible != null) { byCat.get(c.title).push({ ...it, earned, possible }); continue; }
      }
      if (it.earned != null) byCat.get(c.title).push(c.scale ? { ...it, earned: it.earned * c.scale } : it);
    }
  }
  for (const x of extra) {
    if (x.earned == null || !x.possible) continue;
    if (!byCat.has(x.category)) byCat.set(x.category, []);
    byCat.get(x.category).push(x);
  }
  return byCat;
}

function weightOf(course, title, weights = {}) {
  if (weights[title] != null) return weights[title];
  return course.categories.find((c) => c.title === title)?.weight ?? null;
}

/**
 * → { pct, mode, categories: [{ title, weight, earned, possible, pct }] }.
 * mode is 'weighted' when every category with grades has a weight, else
 * 'points' (total points, the fallback). pct is null when nothing is graded.
 */
export function grade(course, opts = {}) {
  const cats = [];
  for (const [title, items] of counted(course, opts)) {
    let earned = 0, possible = 0;
    for (const it of items) { earned += it.earned; possible += it.possible; }
    cats.push({ title, weight: weightOf(course, title, opts.weights), earned, possible, pct: possible > 0 ? (earned / possible) * 100 : null });
  }
  const graded = cats.filter((c) => c.pct != null);
  const weighted = graded.length > 0 && graded.every((c) => c.weight != null);
  let pct = null;
  if (weighted) {
    const W = graded.reduce((s, c) => s + c.weight, 0);
    if (W > 0) pct = graded.reduce((s, c) => s + c.weight * c.pct, 0) / W;
  } else {
    const P = graded.reduce((s, c) => s + c.possible, 0);
    if (P > 0) pct = (graded.reduce((s, c) => s + c.earned, 0) / P) * 100;
  }
  return { pct, mode: weighted ? 'weighted' : 'points', categories: cats };
}

function without(course, id) {
  return { ...course, categories: course.categories.map((c) => ({ ...c, items: c.items.filter((it) => it.id !== id) })) };
}

/**
 * Course % with the item − course % without it. An ungraded item counts only
 * through opts.whatIf (so "a zero costs" is impact with whatIf { id: 0 }).
 * null when the item doesn't count or the course has no grade without it.
 */
export function impact(course, id, opts = {}) {
  const withIt = grade(course, opts);
  const counts = [...counted(course, opts).values()].some((items) => items.some((it) => it.id === id));
  if (!counts || withIt.pct == null) return null;
  const rest = grade(without(course, id), opts).pct;
  return rest == null ? null : withIt.pct - rest;
}

/**
 * Graded items replayed in due-date order, the course % recomputed after each
 * day → [{ day: 'YYYY-MM-DD' | null, pct, items: [title] }]. What-ifs, planned
 * items and drops are not history and are ignored: it's the real term.
 * Undated items come last.
 */
export function history(course, opts = {}) {
  const weights = opts.weights;
  const all = course.categories.flatMap((c) => c.items.filter((it) => it.earned != null).map((it) => ({ it, cat: c.title })));
  all.sort((a, b) => (a.it.due == null) - (b.it.due == null) || (a.it.due || '').localeCompare(b.it.due || ''));
  const out = [];
  const shown = new Set();
  for (let i = 0; i < all.length; i++) {
    const day = all[i].it.due ? all[i].it.due.slice(0, 10) : null;
    shown.add(all[i].it.id);
    const last = out[out.length - 1];
    if (last && last.day === day) last.items.push(all[i].it.title);
    else out.push({ day, pct: null, items: [all[i].it.title] });
    const next = all[i + 1];
    if (next && (next.it.due ? next.it.due.slice(0, 10) : null) === day) continue;
    const so_far = { ...course, categories: course.categories.map((c) => ({ ...c, items: c.items.filter((it) => shown.has(it.id)) })) };
    out[out.length - 1].pct = grade(so_far, { weights }).pct;
  }
  return out;
}

/**
 * The lowest this category's % can drop, the others unchanged, with the course
 * still at `target` or above. ≤ 0: any score holds it. > 100: it can't, with
 * this category alone. null: the category has no grades yet.
 */
export function categoryFloor(course, title, target = A_CUTOFF, opts = {}) {
  const g = grade(course, opts);
  const cat = g.categories.find((c) => c.title === title);
  if (!cat || cat.pct == null) return null;
  const others = g.categories.filter((c) => c.pct != null && c !== cat);
  if (g.mode === 'weighted') {
    const W = others.reduce((s, c) => s + c.weight, 0) + cat.weight;
    if (!cat.weight) return g.pct >= target ? -Infinity : Infinity;
    return (target * W - others.reduce((s, c) => s + c.weight * c.pct, 0)) / cat.weight;
  }
  const P = others.reduce((s, c) => s + c.possible, 0) + cat.possible;
  const E = others.reduce((s, c) => s + c.earned, 0);
  return ((target / 100) * P - E) / cat.possible * 100;
}

/**
 * A planned item worth `possible` points in `category`: what score keeps the
 * course at `target`? → { status: 'any' } (a zero still does), { status: 'out' }
 * (a full score doesn't), or { status: 'need', need } with need in points,
 * exact (the caller rounds up).
 */
export function needFor(course, { category, possible, target = A_CUTOFF }, opts = {}) {
  const at = (earned) => grade(course, { ...opts, extra: [...(opts.extra || []), { category, earned, possible }] }).pct;
  const lo = at(0), hi = at(possible);
  if (lo >= target) return { status: 'any' };
  if (hi < target) return { status: 'out' };
  // The course % is linear in the score: the new item fixes every denominator.
  return { status: 'need', need: (possible * (target - lo)) / (hi - lo) };
}
