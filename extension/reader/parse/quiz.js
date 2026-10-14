import { cleanText } from '../../util.js';
import { htmlToMd } from '../md.js';
import { parseDueText } from './sync.js';

// ── Quizzes ──────────────────────────────────────────────────────────────

// Newer "common assessment" quizzes (/course/X/assessments/Y) embed their data as
// JSON: {"initialization": {title, instructions, score, pointsTotal, submissions: [...]}}
export function parseCommonAssessment(html) {
  const start = html.indexOf('{"initialization"');
  if (start < 0) return null;
  let depth = 0;
  let inStr = false;
  let esc = false;
  for (let k = start; k < html.length; k++) {
    const c = html[k];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{') depth++;
    else if (c === '}' && --depth === 0) {
      try { return JSON.parse(html.slice(start, k + 1)).initialization; } catch { return null; }
    }
  }
  return null;
}

/** A newer quiz's landing page (/course/X/assessments/Y or /course/X/common-assessment/Y). */
export const QUIZ_LANDING_RE = /^\/course\/\d{1,20}\/(assessments|common-assessment)\/\d{1,20}\/?$/;

const htmlText = (s) => String(s || '').replace(/<br\s*\/?>|<\/(p|div|li)>/gi, '\n').replace(/<[^>]+>/g, '')
  .replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&amp;/g, '&')
  .replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);
const unix = (v) => (num(v) > 0 ? new Date(v * 1000).toISOString() : null);

/**
 * What a newer quiz's landing page says, for reading only: { title,
 * instructions, attempts_allowed, attempts_taken, time_limit_min, opens_at,
 * closes_at, overdue, score, max_points, letter, state, attempts: [{ n,
 * finished, at, minutes, can_view }] }. Pure (string in, JSON out), and it
 * copies only these fields: the page also carries a CSRF token and a JWT,
 * which must never leave it. `state` is in_progress (an attempt is open),
 * submitted, or not_started. null when the page has no quiz data.
 */
export function quizInfo(html) {
  const init = parseCommonAssessment(String(html || ''));
  if (!init) return null;
  const subs = Array.isArray(init.submissions) ? init.submissions : [];
  const attempts = subs.map((s, i) => ({
    n: i + 1,
    finished: s.completed === true,
    at: parseDueText(`${s.last_modified_date || ''} at ${s.last_modified_time || ''}`),
    minutes: num(s.minutes_elapsed),
    can_view: s.can_student_view === true,
  }));
  const score = num(init.score);
  const overdue = /"assessmentIsOverdue":(true|false)/.exec(html);
  return {
    title: init.title || null,
    instructions: htmlText(init.instructions) || null,
    attempts_allowed: num(init.numAttemptsAllowed) || null, // 0: no limit
    attempts_taken: num(init.numAttemptsTaken) ?? attempts.filter((a) => a.finished).length,
    time_limit_min: num(init.minutesMax) || null,
    opens_at: unix(init.availableFrom),
    closes_at: unix(init.availableTo),
    overdue: overdue ? overdue[1] === 'true' : null,
    score,
    max_points: num(init.gradebookPointsTotal) ?? num(init.pointsTotal),
    // The letter is only meaningful once there is a score (it says "F" before).
    letter: score != null ? (init.gradebookCustomScaleText || null) : null,
    state: attempts.some((a) => !a.finished) ? 'in_progress' : attempts.length ? 'submitted' : 'not_started',
    attempts,
  };
}

// Assignment-based Test/Quiz: /assignment/ID/assessment_view lists your attempts with "view assessment" links.
export function parseLegacyQuizAttempts(doc, client) {
  return [...doc.querySelectorAll('a[href*="/assessment_view/"]')]
    .filter((a) => /view/i.test(a.textContent))
    .map((a) => client.abs(a.getAttribute('href')));
}

// Review page of one attempt: every question with your answer. Screen-reader text
// ("Selected:", "This answer is correct.") is kept because it marks your choices.
export function parseLegacyQuizReview(doc, client) {
  const questions = [...doc.querySelectorAll('.question-view')].map((qv, i) => {
    const box = qv.parentElement;
    const number = cleanText(box?.querySelector('.question-number')) || `Question ${i + 1}`;
    const score = cleanText(box?.querySelector('.score-grade-score'));
    return { number, score, md: htmlToMd(qv, client.origin, { keepHidden: true }) };
  });
  // "Submission #:1 Started:… Completed:… Time taken:… Total score:20/20" (skip the name/ID before it)
  const t = (doc.querySelector('#main-inner') || doc.body).textContent.replace(/\s+/g, ' ');
  const m = t.match(/Submission #:?\s*\d+.*?Total score:\s*[\d.]+\s*\/\s*[\d.]+/);
  return { summary: m ? m[0] : '', questions };
}
