// The Settings card: opened by the gear beside the Overlay switch, or the
// extension's toolbar icon on a Schoology tab. It is the only place settings
// live (there is no settings tab). docs/extension/OVERLAY-UI.md, "Settings".
//
// A sidebar picks one page at a time, so nothing is a long scroll:
//   Appearance  the THEMES swatch grid (theme.js) and "Percent beside scores",
//               bound to ui.js's setSetting
//   Archive     where archives are saved: Downloads or a chosen folder
//   Statuses    how the To Do words work (overlays/statusguide.js)
//   neo-plan    what it is, who wants it, what syncs; then the token, the
//               Schoology line, Courses and Advanced, framed from
//               panel/neoplan.html so a Schoology page never sees the token
// Each page is a title, one line on what it's for, then grouped rows: a
// title and description on the left, the control on the right. Only the
// page scrolls (the neo-plan frame always grows to fit; it never scrolls).
//
// The sidebar's foot ("Welcome tour") brings back the Welcome card (overlays/intro.js).

import { el, onClick, getSettings, onSettings, setSetting } from './ui.js';
import { THEMES, headerBackground, paint } from './theme.js';
import { createDialog, neoplanFrame, folderInfo, folderText, pickFolder, useDownloads, onFolderChange } from './dialog.js';
import { GUIDE_CSS, guideNodes } from './statusguide.js';

const CSS = `
.card { width: min(600px, calc(100vw - 32px)); height: min(540px, calc(100vh - 64px)); }
.body { flex-direction: row; padding: 0; gap: 0; overflow: hidden; }
.foot { display: none; }

.nav {
  flex: none; width: 152px; padding: 10px 8px; display: flex; flex-direction: column; gap: 2px;
  background: var(--sunk); border-right: 1px solid var(--line);
}
.tab {
  display: flex; align-items: center; min-height: 32px; padding: 6px 10px; border-radius: var(--radius);
  font-weight: 500; color: var(--dim); text-align: left;
}
.tab:hover { color: var(--ink); background: var(--line); }
.tab[aria-selected="true"] { color: var(--accent); background: var(--accent-soft); font-weight: 600; }
.nav .spacer { flex: 1; }
.nav .link { padding: 6px 10px; font-size: 12px; text-align: left; }

.body > .page { flex: 1 1 auto; min-width: 0; min-height: 0; overflow: auto; padding: 18px 20px 20px; display: flex; flex-direction: column; gap: 18px; }
.page > * { flex: none; } /* never squeezed: the page scrolls instead */
.page > header h3 { margin: 0; font-size: 17px; line-height: 22px; font-weight: 600; }
.page > header p { margin: 4px 0 0; font-size: 12px; line-height: 16px; color: var(--dim); }
.page h4 {
  margin: 0 0 8px; font-size: 11px; font-weight: 600; color: var(--dim);
  text-transform: uppercase; letter-spacing: .04em;
}

.group { border: 1px solid var(--line); border-radius: var(--radius); }
.row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; }
.row + .row { border-top: 1px solid var(--line); }
.row .text { flex: 1; min-width: 0; }
.row .title { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row .desc { margin-top: 2px; font-size: 12px; line-height: 16px; color: var(--dim); }
.row .actions { flex: none; display: flex; align-items: center; gap: 12px; }

.themes { display: grid; grid-template-columns: repeat(auto-fill, minmax(64px, 1fr)); gap: 4px; padding: 6px; }
.theme-btn {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 6px 2px; border-radius: var(--radius); border: 1px solid transparent;
  font-size: 11px; line-height: 14px; color: var(--dim); text-align: center;
}
.theme-btn:hover { background: var(--sunk); color: var(--ink); }
.theme-btn[aria-pressed="true"] { color: var(--ink); box-shadow: 0 0 0 2px var(--accent); }
.swatch {
  width: 36px; height: 18px; border-radius: var(--radius-sm); display: flex; overflow: hidden;
  border: 1px solid var(--line); flex: none;
}
.swatch span { flex: 1; }

/* An on/off switch, the same shape as the Overlay switch. */
.toggle {
  appearance: none; -webkit-appearance: none; flex: none; margin: 0; cursor: pointer;
  position: relative; width: 28px; height: 16px; border-radius: 8px; background: var(--line);
}
.toggle::after {
  content: ""; position: absolute; top: 2px; left: 2px;
  width: 12px; height: 12px; border-radius: 50%; background: var(--surface);
}
.toggle:checked { background: var(--accent); }
.toggle:checked::after { left: 14px; }

/* neo-plan: the explainer above the token frame. */
.explain p { margin: 0 0 6px; font-size: 13px; line-height: 18px; }
.explain ul { margin: 0; padding-left: 18px; font-size: 13px; line-height: 19px; }
.explain li.never { color: var(--dim); }
${GUIDE_CSS}
@media (max-width: 600px) {
  .body { flex-direction: column; }
  .nav { width: auto; flex-direction: row; padding: 6px; border-right: 0; border-bottom: 1px solid var(--line); overflow-x: auto; }
  .nav .spacer, .nav .link { display: none; }
}
`;

