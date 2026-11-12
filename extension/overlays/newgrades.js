// New grades: which scores appeared or changed since the student last looked.
// The extension only sees a gradebook when its page is open, so "last looked"
// is the last time a Grades page showed this course (overlays/grades.js).
// Pure; the saved copy lives in chrome.storage.local (coursedata.js).

export const NEW_FOR_MS = 7 * 24 * 60 * 60 * 1000; // a mark nobody opened goes after a week, like the iOS app

const key = (it) => `${it.earned}/${it.possible}`;

/**
 * saved: { scores: { [item id]: "earned/possible" }, fresh: { [item id]: ms first seen new } } or null
 * items: the course's graded items, [{ id, earned, possible }]
 * → the copy to save next: every score as it is now, and `fresh`, the items
 * whose score is new or changed since `saved` (plus the still-unopened ones
 * from before, under a week old). A first look (saved null) marks nothing.
 */
export function freshGrades(saved, items, now = Date.now()) {
  const scores = {};
  const fresh = {};
  for (const it of items) {
    if (it.id == null || it.earned == null) continue;
    scores[it.id] = key(it);
    if (!saved) continue;
    const since = saved.fresh?.[it.id];
    if (saved.scores?.[it.id] !== scores[it.id]) fresh[it.id] = now;
    else if (since != null && now - since < NEW_FOR_MS) fresh[it.id] = since;
  }
  return { scores, fresh };
}
