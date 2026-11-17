// The background grade check: every 30 minutes, read each class's gradebook
// and mark the scores that are new or changed since the student last looked
// (overlays/newgrades.js, the same saved copy the Grades pages use). The
// count goes on the toolbar button's badge. One GET per class, through the
// shared limiter; read-only, like everything else here.

import { periodsFromRows, currentPeriod } from '../overlays/grademath.js';
import { freshGrades, SEEN_KEY, HIDDEN_KEY } from '../overlays/newgrades.js';

export const GRADE_CHECK_MIN = 30;

/**
 * courses: [{ section_id }]. getRows(sectionId) → the gradebook's rows
 * (reader/parse/grades.js), or null when the page couldn't be read. A class
 * hidden on the Grades page is skipped. A class that fails is left as it was.
 * → how many scores are new, over every class.
 */
export async function checkGrades({ courses, getRows, storage, now = Date.now() }) {
  const got = await storage.get([SEEN_KEY, HIDDEN_KEY]);
  const hidden = new Set(got[HIDDEN_KEY] || []);
  const next = {};
  for (const c of courses) {
    const id = String(c.section_id);
    if (hidden.has(id)) continue;
    const rows = await getRows(id);
    const course = rows?.length ? currentPeriod(periodsFromRows(rows)) : null;
    if (!course) continue;
    const graded = course.categories.flatMap((cat) => cat.items).filter((it) => it.earned != null);
    // Read the saved copy again: a Grades page may have marked this class seen meanwhile.
    const saved = (await storage.get(SEEN_KEY))[SEEN_KEY]?.[id] ?? null;
    next[id] = freshGrades(saved, graded, now);
  }
  const all = { ...((await storage.get(SEEN_KEY))[SEEN_KEY] || {}), ...next };
  await storage.set({ [SEEN_KEY]: all });
  return newCount(all, hidden);
}

/** The badge's number: unopened new scores in every class that isn't hidden. */
export function newCount(seen = {}, hidden = new Set()) {
  let n = 0;
  for (const [id, c] of Object.entries(seen || {})) if (!hidden.has(id)) n += Object.keys(c?.fresh || {}).length;
  return n;
}
