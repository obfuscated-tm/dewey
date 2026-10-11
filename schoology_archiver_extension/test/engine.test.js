// node --test test/*.test.js   (from schoology_archiver_extension/)
//
// engine/engine.js, the iOS app's engine: the URL guard, op → request path,
// error mapping (login / quiz / ratelimit / http / parse), pacing, and the
// pure grade summary. A fake fetch stands in for Schoology; DOM parsers are
// replaced by fakes (node has no DOMParser), JSON ones run for real.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEngine, refused, validId, samePath, makeQueue, courseGrade, planTest, call } from '../engine/engine.js';

const ORIGIN = 'https://fuhsd.schoology.com';

function res(body, { status = 200, url, type = 'basic' } = {}) {
  return { status, url, type, text: async () => (typeof body === 'string' ? body : JSON.stringify(body)) };
}

// routes: Map path(+search) → response | (init) => response | Error
function fakeEngine(routes, extra = {}) {
  const calls = [];
  let t = 0;
  const eng = createEngine({
    origin: ORIGIN,
    now: () => t,
    sleep: async (ms) => { t += ms; },
    fetch: async (url, init) => {
      const u = new URL(url);
      const key = u.pathname + u.search;
      calls.push({ key, init });
      let r = routes.get(key);
      if (typeof r === 'function') r = r(init, calls.length);
      if (r === undefined) throw new Error(`unscripted ${key}`);
      if (r instanceof Error) throw r;
      return { ...r, url: r.url || url };
    },
    parseKind: extra.parseKind || (() => { throw new Error('no DOM parser in this test'); }),
    parseMcpKind: extra.parseMcpKind || (() => { throw new Error('no DOM parser in this test'); }),
    online: extra.online,
  });
  return { eng, calls };
}

test('refused: quiz-taking pages, /assessments/ and the dropbox submit form', () => {
  assert.equal(refused(`${ORIGIN}/assignment/5/assessment`), true);
  assert.equal(refused(`${ORIGIN}/course/1/assessments/2`), true);
  assert.equal(refused(`${ORIGIN}/assignment/5/dropbox/submit`), true);
  assert.equal(refused(`${ORIGIN}/assignment/5`), false);
  assert.equal(refused(`${ORIGIN}/assignment/5/info`), false);
});

test('validId / samePath reject anything off-shape or off-host', () => {
  assert.equal(validId(12), '12');
  assert.throws(() => validId('1a'), /bad/);
  assert.deepEqual(samePath('/page/3', ORIGIN), { pathname: '/page/3', search: '' });
  assert.deepEqual(samePath(`${ORIGIN}/page/3?x=1`, ORIGIN), { pathname: '/page/3', search: '?x=1' });
  assert.throws(() => samePath('https://evil.example/page/3', ORIGIN));
  assert.throws(() => samePath('page/3', ORIGIN));
});

test('courses: GET /iapi/course/active, parsed, credentials included, GET only', async () => {
  const body = { body: { courses: { courses: [{ nid: 9, course_title: 'AP Physics 1' }], sections: [{ nid: 101, course_nid: 9, section_title: 'P3' }] } } };
  const { eng, calls } = fakeEngine(new Map([['/iapi/course/active', res(body)]]), {
    parseKind: (kind, text) => ({ data: JSON.parse(text).body.courses.sections.map((s) => ({ section_id: String(s.nid) })) }),
  });
  const r = await eng.courses();
  assert.equal(r.ok, true);
  assert.deepEqual(r.data, [{ section_id: '101' }]);
  assert.equal(calls[0].init.credentials, 'include');
  assert.equal(calls[0].init.method, 'GET');
  assert.ok(r.fetchedAt);
});

test('todo: every page of overdue and upcoming, via @links.next', async () => {
  const ev = (id, next) => ({
    '@extra': [{ '@id': `a${id}`, url: `/assignment/${id}`, title: `HW ${id}`, dueDateInUTCISO: '2026-10-11T06:59:00Z', '@links': { parent: { '@id': 'sec/55' } } }],
    ...(next ? { '@links': { next } } : {}),
  });
  const { eng, calls } = fakeEngine(new Map([
    ['/v2/events/overdue', res(ev(1, '/v2/events/overdue?page=2'))],
    ['/v2/events/overdue?page=2', res(ev(2))],
    ['/v2/events/upcoming', res(ev(3))],
  ]));
  const r = await eng.todo();
  assert.equal(r.ok, true);
  assert.deepEqual(r.data.overdue.map((x) => x.schoology_id), ['1', '2']);
  assert.deepEqual(r.data.upcoming.map((x) => x.schoology_id), ['3']);
  assert.equal(r.data.upcoming[0].section_id, '55');
  assert.equal(calls[0].init.headers.Accept, 'application/json');
});