/**
 * A theme's swatch: its header, then its page with the accent. Painted for
 * the page it sits on: under a dark theme this card is inverted too, so the
 * colours are pre-inverted to still show as they will look.
 */
function paintSwatch(s, theme, pageDark) {
  const [left, right] = s.children;
  left.style.background = headerBackground(theme, pageDark);
  right.style.background = paint(theme.accent, pageDark);
  right.style.boxShadow = `inset 0 -6px 0 ${paint(theme.page || '#F1F4F4', pageDark)}`;
}

function page(title, lede) {
  const p = el('section', 'page');
  p.setAttribute('role', 'tabpanel');
  const h = el('header');
  h.append(el('h3', null, title), el('p', null, lede));
  p.append(h);
  return p;
}

/** A row: title and description on the left, `controls` on the right. */
function row(title, desc, ...controls) {
  const r = el('div', 'row');
  const text = el('div', 'text');
  const t = el('div', 'title', title);
  const d = el('div', 'desc', desc);
  text.append(t, d);
  const actions = el('div', 'actions');
  actions.append(...controls);
  r.append(text, actions);
  return { row: r, title: t, desc: d };
}

function block(heading, ...children) {
  const b = el('div');
  if (heading) b.append(el('h4', null, heading));
  const g = el('div', 'group');
  g.append(...children);
  b.append(g);
  return b;
}

let singleton = null;

