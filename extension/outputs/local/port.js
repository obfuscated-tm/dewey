// Local marks (marks.js) → neo-plan, once a token is saved: what the student
// marked in this browser before signing up goes onto their neo-plan board.
//
// Rules:
// - Only the Schoology account signed in when the token was saved (background.js
//   works that out), so another student's marks on the same Chrome never land
//   on this board.
// - Only items with a mark of the student's own: Done / Studied, Turned in,
//   Removed, or a type they changed. Untouched open work isn't sent: neo-plan's
//   sync adds what's open anyway, and sending a year of old rows would bury it.
// - neo-plan already has the assignment with a mark on it: neo-plan's copy wins,
//   nothing is sent (it may have marks from the iPhone app or the website).
//   Already there but unmarked (neo-plan's sync added it, or a port was cut off
//   after the add): the local mark fills it in, type left as neo-plan has it.
// - Only marks changed since the last port, so saving a token again later sends
//   just what was marked in between.
// - A refused token (401) stops it; it starts over next time, and what was
//   already sent is then "neo-plan has it".
//
// `np(op, args)` → { ok, status, data } (api.js's handleNeoplan, as a function).

import { guessType } from './marks.js';

/** Whether this local item carries a mark worth sending. Pure. */
export function hasOwnMark(it) {
  if (!it) return false;
  if (it.removed || it.work === 'ready') return true;
  if (it.cleared && it.cleared_by === 'user') return true;
  return !!it.type && it.type !== guessType(it.title, undefined);
}

/** A neo-plan item nobody has marked yet: open, not done, not removed. Pure. */
export function unmarked(npItem) {
  return !!npItem && !npItem.removed && !npItem.cleared && npItem.work !== 'ready';
}

/** The items to send: marked, and changed after `since` (ms, or 0). Oldest first. Pure. */
export function toPort(items, since = 0) {
  return Object.values(items || {})
    .filter((it) => hasOwnMark(it) && Date.parse(it.updated_at) > since)
    .sort((a, b) => Date.parse(a.updated_at) - Date.parse(b.updated_at));
}

/**
 * The calls that put one local item's mark on the neo-plan item just made for
 * it. Pure. → [{ op, ...args }]
 */
export function markCalls(local, npItem, { type = true } = {}) {
  const id = npItem.id;
  const calls = [];
  if (type && local.type && local.type !== npItem.type) calls.push({ op: 'setType', id, itemType: local.type });
  if (local.removed) { calls.push({ op: 'remove', id }); return calls; }
  if (local.cleared && local.cleared_by === 'user' && local.type === 'assignment') calls.push({ op: 'turnIn', id });
  else if (local.work === 'ready' && npItem.work !== 'ready') calls.push({ op: 'work', id, work: 'ready' });
  return calls;
}

/**
 * Sends `list` (toPort's) to neo-plan. → { moved, kept, failed, stopped }.
 * moved: added with its mark; kept: neo-plan had it already; failed: refused;
 * stopped: the token was refused, so nothing more was tried.
 */
export async function portItems(list, np) {
  const out = { moved: 0, kept: 0, failed: 0, stopped: false };
  for (let i = 0; i < list.length; i += 100) {
    const batch = list.slice(i, i + 100);
    const r = await np('items', { schoology_ids: batch.map((x) => x.source_id) });
    if (!r.ok) {
      if (r.status === 401) { out.stopped = true; return out; }
      out.failed += batch.length;
      continue;
    }
    const have = new Map((r.data?.items || []).map((x) => [String(x.source_id), x]));
    for (const it of batch) {
      const there = have.get(String(it.source_id));
      if (there && !unmarked(there)) { out.kept++; continue; }
      if (there) {
        const calls = markCalls(it, there, { type: false });
        if (!calls.length) { out.kept++; continue; } // only a type: neo-plan's stands
        const res = await send(calls);
        if (res === 'stop') { out.stopped = true; out.failed++; return out; }
        out[res ? 'moved' : 'failed']++;
        continue;
      }
      const added = await np('addItem', {
        item: {
          schoology_id: it.source_id,
          section_id: it.section_id,
          title: it.title,
          due_at: it.due?.at || null,
          source_url: it.source_url,
          ...(it.type === 'exam' || it.type === 'assignment' ? { type: it.type } : {}),
        },
      });
      if (!added.ok || !added.data?.id) {
        if (added.status === 401) { out.stopped = true; return out; }
        out.failed++;
        continue;
      }
      const res = await send(markCalls(it, added.data));
      if (res === 'stop') { out.stopped = true; out.failed++; return out; }
      out[res ? 'moved' : 'failed']++;
    }
  }
  return out;

  // → true (all took), false (one was refused), 'stop' (the token was).
  async function send(calls) {
    let good = true;
    for (const { op, ...args } of calls) {
      const res = await np(op, args);
      if (res.status === 401) return 'stop';
      if (!res.ok) good = false;
    }
    return good;
  }
}

// ── What the neo-plan settings page says (panel/neoplan.js) ──
const PORT_SHOW_MS = 24 * 3600_000;

/** The line under Connected for background.js's `localPort` record, or ''. Shown for a day after. Pure. */
export function portText(port, running, now = Date.now()) {
  if (!port) return '';
  if (port.state === 'waiting') return running || port.account ? 'Moving your marks from this browser…' : '';
  if (now - (port.at || 0) > PORT_SHOW_MS) return '';
  const r = port.result;
  if (port.state === 'stopped') return 'neo-plan refused the token, so your marks from this browser weren’t moved.';
  if (!r || (!r.moved && !r.failed)) return '';
  const n = (k) => `${k} mark${k === 1 ? '' : 's'}`;
  const parts = [];
  if (r.moved) parts.push(`Moved ${n(r.moved)} from this browser to neo-plan.`);
  if (r.failed) parts.push(`${n(r.failed)} couldn’t be moved; they’ll be tried again next time you connect.`);
  return parts.join(' ');
}
