// node --test test/*.test.js   (from extension/)
//
// outputs/local/: Done / Studied / Turned in kept in this browser, per
// Schoology account, when no neo-plan token is saved; whose they are (ui.js
// pageUserId); and moving them to neo-plan (port.js).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyOp, applySubmitted, prune, storeKey, guessType, handleLocal, markSubmittedLocal, readRecord, setPortedAt } from '../outputs/local/marks.js';
import { hasOwnMark, toPort, markCalls, portItems, portText } from '../outputs/local/port.js';
import { pageUserId } from '../overlays/ui.js';

const NOW = '2026-11-03T16:00:00.000Z';
const HOST = 'fuhsd.schoology.com';

/** A chrome.storage area in memory. */
function memStore() {
  const data = {};
  return {
    data,
    async get(k) { return k in data ? { [k]: structuredClone(data[k]) } : {}; },
    async set(o) { for (const [k, v] of Object.entries(o)) data[k] = structuredClone(v); },
    async remove(k) { delete data[k]; },
  };
}

const add = (items, sid, extra = {}) => applyOp(items, { op: 'addItem', item: { schoology_id: sid, title: `Lab ${sid}`, due_at: '2026-11-05T15:00:00Z', ...extra } }, NOW);

test('addItem: an Item shaped like neo-plan\'s, and the same one back the second time', () => {
  const r = add({}, '42', { section_id: '7' });
  assert.equal(r.reply.ok, true);
  const it = r.reply.data;
  assert.equal(it.id, 'local-42');
  assert.equal(it.source_id, '42');
  assert.equal(it.section_id, '7');
  assert.equal(it.type, 'assignment');
  assert.equal(it.work, 'todo');
  assert.deepEqual(it.due, { at: '2026-11-05T15:00:00Z' });
  assert.equal(it.removed, false);
  assert.equal(it.cleared, false);
  const again = applyOp(r.items, { op: 'addItem', item: { schoology_id: '42', title: 'Changed' } }, NOW);
  assert.equal(again.items, null);
  assert.equal(again.reply.data.title, 'Lab 42');
  assert.equal(applyOp({}, { op: 'addItem', item: { schoology_id: 'x' } }, NOW).reply.status, 400);
});

test('guessType: a quiz link, or test words in the title', () => {
  assert.equal(guessType('Unit 3 Test', undefined), 'exam');
  assert.equal(guessType('Interro: le passé composé', undefined), 'exam');
  assert.equal(guessType('Chapter 4 quiz', undefined), 'exam');
  assert.equal(guessType('Lab 4 writeup', undefined), 'assignment');
  assert.equal(guessType('Lab 4 writeup', 'exam'), 'exam');
  assert.equal(guessType('Contest essay', undefined), 'assignment'); // "test" inside a word doesn't count
});

test('items: only the ones on the list; others are null to the markers', () => {
  const { items } = add({}, '1');
  const r = applyOp(items, { op: 'items', schoology_ids: ['1', '2'] }, NOW);
  assert.deepEqual(r.reply.data.items.map((x) => x.source_id), ['1']);
  assert.equal(r.items, null);
  assert.equal(applyOp(items, { op: 'items', schoology_ids: [] }, NOW).reply.status, 400);
});

test('work, turn in, put back, remove, restore, type', () => {
  let { items } = add({}, '1');
  const step = (msg) => { const r = applyOp(items, { id: 'local-1', ...msg }, NOW); if (r.items) items = r.items; return r.reply; };
  assert.equal(step({ op: 'work', work: 'ready' }).data.work, 'ready');
  assert.equal(step({ op: 'work', work: 'nope' }).status, 400);
  const t = step({ op: 'turnIn' }).data;
  assert.equal(t.cleared, true);
  assert.equal(t.cleared_by, 'user');
  const back = step({ op: 'putBack' }).data;
  assert.equal(back.cleared, false);
  assert.equal(back.work, 'ready'); // done, not turned in, as neo-plan does
  assert.equal(step({ op: 'remove' }).data.removed, true);
  assert.equal(step({ op: 'restore' }).data.removed, false);
  assert.equal(step({ op: 'setType', itemType: 'exam' }).data.type, 'exam');
  assert.equal(step({ op: 'turnIn' }).status, 409); // a test isn't turned in
  assert.equal(step({ op: 'setType', itemType: 'meeting' }).status, 400);
  assert.equal(applyOp(items, { op: 'work', id: 'local-9', work: 'ready' }, NOW).reply.status, 404);
  assert.equal(applyOp(items, { op: 'work', id: '1', work: 'ready' }, NOW).reply.status, 404); // a neo-plan-style id
});

