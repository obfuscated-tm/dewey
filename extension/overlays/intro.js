// The Welcome card: shown once, the first time the overlay appears on a
// Schoology page (overlays/switch.js), and again from Settings → "Show
// welcome again". Two short steps, the second optional:
//
//   1  What Dewey adds, and where its buttons are
//   2  Where archives are saved (Downloads, or a folder you choose)
//
// neo-plan isn't here: it's optional, and explained in Settings → neo-plan.
//
// Closing it any way (Skip setup, ×, Esc, Done) counts as seen: it never
// nags. Everything here is also in Settings.

import { el, onClick } from './ui.js';
import { createDialog, folderInfo, folderText, pickFolder, useDownloads, onFolderChange, DOWNLOADS_TEXT } from './dialog.js';

export const INTRO_KEY = 'introSeen';

const CSS = `
.step { display: flex; flex-direction: column; gap: 12px; }
.step h3 { margin: 0; font-size: 17px; line-height: 22px; font-weight: 600; }
.step p { margin: 0; font-size: 13px; color: var(--ink); }
.step p.dim { color: var(--dim); font-size: 12px; }
.tour { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.tour li { display: grid; grid-template-columns: 92px 1fr; gap: 10px; font-size: 13px; }
.tour b { font-weight: 600; }
.choice {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border: 1px solid var(--line); border-radius: var(--radius); background: var(--sunk);
}
.choice .name { font-weight: 600; margin-right: auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dots { display: flex; gap: 6px; margin-right: auto; }
.dots span { width: 6px; height: 6px; border-radius: 50%; background: var(--line); }
.dots span.on { background: var(--accent); }
.optional { color: var(--dim); font-weight: 400; font-size: 12px; margin-left: 6px; }
`;

/** Has the Welcome card been seen? (false outside the extension) */
export async function introSeen(storage = globalThis.chrome?.storage?.local) {
  try { return !!(await storage.get(INTRO_KEY))?.[INTRO_KEY]; } catch { return true; }
}

let singleton = null;

export function start({ doc = document, storage = globalThis.chrome?.storage?.local } = {}) {
  if (singleton) return singleton;
  const d = createDialog('np-intro-card', { title: 'Welcome to Dewey', css: CSS, doc });
  d.onClose(() => { storage?.set({ [INTRO_KEY]: true }).catch(() => {}); });

  // 1 · What it adds
  const s1 = el('div', 'step');
  s1.append(
    el('h3', null, 'Schoology, a little easier'),
    el('p', null, 'Dewey adds a To Do list grouped by class, grades you can play what-if with, and an archiver that saves your classes before they disappear.'),
  );
  const tour = el('ul', 'tour');
  for (const [name, text] of [
    ['Overlay', 'Bottom left. Turns Dewey off to see plain Schoology (Alt+S).'],
    ['Archive', 'The box beside it. Saves a course’s files, work and grades.'],
    ['Settings', 'The gear. Theme, grades, archive folder and how statuses work.'],
  ]) {
    const li = el('li');
    li.append(el('b', null, name), el('span', null, text));
    tour.append(li);
  }
  s1.append(tour);

  // 2 · Archive folder
  const s2 = el('div', 'step');
  const h2 = el('h3', null, 'Where should archives go?');
  h2.append(el('span', 'optional', 'Optional'));
  const choice = el('div', 'choice');
  const folderName = el('span', 'name', DOWNLOADS_TEXT);
  const choose = el('button', 'secondary', 'Choose folder…');
  choose.type = 'button';
  const reset = el('button', 'link', 'Use Downloads');
  reset.type = 'button';
  reset.hidden = true;
  choice.append(folderName, reset, choose);
  const note = el('p', 'dim', 'Downloads works fine. Pick a folder of your own if you want Claude or another app to find your archives in one place. You can change this later in Settings.');
  s2.append(h2, choice, note);

  async function drawFolder() {
    const info = await folderInfo();
    folderName.textContent = folderText(info);
    choose.textContent = info?.custom ? 'Change…' : 'Choose folder…';
    reset.hidden = !info?.custom;
  }
  onClick(choose, async () => {
    if (!(await pickFolder())) note.textContent = 'Reload the extension, then this page.';
  });
  onClick(reset, async () => { await useDownloads(); drawFolder(); });
  onFolderChange(() => { if (d.isOpen) drawFolder(); });

  const steps = [s1, s2];
  let at = 0;
  d.body.append(...steps);

  const dots = el('div', 'dots');
  for (let i = 0; i < steps.length; i++) dots.append(el('span'));
  const back = el('button', 'secondary', 'Back');
  back.type = 'button';
  const skip = el('button', 'link', 'Skip setup');
  skip.type = 'button';
  const next = el('button', 'primary', 'Next');
  next.type = 'button';
  d.foot.append(dots, skip, back, next);

  function show(i) {
    at = Math.max(0, Math.min(steps.length - 1, i));
    steps.forEach((s, n) => { s.hidden = n !== at; });
    [...dots.children].forEach((dot, n) => dot.classList.toggle('on', n === at));
    back.hidden = at === 0;
    skip.hidden = at === steps.length - 1;
    next.textContent = at === 0 ? 'Get started' : at === steps.length - 1 ? 'Done' : 'Next';
    d.setSub(`Step ${at + 1} of ${steps.length}`);
    if (at === 1) drawFolder();
  }
  onClick(back, () => show(at - 1));
  onClick(skip, () => d.close());
  onClick(next, () => (at === steps.length - 1 ? d.close() : show(at + 1)));

  function open() {
    show(0);
    d.open();
  }

  singleton = { ...d, open, get isOpen() { return d.isOpen; } };
  return singleton;
}