export function start({ doc = document } = {}) {
  if (singleton) return singleton;
  const d = createDialog('np-settings-card', { title: 'Settings', css: CSS, doc });

  // ── Appearance ──
  const appearance = page('Appearance', 'How Dewey and Schoology look on every page.');
  const grid = el('div', 'themes');
  const themeBtns = new Map();
  for (const [key, theme] of Object.entries(THEMES)) {
    const tb = el('button', 'theme-btn');
    tb.type = 'button';
    tb.setAttribute('aria-pressed', 'false');
    const sw = el('span', 'swatch');
    sw.append(el('span'), el('span'));
    tb.append(sw, el('span', null, theme.name));
    onClick(tb, () => setSetting('theme', key));
    themeBtns.set(key, tb);
    grid.append(tb);
  }
  const percentBox = doc.createElement('input');
  percentBox.type = 'checkbox';
  percentBox.className = 'toggle';
  percentBox.setAttribute('aria-label', 'Percent beside scores');
  percentBox.addEventListener('change', () => setSetting('percent', percentBox.checked));
  appearance.append(
    block('Theme', grid),
    block('Grades', row('Percent beside scores', 'A small 95% next to each graded score.', percentBox).row),
  );

  // ── Archive ──
  const archive = page('Archive', 'Where the Archive button saves a course’s files, work and grades.');
  const choose = el('button', 'secondary', 'Change…');
  choose.type = 'button';
  const downloads = el('button', 'link', 'Use Downloads');
  downloads.type = 'button';
  const folder = row('', '', downloads, choose);
  archive.append(block('Save to', folder.row));

  async function drawFolder() {
    const info = await folderInfo();
    folder.title.textContent = folderText(info);
    folder.desc.textContent = !info ? ''
      : !info.custom ? 'Chrome’s Downloads folder. Choose your own to keep archives in one place for Claude or another app.'
        : info.granted ? 'Your folder. Dewey writes into it directly.'
          : 'Chrome needs you to allow this folder again. Until then, archives go to Downloads.';
    choose.textContent = info?.custom ? (info.granted ? 'Change…' : 'Allow again…') : 'Choose folder…';
    downloads.hidden = !info?.custom;
  }
  onClick(choose, async () => {
    if (!(await pickFolder())) folder.desc.textContent = 'Reload the extension, then this page.';
  });
  onClick(downloads, async () => { await useDownloads(); drawFolder(); });
  onFolderChange(() => { if (d.isOpen) drawFolder(); });

  // ── Statuses ──
  const statuses = page('How statuses work', 'What the words on your To Do mean. In the extension, Done and Turn in need neo-plan connected.');
  statuses.append(guideNodes());

  // ── neo-plan: the explainer, then the frame, built the first time this page shows ──
  const neoplan = page('neo-plan', 'Optional. Dewey works fully without it.');
  const explain = (heading, ...kids) => {
    const b = el('div', 'explain');
    b.append(el('h4', null, heading), ...kids);
    return b;
  };
  const syncs = el('ul');
  for (const [text, cls] of [
    ['New work from your To Do goes onto the board.'],
    ['Done, Studied and Turned in, both ways (with the iPhone app too).'],
    ['Submitted on Schoology clears it from the board, and neo-plan flags what’s Missing.'],
    ['Type: HW, Test, Task or Classwork.'],
    ['Never your Schoology password, grades or messages.', 'never'],
  ]) syncs.append(el('li', cls, text));
  neoplan.append(
    explain('What it is', el('p', null, 'A planner website (neo-plan.vercel.app) that lays your week out as a board, one column per class. Dewey fills it from Schoology for you.')),
    explain('Who it’s for', el('p', null, 'People who already plan in neo-plan, or want one board shared between this computer and the Dewey iPhone app. It also turns on Done and Turn in on the To Do list here. No account? Skip this.')),
    explain('What Dewey syncs', syncs),
    explain('Connect', el('p', null, 'Sign in at neo-plan.vercel.app, open its Settings and make an extension token (it starts with np_). Paste it below.')),
  );
  let frame = null;

  // ── Sidebar ──
  const pages = [['Appearance', appearance], ['Archive', archive], ['Statuses', statuses], ['neo-plan', neoplan]];
  const nav = el('nav', 'nav');
  nav.setAttribute('role', 'tablist');
  nav.setAttribute('aria-orientation', 'vertical');
  const tabs = pages.map(([name, p], i) => {
    const t = el('button', 'tab', name);
    t.type = 'button';
    t.setAttribute('role', 'tab');
    t.id = `np-settings-tab-${i}`;
    p.setAttribute('aria-labelledby', t.id);
    onClick(t, () => show(i));
    t.addEventListener('keydown', (e) => {
      const step = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 }[e.key];
      if (!step) return;
      e.preventDefault();
      show((i + step + pages.length) % pages.length);
      tabs[current].focus();
    });
    return t;
  });
  const welcome = el('button', 'link', 'Welcome tour');
  welcome.type = 'button';
  onClick(welcome, async () => {
    d.close();
    const { start: startIntro } = await import('./intro.js');
    startIntro({ doc }).open();
  });
  nav.append(...tabs, el('div', 'spacer'), welcome);
  d.body.append(nav, ...pages.map(([, p]) => p));

  let current = 0;
  function show(i) {
    current = i;
    pages.forEach(([, p], n) => { p.hidden = n !== i; });
    tabs.forEach((t, n) => {
      t.setAttribute('aria-selected', String(n === i));
      t.tabIndex = n === i ? 0 : -1;
    });
    if (pages[i][1] === neoplan && !frame) { frame = neoplanFrame('settings', { doc }); neoplan.append(frame); }
    if (pages[i][1] === archive) drawFolder();
  }

  const drawSettings = (s) => {
    const pageDark = !!THEMES[s.theme]?.dark;
    for (const [key, tb] of themeBtns) {
      tb.setAttribute('aria-pressed', String(s.theme === key));
      paintSwatch(tb.firstChild, THEMES[key], pageDark);
    }
    percentBox.checked = !!s.percent;
  };
  drawSettings(getSettings());
  onSettings(drawSettings);

  /** name: a sidebar page to open on ('Statuses'); otherwise the last one shown. */
  function open(name) {
    const at = pages.findIndex(([n]) => n === name);
    show(at >= 0 ? at : current);
    d.open();
  }

  singleton = { ...d, open, get isOpen() { return d.isOpen; } };
  return singleton;
}