test('applySubmitted: Schoology\'s submit clears an item on the list, once', () => {
  const { items } = add({}, '1');
  const after = applySubmitted(items, '1', NOW);
  assert.equal(after['1'].cleared_by, 'schoology');
  assert.equal(after['1'].cleared, true);
  assert.equal(applySubmitted(after, '1', NOW), null);
  assert.equal(applySubmitted(items, '2', NOW), null); // not on the list: nothing made up
});

test('prune: untouched for 400 days go, then the oldest past the cap', () => {
  const now = Date.parse(NOW);
  const at = (days) => ({ updated_at: new Date(now - days * 86_400_000).toISOString() });
  const kept = prune({ a: at(1), b: at(399), c: at(401), d: { updated_at: 'junk' } }, now);
  assert.deepEqual(Object.keys(kept).sort(), ['a', 'b']);
  const capped = prune({ a: at(3), b: at(1), c: at(2) }, now, { max: 2 });
  assert.deepEqual(Object.keys(capped).sort(), ['b', 'c']);
});

test('storeKey: one per host and account, whatever the case', () => {
  assert.equal(storeKey('FUHSD.schoology.com', ' 1234567 '), 'localMarks:fuhsd.schoology.com:1234567');
  assert.notEqual(storeKey(HOST, '1234567'), storeKey(HOST, '7654321'));
});

test('handleLocal: kept across "sign-outs", apart per account, and safe with two at once', async () => {
  const store = memStore();
  const now = () => new Date(NOW);
  const a = { host: HOST, uid: '1111111', store, now };
  const b = { host: HOST, uid: '2222222', store, now };
  // Two tabs adding at the same moment: both survive.
  await Promise.all([
    handleLocal({ op: 'addItem', item: { schoology_id: '1', title: 'One' } }, a),
    handleLocal({ op: 'addItem', item: { schoology_id: '2', title: 'Two' } }, a),
  ]);
  await handleLocal({ op: 'work', id: 'local-1', work: 'ready' }, a);
  // The next day (a fresh read of storage, as after Schoology signs out and back in).
  const r = await handleLocal({ op: 'items', schoology_ids: ['1', '2'] }, a);
  assert.deepEqual(r.data.items.map((x) => [x.source_id, x.work]), [['1', 'ready'], ['2', 'todo']]);
  // Another student on the same Chrome sees none of it.
  const other = await handleLocal({ op: 'items', schoology_ids: ['1', '2'] }, b);
  assert.deepEqual(other.data.items, []);
  assert.equal(await markSubmittedLocal('2', a), true);
  const sub = await handleLocal({ op: 'items', schoology_ids: ['2'] }, a);
  assert.equal(sub.data.items[0].cleared_by, 'schoology');
  assert.deepEqual(Object.keys(store.data).sort(), [storeKey(HOST, '1111111')]);
});

test('handleLocal: an unreadable store is "Not saved", never a throw', async () => {
  const store = { get: async () => { throw new Error('quota'); }, set: async () => {} };
  const r = await handleLocal({ op: 'items', schoology_ids: ['1'] }, { host: HOST, uid: '1', store });
  assert.equal(r.ok, false);
  assert.equal(r.data.problem, 'Not saved');
});

// ── ui.js pageUserId: whose marks, from the page itself ──

