// Quiz quiet mode: while any Schoology tab is in a Test/Quiz, the
// extension does nothing in the background — no sync (alarm or tab-load), no
// MCP bridge, no archive. background.js checks `quizTabOpen()` before each of
// those and stops everything when a tab enters a quiz page; the content
// scripts stay off quiz-taking pages (manifest exclude_matches, boot.js).
//
// Not a quiz in progress: an old-style quiz's attempts and answer review
// (/assignment/ID/assessment_view…), and a newer quiz's start page once
// overlays/quizpage.js has seen that's all it shows. That page runs the
// attempt on the same URL, so until it says so, and from the moment an
// attempt starts, it counts.
//
// "Quiz page" is deliberately broad: anything with "assessment" in the path
// (/assignment/ID/assessment, /assignment/ID/assessment_view/…,
// /course/ID/assessments/ID/…) plus the take/start/resume URLs
// reader/client.js's isQuizTakingUrl never fetches.

import { isQuizTakingUrl } from '../reader/client.js';

const SCHOOLOGY_RE = /^https:\/\/[a-z0-9-]+\.schoology\.com\//i;

/** Is `url` a Schoology Test/Quiz page (taking it, or its attempts/review)? */
export function isQuizPageUrl(url) {
  if (!url || !SCHOOLOGY_RE.test(url)) return false;
  try {
    return /assessment/i.test(new URL(url).pathname) || isQuizTakingUrl(url);
  } catch {
    return false;
  }
}

/** An old-style quiz's attempts list or one attempt's answer review: history, not a quiz in progress. */
export function isQuizReviewUrl(url) {
  try { return /^\/assignment\/\d+\/assessment_view(\/\d+)?\/?$/.test(new URL(url).pathname); } catch { return false; }
}

/** A newer quiz's own page: its start page, but also where its attempts run. */
export function isQuizLandingUrl(url) {
  try { return /^\/course\/\d+\/assessments\/\d+\/?$/.test(new URL(url).pathname); } catch { return false; }
}

/**
 * Is `tab` in a quiz? Pure. A quiz page counts unless it's an old-style
 * quiz's attempts/review, or a newer quiz's page that overlays/quizpage.js
 * has said shows only its start page, at this very URL (`landing`:
 * { [tabId]: url }). A tab still loading counts by where it's going.
 */
export function tabInQuiz(tab, { ours = new Set(), landing = {} } = {}) {
  if (ours.has(tab.id)) return false;
  return [tab.url, tab.pendingUrl].some((u) => isQuizPageUrl(u)
    && !isQuizReviewUrl(u)
    && !(u === tab.url && !tab.pendingUrl && isQuizLandingUrl(u) && landing[tab.id] === u));
}

/**
 * Is any tab in a quiz? The archiver's own quiz-review tabs (storage.session
 * `reviewTabs`) don't count — those are its read-only /assessment_view
 * visits, not the student in a quiz.
 */
export async function quizTabOpen() {
  const tabs = await chrome.tabs.query({ url: 'https://*.schoology.com/*' }).catch(() => []);
  const { reviewTabs = [], quizLanding = {} } = await chrome.storage.session.get(['reviewTabs', 'quizLanding']).catch(() => ({}));
  const ours = new Set(reviewTabs);
  return tabs.some((t) => tabInQuiz(t, { ours, landing: quizLanding }));
}

/** overlays/quizpage.js's word on tab `tabId` (at `url`): 'landing' or 'taking'. */
export async function noteQuizPage(tabId, url, state) {
  const { quizLanding = {} } = await chrome.storage.session.get('quizLanding').catch(() => ({}));
  const was = quizLanding[tabId];
  if (state === 'landing' && isQuizLandingUrl(url)) quizLanding[tabId] = url;
  else delete quizLanding[tabId];
  if (quizLanding[tabId] === was) return;
  await chrome.storage.session.set({ quizLanding }).catch(() => {});
}
