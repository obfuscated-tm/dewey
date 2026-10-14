// Pure URL guard, shared by reader/client.js (the extension) and
// engine/engine.js (the iOS app's engine). No chrome.*, no DOM.

// VIEW ONLY safety net: pages that take/start/resume a quiz are never requested.
// /assignment/ID/assessment is the Test/Quiz "take" tab (/assessment_view is the read-only history).
export function isQuizTakingUrl(url) {
  const p = new URL(url).pathname;
  return /\/assessment\/?$/.test(p) || /\/assessment\/(start|take|resume|attempt|submit)/.test(p)
    || /\/(start|take|resume)[_-]?(attempt|assessment|quiz)/i.test(p)
    // /assessment/5/start, /course/1/assessments/2/take, …/assessments/2/resume/
    || /\/assessments?\/(\d+\/)*(start|take|resume|submit|begin)(\/|$)/i.test(p)
    // the dropbox submit form: opening it submits nothing, but nothing here needs it
    || /\/dropbox\/submit(\/|$)/i.test(p)
    // a newer quiz being taken: /common-assessment-delivery/start/ID?action=onresume…
    || /^\/common-assessment-delivery(\/|$)/i.test(p);
}
