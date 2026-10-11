// Content script (classic, document_start): applies the page theme before
// first paint, so a dark theme doesn't flash white while boot.js and the
// rest wait for document_idle. Just dynamic-imports theme.js (a module) and
// calls its start(), which waits for the Overlay switch and the settings
// itself (see overlays/theme.js and docs/OVERLAY-UI.md, "Themes and settings").
(() => {
  if (window.top !== window) return;
  // Quiz pages: only an old-style quiz's attempts and answer review here. A
  // newer quiz's start page is themed by overlays/quizpage.js (it can tell the
  // start page from an attempt; this early, nothing can). Taking one: never.
  const p = location.pathname;
  if ((/assessment/i.test(p) || /\/(start|take|resume)[_-]?(attempt|quiz)/i.test(p))
    && !/^\/assignment\/\d+\/assessment_view(\/\d+)?\/?$/.test(p)) return;
  import(chrome.runtime.getURL('overlays/theme.js'))
    .then((m) => m.start())
    .catch((e) => console.debug('[neo-plan theme]', e));
})();
