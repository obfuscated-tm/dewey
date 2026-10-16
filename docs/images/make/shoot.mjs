// Takes the README's extension screenshots from the stand-in pages in
// extension/test/ (made-up classes), at 2x, with headless Chrome.
//
//   python3 -m http.server 8765 --directory extension     (in one terminal)
//   node docs/images/make/shoot.mjs                       (writes docs/images/*.png)
//
// Each shot loads a page, optionally runs `prep` in it, then clips to the
// rectangle `clip` returns (CSS pixels).
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const BASE = process.env.BASE || 'http://localhost:8765/test/';
const OUT = new URL('../', import.meta.url).pathname;
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9333;

const rect = (sel, pad = 0, maxH = Infinity) => `(() => {
  const r = document.querySelector(${JSON.stringify(sel)}).getBoundingClientRect();
  return { x: r.x - ${pad}, y: r.y - ${pad}, width: r.width + ${2 * pad}, height: Math.min(r.height, ${maxH}) + ${2 * pad} };
})()`;

const SHOTS = [
  { name: 'home', page: 'home-page.html', width: 1000, clip: rect('np-todo', 2, 600) },
  {
    name: 'grades', page: 'grades-page.html', width: 1000,
    // Fold Labs & Homework so both categories fit.
    prep: `document.querySelector('tr.category-row').click()`,
    // From the heading to the end of the last category (no "(no grading period)").
    clip: `(() => { const a = document.getElementById('h').getBoundingClientRect(), t = document.querySelector('#list table').getBoundingClientRect();
      const end = [...document.querySelectorAll('tr.period-row')].pop().getBoundingClientRect().top;
      return { x: a.x - 8, y: a.y - 8, width: t.width + 16, height: end - a.y + 8 }; })()`,
  },
  {
    name: 'materials', page: 'materials-page.html', width: 960,
    clip: `(() => { const r = document.getElementById('center-inner').getBoundingClientRect();
      return { x: r.x - 8, y: r.y - 2, width: r.width + 16, height: r.height + 10 }; })()`,
  },
  {
    name: 'assignment', page: 'assignment-page.html', width: 1000,
    clip: `(() => { const r = document.getElementById('main-inner').getBoundingClientRect();
      const c = document.querySelector('.comment-container').getBoundingClientRect();
      return { x: r.x - 8, y: r.y - 8, width: r.width + 16, height: Math.max(c.bottom, 0) - r.y + 16 }; })()`,
  },
  // The card sits centred in a 1000x800 window, 440 wide; keep a little of the backdrop.
  { name: 'archive', page: 'archive-card.html', width: 1000, height: 800, clip: `({ x: 264, y: 213, width: 472, height: 374 })` },
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function cdp(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0;
  const waiting = new Map();
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && waiting.has(m.id)) { waiting.get(m.id)(m); waiting.delete(m.id); }
  };
  const send = (method, params = {}) => new Promise((r, j) => {
    const n = ++id;
    waiting.set(n, (m) => (m.error ? j(new Error(`${method}: ${m.error.message}`)) : r(m.result)));
    ws.send(JSON.stringify({ id: n, method, params }));
  });
  return { send, close: () => ws.close() };
}

const profile = mkdtempSync(join(tmpdir(), 'dewey-shoot-'));
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--hide-scrollbars', '--no-first-run', 'about:blank'], { stdio: 'ignore' });
try {
  let targets;
  for (let i = 0; i < 50 && !targets; i++) {
    await sleep(200);
    targets = await fetch(`http://127.0.0.1:${PORT}/json`).then((r) => r.json()).catch(() => null);
  }
  const page = targets.find((t) => t.type === 'page');
  const c = await cdp(page.webSocketDebuggerUrl);
  await c.send('Page.enable');
  for (const s of SHOTS) {
    await c.send('Emulation.setDeviceMetricsOverride', { width: s.width, height: s.height || 1600, deviceScaleFactor: 2, mobile: false });
    await c.send('Page.navigate', { url: BASE + s.page });
    await sleep(2000);
    if (s.prep) { await c.send('Runtime.evaluate', { expression: s.prep }); await sleep(600); }
    let clip;
    if (s.clip) {
      const { result } = await c.send('Runtime.evaluate', { expression: s.clip, returnByValue: true });
      clip = { ...result.value, scale: 1 };
    }
    const { data } = await c.send('Page.captureScreenshot', { format: 'png', ...(clip ? { clip } : {}) });
    writeFileSync(join(OUT, `${s.name}.png`), Buffer.from(data, 'base64'));
    console.log(`${s.name}.png`, clip ? `${Math.round(clip.width)}x${Math.round(clip.height)}` : 'full');
  }
  c.close();
} finally {
  chrome.kill();
}