test('a final URL on /login, or another host, is error: login', async () => {
  const { eng } = fakeEngine(new Map([['/iapi/course/active', res('<form id="s-user-login-form">', { url: `${ORIGIN}/login?destination=x` })]]));
  assert.deepEqual(await eng.courses(), { ok: false, error: 'login' });
  const g = fakeEngine(new Map([['/iapi/course/active', res('x', { url: 'https://accounts.google.com/o/oauth2' })]]));
  assert.deepEqual(await g.eng.courses(), { ok: false, error: 'login' });
});

test('a CORS failure while signed out (probe redirects) is login; while online and signed in, network', async () => {
  let probe = 0;
  const { eng } = fakeEngine(new Map([['/iapi/course/active', (init) => {
    if (init.redirect === 'manual') { probe++; return res('', { type: 'opaqueredirect', status: 0 }); }
    return new TypeError('Failed to fetch');
  }]]));
  assert.deepEqual(await eng.courses(), { ok: false, error: 'login' });
  assert.equal(probe, 1);
  const off = fakeEngine(new Map([['/iapi/course/active', new TypeError('Failed to fetch')]]), { online: () => false });
  const r = await off.eng.courses();
  assert.equal(r.error, 'network');
});

test('status: a redirect (a newer quiz) is reported, never followed', async () => {
  const { eng, calls } = fakeEngine(new Map([['/assignment/7/info', res('', { type: 'opaqueredirect', status: 0 })]]));
  const r = await eng.status({ id: '7' });
  assert.deepEqual(r.data, { state: 'unknown', quiz: true });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.redirect, 'manual');
});

test('a page that lands on /assessments/ is error: quiz', async () => {
  const { eng } = fakeEngine(new Map([['/assignment/7', res('<html>', { url: `${ORIGIN}/course/1/assessments/7` })]]), {
    parseMcpKind: () => ({ data: {} }),
  });
  assert.equal((await eng.assignment({ id: 7 })).error, 'quiz');
});

test('material: refuses paths outside the allowed shapes before fetching', async () => {
  const { eng, calls } = fakeEngine(new Map());
  assert.equal((await eng.material({ path: '/course/1/assessments/2' })).error, 'bad_args');
  assert.equal((await eng.material({ path: '/home' })).error, 'bad_args');
  assert.equal(calls.length, 0);
});

test('dropbox: only /assignment/{id}/dropbox/view/{n}, fetched with ?revision=1', async () => {
  const { eng, calls } = fakeEngine(new Map([['/assignment/7/dropbox/view/99?revision=1', res('<html>')]]), {
    parseMcpKind: (kind) => ({ data: { kind } }),
  });
  assert.equal((await eng.dropbox({ url: `${ORIGIN}/assignment/7/dropbox/submit` })).error, 'bad_args');
  const r = await eng.dropbox({ url: `${ORIGIN}/assignment/7/dropbox/view/99` });
  assert.deepEqual(r.data, { kind: 'dropboxFull' });
  assert.equal(calls.length, 1);
});

test('rate limit: backs off and retries, then gives up with error: ratelimit', async () => {
  let n = 0;
  const { eng } = fakeEngine(new Map([['/iapi/course/active', () => { n++; return res('slow', { status: 429 }); }]]));
  assert.equal((await eng.courses()).error, 'ratelimit');
  assert.equal(n, 4);
  let m = 0;
  const ok = fakeEngine(new Map([['/iapi/course/active', () => (++m < 2 ? res("You have reached Schoology's Web App request limit") : res({ body: {} }))]]), {
    parseKind: () => ({ data: [] }),
  });
  assert.equal((await ok.eng.courses()).ok, true);
});

