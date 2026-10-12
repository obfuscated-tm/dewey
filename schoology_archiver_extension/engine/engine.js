// The iOS app's engine (docs/ios/ENGINE.md): the extension's own parsers and
// grade math, plus GET-only fetching, as one global `SchoologyEngine`. The app
// bundles this file (esbuild --format=iife --global-name=SchoologyEngine) and
// injects it into a hidden WKWebView on the Schoology origin, so fetch() carries
// the session cookies and DOMParser exists. Nothing here touches chrome.*.
//
// Every op is async, takes one plain object and resolves (never rejects) to
//   { ok: true, data, url, fetchedAt } | { ok: false, error, status?, message? }
// with error one of: login, quiz, ratelimit, parse, http, network, bad_args, op.
// `callJSON(op, args)` is what Swift calls: the same result as a JSON string.
//
// Fetch rules (ported from reader/client.js getText): refuse quiz-taking and
// /assessments/ URLs before requesting; GET only, credentials included; a
// sign-in page or another host is `login`; Schoology's rate-limit page backs
// off and retries; at most 2 requests in flight, starts at least 300 ms apart.

import { parseKind, parseEventsJson, nextEventsUrl, isAssessmentUrl, asJson } from '../reader/parse/sync.js';
import { parseMcpKind } from '../reader/parse/mcp.js';
import { SHOW_MORE_RE } from '../reader/parse/feed.js';
import { rubricPaths, parseRubric } from '../reader/parse/rubric.js';
import { isQuizTakingUrl } from '../reader/guard.js';
import { isRateLimited } from '../reader/ratelimit.js';
import * as grademath from '../overlays/grademath.js';
import * as asgstate from '../overlays/asgstate.js';
import * as matstate from '../overlays/matstate.js';
import * as todologic from '../overlays/todologic.js';

export const VERSION = 1;

const ID_RE = /^\d{1,20}$/;
const MAX_EVENT_PAGES = 20;
const MAX_IN_FLIGHT = 2;
const MIN_GAP_MS = 300;
const RATE_WAITS_MS = [5000, 10000, 20000];
const SERVER_WAITS_MS = [3000, 6000];
const LOGIN_RE = /s-user-login-form|login-container/;

function codeError(code, extra = {}) {
  const e = new Error(extra.message || code);
  e.code = code;
  Object.assign(e, extra);
  return e;
}

/** `^\d{1,20}$` or a `bad_args` error. Returns the id as a string. */
export function validId(id) {
  const s = String(id ?? '');
  if (!ID_RE.test(s)) throw codeError('bad_args', { message: `bad id: ${s.slice(0, 40)}` });
  return s;
}

/** True for any URL the engine must never request (quiz-taking pages, /assessments/, the dropbox submit form). */
export function refused(url) {
  return isQuizTakingUrl(url) || isAssessmentUrl(url);
}

// The exact shapes `material({ path })` may open (outputs/mcp/ops.js MATERIAL_SHAPES).
export const MATERIAL_SHAPES = [
  { kind: 'gp', re: /^\/course\/\d{1,20}\/materials\/gp\/\d{1,20}$/ },
  { kind: 'link_view', re: /^\/course\/\d{1,20}\/materials\/link\/view\/\d{1,20}$/ },
  { kind: 'page', re: /^\/page\/\d{1,20}$/ },
  { kind: 'discussion', re: /^\/discussion\/\d{1,20}$/ },
  { kind: 'assignment', re: /^\/assignment\/\d{1,20}$/ },
];

/** A dropbox page of one assignment: /assignment/{id}/dropbox/view/{n} (query dropped). */
const pageNo = (page) => (Number.isInteger(page) && page >= 0 && page < 1000 ? page : 0);
const DROPBOX_RE = /^\/assignment\/\d{1,20}\/dropbox\/view\/\d{1,20}$/;