/** Just enough of a document: its inline scripts and one querySelector. */
function fakeDoc({ scripts = [], tracker = null } = {}) {
  return {
    scripts: scripts.map((textContent) => ({ textContent })),
    querySelector: (sel) => (sel.includes('session-tracker') && tracker ? { getAttribute: () => tracker } : null),
  };
}
const NAV = (uid) => `window.siteNavigationUiProps={"locales":{"en":{"x":"y"}},"props":{"user":{"language":"en","logoutToken":"abc","userSessionId":"s1","uid":${uid},"name":"A Student","parentUid":null}}};`;

test('pageUserId: the navigation data first, then the session tracker, else null', () => {
  assert.equal(pageUserId(fakeDoc({ scripts: ['var a = 1;', NAV(1234567890)] })), '1234567890');
  assert.equal(pageUserId(fakeDoc({ scripts: [NAV('"1234567890"')] })), '1234567890');
  assert.equal(pageUserId(fakeDoc({ tracker: 'https://asset-cdn.schoology.com/sites/all/modules/schoology_core/session-tracker.html?id=987654321' })), '987654321');
  assert.equal(pageUserId(fakeDoc({ scripts: ['window.siteNavigationUiProps={"props":{}}'] })), null);
  assert.equal(pageUserId(fakeDoc({ tracker: 'https://asset-cdn.schoology.com/session-tracker.html?id=abc' })), null);
  assert.equal(pageUserId(fakeDoc()), null);
  assert.equal(pageUserId(null), null);
});

// ── port.js: local marks onto neo-plan once a token is saved ──

const local = (sid, extra = {}) => ({
  id: `local-${sid}`, source_id: sid, section_id: '7', title: `Lab ${sid}`, type: 'assignment', work: 'todo',
  removed: false, cleared: false, cleared_by: null, due: { at: '2026-11-05T15:00:00Z' }, source_url: `https://${HOST}/assignment/${sid}`,
  updated_at: NOW, ...extra,
});

/** A neo-plan that answers like the real one, recording every call. */
function fakeNeoplan(have = {}) {
  const calls = [];
  const items = new Map(Object.entries(have));
  let n = 0;
  const np = async (op, args = {}) => {
    calls.push([op, args.id ?? args.item?.schoology_id ?? args.schoology_ids?.join(','), args.work ?? args.itemType ?? '']);
    if (op === 'items') return { ok: true, status: 200, data: { items: args.schoology_ids.filter((s) => items.has(s)).map((s) => items.get(s)) } };
    if (op === 'addItem') {
      const it = { id: `np${++n}`, source_id: args.item.schoology_id, type: args.item.type || 'assignment', work: 'todo', removed: false, cleared: false };
      items.set(it.source_id, it);
      return { ok: true, status: 200, data: it };
    }
    return { ok: true, status: 200, data: {} };
  };
  return { np, calls };
}

test('hasOwnMark / toPort: only what the student marked, changed since the last port, oldest first', () => {
  assert.equal(hasOwnMark(local('1')), false); // just auto-added from To Do
  assert.equal(hasOwnMark(local('1', { work: 'ready' })), true);
  assert.equal(hasOwnMark(local('1', { removed: true })), true);
  assert.equal(hasOwnMark(local('1', { cleared: true, cleared_by: 'user' })), true);
  assert.equal(hasOwnMark(local('1', { cleared: true, cleared_by: 'schoology' })), false); // neo-plan's sync sees that itself
  assert.equal(hasOwnMark(local('1', { type: 'exam' })), true); // "Lab 1" would be HW
  assert.equal(hasOwnMark(local('1', { title: 'Unit test', type: 'exam' })), false); // the guess, not a choice
  const items = {
    a: local('1', { work: 'ready', updated_at: '2026-11-02T00:00:00Z' }),
    b: local('2', { removed: true, updated_at: '2026-10-01T00:00:00Z' }),
    c: local('3'),
  };
  assert.deepEqual(toPort(items).map((x) => x.source_id), ['2', '1']);
  assert.deepEqual(toPort(items, Date.parse('2026-10-15T00:00:00Z')).map((x) => x.source_id), ['1']);
});

