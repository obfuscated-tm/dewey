// A newer quiz's landing page (/course/X/assessments/Y), trimmed to what
// reader/parse/quiz.js quizInfo reads, with fake secrets that must never leak.
// Shapes verified live 2026-10-14 (docs/ios/FINDINGS.md).
export function quizLandingHtml({ submissions = [], score = null, letter = 'F', overdue = false } = {}) {
  const props = {
    initialization: {
      title: 'Worksheet 2.5 Quiz',
      instructions: '<p>Answer every question.&nbsp;No notes.</p><p>Show work &amp; units.</p>',
      numAttemptsAllowed: 2,
      numAttemptsTaken: submissions.filter((s) => s.completed).length,
      minutesMax: 0,
      availableFrom: 0,
      availableTo: 0,
      score,
      gradebookCustomScaleText: letter,
      gradebookPointsTotal: 10,
      pointsTotal: 11,
      submissions,
    },
    assessmentIsOverdue: overdue,
    launchScenario: 'DELIVERY_SOME_ATTEMPTS_LEFT',
    csrfToken: 'SECRET-CSRF-TOKEN',
    csrfKey: 'SECRET-CSRF-KEY',
    androidVersion: 'eyJhbGciOiJIUzI1NiJ9.SECRET.JWT',
  };
  return `<html><body><h1 class="page-title">Worksheet 2.5 Quiz</h1><script>
window.initSgyUiApp("x", "caAssessmentDeliveryLanding.App", "en", {"k": "v"}, ${JSON.stringify(props)});
</script></body></html>`;
}

export const DONE = {
  id: 1978368064, completed: true, status: '6', can_student_view: true, can_student_resume: false,
  last_modified_date: 'Oct 14, 2026', last_modified_time: '4:16 pm', minutes_elapsed: 1,
};
export const OPEN = {
  id: 1978368999, completed: false, status: '0', can_student_view: false, can_student_resume: true,
  last_modified_date: 'Oct 14, 2026', last_modified_time: '4:30 pm', minutes_elapsed: 3,
};
