# iOS app: screens

What every screen shows, where its data comes from, and what each tap does. Build plan: [IOS-APP.md](IOS-APP.md). Data calls are the engine ops in [ENGINE.md](ENGINE.md).

## Mockups

- Layout direction (2026-10-18, Owen's picks from [design/Layouts.html](design/Layouts.html)): G1 report-card row, C1 shelf, A1 checkout card, T1 drop and ripple, Dew as the default theme. Where the older per-screen mockups differ, Layouts.html and this file win.
- Static copies in this repo: [design/](design/). One file per screen (`Main.dc.html` = To Do, etc.), 390×844 pt. Open one in a browser to see it; the `{{accent}}` placeholders are `#1D5EA8`. `design/canvas.json` holds the notes that sat under each screen (the same text as below).
- Live canvas: https://claude.ai/artifact/GSX8QmamsY6nkjpdjckhue (private to Owen; read it with the Artifact tool if you have access, else use the copies).
- The mockups are the **look**; this file is the **behaviour**. Where they differ, this file wins. Copy in `[brackets]` in a mockup is placeholder text.

## The rule for the whole app

Native screens **read** Schoology and keep the student's **own** state: done, studied, marked turned in, neo-plan type, what-if.

Anything that **changes** Schoology (submit, take a quiz, post in a discussion, a Google Assignment) happens on Schoology's real page in the **Schoology sheet** (1c). The app never POSTs to Schoology itself.

**Two kinds of "turned in", never mixed up:**

| Mark | Meaning | Who sets it |
|---|---|---|
| Accent ring with an accent ✓ (outlined, not solid) | You marked it done | Student |
| Solid green seal with ✓ | Schoology shows a submission (dropbox work only) | Schoology, read by the app |
| Solid green seal with ✓ (same look; the status line says "not on Schoology") | You marked it turned in (paper, lab site, elsewhere) | Student (local / neo-plan only) |
| Rounded square | A test: Studied / not studied | Student |
| Dashed outline | Not in neo-plan yet | — |

Arrows: `›` opens a native screen; `↗` opens Schoology or a website (sheet or browser).

## Look

| Token | Value |
|---|---|
| accent | `#1D5EA8` in the mockups; the app uses Dew's `#0F6E56` (dark `#5DCAA5`), not user-changeable |
| accent soft (fills) | `#E7EFF8` |
| background | `#F6F7F9`; cards `#FFFFFF`, border `#E3E6EA`, dividers `#E9ECEF` |
| text / secondary / tertiary | `#1F2429` / `#5E6670` / `#8A929B` |
| overdue / late | `#B8430F` on `#FBEDE5` |
| submitted / turned in | `#23794A` |
| HW tag | `#5E6670` on `#EEF0F3`; TEST tag white on `#1F2429` |
| fonts | SF Pro, seven styles in `Theme` (all Dynamic Type): largeTitle 34 bold · title 22 bold · headline 17 semibold · body 17 · subhead 15 · footnote 13 · caption 12. Plus New York (system serif) for character in two places only: `display` 34 bold for screen titles and the big grade letter, `displaySmall` 22 bold for the letter on a Grades card. Numbers add `.monospacedDigit()`; no code font in the UI. ThemeTests fails on a raw point size. |
| shapes | `Theme.Radius`: 6 for tags and type bars, 14 (continuous) for every card; buttons are capsules, checkboxes circles. Spacing 4 / 8 / 12 / 16 / 24 (`Theme.Space`). Touch targets 44 pt; pill buttons 30 pt tall inside a 44 pt row. |

These are the mockups' values. The app has one palette, **Dew** (brand teal), in `Palette.dew` (`Theme.swift`), with a light and a dark value for every colour. Settings → Appearance picks System (the default), Light or Dark. No other themes or accent choices (2026-10-18: fewer palettes to keep readable). ThemeTests checks contrast in light and dark.

Tabs: **To Do · Grades · Classes · Schoology**. To Do, Grades, Inbox and Classes each have one gear beside the title (`ScreenOptions`). One tap opens Settings on that tab's own page, with the full Settings list one tap back. The To Do tab shows a red badge with the overdue count.

## 1 · To Do and turning in

### 1a To Do (tab 1) — build first · `design/Main.dc.html`
- **Shows:** everything due, grouped by class in Course Dashboard order (`courses()` order). Header: date, "To Do", ripple progress (a ring per item due today, filling as they're done), gear (→ 4b). The week strip is bare type; the selected day sits in a drop. Each class group is a library catalog card: heading with the class's Dewey call number in its colour (`CallNumber`: a precise number only for a clear subject word, the main class for a clear area, none otherwise; set by hand from a long-press on the heading or Settings → To Do → Call numbers (the gear lands on that page)), the class name and period over a red rule, then the work on pale blue ruled lines. Three count buttons: Overdue (red), Due today (accent), This week; tapping one filters the list. **View** (Settings → To Do, where the gear lands; remembered): **Week** (default: by class under the week strip) or **Urgent first** (no strip; one list in Late · Today · Next 7 days · Later · Done sections, each row with its class above the title).
- **Row:** check mark (see table above), title, type tag (HW / TEST / TASK / CW), due time in mono (red with "N d late" when overdue), a status line ("Done · not submitted yet", "Submitted on Schoology · 9:40 pm", "Not studied", "Turned in · not on Schoology"), and on dropbox work a **Submit ↗** pill.
- **Data:** `todo()` (upcoming + overdue + events) merged with the app's own saved list (paper work that Schoology dropped from overdue stays until ticked). Per-row `status({id})` only for rows that might have a dropbox. Type: TEST if the gradebook category says Test/Quiz/Exam/Summative or the title says Exam/Quiz/Interro, else HW; neo-plan's type wins when connected. Overdue > 60 days → collapsed "Older" group.
- **Taps:** circle = Done toggle (square = Studied toggle). Submit ↗ only on dropbox work → 1c. Swipe left → "Mark turned in" + the class's real turn-in place ("Turn in on lab site ↗" for AP Bio, "Submit ↗" for dropbox, nothing extra for paper). Row → 1b. Pull → refresh.
- **List · Plan** (a switch under the title; remembered). List is everything above. **Show month** under the week strip opens it up to a month grid (a dot per open item, black = test); tapping a day lists it and the day after. The **+** beside the gear adds a task of the student's own (1d).
- **Status tip** (`design/Tutorial.options.html`, option C): the first time anything is ticked (Done, Studied or Turn in), a tip goes under that row with the next step and **How statuses work** (the full guide, also in Settings → Help). Once ever; stays until its × is tapped.

### 1a′ Plan (To Do › Plan)
- **Shows:** **Up next**, numbered 1…N: late work, everything due in the next 7 days, the student's own tasks and anything added by hand, in the student's order. Work not placed yet slots in by due date (before the first placed item due later), so new work due tomorrow never lands under last week's order. A tip above the list until closed: hold a row, then drag; swipe left for Not today. Then **Not today** (back tomorrow; each row has Put back) and **Done today** (folded), then **New task** and **Add work due later** (a sheet of the open work outside the 7 days).
- **Taps:** hold and drag = reorder (no hold menu on these rows: a menu would take the hold). Circle = Done. Swipe left = Not today (full swipe works); swipe right = Done. VoiceOver: Move up / Move down / Not today actions. The header's done-today ring is hidden here, so there's only one "done today" count.
- **Data:** `planRank`, `skipDay`, `planAdded` on `ItemState` (PlanLogic, PlanTests). Phone only.

### 1d A task of your own (TaskEditor)
- Title, kind (Task / Homework / Test / Classwork), class (or none), due date with an optional time (no time = 11:59 pm), notes. Delete when editing. "Only on this phone. Nothing is added to Schoology." Shows on To Do (a class's card, or **My tasks**), Plan, widgets, reminders, Siri ("Add a task in Dewey") and the Calendar app. Opened from a widget or class page, it shows `MineTaskPage`, never Schoology.

### 1b Assignment · `design/Assignment.dc.html`
- **Shows:** a catalog line in the class colour (class · period · term · category), the title in New York, then the Submission card as a library date-due card (ruled Date due / Turn in columns, a class-colour band, and a stamp: DUE OCT 18, N DAYS LATE, SUBMITTED, or TURNED IN); instructions unboxed; Instructions (text + attachments, each with where it opens); "In neo-plan as" HW / Test / Task / CW switch; a dashed "What if I get __ / 20?" button. Bottom bar: Done + the main action. Globe button top right = this page in the Schoology sheet.
- **Data:** `assignment({id})` + `status({id})` + gradebook row (category, score, comment).
- **Submission card, exactly one of:**
  - Dropbox the class uses → "Submitted on Schoology" (green) / "Not submitted on Schoology" (red) + "Checked N min ago". Main action: **Submit on Schoology ↗**.
  - No dropbox → "Turned in outside Schoology"; if the instructions link an outside submit page (AP Bio lab site), that link is the main action ↗.
  - Quiz (`type: 'quiz'` or `/assessments/`) → "Take on Schoology ↗", opened only on the student's tap.
  - Google Assignment (`type: 'external_tool'`) → "Open in Schoology ↗"; never shows submitted.
- **Mark as turned in** is always present under the card and only changes the student's list.

### 1c Schoology sheet (submit) · `design/SubmitSheet.dc.html`
- **One reusable component:** a `WKWebView` in a sheet, same cookie store. Header: Done, "Schoology" + lock + host, Reload. Used for Submit, quizzes, discussions, every "Open in Schoology ↗", and outside pages (lab site, Google Docs fallback).
- **On Done/close:** re-run `status({id})`. If it now says submitted: toast "Submitted on Schoology · Cleared from To Do and neo-plan", mark the row green, call neo-plan `turnIn` if connected.
- **Upload:** `<input type=file>` gets the system Photos / Camera / Files menu for free. Needs `NSCameraUsageDescription`. Spike S1 checks it on the real dialog.
- Handle `window.open` / `target=_blank` by loading in the same sheet.

## 2 · Grades: the facts first, what-if second

### 2a Grades (tab 2) · `design/Grades.dc.html`
- **Report card row** (Settings → Grades → Letters across the top, on by default): every class's letter in New York, in its class colour, with a short name; a dot marks new grades. Below it nothing is boxed.
- **Row per class** (was a card), under a "Classes" heading with a bigger gap above it when New grades shows: letter + % (the letter only when the row above is hidden), small history graph (`history`), distance to the A line, latest score and its +/- effect (`impact`). Tap → 2b.
- **New grades** (top, only when there are some): scores that changed since the last saved gradebook, newest first, with "Mark all seen". Seen = opened, its class opened, or a week old. Class cards show "N new"; the tab shows the count.
- **Recently graded:** newest scores across all classes, ordered by when the score appeared, then due date; unseen ones keep a NEW tag.
- **Data:** `gradebook({sectionId})` per class (cached ≤ 30 min) → `periodsFromRows` → `currentPeriod` → `grade`.
- Not designed yet: a class with no grades, pass/fail classes, other terms. Show the class with "No grades yet".

### 2b Course grades · `design/CourseGrades.dc.html`
- Every item: score, %, date, effect on the grade, Comment / Rubric markers. Categories show weight and "A holds down to" (`categoryFloor`). Row → 2c.
- **What-if** button (top right) switches what-if mode on: ungraded rows get an input; "Plan an upcoming test" (`needFor`) lives there. Off by default; leaving the screen clears it.
- Handle `25 / 24` (show > 100% as is), `0 / 0`, `—Collected` (show the word), the empty "(no grading period)" block (skip).

### 2c Graded work · `design/GradeDetail.dc.html`
- Score, teacher comment (can be a long paragraph), **rubric**, submitted files, effect on the course grade. What-if is a small link.
- **Rubric:** if the row has `rubric`, call `rubric({ rubricId, enrollmentId, itemId: row.id })` and show each criterion: points `earned / max`, level text, comment; total at the top (`earned` is the teacher's override when set). Attached rubric PDFs / Docs come from the assignment's attachments.
- **Data:** gradebook row + `dropbox({url})` (grade, comments without system lines, files).

## 3 · Classes, materials, files, full Schoology

### 3a Classes (tab 3) · `design/Classes.dc.html`
- A shelf of book spines in Course Dashboard order, each in its class colour (`Theme.classColors`, white text ≥ 4.5:1), name up the spine, a white call-number label with the period and letter. A bookmark ribbon (notched end, words running up it: LATE / TODAY / TMRW) hangs from the top right of a book with work in that state; a book lying flat shows the same word as a small tag. Long-press (or Settings → Classes, where the gear lands) takes a class off the shelf; it then lies flat under it. At the accessibility text sizes every class lies flat.
- **Classes and groups** (Settings → Classes, and Settings → Inbox): every Schoology course as Class or Group. Dewey guesses from the name (`Course.looksLikeClass`: a class ends in "- 12345"); a choice is kept only when it differs from the guess (`CourseKinds`, "Changed from …"). Classes get a book and a place in Grades, and their messages count as from teachers; groups (hubs, counseling, advisory) feed Inbox → School updates (first feed page of each group).
- Search: every class's materials by name, from a cached index (build it like the MCP's `find_material`: walk each Materials tree, cache, refresh in the background).
- Updates tile → feed (screen not designed; v1 can open the course feed in the Schoology sheet). Recently opened = local history.

### 3b Materials · `design/Materials.dc.html`
- Folders with open-work status and score (`rowState`, `folderTotals`). At the class's root, "Open work" first: the class's late work and everything still due, soonest first, each opening its Assignment (it's what the book's LATE / TODAY / TMRW ribbon is about). Then the Materials / Grades / Updates switch for the class.
- **Late** (To Do, the shelf ribbon, the tab badge, Open work): past due, still on Schoology's overdue list, and not submitted, turned in or marked Done. A test is late only as an online quiz not marked Studied (in-class quizzes stay on Schoology's overdue list as "not started"). Quizzes are checked on their landing page (`assignment` op → `quiz.state`). Work Schoology dropped after its due date (no dropbox: paper, a lab site) shows its date, not "late".
- **Every item says where it opens:**

| Item | Opens |
|---|---|
| Assignment | 1b › |
| PDF, Word, PowerPoint, image | 3c › |
| Google Doc / Slides / Sheet | Google's app if installed (universal link), else in-app browser ↗ |
| Other link | in-app browser ↗ |
| Quiz, discussion | 1c ↗ (quiz only on tap) |

### 3c File viewer · `design/PDFViewer.dc.html`
- Download the file with the Schoology session (through the engine web view's cookies), then show it natively: PDFKit for PDFs (Find, Mark up), QuickLook for Word / PowerPoint / images. Save to Files, Share. Cache opened files for offline.
- Google Docs never open here.

### 3d Schoology (tab 4) · `design/SchoologyTab.dc.html`
- The full site in a `WKWebView` for everything not native (messages, calendar, groups, discussions, profile). Bottom bar: back, forward, reload, share.
- When the current URL is something the app has a screen for (`/assignment/{id}`, `/course/{id}/student_grades`, `/course/{id}/materials`), show an "Open in app" pill.

## 4 · Setup and shared states

### 4a First launch · `design/SignIn.dc.html`
1. Sign in (required): fuhsd.schoology.com in a web view → Google SSO, with the automatic Safari-UA fallback ([FINDINGS.md](FINDINGS.md#login-tested-with-a-throwaway-app)).
2. Reminders (optional): notification permission.
3. **Tour** (`TourView`, full screen, Skip any time): shown once after sign-in, and once to anyone already signed in when it shipped (`tourShown`); again from Settings → Help → Take the tour. A welcome card, then **guided practice** (`PracticeView`), then **Using neo-plan?** (for people who also use the desktop extension: what syncs, Connect neo-plan → the Help page; Done skips it). Demo mode skips the tour unless `-DeweyTour` is passed.
   - **Practice** is a pretend week in the real screens: a second `AppModel(practice: true)` (in memory, engine answered by `Practice`, its own `cache-practice` folder, emptied at start and end). While `Practice.active`, nothing reaches reminders, widgets, neo-plan or the real saved copies, the Schoology sheet is a pretend dropbox (`PracticeDropbox`; the real page and session are never loaded), and the first-tick status tip is left alone.
   - **Coach** (`Coach`): its own window above the app and its sheets. It dims everything except a hole around one target and swallows taps outside the hole (not with VoiceOver on). Dewey (`DeweyHello`) bobs beside a bubble with the step and Skip. Screens mark targets with `.coachTarget(key)` (practice only); `tab.N` is found in UIKit. A step moves on when the practice model, a target appearing or disappearing, or a screen's `Coach.note` shows it happened. A "free" step (press-and-hold menus, the back button) drops the dim and lets every tap through, with the bubble at the bottom. `-DeweyDemo -DeweyTourStep N` starts practice at step N.
   - **Steps:** *To Do:* hello → tap the lab's circle (Done) → Submit ↗ → pretend dropbox Submit → Done (Dewey checks again; the row turns green) → the green seal explained → the paper worksheet: Turn in or swipe left → Mark turned in → the quiz: Studied → recap. *Grades:* Grades tab → Chemistry → Try what-if scores → type 20 on the Flame Test lab → the real → what-if bar, preview only. *Classes:* Classes tab → hold English 10 → Take off the shelf → it lies flat (hold to put back). *Settings:* the gear (opens on Classes) → back to the list → Appearance → Notifications → Finish. Practice's shelf and opened-category settings are removed when it ends.
   - **Tour cards** (Help): reference cards: where everything is, marking work, turning in, grades and what-if, Settings.

Apart from the tour's last card, neo-plan stays out of sight until connected; it's explained in Settings → Help → neo-plan.

### 4b Settings · `design/Settings.dc.html`
A short list of rows, each with a class-colour icon square and its current value on the right, each opening a page (`SettingsPage`):
- **Schoology** (subtitle: the host): signed in to, Sign out (clears the data store), Login compatibility mode.
- **Notifications** (On / Off): Due work (2 hours before if not submitted, night before a test, homework the evening before + time, morning summary + time) and Alerts (new grades, messages, new materials, signed out; best effort).
- **Appearance** (System / Light / Dark): a picker right in the list, not a page.
- **Student ID** (Added / Not added): the number (digits only; a note when it isn't 7 long), Fill in from Schoology, Scan my ID card (camera, Code 39), Show my barcode, Remove. The card (`StudentIDCard`): black-on-white Code 39 and the number, full brightness and no auto-lock while open. Also from the ID button on Classes, Siri ("Show my student ID in Dewey"), the Student ID widget and the Control Center button (`classwork://id`). Kept in the Keychain; never in the widgets' shared data.
- **To Do** (view): View, Call numbers, the 2-hour and test-night reminders (the same switches as Notifications), and **Show in the Calendar app** (a Dewey calendar; turning it off removes that calendar).
- **Grades** (Letters / List): Letters across the top, and **Classes in Grades** (a switch per class; a hidden class also leaves New, Recently graded, the badge and grade alerts; pressing and holding a class in Grades hides it too).
- **Classes** (N on shelf): which classes stand on the shelf.
- **Inbox**: Mark messages read when opened.
- **Help** ("neo-plan on" when connected): **Take the tour** and **Tour cards** (4a), **How statuses work** (the full status guide) and **neo-plan** ("Not connected" / "Connected"), a page that explains what it is, who it's for and what syncs, then connects (token field, Keychain) or shows the masked token, Sync now and Disconnect (`design/Tutorial.options.html`, layout 1).
- **About** (days left): version, this build's expiry date (free account: 7 days from install), Developer.

The gear on To Do, Grades, Inbox and Classes opens Settings already on that page. Footers are one line.

### 4c Shared states · `design/States.dc.html` — every screen needs all of these
First load (skeleton rows) · Offline: last saved copy + "as of N min ago" · Signed out by Schoology (engine `error: 'login'`) → Sign in · Couldn't read the page (engine `error: 'parse'`) → "Open this page in Schoology ↗", never a blank screen · neo-plan not connected · Nothing due.

## Not designed (decide before building them)

Inbox tab (messages + notifications, roadmap R2), dark mode and Dynamic Type passes (R5), VoiceOver actions for the swipe buttons, iPad. App name (Dewey) and icon are done. Dropped: a class Updates feed. Saving a whole class offline is out of v1.