test('markCalls: type, then removed, turned in or done', () => {
  const np = { id: 'n1', type: 'assignment', work: 'todo' };
  assert.deepEqual(markCalls(local('1', { work: 'ready' }), np), [{ op: 'work', id: 'n1', work: 'ready' }]);
  assert.deepEqual(markCalls(local('1', { cleared: true, cleared_by: 'user', work: 'ready' }), np), [{ op: 'turnIn', id: 'n1' }]);
  assert.deepEqual(markCalls(local('1', { type: 'exam', work: 'ready' }), np),
    [{ op: 'setType', id: 'n1', itemType: 'exam' }, { op: 'work', id: 'n1', work: 'ready' }]);
  assert.deepEqual(markCalls(local('1', { removed: true, work: 'ready' }), np), [{ op: 'remove', id: 'n1' }]);
  assert.deepEqual(markCalls(local('1', { type: 'exam' }), np, { type: false }), []);
});

test('portItems: adds what neo-plan lacks, fills in what it has unmarked, leaves its own marks alone', async () => {
  const { np, calls } = fakeNeoplan({
    '2': { id: 'np-old', source_id: '2', type: 'assignment', work: 'ready', removed: false, cleared: false }, // marked on the iPhone
    '3': { id: 'np-sync', source_id: '3', type: 'assignment', work: 'todo', removed: false, cleared: false }, // the sync added it
  });
  const out = await portItems([
    local('1', { work: 'ready' }),
    local('2', { removed: true }),
    local('3', { cleared: true, cleared_by: 'user', work: 'ready' }),
  ], np);
  assert.deepEqual(out, { moved: 2, kept: 1, failed: 0, stopped: false });
  assert.deepEqual(calls, [
    ['items', '1,2,3', ''],
    ['addItem', '1', ''],
    ['work', 'np1', 'ready'],
    ['turnIn', 'np-sync', ''],
  ]);
});

test('portItems: a refused token stops it; another refusal is counted and it goes on', async () => {
  const refused = async () => ({ ok: false, status: 401, data: {} });
  assert.deepEqual(await portItems([local('1', { work: 'ready' })], refused), { moved: 0, kept: 0, failed: 0, stopped: true });
  const { np } = fakeNeoplan();
  const flaky = async (op, args) => (op === 'addItem' && args.item.schoology_id === '1' ? { ok: false, status: 500, data: {} } : np(op, args));
  assert.deepEqual(await portItems([local('1', { work: 'ready' }), local('2', { work: 'ready' })], flaky), { moved: 1, kept: 0, failed: 1, stopped: false });
});

test('setPortedAt keeps the items, and handleLocal keeps portedAt', async () => {
  const store = memStore();
  const who = { host: HOST, uid: '1111111', store, now: () => new Date(NOW) };
  await handleLocal({ op: 'addItem', item: { schoology_id: '1', title: 'One' } }, who);
  await setPortedAt(storeKey(HOST, '1111111'), 123, store);
  await handleLocal({ op: 'work', id: 'local-1', work: 'ready' }, who);
  const rec = await readRecord(store, storeKey(HOST, '1111111'));
  assert.equal(rec.portedAt, 123);
  assert.equal(rec.items['1'].work, 'ready');
});

test('portText: what the neo-plan page says', () => {
  const now = Date.parse(NOW);
  assert.equal(portText(null, false, now), '');
  assert.equal(portText({ state: 'waiting', account: { host: HOST, uid: '1' } }, false, now), 'Moving your marks from this browser…');
  assert.equal(portText({ state: 'waiting', account: null }, false, now), '');
  assert.equal(portText({ state: 'done', at: now, result: { moved: 1, failed: 0 } }, false, now), 'Moved 1 mark from this browser to neo-plan.');
  assert.match(portText({ state: 'done', at: now, result: { moved: 3, failed: 2 } }, false, now), /^Moved 3 marks .* 2 marks couldn’t be moved/);
  assert.equal(portText({ state: 'done', at: now, result: { moved: 0, failed: 0, kept: 4 } }, false, now), '');
  assert.equal(portText({ state: 'done', at: now - 2 * 86_400_000, result: { moved: 3, failed: 0 } }, false, now), '');
  assert.match(portText({ state: 'stopped', at: now, result: { stopped: true } }, false, now), /refused the token/);
});
