// How statuses work: the words on the To Do rows (To do → Done, not
// submitted → Turned in / Submitted), what "on Schoology" means, why paper
// or lab-site work isn't missing just because Schoology says not submitted,
// and Studied for tests. Same words as the iOS app's StatusGuideView
// (docs/ios/design/Tutorial.options.html, option C).
//
// Two places use it:
//   Settings → Statuses     guideNodes(): the whole guide (overlays/settings.js)
//   a To Do row             tipNode(): shown once, under the first row the
//                           student marks Done (or Studied, or Turn in), until
//                           closed (marks.js calls row.onTick, todorows.js draws it)

import { el, onClick } from './ui.js';

export const TIP_KEY = 'statusTipSeen';

/**
 * Which tip a change earns, or null: a row going from to do to done (ready),
 * or turned in. Pure. op: 'work' (with work: 'ready' | 'todo') or 'turnIn'.
 */
export function tipKindFor(op, { work, exam } = {}) {
  if (op === 'turnIn') return 'turnedIn';
  if (op === 'work' && work === 'ready') return exam ? 'studied' : 'done';
  return null;
}

/** The tip's words: [[text, bold], …]. Pure. */
export function tipParts(kind) {
  switch (kind) {
    case 'studied':
      return [['Studied stops the night-before reminder. ', true], ['Tests are never turned in.', false]];
    case 'turnedIn':
      return [['Turned in ', true], ['means you handed it in on paper, on a lab site or in class. Dewey says Submitted only when Schoology does.', false]];
    default:
      return [['Done is just for you. ', true], ['Hand it in next: ', false], ['Submit', true],
        [' on the assignment page for a Schoology dropbox, ', false], ['Turn in', true], [' for paper or a lab site.', false]];
  }
}

export const TIP_CSS = `
.tip {
  grid-column: 2 / span 2; grid-row: 4; margin-top: 6px; padding: 8px 28px 8px 10px; position: relative;
  background: var(--accent-soft); border-radius: var(--radius); font-size: 12px; line-height: 17px; color: var(--ink);
}
.tip[hidden] { display: none; }
.tip b { font-weight: 600; }
.tip .more { display: inline; margin: 0 0 0 4px; }
.tip .x {
  position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; padding: 0; border: 0; background: none;
  color: var(--faint); font-size: 14px; line-height: 22px; cursor: pointer;
}
.tip .x:hover { color: var(--ink); }
`;

/** The tip under a row. onGuide: open Settings → Statuses. onClose: take it away. */
export function tipNode(kind, { onGuide, onClose }) {
  const box = el('div', 'tip');
  box.setAttribute('role', 'note');
  for (const [text, bold] of tipParts(kind)) box.append(bold ? el('b', null, text) : document.createTextNode(text));
  const more = el('a', 'more', 'How statuses work');
  more.href = '#';
  onClick(more, () => onGuide?.());
  const x = el('button', 'x', '×');
  x.type = 'button';
  x.setAttribute('aria-label', 'Close tip');
  onClick(x, () => onClose?.());
  box.append(more, x);
  return box;
}

export const GUIDE_CSS = `
.ladder { display: flex; flex-direction: column; gap: 12px; }
.lstep { display: grid; grid-template-columns: 22px 1fr; gap: 10px; align-items: start; }
.lstep .w { font-weight: 600; }
.lstep .d { margin-top: 2px; font-size: 12px; line-height: 17px; color: var(--dim); }
.mk { width: 18px; height: 18px; margin-top: 1px; border-radius: 50%; border: 1.5px solid var(--dim); box-sizing: border-box;
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; }
.mk.done { border: 2px solid var(--accent); color: var(--accent); background: var(--accent-soft); }
.mk.in { border: 0; background: var(--good, #23794A); color: #fff; }
.mk.sq { border-radius: 4px; }
.mk.plain { border: 0; color: var(--dim); font-size: 14px; }
.branch { margin-top: 6px; display: flex; flex-direction: column; gap: 4px; }
.branch div { padding: 6px 8px; border-radius: var(--radius); background: var(--sunk); font-size: 12px; line-height: 17px; }
.branch b { color: var(--good, #23794A); }
.callout { padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--radius); font-size: 12px; line-height: 17px; color: var(--dim); }
.callout b { display: block; margin-bottom: 2px; color: var(--ink); font-size: 13px; }
`;

function lstep(mark, cls, word, detail) {
  const s = el('div', 'lstep');
  const m = el('span', `mk ${cls}`.trim(), mark);
  m.setAttribute('aria-hidden', 'true');
  const t = el('div');
  t.append(el('div', 'w', word), el('div', 'd', detail));
  s.append(m, t);
  return { s, t };
}

/** The whole guide, for Settings → Statuses. */
export function guideNodes() {
  const ladder = el('div', 'ladder');
  const turned = lstep('✓', 'in', 'Turned in · Submitted', 'Your teacher has it. Two kinds:');
  const branch = el('div', 'branch');
  for (const [word, rest] of [
    ['Submitted', ': Schoology’s dropbox has your file. Dewey says this only when Schoology does.'],
    ['Turned in', ': you clicked Turn in because it went in on paper, on a lab site or in class.'],
  ]) {
    const line = el('div');
    line.append(el('b', null, word), document.createTextNode(rest));
    branch.append(line);
  }
  turned.t.append(branch);
  const callout = el('div', 'callout');
  callout.append(
    el('b', null, 'Why paper work isn’t missing'),
    document.createTextNode('Schoology only knows about its own dropbox. Paper, lab sites and Google Assignments look “not submitted” there even after you hand them in, and some teachers grade paper even when there’s a dropbox. If one says Missing, click Turn in once it’s handed in and Dewey clears it.'),
  );
  ladder.append(
    lstep('', '', 'To do', 'Not finished yet.').s,
    lstep('✓', 'done', 'Done, not submitted', 'You finished it. Nothing is handed in yet. If it has a Schoology dropbox, it still needs Submit on the assignment page.').s,
    turned.s,
    callout,
    lstep('', 'sq', 'Tests: Studied', 'Tests aren’t turned in. Mark Studied when you’re ready.').s,
    lstep('↺', 'plain', 'Changed your mind?', 'Not done and Undo put a row back.').s,
  );
  return ladder;
}
