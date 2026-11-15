// The service worker's side of the sync: when it may run (run lock, not
// twice at once, not while an Archive runs), which Schoology site, the
// offscreen parser, and the keep-alive. sync.js is the run itself.

import { SchoologyClient } from '../reader/client.js';
import { handleNeoplan } from '../outputs/neoplan/api.js';
import { runSync, KEY_SNAPSHOT, submittedPayload } from './sync.js';
import { limiter, persistPausedUntil } from './limiter.js';
import { ensureOffscreen } from '../offscreen/ensure.js';
import { isSchoolHost } from '../util.js';
import { checkGrades } from './gradecheck.js';

export const SYNC_LOCK_ID = 'sync';
export const KEY_HOST = 'schoologyHost';
export const DEFAULT_HOST = 'fuhsd.schoology.com';
const KEY_REPORTED = 'submittedReported'; // { schoology_id: ms } — one enrich per submit is enough
let current = null; // the running sync's promise, in this service worker
let controller = null;

export const syncRunning = () => current;

/** Abort a running sync (kill switch). */
export function stopSync() {
  controller?.abort();
  gradeController?.abort();
}

export async function offscreenParse(kind, text, url) {
  await ensureOffscreen();
  const r = await chrome.runtime.sendMessage({ type: 'parse', target: 'offscreen', kind, text, url });
  if (!r?.ok) throw new Error(r?.error || 'parser did not answer');
  return r.result;
}

// ── Which Schoology ──────────────────────────────────────────────────────
// The district site last visited. A host saved before Schoology's own sites
// (developers.schoology.com, …) were told apart is skipped, not trusted.
export async function schoologyHost() {
  const got = await chrome.storage.local.get([KEY_HOST, 'options']);
  return [got[KEY_HOST], got.options?.host].find(isSchoolHost) || DEFAULT_HOST;
}

// ── One run ──────────────────────────────────────────────────────────────
/**
 * Runs a sync unless one is running (returns that one) or an Archive holds
 * the run lock (returns { skipped: 'busy' }). `isArchiveRunning(running)`
 * says whether the lock's holder is a live archive panel.
 */
export function requestSync({ isArchiveRunning, parse = offscreenParse, np = handleNeoplan, reason = '' } = {}) {
  if (current) return current;
  current = (async () => {
    const { running } = await chrome.storage.session.get('running');
    if (running?.id && running.id !== SYNC_LOCK_ID && isArchiveRunning(running)) return { skipped: 'busy' };
    await chrome.storage.session.set({ running: { id: SYNC_LOCK_ID, since: Date.now(), reason } });
    controller = new AbortController();
    const keepAlive = setInterval(() => { chrome.runtime.getPlatformInfo?.().catch?.(() => {}); }, 20_000);
    try {
      const client = new SchoologyClient({
        host: await schoologyHost(),
        log: () => {},
        signal: controller.signal,
        // The SW can't sendMessage to itself, so the sync's client uses the
        // shared limiter directly (same instance background.js answers
        // schoologySlot/schoologyRateLimited with).
        acquire: (s) => limiter.acquire(s),
        onRateLimited: () => { limiter.pause(); persistPausedUntil(); },
      });
      return await runSync({
        client,
        parse,
        np: (op, args = {}) => np({ type: 'neoplan', op, ...args }),
        storage: chrome.storage.local,
        byHand: reason === 'panel',
      });
    } finally {
      clearInterval(keepAlive);
      controller = null;
      const { running: r } = await chrome.storage.session.get('running');
      if (r?.id === SYNC_LOCK_ID) await chrome.storage.session.remove('running');
    }
  })().finally(() => { current = null; });
  return current;
}

// ── The grade check (sync/gradecheck.js) ─────────────────────────────────
let gradeCheck = null;
let gradeController = null;
/**
 * Reads every class's gradebook for new scores, unless a sync, an archive or
 * another check is running (then it waits for the next alarm). The classes
 * are the last sync's. → the number of new scores, or null when it didn't run
 * or was cut short (signed out, rate limited, stopped).
 */
export function requestGradeCheck({ parse = offscreenParse } = {}) {
  if (gradeCheck) return gradeCheck;
  gradeCheck = (async () => {
    if (current) return null;
    const { running } = await chrome.storage.session.get('running');
    if (running?.id) return null;
    const courses = (await chrome.storage.local.get(KEY_SNAPSHOT))[KEY_SNAPSHOT]?.courses || [];
    if (!courses.length) return null;
    gradeController = new AbortController();
    const client = new SchoologyClient({
      host: await schoologyHost(),
      log: () => {},
      signal: gradeController.signal,
      acquire: (s) => limiter.acquire(s),
      onRateLimited: () => { limiter.pause(); persistPausedUntil(); },
    });
    try {
      return await checkGrades({
        courses,
        storage: chrome.storage.local,
        getRows: async (id) => {
          const r = await client.getText(`/course/${id}/student_grades`);
          if (r.redirected) return null;
          const p = await parse('gradesFull', r.text, r.url);
          if (p?.login) throw new Error('signed out');
          return p?.data || null;
        },
      });
    } catch {
      return null; // signed out, rate limited or stopped: try again at the next alarm
    } finally {
      gradeController = null;
    }
  })().finally(() => { gradeCheck = null; });
  return gradeCheck;
}

// ── Submit detection ─────────────────────────────────────────────────────
/** The assignment page saw "submitted": one enrich for that item, once. */
export async function reportSubmitted({ schoology_id, late }, np = handleNeoplan) {
  const id = String(schoology_id ?? '');
  if (!/^\d{1,20}$/.test(id)) return { ok: false, status: 400, data: { error: 'body' } };
  const got = await chrome.storage.local.get([KEY_REPORTED, KEY_SNAPSHOT]);
  const reported = got[KEY_REPORTED] || {};
  if (reported[id]) return { ok: true, status: 200, data: { already: true } };
  const section_id = got[KEY_SNAPSHOT]?.sectionOf?.[id] || null;
  const r = await np({ type: 'neoplan', op: 'enrich', body: submittedPayload({ schoology_id: id, late, section_id }, new Date().toISOString()) });
  // Remembered only once neo-plan knew the item: one added later still gets its enrich.
  const known = r.ok && !(r.data?.results || []).some((x) => (x.did || []).includes('unknown'));
  if (known) {
    reported[id] = Date.now();
    const keys = Object.keys(reported);
    for (const k of keys.slice(0, Math.max(0, keys.length - 500))) delete reported[k];
    await chrome.storage.local.set({ [KEY_REPORTED]: reported });
  }
  return r;
}

/** Realm "Course : Section" → section_id, from the last Snapshot. */
export async function sectionForRealm(realm) {
  const snap = (await chrome.storage.local.get(KEY_SNAPSHOT))[KEY_SNAPSHOT];
  const key = String(realm || '').replace(/\s+/g, ' ').trim().toLowerCase();
  if (!key) return null;
  const c = (snap?.courses || []).find((x) => `${x.title} : ${x.section_title}`.replace(/\s+/g, ' ').trim().toLowerCase() === key);
  return c?.section_id || null;
}
