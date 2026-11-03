// Done / Studied / Turned in / Removed, kept in this browser when no neo-plan
// token is saved. The same ops and the same Item shape neo-plan answers with
// (outputs/neoplan/api.js), so the overlays (marks.js, materials.js,
// assignment.js) work unchanged: background.js sends a content script's item
// ops here instead of to neo-plan.
//
// Storage: chrome.storage.local, one key per Schoology account,
// `localMarks:<host>:<user id>` → { v: 1, items: { [schoology_id]: Item },
// portedAt? } (portedAt: when port.js last sent them to neo-plan, ms).
// Schoology signing the student out (about once a day) doesn't touch it, so the
// same account gets its marks back at the next sign-in; another account on the
// same Chrome gets a key of its own. The user id is Schoology's own, read from
// the page that asked (overlays/ui.js pageUserId), so it is always whoever is
// signed in there and never changes.
//
// Size: an Item is ~300 bytes and To Do adds one per piece of work, so a school
// year is a few hundred KB. Items untouched for KEEP_DAYS go, and past
// MAX_ITEMS the least recently changed go first.
//
// Pure apart from `store` (tests pass a plain object with get/set).

const VERSION = 1;
const PREFIX = 'localMarks:';
const KEEP_DAYS = 400; // a school year and the summer after it
const MAX_ITEMS = 3000;
const ID_RE = /^\d{1,20}$/;
const TYPES = new Set(['assignment', 'exam', 'classwork', 'task']);
// The iOS app's rule (ItemState.swift): these words in the title make it a test.
const TEST_RE = /\b(exam|quiz|interro|test|midterm|final exam)\b/i;

export const LOCAL_OPS = new Set(['items', 'addItem', 'work', 'setType', 'turnIn', 'putBack', 'remove', 'restore']);

export function storeKey(host, uid) {
  return `${PREFIX}${String(host).toLowerCase()}:${String(uid).trim()}`;
}

/** Item ids are the Schoology id with a prefix, so they can't be mistaken for neo-plan's. */
const itemId = (sid) => `local-${sid}`;
const sidOf = (id) => (/^local-(\d{1,20})$/.exec(String(id ?? '')) || [])[1] || null;

export function guessType(title, given) {
  if (given === 'exam' || given === 'assignment') return given;
  return TEST_RE.test(String(title || '')) ? 'exam' : 'assignment';
}

const ok = (data) => ({ ok: true, status: 200, data });
const fail = (status, error, problem) => ({ ok: false, status, data: problem ? { error, problem } : { error } });

/**
 * One op on one account's items. → { reply, items } where `items` is the new
 * map when something changed, else null. Pure; `now` is an ISO string.
 */
export function applyOp(items, msg, now) {
  switch (msg?.op) {
    case 'items': {
      const ids = [...new Set((msg.schoology_ids || []).map(String))];
      if (!ids.length || ids.length > 100 || !ids.every((x) => ID_RE.test(x))) return { reply: fail(400, 'body'), items: null };
      return { reply: ok({ items: ids.map((sid) => items[sid]).filter(Boolean) }), items: null };
    }
    case 'addItem': {
      const b = msg.item || {};
      const sid = String(b.schoology_id ?? '');
      if (!ID_RE.test(sid)) return { reply: fail(400, 'body'), items: null };
      // Already there (removed or not): answer with it, as neo-plan does.
      if (items[sid]) return { reply: ok(items[sid]), items: null };
      const title = String(b.title || '').trim().slice(0, 500) || 'Untitled';
      const due = typeof b.due_at === 'string' && !Number.isNaN(Date.parse(b.due_at)) ? b.due_at : null;
      const it = {
        id: itemId(sid),
        source_id: sid,
        section_id: b.section_id != null && ID_RE.test(String(b.section_id)) ? String(b.section_id) : null,
        title,
        type: guessType(title, b.type),
        work: 'todo',
        removed: false,
        cleared: false,
        cleared_by: null,
        cleared_at: null,
        missing: false,
        due: due ? { at: due } : null,
        source_url: String(b.source_url || ''),
        local: true,
        created_at: now,
        updated_at: now,
      };
      return { reply: ok(it), items: { ...items, [sid]: it } };
    }
    case 'work': case 'setType': case 'turnIn': case 'putBack': case 'remove': case 'restore': {
      const sid = sidOf(msg.id);
      const it = sid && items[sid];
      if (!it) return { reply: fail(404, 'not_found'), items: null };
      let next;
      if (msg.op === 'work') {
        if (msg.work !== 'todo' && msg.work !== 'ready') return { reply: fail(400, 'work'), items: null };
        next = { ...it, work: msg.work };
      } else if (msg.op === 'setType') {
        if (!TYPES.has(msg.itemType)) return { reply: fail(400, 'type'), items: null };
        next = { ...it, type: msg.itemType };
      } else if (msg.op === 'turnIn') {
        if (it.type !== 'assignment') return { reply: fail(409, 'type', 'Only an assignment is turned in'), items: null };
        next = { ...it, work: 'ready', cleared: true, cleared_by: 'user', cleared_at: now };
      } else if (msg.op === 'putBack') {
        // Like neo-plan: back to done, not turned in.
        next = { ...it, work: 'ready', cleared: false, cleared_by: null, cleared_at: null };
      } else {
        next = { ...it, removed: msg.op === 'remove' };
      }
      next.updated_at = now;
      return { reply: ok(next), items: { ...items, [sid]: next } };
    }
    default:
      return { reply: fail(400, 'op'), items: null };
  }
}