/** `path` or a same-origin absolute URL → a pathname + search on `origin`, else `bad_args`. */
export function samePath(raw, origin) {
  const s = String(raw ?? '');
  let u;
  try { u = new URL(s, origin); } catch { throw codeError('bad_args'); }
  if (!/^https?:\/\//i.test(s) && !s.startsWith('/')) throw codeError('bad_args');
  if (u.origin !== new URL(origin).origin) throw codeError('bad_args');
  return { pathname: u.pathname, search: u.search };
}

// ── Pacing: ≤ 2 in flight, starts ≥ 300 ms apart ─────────────────────────

export function makeQueue({ now, sleep, max = MAX_IN_FLIGHT, gap = MIN_GAP_MS }) {
  let inFlight = 0;
  let lastStart = -Infinity;
  const waiting = [];
  let chain = Promise.resolve();
  const release = () => {
    inFlight--;
    const next = waiting.shift();
    if (next) next();
  };
  // Serialized so two callers never both slip through the gap check.
  const acquire = () => {
    const run = chain.then(async () => {
      if (inFlight >= max) await new Promise((r) => waiting.push(r));
      const wait = lastStart + gap - now();
      if (wait > 0) await sleep(wait);
      inFlight++;
      lastStart = now();
    });
    chain = run.catch(() => {});
    return run;
  };
  return {
    async run(fn) {
      await acquire();
      try { return await fn(); } finally { release(); }
    },
    get inFlight() { return inFlight; },
  };
}

// ── The engine ───────────────────────────────────────────────────────────

/**
 * deps: { origin, fetch, sleep, now, parseKind, parseMcpKind, online }. All
 * default to the real thing in a page; tests pass fakes.
 */
export function createEngine(deps = {}) {
  const origin = deps.origin || globalThis.location?.origin || 'https://fuhsd.schoology.com';
  const host = new URL(origin).host;
  const doFetch = deps.fetch || ((...a) => globalThis.fetch(...a));
  const sleep = deps.sleep || ((ms) => new Promise((r) => setTimeout(r, ms)));
  const now = deps.now || (() => Date.now());
  const pKind = deps.parseKind || parseKind;
  const pMcp = deps.parseMcpKind || parseMcpKind;
  const online = deps.online || (() => globalThis.navigator?.onLine !== false);
  const queue = makeQueue({ now, sleep });

  // Session gone? Schoology redirects every page to /login (then Google) when
  // it is: a manual-redirect probe of a page that never redirects otherwise.
  async function sessionGone() {
    try {
      const r = await queue.run(() => doFetch(`${origin}/iapi/course/active`, { credentials: 'include', redirect: 'manual' }));
      return r.type === 'opaqueredirect' || (r.status >= 300 && r.status < 400);
    } catch {
      return false;
    }
  }

  /** One GET. → { text, url, status } | { redirected: true, url }. Throws coded errors. */
  async function get(path, { redirect = 'follow', headers } = {}) {
    const url = new URL(path, origin).href;
    if (new URL(url).host !== host) throw codeError('bad_args');
    if (refused(url)) throw codeError('quiz');
    let rateHits = 0;
    let serverHits = 0;
    for (;;) {
      let r;
      try {
        r = await queue.run(() => doFetch(url, { method: 'GET', credentials: 'include', redirect, ...(headers ? { headers } : {}) }));
      } catch (e) {
        // A redirect to Google sign-in shows up as a CORS TypeError.
        if (!online()) throw codeError('network', { message: 'offline' });
        if (await sessionGone()) throw codeError('login');
        throw codeError('network', { message: String(e?.message || e) });
      }
      if (r.type === 'opaqueredirect') return { redirected: true, url };
      const text = await r.text();
      if (isRateLimited(r.status, text)) {
        if (rateHits >= RATE_WAITS_MS.length) throw codeError('ratelimit');
        await sleep(RATE_WAITS_MS[rateHits++]);
        continue;
      }
      if (r.status >= 500) {
        if (serverHits >= SERVER_WAITS_MS.length) throw codeError('http', { status: r.status });
        await sleep(SERVER_WAITS_MS[serverHits++]);
        continue;
      }
      const final = new URL(r.url || url);
      if (final.host !== host || final.pathname.startsWith('/login')) throw codeError('login');
      if (r.status === 401 || r.status === 403) throw codeError('login');
      if (r.status >= 400) throw codeError('http', { status: r.status });
      if (isAssessmentUrl(final.href)) throw codeError('quiz');
      return { text, url: final.href, status: r.status };
    }
  }

  // GET + one of the extension's parsers; { login: true } → `login`, a throw → `parse`.
  async function getParsed(path, kind, opts) {
    const r = await get(path, opts);
    if (r.redirected) throw codeError('quiz');
    let p;
    try {
      p = (kind.mcp ? pMcp : pKind)(kind.name, r.text, r.url);
    } catch (e) {
      throw codeError('parse', { message: String(e?.message || e) });
    }
    if (p?.login) throw codeError('login');
    return { data: p.data, url: r.url };
  }
  const K = (name) => ({ name, mcp: false });
  const M = (name) => ({ name, mcp: true });

  async function getJson(path, opts) {
    const r = await get(path, { headers: { Accept: 'application/json' }, ...opts });
    if (r.redirected) throw codeError('quiz');
    const j = asJson(r.text);
    if (j == null) {
      if (LOGIN_RE.test(r.text)) throw codeError('login');
      throw codeError('parse', { message: `not JSON: ${r.url}` });
    }
    return { json: j, text: r.text, url: r.url };
  }

  async function wrap(fn) {
    try {
      const { data, url = null } = await fn();
      return { ok: true, data, url, fetchedAt: new Date(now()).toISOString() };
    } catch (e) {
      const error = e?.code || 'parse';
      const out = { ok: false, error };
      if (e?.status) out.status = e.status;
      if (error === 'parse' || error === 'network' || error === 'bad_args' || !e?.code) out.message = String(e?.message || e);
      return out;
    }
  }

  // ── Ops ────────────────────────────────────────────────────────────────

  const ops = {
    /** Course Dashboard order: [{ section_id, title, section_title }]. */
    courses: () => wrap(() => getParsed('/iapi/course/active', K('courses'))),

    /**
     * { overdue, upcoming }: /v2/events/{list}, every page, the same source as
     * the extension's To Do sidebar (overlays/todo.js). Rows: { schoology_id,
     * title, course, section_id, due, graded, url }.
     */
    todo: () => wrap(async () => {
      const out = {};
      for (const list of ['overdue', 'upcoming']) {
        const rows = [];
        let url = `/v2/events/${list}`;
        const seen = new Set();
        for (let page = 0; url && page < MAX_EVENT_PAGES && !seen.has(url); page++) {
          seen.add(url);
          const r = await getJson(url);
          rows.push(...parseEventsJson(r.json));
          url = nextEventsUrl(r.json);
        }
        out[list] = rows;
      }
      return { data: out, url: null };
    }),

    /** Schoology's own submission status. A redirect (a quiz) is never followed. */
    status: ({ id } = {}) => wrap(async () => {
      const aid = validId(id);
      const r = await get(`/assignment/${aid}/info`, { redirect: 'manual' });
      if (r.redirected) return { data: { state: 'unknown', quiz: true }, url: r.url };
      let p;
      try { p = pKind('status', r.text, r.url); } catch (e) { throw codeError('parse', { message: String(e?.message || e) }); }
      if (p?.login) throw codeError('login');
      return { data: p.data, url: r.url };
    }),

    /** { id, title, type, due, instructions_md, attachments, submission, dropboxUrl }. */
    assignment: ({ id } = {}) => wrap(async () => {
      const aid = validId(id);
      const r = await getParsed(`/assignment/${aid}`, M('assignmentFull'));
      return { data: { id: aid, ...r.data }, url: r.url };
    }),

    /** The gradebook's rows (reader/parse/grades.js). */
    gradebook: ({ sectionId } = {}) => wrap(async () => {
      const sid = validId(sectionId);
      return getParsed(`/course/${sid}/student_grades`, M('gradesFull'));
    }),

    /** { grade, comments } of an assignment's dropbox page. */
    dropbox: ({ url } = {}) => wrap(async () => {
      const { pathname } = samePath(url, origin);
      if (!DROPBOX_RE.test(pathname)) throw codeError('bad_args');
      return getParsed(`${pathname}?revision=1`, M('dropboxFull'));
    }),

    /** { title, total, earned, criteria: [{ title, max, earned, level, comment }] }. */
    rubric: ({ rubricId, enrollmentId, itemId } = {}) => wrap(async () => {
      const paths = rubricPaths({ rubricId: validId(rubricId), enrollmentId: validId(enrollmentId), itemId: validId(itemId) });
      const r = await getJson(paths.rubric);
      const g = await getJson(paths.gradeInfo);
      try {
        return { data: parseRubric(r.json, g.json), url: r.url };
      } catch (e) {
        throw codeError('parse', { message: String(e?.message || e) });
      }
    }),

    /** A Materials folder's rows: [{ kind, title, id, url }]. */
    materials: ({ sectionId, folderId } = {}) => wrap(async () => {
      const sid = validId(sectionId);
      const fid = folderId == null || folderId === '' ? null : validId(folderId);
      return getParsed(`/course/${sid}/materials${fid ? `?f=${fid}` : ''}`, M('materialsFull'));
    }),

    /** One material page: { url, kind, title, body_md, target, files, links }. */
    material: ({ path } = {}) => wrap(async () => {
      const { pathname } = samePath(path, origin);
      const shape = MATERIAL_SHAPES.find((s) => s.re.test(pathname));
      if (!shape) throw codeError('bad_args');
      if (shape.kind === 'assignment') {
        const r = await getParsed(pathname, M('assignmentFull'));
        return {
          data: {
            url: r.url, kind: 'assignment', title: r.data.title, body_md: r.data.instructions_md, target: null,
            files: (r.data.attachments || []).map((f) => ({ title: f.title, url: f.url, ext: null })), links: [],
          },
          url: r.url,
        };
      }
      return getParsed(pathname, M('materialFull'));
    }),

    /**
     * A course's updates: [{ id, author, date, time, body_md }]. A post cut off
     * at "Show More" gets its full body (one more GET each).
     */
    feed: ({ sectionId, page = 0 } = {}) => wrap(async () => {
      const sid = validId(sectionId);
      const p = pageNo(page);
      const r = await getParsed(`/course/${sid}/feed?page=${p}`, M('updatesFeed'));
      const posts = [];
      for (const { more, ...post } of r.data) {
        if (more && SHOW_MORE_RE.test(more)) {
          try { post.body_md = (await getParsed(more, M('postBody'))).data.body_md || post.body_md; } catch (e) { if (e?.code === 'login') throw e; }
        }
        posts.push(post);
      }
      return { data: posts, url: r.url };
    }),

    /** /home/notifications: { items: [{ key, type, course, sectionId, text, date, items }], hasMore }. */
    notifications: ({ page = 0 } = {}) => wrap(() => {
      const p = pageNo(page);
      return getParsed(`/home/notifications${p ? `?page=${p}` : ''}`, M('notificationsFull'));
    }),

    /** The messages list: { threads: [{ id, subject, group, preview, from, fromId, date, unread }], unreadCount, hasMore }. */
    messages: ({ page = 0 } = {}) => wrap(() => {
      const p = pageNo(page);
      return getParsed(`/messages${p ? `?page=${p}` : ''}`, M('messagesList'));
    }),

    /**
     * One thread: { id, subject, group, participants, messages }. Opening it
     * marks it read on Schoology, so the app calls this only when the student
     * opens a message with "Mark messages read when opened" on.
     */
    message: ({ id } = {}) => wrap(async () => {
      const mid = validId(id);
      const r = await getParsed(`/messages/view/${mid}`, M('messageFull'));
      return { data: { id: mid, ...r.data }, url: r.url };
    }),
  };

  // Pure, no fetching: the grade math the screens show. Errors are `parse`.
  const pure = (fn) => (args = {}) => wrap(async () => ({ data: fn(args), url: null }));
  ops.courseGrade = pure(courseGrade);
  ops.planTest = pure(planTest);
  ops.annotate = pure(({ overdue = [], upcoming = [], now: n } = {}) => todologic.sortDueItems(
    todologic.annotateItems({ overdue, upcoming, now: n ? new Date(n) : new Date(now()) }),
  ));

  return ops;
}

// ── Grade summaries (pure) ───────────────────────────────────────────────

const finite = (x) => (typeof x === 'number' && Number.isFinite(x) ? x : null);

/**
 * Gradebook rows → everything the Grades screens show, for the current period:
 * { period, pct, letter, mode, categories: [{ title, weight, earned, possible,
 * pct, floor, floorStatus }], items: [{ id, category, title, due, grade,
 * comment, rubric, earned, possible, pct, impact }], history, periods }.
 * `opts` is grademath's (weights, whatIf, extra, drop); `target` the A line.
 * pct is null when nothing is graded.
 */
export function courseGrade({ rows = [], opts = {}, target = grademath.A_CUTOFF } = {}) {
  const periods = grademath.periodsFromRows(rows);
  const course = grademath.currentPeriod(periods) || periods.find((p) => p.title && !/no grading period/i.test(p.title)) || periods[0] || null;
  const titles = periods.map((p) => p.title);
  if (!course) return { period: null, pct: null, letter: null, mode: 'points', categories: [], items: [], history: [], periods: titles };
  const g = grademath.grade(course, opts);
  const categories = g.categories.map((c) => {
    const f = grademath.categoryFloor(course, c.title, target, opts);
    return {
      title: c.title, weight: c.weight, earned: c.earned, possible: c.possible, pct: c.pct,
      floor: finite(f), floorStatus: f == null ? 'none' : f <= 0 ? 'any' : f > 100 ? 'out' : 'need',
    };
  });
  // Categories with no grades yet still get a row.
  for (const c of course.categories) {
    if (!categories.some((x) => x.title === c.title)) {
      categories.push({ title: c.title, weight: c.weight, earned: 0, possible: 0, pct: null, floor: null, floorStatus: 'none' });
    }
  }
  const items = course.categories.flatMap((c) => c.items.map((it) => ({
    id: it.id,
    category: c.title,
    title: it.title,
    due: it.due,
    grade: it.src?.grade ?? '',
    comment: it.src?.comment || '',
    rubric: it.src?.rubric || null,
    earned: it.earned,
    possible: it.possible,
    pct: it.earned != null && it.possible > 0 ? (it.earned / it.possible) * 100 : null,
    impact: finite(grademath.impact(course, it.id, opts)),
  })));
  return {
    period: course.title || null,
    pct: g.pct,
    letter: grademath.letter(g.pct),
    mode: g.mode,
    categories,
    items,
    history: grademath.history(course, opts),
    periods: titles,
  };
}

/** A planned item: { status: 'any' | 'out' | 'need', need? } (grademath.needFor). */
export function planTest({ rows = [], category, possible, target = grademath.A_CUTOFF, opts = {} } = {}) {
  const course = grademath.currentPeriod(grademath.periodsFromRows(rows));
  if (!course || !category || !(possible > 0)) return { status: 'none' };
  return grademath.needFor(course, { category, possible, target }, opts);
}

// ── The page-global instance ─────────────────────────────────────────────

let instance = null;
const engine = () => (instance ||= createEngine());
const OP_NAMES = ['courses', 'todo', 'status', 'assignment', 'gradebook', 'dropbox', 'rubric', 'materials', 'material', 'feed',
  'notifications', 'messages', 'message', 'courseGrade', 'planTest', 'annotate'];

/** Runs one op by name; always resolves. */
export async function call(op, args) {
  if (!OP_NAMES.includes(op)) return { ok: false, error: 'op' };
  return engine()[op](args || {});
}

/** What Swift calls: `return await SchoologyEngine.callJSON(op, args)`. */
export async function callJSON(op, args) {
  return JSON.stringify(await call(op, args));
}

export const courses = (a) => call('courses', a);
export const todo = (a) => call('todo', a);
export const status = (a) => call('status', a);
export const assignment = (a) => call('assignment', a);
export const gradebook = (a) => call('gradebook', a);
export const dropbox = (a) => call('dropbox', a);
export const rubric = (a) => call('rubric', a);
export const materials = (a) => call('materials', a);
export const material = (a) => call('material', a);
export const feed = (a) => call('feed', a);
export const notifications = (a) => call('notifications', a);
export const messages = (a) => call('messages', a);
export const message = (a) => call('message', a);

/** The pure modules, for anything the screens need that has no op yet. */
export const math = { ...grademath, ...asgstate, ...matstate, ...todologic };
