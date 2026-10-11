import { cleanText } from '../../util.js';
import { weightFromText } from '../../overlays/grademath.js';
import { rubricArgs } from './rubric.js';

/**
 * The gradebook's rows, in page order. `root` is a document or an element.
 * With `els`, each row also carries `el`, its <tr>, for the grades overlay
 * (a DOM node can't be cloned into a message, so it's opt-in). Rows Schoology
 * Plus adds for its own what-if grades are skipped.
 */
export function parseGrades(root, { els = false } = {}) {
  const rows = [];
  for (const tr of root.querySelectorAll('tr.report-row')) {
    if (tr.matches('.grade-add-indicator, .added-fake-assignment')) continue;
    const level = (tr.className.match(/\b(course|period|category|item)-row\b/) || [])[1] || 'item';
    const th = tr.querySelector('th, .title-column');
    const titleEl = th?.querySelector('.title') || th;
    const title = cleanText(titleEl).replace(/\s*(assignment|assessment|discussion)$/i, '');
    const due = cleanText(th?.querySelector('.due-date'));
    // A rubric-graded item's cell repeats its score in the rubric button,
    // "4.6" then "4.6 / 5" with no space between: read it without the button.
    const gradeCell = tr.querySelector('.grade-column')?.cloneNode(true);
    const rubricBtn = tr.querySelector('.grade-column .s-grades-rubric-grading-launch-btn');
    gradeCell?.querySelectorAll('.s-grades-rubric-grading-launch-btn').forEach((n) => n.remove());
    const grade = cleanText(gradeCell).replace(/\s+/g, ' ');
    const comment = cleanText(tr.querySelector('.comment-column'));
    const row = { level, title: due ? title.replace(due, '').trim() : title, due, grade, comment };
    // A category's weight is a sibling of .title in the title cell:
    // <span class="percentage-contrib">(25%)</span>. An item row's data-id is
    // I-{assignment id}.
    if (level === 'category') row.weight = weightFromText((th?.querySelector('.percentage-contrib') || th)?.textContent);
    if (level === 'item') row.id = ((tr.getAttribute('data-id') || '').match(/^I-(\d+)$/) || [])[1] || null;
    // A rubric-graded item: { rubricId, enrollmentId } for rubric.js's two GETs
    // (null ids when neither the rendered button nor the page settings say).
    if (rubricBtn) row.rubric = rubricIds(rubricBtn, root);
    if (els) row.el = tr;
    rows.push(row);
  }
  return rows;
}

// The rendered button carries the ids as attributes; in fetched HTML it's an
// empty placeholder whose ids sit in the page's Drupal settings under its tpl-id.
const argsCache = new WeakMap();
function rubricIds(btn, root) {
  const span = btn.querySelector('[rubric-id]');
  if (span) return { rubricId: span.getAttribute('rubric-id'), enrollmentId: span.getAttribute('enrollment-id') };
  const doc = root.ownerDocument || root;
  if (!argsCache.has(doc)) {
    argsCache.set(doc, rubricArgs([...doc.querySelectorAll('script')].map((s) => s.textContent).join('\n')));
  }
  const a = argsCache.get(doc).get(btn.getAttribute('tpl-id'));
  return { rubricId: a?.rubricId ?? null, enrollmentId: a?.enrollmentId ?? null };
}