test('http errors and parser throws map to their codes', async () => {
  const { eng } = fakeEngine(new Map([['/course/1/student_grades', res('nope', { status: 404 })]]));
  assert.deepEqual(await eng.gradebook({ sectionId: 1 }), { ok: false, error: 'http', status: 404 });
  const p = fakeEngine(new Map([['/course/1/student_grades', res('<html>')]]), { parseMcpKind: () => { throw new Error('boom'); } });
  const r = await p.eng.gradebook({ sectionId: 1 });
  assert.equal(r.error, 'parse');
  assert.equal(r.message, 'boom');
});

test('rubric: the two JSON GETs, parsed by rubric.js', async () => {
  const { eng } = fakeEngine(new Map([
    ['/iapi/grades/rubric/5', res({ body: { title: 'R', total_points: 4, rows: [{ id: 'r1', title: 'Claim', max_points: 4, columns: [{ pts: 4, description: 'Strong' }] }] } })],
    ['/iapi/grades/rubric_grade_info/5/8/6', res({ body: { gradeInfo: { r1: { grade: '4', comment: 'nice' } } } })],
  ]));
  const r = await eng.rubric({ rubricId: '5', enrollmentId: '6', itemId: '8' });
  assert.deepEqual(r.data, { title: 'R', total: 4, earned: 4, criteria: [{ title: 'Claim', max: 4, earned: 4, level: 'Strong', comment: 'nice' }] });
});

test('queue: at most 2 in flight and starts at least 300 ms apart', async () => {
  let t = 0;
  const starts = [];
  let live = 0, peak = 0;
  const q = makeQueue({ now: () => t, sleep: async (ms) => { t += ms; } });
  await Promise.all([1, 2, 3, 4].map(() => q.run(async () => {
    starts.push(t); live++; peak = Math.max(peak, live);
    await new Promise((r) => setImmediate(r));
    live--;
  })));
  assert.ok(peak <= 2);
  for (let i = 1; i < starts.length; i++) assert.ok(starts[i] - starts[i - 1] >= 300);
});

test('call: an unknown op is error: op', async () => {
  assert.deepEqual(await call('post', {}), { ok: false, error: 'op' });
});

const ROWS = [
  { level: 'period', title: 'Semester 1', due: '', grade: '', comment: '' },
  { level: 'category', title: 'Tests', due: '', grade: '', comment: '', weight: 60 },
  { level: 'item', id: '1', title: 'Unit 1 Test', due: '9/10/26 11:59pm', grade: '18 / 20', comment: 'good' },
  { level: 'item', id: '2', title: 'Unit 2 Test', due: '', grade: '—', comment: '' },
  { level: 'category', title: 'Homework', due: '', grade: '', comment: '', weight: 40 },
  { level: 'item', id: '3', title: 'HW 1', due: '9/3/26 11:59pm', grade: '25 / 24', comment: '', rubric: { rubricId: '5', enrollmentId: '6' } },
];

test('courseGrade: weighted %, letter, per-item impact, floors, history', () => {
  const g = courseGrade({ rows: ROWS });
  assert.equal(g.period, 'Semester 1');
  assert.equal(g.mode, 'weighted');
  assert.ok(Math.abs(g.pct - (0.6 * 90 + 0.4 * (25 / 24) * 100)) < 1e-9);
  assert.equal(g.letter, 'A');
  const hw = g.items.find((i) => i.id === '3');
  assert.deepEqual(hw.rubric, { rubricId: '5', enrollmentId: '6' });
  assert.ok(hw.impact > 0);
  assert.equal(g.items.find((i) => i.id === '2').impact, null);
  assert.equal(g.categories.length, 2);
  assert.equal(g.history.length, 2);
  assert.doesNotThrow(() => JSON.stringify(g));
  const w = courseGrade({ rows: ROWS, opts: { whatIf: { 2: { earned: 0, possible: 20 } } } });
  assert.ok(w.pct < g.pct);
});

test('courseGrade: no rows → empty summary, no throw', () => {
  assert.deepEqual(courseGrade({ rows: [] }).items, []);
});

test('planTest: needFor on the current period', () => {
  const r = planTest({ rows: ROWS, category: 'Tests', possible: 20, target: 93 });
  assert.ok(['any', 'need', 'out'].includes(r.status));
  assert.equal(planTest({ rows: ROWS, category: 'Tests', possible: 0 }).status, 'none');
});
