// A newer quiz's start page (/course/{c}/assessments/{id}): the assignment
// chip (overlays/assignment.js) and the page theme, while the page is only
// the start page. Schoology runs the start page, the attempt and the answer
// review as one app on that one URL, so the URL can't say which it is; the
// page can. On fuhsd.schoology.com (2026-10-10) the start page is the React
// root div.assessment-delivery-landing-app holding "Start Attempt" (or
// Resume) and a Previous Attempts list, with no answer fields.
//
// The moment that stops being true (an attempt starts, an answer field shows
// up, the URL moves) everything comes off for good: the chip, the what-if,
// the theme, and the worker is told, so sync and the MCP bridge go quiet
// (sync/quiet.js). Nothing here clicks or changes anything of Schoology's.

import { parseCommonAssessment } from '../reader/parse/quiz.js';
import { cleanText } from '../util.js';
import { removeAllHosts } from './ui.js';
import { start as startTheme, stop as stopTheme } from './theme.js';
import { start as startChip } from './assignment.js';

const LANDING = '.assessment-delivery-landing-app';
const WAIT_MS = 10_000; // the app renders after document_idle; give it this long
const PATH_RE = /^\/course\/(\d+)\/assessments\/(\d+)\/?$/;

/** What the chip needs from the quiz's embedded data. Pure. */
export function quizFacts(init, url) {
  const m = new URL(url).pathname.match(PATH_RE);
  if (!m) return null;
  const subs = init?.submissions || [];
  return {
    id: String(init?.gradeItemNid || m[2]),
    section: String(init?.sectionNid || m[1]),
    title: init?.title || null,
    submitted: subs.some((s) => s.completed === true),
  };
}

/** Is this the start page and nothing more? (no answer field anywhere in the content) */
export function onStartPage(doc) {
  const app = doc.querySelector(LANDING);
  if (!app) return false;
  const content = doc.querySelector('#center-inner') || doc.body;
  return !content.querySelector('input:not([type=hidden]), textarea, select, [contenteditable=""], [contenteditable="true"], iframe:not([src*="session-tracker"])');
}

export function start({ doc = document, loc = location, send = (m) => chrome.runtime.sendMessage(m) } = {}) {
  const path = loc.pathname;
  if (!PATH_RE.test(path)) return null;
  const tell = (state) => { try { send({ type: 'quizPage', state })?.catch?.(() => {}); } catch { /* worker asleep */ } };

  let chip = null;
  let anchor = null;
  let halted = false;
  let observer = null;

  function halt() {
    if (halted) return;
    halted = true;
    observer?.disconnect();
    chip?.stop();
    removeAllHosts();
    anchor?.remove();
    stopTheme();
    tell('taking');
  }

  function begin() {
    const facts = quizFacts(parseCommonAssessment(doc.documentElement.outerHTML), loc.href);
    tell('landing');
    startTheme();
    if (facts) {
      // Beside the app's own container, never inside React's tree.
      const box = doc.querySelector(LANDING).parentElement;
      anchor = doc.createElement('np-anchor');
      anchor.hidden = true;
      box.insertAdjacentElement('beforebegin', anchor);
      const title = facts.title || cleanText(doc.querySelector('h1.page-title')) || null;
      chip = startChip({
        doc, loc, send,
        quiz: { id: facts.id, page: { schoology_id: facts.id, title, section_id: facts.section, due_at: null, anchor, quiz: true }, submitted: facts.submitted },
      });
    }
    observer = new MutationObserver(() => { if (loc.pathname !== path || !onStartPage(doc)) halt(); });
    observer.observe(doc.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['contenteditable'] });
  }

  if (onStartPage(doc)) { begin(); return { halt }; }
  // Not rendered yet: wait for the start page; anything else, stay off.
  const waiting = new MutationObserver(() => {
    if (loc.pathname !== path) { waiting.disconnect(); clearTimeout(timer); tell('taking'); return; }
    if (onStartPage(doc)) { waiting.disconnect(); clearTimeout(timer); begin(); }
  });
  const timer = setTimeout(() => { waiting.disconnect(); tell('taking'); }, WAIT_MS);
  waiting.observe(doc.body, { childList: true, subtree: true });
  return { halt };
}

if (typeof chrome !== 'undefined' && chrome.runtime?.id && typeof location !== 'undefined' && !globalThis.__npNoAutoStart) start();