/** Schoology says it was submitted (overlays/assignment.js's Submit): cleared, if it's on the list. */
export function applySubmitted(items, schoologyId, now) {
  const sid = String(schoologyId ?? '');
  const it = items[sid];
  if (!it || it.cleared_by === 'schoology') return null;
  return { ...items, [sid]: { ...it, work: 'ready', cleared: true, cleared_by: 'schoology', cleared_at: it.cleared_at || now, updated_at: now } };
}

/** Old items out: untouched for KEEP_DAYS, then the least recently changed past MAX_ITEMS. Pure. */
export function prune(items, nowMs, { keepDays = KEEP_DAYS, max = MAX_ITEMS } = {}) {
  const cutoff = nowMs - keepDays * 86_400_000;
  let list = Object.entries(items).filter(([, it]) => {
    const t = Date.parse(it?.updated_at);
    return !Number.isNaN(t) && t >= cutoff;
  });
  if (list.length > max) {
    list.sort((a, b) => Date.parse(b[1].updated_at) - Date.parse(a[1].updated_at));
    list = list.slice(0, max);
  }
  return Object.fromEntries(list);
}

// One read-modify-write at a time per key: two tabs marking at once would
// otherwise each write back the other's change undone.
const queues = new Map();
function serial(key, run) {
  const prev = queues.get(key) || Promise.resolve();
  const next = prev.then(run, run);
  queues.set(key, next.catch(() => {}));
  return next;
}

/** One account's record: { v, items, portedAt? }; a fresh one when there's none. */
export async function readRecord(store, key) {
  const got = (await store.get(key))[key];
  return got?.v === VERSION && got.items && typeof got.items === 'object' ? got : { v: VERSION, items: {} };
}

// Read, change, write back, one at a time for the key. `change(record)` → the
// new items, or null for no change.
function update(store, key, now, change) {
  return serial(key, async () => {
    const rec = await readRecord(store, key);
    const t = now();
    const out = change(rec, t);
    if (out.items) await store.set({ [key]: { ...rec, items: prune(out.items, t.getTime()) } });
    return out.value;
  });
}

/** Port bookkeeping (port.js): when the items were last sent to neo-plan. */
export function setPortedAt(key, at, store = chrome.storage.local) {
  return serial(key, async () => {
    const rec = await readRecord(store, key);
    await store.set({ [key]: { ...rec, portedAt: at } });
  });
}

/** One content-script item op for one account. Always resolves to { ok, status, data }. */
export function handleLocal(msg, { host, uid, store = chrome.storage.local, now = () => new Date() }) {
  const key = storeKey(host, uid);
  return update(store, key, now, (rec, t) => {
    const { reply, items } = applyOp(rec.items, msg, t.toISOString());
    return { items, value: reply };
  }).catch((e) => fail(0, String(e?.message || e), 'Not saved'));
}

export function markSubmittedLocal(schoologyId, { host, uid, store = chrome.storage.local, now = () => new Date() }) {
  const key = storeKey(host, uid);
  return update(store, key, now, (rec, t) => {
    const items = applySubmitted(rec.items, schoologyId, t.toISOString());
    return { items, value: !!items };
  });
}
