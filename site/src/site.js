// The page's small interactive bits: theme switch, today's stamp, the due-date
// card, the grade what-if, the archive shelf, the install tabs, and a download
// button that fits the device. Everything works (as plain content) without it.

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const dayStamp = (d) => `${MONTHS[d.getMonth()]} ${String(d.getDate()).padStart(2, '0')}`;

// ---------- theme ----------

const themeBtn = $('.theme');
if (themeBtn) {
  themeBtn.addEventListener('click', () => {
    const root = document.documentElement;
    const dark = root.dataset.theme
      ? root.dataset.theme === 'dark'
      : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    try { localStorage.setItem('theme', root.dataset.theme); } catch { /* private window */ }
  });
}

// ---------- today's stamp ----------

const today = new Date();
for (const el of $$('[data-today]')) el.textContent = `${dayStamp(today)} ${today.getFullYear()}`;

// ---------- due-date card ----------

const rows = $$('[data-todo] li');
const left = $('[data-left]');

function stamp(text, cls = '', r = 0) {
  const s = document.createElement('span');
  s.className = `stamp ${cls}`.trim();
  s.textContent = text;
  if (r) s.style.setProperty('--r', `${r}deg`);
  return s;
}

rows.forEach((li, i) => {
  const off = Number(li.dataset.due);
  const when = $('.when', li);
  const tilt = [-4, 2, -2, 3, -3][i % 5];
  if (off < 0) when.append(stamp(`${-off} d late`, '', tilt));
  else if (off === 0) when.append(stamp('Today', '', tilt));
  else {
    const d = new Date(today);
    d.setDate(d.getDate() + off);
    when.append(stamp(dayStamp(d), 'due', tilt));
  }

  const btn = $('button', li);
  btn.addEventListener('click', () => {
    const done = !li.classList.contains('done');
    li.classList.toggle('done', done);
    btn.setAttribute('aria-pressed', String(done));
    $('.done-stamp', li)?.remove();
    if (done) when.append(stamp('test' in li.dataset ? 'Studied' : 'Done', 'done-stamp'));
    left.textContent = rows.filter((r) => !r.classList.contains('done')).length;
  });
});

// ---------- grade what-if ----------
// A weighted average, like the gradebook: Tests 50%, Labs 30%, Homework 20%.

const grade = $('[data-grade]');
if (grade) {
  const TESTS = { earned: 182, possible: 200 }; // 46/50, 44/50, 92/100 so far
  const LABS = 94;
  const HW = 98;
  const OUT_OF = 50;
  const course = (score) => {
    const tests = ((TESTS.earned + score) / (TESTS.possible + OUT_OF)) * 100;
    return { tests, total: tests * 0.5 + LABS * 0.3 + HW * 0.2 };
  };
  const letter = (g) => (g >= 90 ? 'A' : g >= 80 ? 'B' : g >= 70 ? 'C' : g >= 60 ? 'D' : 'F');
  const now = (TESTS.earned / TESTS.possible) * 100 * 0.5 + LABS * 0.3 + HW * 0.2;
  let need = OUT_OF + 1;
  for (let s = 0; s <= OUT_OF; s++) if (course(s).total >= 90) { need = s; break; }

  const input = $('#whatif', grade);
  const render = () => {
    const score = Number(input.value);
    const { tests, total } = course(score);
    const r = Math.round(total * 10) / 10;
    const delta = Math.round((r - Math.round(now * 10) / 10) * 10) / 10;
    $('[data-g-letter]', grade).textContent = letter(r);
    $('[data-g-num]', grade).textContent = r.toFixed(1);
    $('[data-g-tests]', grade).textContent = `${tests.toFixed(1)}%`;
    $('[data-g-score]', grade).textContent = `${score} / ${OUT_OF}`;
    const move = delta === 0 ? 'No change from today.'
      : `<span class="${delta < 0 ? 'down' : ''}">${delta > 0 ? '+' : '−'}${Math.abs(delta).toFixed(1)}</span> from today's ${now.toFixed(1)}.`;
    const keep = r >= 90
      ? `You need <b>${need}/${OUT_OF}</b> to keep your A.`
      : `That drops you to a <b>${letter(r)}</b>. ${need}/${OUT_OF} keeps the A.`;
    $('[data-g-line]', grade).innerHTML = `${move} ${keep}`;
  };
  input.addEventListener('input', render);
  render();
}

// ---------- archive shelf ----------

const archiveBtn = $('[data-archive]');
if (archiveBtn) {
  const files = $$('[data-files] li');
  const book = $('[data-book]');
  let saved = false;
  archiveBtn.addEventListener('click', async () => {
    if (saved) {
      files.forEach((f) => f.classList.remove('got'));
      book.classList.remove('in');
      archiveBtn.textContent = 'Archive Chemistry';
      saved = false;
      return;
    }
    archiveBtn.disabled = true;
    archiveBtn.textContent = 'Saving…';
    for (const f of files) {
      await new Promise((r) => setTimeout(r, reduced ? 0 : 320));
      f.classList.add('got');
    }
    book.classList.add('in');
    archiveBtn.disabled = false;
    archiveBtn.textContent = 'Saved. Run it again';
    saved = true;
  });
}

// ---------- install tabs ----------

const tabs = $$('[role="tab"]');
function selectTab(tab) {
  for (const t of tabs) {
    const on = t === tab;
    t.setAttribute('aria-selected', String(on));
    t.tabIndex = on ? 0 : -1;
    document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
  }
}
tabs.forEach((t, i) => {
  t.addEventListener('click', () => selectTab(t));
  t.addEventListener('keydown', (e) => {
    const step = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (!step) return;
    const next = tabs[(i + step + tabs.length) % tabs.length];
    selectTab(next);
    next.focus();
  });
});

// ---------- a download button that fits the device ----------

const get = $('[data-get]');
if (get) {
  const ua = navigator.userAgent;
  const btn = $('[data-get-btn]', get);
  const note = $('[data-get-note]');
  const tab = (id) => selectTab(document.getElementById(id));
  if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) {
    btn.textContent = 'iPhone app: coming soon';
    btn.href = '#install';
    note.textContent = "It's in testing. The Chrome extension works on your computer today.";
    tab('tab-iphone');
  } else if (/Android/.test(ua)) {
    btn.textContent = 'Download for Android';
    btn.href = '/download/dewey-android.apk';
    note.textContent = `Beta ${get.dataset.android} · Android 8.0 or newer · free`;
    tab('tab-android');
  } else if (!/Chrome\//.test(ua) || /Firefox\//.test(ua)) {
    note.textContent = `Version ${get.dataset.ext} · free · needs Google Chrome`;
  }
}
