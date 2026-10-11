// Schoology rubrics, read-only. In the gradebook a rubric-graded item's grade
// cell holds an empty placeholder
//   <div tpl-id="s_grades_rubric_grading_launch_btn_1" class="… s-grades-rubric-grading-launch-btn">
// that Schoology's Angular code fills in from the page's Drupal settings:
//   "s_grades_rubric_grading_launch_btn_1":{"template":"s_grades_rubric_grading_launch_btn",
//     "module":"gradingRubric","args":["55379888","3602079887",8538134342,…,10,…]}
// args = rubric id, enrollment id, item id, grade html, …, total points. Once
// rendered, the same ids are attributes on the button's
// <span rubric-id enrollment-id item-id total-rubric-points>.
//
// The rubric itself is two GETs (what the popup loads):
//   /iapi/grades/rubric/{rubricId}
//     → { body: { title, total_points, rows: [{ id, title, description, max_points,
//          columns: [{ pts, description }] }] } }
//   /iapi/grades/rubric_grade_info/{rubricId}/{itemId}/{enrollmentId}
//     → { body: { gradeInfo: { [rowId]: { grade: "3", comment } }, overrideGrade } }

const ARGS_RE = /"(s_grades_rubric_grading_launch_btn_\d+)":\{"template":"s_grades_rubric_grading_launch_btn","module":"gradingRubric","args":\["(\d+)","(\d+)",(\d+)/g;

/** Page text (scripts included) → Map tpl-id → { rubricId, enrollmentId, itemId }. */
export function rubricArgs(text) {
  const out = new Map();
  for (const m of String(text ?? '').matchAll(ARGS_RE)) {
    out.set(m[1], { rubricId: m[2], enrollmentId: m[3], itemId: m[4] });
  }
  return out;
}

/** Paths of the two GETs behind a rubric popup. */
export function rubricPaths({ rubricId, enrollmentId, itemId }) {
  return {
    rubric: `/iapi/grades/rubric/${rubricId}`,
    gradeInfo: `/iapi/grades/rubric_grade_info/${rubricId}/${itemId}/${enrollmentId}`,
  };
}

const num = (v) => (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v));

/**
 * The two responses → { title, total, earned, criteria: [{ title, max, earned,
 * level, comment }] }. `level` is the description of the column whose points
 * match the grade (null when none does, e.g. a grade between columns).
 * `earned` is the override grade when the teacher set one, else the sum.
 */
export function parseRubric(rubricJson, gradeInfoJson) {
  const r = rubricJson?.body ?? rubricJson ?? {};
  const g = gradeInfoJson?.body ?? gradeInfoJson ?? {};
  const info = g.gradeInfo ?? {};
  const criteria = (r.rows ?? []).map((row) => {
    const gi = info[row.id] ?? {};
    const earned = num(gi.grade);
    const col = earned == null ? null : (row.columns ?? []).find((c) => num(c.pts) === earned);
    return {
      title: row.title || '',
      max: num(row.max_points),
      earned,
      level: col ? col.description || null : null,
      comment: gi.comment || '',
    };
  });
  const graded = criteria.filter((c) => c.earned != null);
  const sum = graded.length ? graded.reduce((s, c) => s + c.earned, 0) : null;
  return {
    title: r.title || '',
    total: num(r.total_points),
    earned: num(g.overrideGrade) ?? sum,
    criteria,
  };
}
