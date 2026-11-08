# iOS app: screens

What every screen shows, where its data comes from, and what each tap does. Build plan: [IOS-APP.md](IOS-APP.md). Data calls are the engine ops in [ENGINE.md](ENGINE.md).

## Mockups

- Layout direction (2026-10-18, Owen's picks from [design/Layouts.html](design/Layouts.html)): G1 report-card row, C1 shelf, A1 checkout card, T1 drop and ripple, Dew as the default theme. Where the older per-screen mockups differ, Layouts.html and this file win.
- iPhone Duo, open (regular width): [design/Duo.html](design/Duo.html), built 2026-10-24 (`App/Screens/Wide/`), reworked the same day after a first try felt squished on the left and idle on the right. An icon rail instead of tabs; To Do, Grades and Inbox show the list and something useful side by side. Folded, the app is unchanged.
  - Chosen by `horizontalSizeClass == .regular` in RootView (WideShell), never by "is it open". Rail (72 pt) + list fill the left half, the right pane the rest, so nothing sits on the hinge.
  - Rail: the Dewey mark, the five tabs (icon, badge, label), each class as its report-card short name with a colour dot ("AP BIOL"; tinted when picked, since a colour alone didn't say which class), Settings at the bottom. The mark slides the full sidebar over the list (tab and class names with counts); picking anything slides it away. A class filters To Do ("Only Chemistry H · Show all"); on Grades it opens that class.
  - Right pane with nothing open: To Do → Plan (the same Plan as the phone: order the day, tick work off, + for a task); Grades → the class with the newest grade, open as if tapped (picked once the gradebooks are in; a new grade landing later moves it while it's still Dewey's pick, a tapped class stays); Inbox → quiet (unread counts, Open newest unread, New message ↗) with New in your classes under it, moved off the list. It never opens a message by itself, since opening marks it read.
  - Every list row that would push (`SplitLink`) opens on the right instead and stays tinted; each tab keeps its own open item, and "‹" goes back to Plan / the quiet Inbox. Graded work and the assignment a message links to push inside the right pane. To Do's mode chip is hidden (the list is always List; Plan is beside it).
  - Not built from the mockup: the Schoology sheet still covers the whole screen (not just the right half), and the folded tweaks (F2 one-line To Do header, shorter date-due card). On iOS 27 the folded Duo draws the status bar and tab bar down the right edge, so the bottom tab bar the mockup measured isn't there; check on the phone before building them.
- Static copies in this repo: [design/](design/). One file per screen (`Main.dc.html` = To Do, etc.), 390×844 pt. Open one in a browser to see it; the `{{accent}}` placeholders are `#1D5EA8`. `design/canvas.json` holds the notes that sat under each screen (the same text as below).
- Live canvas: https://claude.ai/artifact/GSX8QmamsY6nkjpdjckhue (private to Owen; read it with the Artifact tool if you have access, else use the copies).
- The mockups are the **look**; this file is the **behaviour**. Where they differ, this file wins. Copy in `[brackets]` in a mockup is placeholder text.

## The rule for the whole app

Native screens **read** Schoology and keep the student's **own** state: done, studied, marked turned in, neo-plan type, what-if.

Anything that **changes** Schoology (submit, take a quiz, post in a discussion, a Google Assignment) happens on Schoology's real page in the **Schoology sheet** (1c). The app never POSTs to Schoology itself.

**Two kinds of "turned in", never mixed up:**

| Mark | Meaning | Who sets it |
|---|---|---|
| Accent ring with a small ↑ (no fill, no ✓) | You marked it done; it still has to be handed in | Student |
| Accent ring with an accent ✓ (outlined, not solid) | Your own task done, or a test studied | Student |
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

### Design rules (2026-11-06, read before adding anything to a screen)

The app felt cluttered because every change added a line, a label, a colour or a setting and nothing was ever taken out. These rules come from Apple's HIG (Color, Typography, Motion) and Nielsen Norman Group (visual hierarchy, minimalist design, progressive disclosure, animation duration). Follow them in every change, AI-made or not.

1. **One in, one out.** A new feature removes or merges something on the same screen.
2. **Red means late, nothing else.** Rules under titles and headings are ink or the divider colour, except a To Do class card's rule, which is red when that class has late work (on purpose, 2026-11-07: red still means late there). Class colours mark classes; the accent marks the one main action and "done". No new colours.
3. **Text styles:** at most about four per screen (title, body, secondary, one emphasis). Nothing smaller than Apple's 11 pt `caption2`, and no fixed sizes: everything follows Dynamic Type.
4. **No spaced capitals.** Labels are sentence case ("Your status", not "YOUR STATUS").
5. **A row is two lines:** the title and one grey line (due, score). A third line only for something the circle can't say (Submitted vs Turned in, a task's notes, Google Assignment).
6. **Explanations live in Help, not on screens.** No captions like "Drag across the graph" or "Only you see this". A screen may keep one short line only when leaving it out would mislead (Google Assignments: Schoology doesn't tell Dewey).
7. **One way per action, two at most.** Done = the circle. Hand in = the row's button (Submit ↗ filled, Turn in outlined, Undo once turned in; on every row that can be handed in) or a swipe. Hold menus are for everything else.
8. **Group with space first;** a card or border only when space isn't enough. No coloured band on one side of a card. **Stamps are the one badge allowed, on purpose (2026-11-07):** at most one per To Do class card (LATE in red or DUE TODAY in ink, the most urgent state in the class, straight, never TMRW), and the big tilted stamp on the Assignment card (due / N days late / submitted / turned in). Nowhere else; no new kinds of stamp.
9. **Settings are not decisions.** Pick the best default; add a switch only when students really disagree (call numbers: on by default since 2026-11-07, Settings → To Do → Call numbers).
10. **Motion is feedback, not decoration.** About 100 ms for presses and ticks (`PressStyle`), 200–300 ms for view changes, `.snappy`, no bounce on everyday controls, never on something seen many times a day (no staggered list rows). Respect Reduce Motion. The stamp slam is feedback (it plays only when the status changes), and the zoom replaces the push, so neither adds motion to a normal visit.
11. **Same number, same format everywhere:** a class grade is two decimals on every screen (89.46%), like Schoology.

The brand's character goes in a few places, on purpose: New York titles and grade letters, the Classes shelf, the To Do card stamps and the Assignment stamp (and its slam), the done ripple, the mascot on empty screens and the tour.

Tabs: **To Do · Grades · Classes · Schoology**. To Do, Grades, Inbox and Classes each have one gear beside the title (`ScreenOptions`). One tap opens Settings on that tab's own page, with the full Settings list one tap back. The To Do tab shows a red badge with the overdue count.

## 1 · To Do and turning in

### 1a To Do (tab 1) — build first · `design/Main.dc.html`
- **Shows:** everything due, grouped by class in Course Dashboard order (`courses()` order). Header: date, "To Do", gear (→ 4b); no done-today ring (2026-11-06: the week strip's dots already count). The week strip is bare type; the selected day sits in a drop. Each class group is a catalog card (restored 2026-11-07: Owen found the calm version "basic and AI", not Dewey): the class's Dewey call number in its colour (a dot when there is none, or with Settings → To Do → Call numbers off; `CallNumber`, a precise number only for a clear subject word, the main class for a clear area, none otherwise; set by hand there or from a long-press on the heading) the class name, "P1 · N open" at the right, a 2 pt rule in the class colour under it, then the work, on pale blue index-card lines (`Theme.catalogLine`). Rows keep their status line ("Not on Schoology anymore · tick when done", "Done · not submitted yet", "Turned in · not on Schoology"…). The date above the title is in small caps; the To Do title is underlined in the accent. **Stamp** (Week view only, 2026-11-07): at most one, at the heading's right end, for the most urgent state in the class (`TodoLogic.cardStamp`): **LATE** (red) when any row there is late, else **DUE TODAY** (ink) when work still owed (not handed in; a test not studied, your own task not done) is due later today. No TMRW, no tilt. The heading rule turns red only when the class has late work. Three count buttons: Overdue (red), Due today (accent), This week; tapping one filters the list. **View** (Settings → To Do, where the gear lands; remembered): **Week** (default: by class under the week strip) or **Urgent first** (no strip; one list in Late · Today · Next 7 days · Later · Done sections, each row with its class above the title).
- **Row:** check mark (see table above), title, type bar (HW / TEST / TASK / CW, `caption2`, scales), due time (red with "N days late" when late, which includes work ticked Done but not yet submitted or marked turned in; tests add the countdown). Done-but-late work stays with the late work (Late filter, Urgent first's Late section, the tab badge) until it's handed in. A third line only for Submitted / Submitted late (Schoology's word), Turned in (the student's), Google Assignment, or a task's notes. **Submit ↗** (dropbox) or **Turn in** (outlined) only when the work is done, late, or due by the end of tomorrow; otherwise the swipe.
- **Data:** `todo()` (upcoming + overdue + events) merged with the app's own saved list (paper work that Schoology dropped from overdue stays until ticked). Per-row `status({id})` only for rows that might have a dropbox. Type: TEST if the gradebook category says Test/Quiz/Exam/Summative or the title says Exam/Quiz/Interro, else HW; neo-plan's type wins when connected. Overdue > 60 days → collapsed "Older" group.
- **Taps:** circle = Done toggle (Studied for a test; on a turned-in row it goes back to done). Submit ↗ only on dropbox work → 1c. Swipe left → "Mark turned in" + the class's real turn-in place ("Turn in on lab site ↗" for AP Bio, "Submit ↗" for dropbox, nothing extra for paper). No swipe right and no status list in the hold menu (2026-11-06: one way per action). Row → 1b. Pull → refresh.
- **List · Plan**: the title is the switch: the current view large, and a tinted chip beside it with the other one ("Plan" with a numbered-list icon, or "To Do"); tap the chip to swap (remembered). List is everything above. The week strip opens up to a month grid (List only): pull down anywhere on the strip or its chevron (or tap the chevron), push up to close; it follows the finger, the chevron turns, then it springs. Sideways on the month steps a month (the days slide in from that side). The day cells are tap gestures, not Buttons, so a pull can start on a day (a dot per open item, black = test); tapping a day lists it and the day after. A round **+** at the bottom right, above the tab bar, adds a task of the student's own (1d), on List and Plan.
- **Status tip** (`design/Tutorial.options.html`, option C): the first time anything is ticked (Done, Studied or Turn in), a tip goes under that row with the next step and **How statuses work** (the full guide, also in Settings → Help). Once ever; stays until its × is tapped.

### 1a′ Plan (To Do › Plan)
- **Shows:** one list in three sections, each with its heading and count: **To do** (numbered 1…N), **Later** and **Done** (finished today, dimmed). Until the student moves it, work goes where its due date puts it: late work, work due today or tomorrow, tests in the next 3 days and own tasks with no date in To do; the rest in Later. Inside a section, work not placed yet slots in by due date (before the first placed item due later), so new work due tomorrow never lands under last week's order. Every row has a dotted grip under its number. Later and Done fold with a tap on their heading (chevron; Done starts folded, remembered); a folded heading says "Folded. Tap to show, or drag work here." A tip above the list until closed or after the first drag. An empty section says "Drag work here." Then **New task**, with a footer explaining the due-date rule.
- **Taps:** drag the grip = reorder at once, no hold (the row swaps with each neighbour as the finger passes its middle; held near the top or bottom of the screen, it keeps moving a row every 0.22 s and the list scrolls with it); hold anywhere else on the row, then drag = the same through the list's own drag (slower start, scrolls at the edges). Either way a drag can cross a heading into another section, folded ones too (it lands at the top); into Done marks it done, out of Done marks it to do. No hold menu on these rows: a menu would take the hold. Circle = Done. Swipe left = Later (in To do) or To do (in Later), full swipe works; swipe right = Done. VoiceOver: Move up / Move down / Move to To do or Later actions. The header's done-today ring is hidden here, so there's only one "done today" count.
- Work finished while Plan is open (a tick, swipe or Submit) stays in its row, ticked and dimmed, until Plan closes (List, another tab): it doesn't drop into Done at the bottom while you're still there (`AppModel.planKeep`).
- **Data:** `planSection` (nil = by due date) and `planRank` on `ItemState` (PlanLogic, PlanTests). Phone only.

### 1d A task of your own (TaskEditor)

- **New (QuickTask):** a short sheet over the keyboard, as tall as what's in it. Type the task with its day, time, class and kind in plain words ("chem lab fri after school", "precalc hw tmrw 8am", "quiz next tue"; TaskParse, TaskParseTests): the date and time words leave the title, the class and kind words stay. Under the field, a **grey line says what ↑ will add** ("chem lab · Fri, Nov 13 · 3:30 pm · Chemistry H"), from the words and the pills together. Under that, **one row of pills**, each as wide as what it says (stacked at the biggest text sizes): **When** (one menu: Today, Tomorrow, Pick a day… (the calendar; picking a day closes it); Before school · 8:00, After school · 3:30 from Settings › To Do › School day, since Dewey can't know the bell schedule; Tonight · 11:59 pm; Pick a time…; No date; Use what I typed), **Class** (or No class) and **Kind** (Task, Homework, Test, Classwork). **Teal = picked on a pill**; what was only typed shows in the grey line, never on a pill, so colour means one thing. A pill wins over the typed words. No day or time = no date; a day alone = by 11:59 pm; a time alone = today, or tomorrow once it's passed. "next tue" = that day in next calendar week.
- **Changing one (TaskEditor):** tap its row: the full form below, with notes and Delete.
- Title, kind (Task / Homework / Test / Classwork), class (or none), due date with an optional time (no time = 11:59 pm), notes. Delete when editing. "Only on this phone. Nothing is added to Schoology." Shows on To Do (a class's card, or **My tasks**), Plan, widgets, reminders, Siri ("Add a task in Dewey") and the Calendar app. Opened from a widget or class page, it shows `MineTaskPage`, never Schoology.

### 1b Assignment · `design/Assignment.dc.html`
Reworked 2026-11-07 (Owen: instructions were sandwiched between cards, and the status was said three times). Research: Google Classroom (instructions are the page, your work at the bottom), Canvas Student (status + one big Submit; their "Try Again" label confused students, so the button must say what happens), Things 3 (title and notes are the page, colour only for meaning).
- **Header: the checkout card** (old character back 2026-11-07, Owen: the calm one-line-after-another card "has no hierarchy, bland / empty card"; kept compact and the stamp's overlap small): a 5 pt **class-colour band** across the top (the stamp sits on it); the **catalog line** in small caps, class colour ("ALGE 2 · P1 · 26-27 T1 · HOMEWORK", stopping short of the stamp's measured width); the title in New York; the type tag and points; a **date-due slip**: DATE DUE / TURN IN column labels over an ink rule, the due date (with the **live countdown** "· in 2 h 14 min" in the last 12 hours of work still owed) beside where it's turned in (Dropbox, lab.site, Off Schoology, On Schoology, Google); then the **status** with its icon, merging Schoology's fact with your mark ("Not submitted on Schoology · you marked it done", "Done · not turned in yet", "Turned in · marked by you"), red only while late, and **why** under it in grey ("Checked just now", "No Schoology dropbox. Hand it in the way your teacher asked, then mark it turned in.", "This class takes it on its own site…", the Google Assignment warning); **Dewey's worried mascot** beside the status, only while it's overdue; then the grade row (score, comment, effect ›) once graded. No "Your status" card: the bottom bar's circle is the one place for your mark.
- **The stamp** (`DueStamp`, unchanged): big, tilted, overlapping the header's top right (inside it, straight, at the accessibility sizes): DUE + date, DUE date + N DAYS LATE (red, until handed in), SCHOOLOGY SUBMITTED or MARKED TURNED IN (green). When what it says changes while the page is open (back from the Schoology sheet, or marked turned in), it **slams** down again: in from 1.8×, settles in ~300 ms, heavy haptic (`SlammingStamp`; never on opening the page; only the haptic with Reduce Motion).
- **Instructions are the page:** an "Instructions" heading, then the text unboxed at body size; links in the accent, underlined (the first link that isn't Schoology, Google or YouTube is what "Turn in on <site> ↗" opens when there's no dropbox, so it must look like a link). Attachments under them as cards with a **preview of the first page** (`AttachmentCard`, QuickLook thumbnails of the first 4 files, downloaded into the viewer's cache) and where each opens. No instructions and no files: "No instructions on Schoology." A quiz adds its score / attempts / times under a "Quiz" heading.
- **Bottom bar:** your **circle** (the same `CheckMark` as To Do, with its done ripple, and its word: To do / Done / Studied / Turned in; tap = to do ↔ done; hold = every status) and the **main action**: Submit on Schoology ↗ (dropbox), Turn in on lab.site ↗, **Mark turned in** (paper, until it is), Take / Resume / Review on Schoology ↗ (quiz), Open in Schoology ↗ (Google Assignment).
- **Type:** the tag under the title (HW ⌃⌄) is a menu: tap it to change the type (titled "In neo-plan as" when neo-plan is connected). Dewey guesses the type from Schoology's words and can be wrong, and the type decides lateness and the status words, so fixing it is one tap where it shows, not in ⋯.
- **⋯ (top right):** Open in Schoology, What if I get __ / N? (ungraded work), Countdown on Lock Screen (the last 12 hours).
- **Opening it** from a To Do row zooms the row into the page (iOS 18 `.zoom`; iOS 17 keeps the push).
- **Data:** `assignment({id})` + `status({id})` (the saved row's last status until the page's own check answers) + gradebook row (category, score, comment).
- **Kinds** (the status line, stamp and main action follow): dropbox the class uses (Submitted / Not submitted on Schoology); no dropbox (handed in outside Schoology; an outside submit page linked in the instructions, like AP Bio's lab site, is the main action ↗); quiz (`type: 'quiz'` or `/assessments/`, opened only on the student's tap); Google Assignment (`type: 'external_tool'`, never shows submitted).

### 1c Schoology sheet (submit) · `design/SubmitSheet.dc.html`
- **One reusable component:** a `WKWebView` in a sheet, same cookie store. Header: Done, "Schoology" + lock + host, Reload. Used for Submit, quizzes, discussions, every "Open in Schoology ↗", and outside pages (lab site, Google Docs fallback).
- **On Done/close:** re-run `status({id})`. If it now says submitted: toast "Submitted on Schoology · Cleared from To Do and neo-plan", mark the row green, call neo-plan `turnIn` if connected.
- **Upload:** `<input type=file>` gets the system Photos / Camera / Files menu for free. Needs `NSCameraUsageDescription`. Spike S1 checks it on the real dialog.
- Handle `window.open` / `target=_blank` by loading in the same sheet.

## 2 · Grades: the facts first, what-if second

### 2a Grades (tab 2) · `design/Grades.dc.html`
- **Report card row** (Settings → Grades → Letters across the top, on by default): every class's letter in New York, in its class colour, with a short name; a dot marks new grades. Below it nothing is boxed.
- **Row per class** (was a card), under a "Classes" heading with a bigger gap above it when New grades shows: letter + % to two decimals, like 2b (the letter only when the row above is hidden), the class-colour stripe (call number beside it only when shown), latest score and its +/- effect (`impact`). No period here (2026-11-06). Tap → 2b.
- **New grades** (top, only when there are some): scores that changed since the last saved gradebook, newest first, with "Mark all seen". Seen = opened, its class opened, or a week old. Class cards show "N new"; the tab shows the count.
- **Recently graded:** newest scores across all classes, ordered by when the score appeared, then due date; unseen ones keep a NEW tag.
- **Data:** `gradebook({sectionId})` per class (cached ≤ 30 min) → `periodsFromRows` → `currentPeriod` → `grade`.
- Not designed yet: a class with no grades, pass/fail classes, other terms. Show the class with "No grades yet".

### 2b Course grades · `design/CourseGrades.dc.html`
- Every item: score, %, date, effect on the grade, Comment / Rubric markers. Categories show weight and "A holds down to" (`categoryFloor`). Row → 2c.
- **What-if** button (top right) switches what-if mode on: ungraded rows get an input; "Plan an upcoming test" (`needFor`) lives there and counts the what-if scores on screen. Off by default. **Show real** hides the what-if scores without clearing them (the summary's pill becomes "Back to what-if · N changes"); a tab switch keeps them, Reset or leaving the class clears them.
- **Goal** chip under the grade (and on the planner): the grade the student aims for, A down to C-, saved per class (`CourseState.goal`; the A line until picked). Shows "N to go"; the chart's dashed line, the planner and the grade sheet's "You need" use it.
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
- **Late** (To Do, the shelf ribbon, the tab badge, Open work): past due, still on Schoology's overdue list, and not submitted or turned in. Marked Done doesn't clear it (2026-11-07: done means still to hand in), except on your own tasks, which have nothing to hand in. A test is late only as an online quiz not marked Studied (in-class quizzes stay on Schoology's overdue list as "not started"). Quizzes are checked on their landing page (`assignment` op → `quiz.state`). Work Schoology dropped after its due date (no dropbox: paper, a lab site) shows its date, not "late".
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
3. **Tour** (`TourView`, full screen, Skip any time): shown once after sign-in, and once to anyone already signed in when it shipped (`tourShown`); again from Settings → Help → Take the tour. A welcome card, then **guided practice** (`PracticeView`), then **Your student ID** (on the real account now that practice is over, so Fill in from Schoology gives the real number; Next skips it), then **Using neo-plan?** (for people who also use the desktop extension: what syncs, Connect neo-plan → the Help page; Done skips it). Demo mode skips the tour unless `-DeweyTour` is passed.
   - **Practice** is a pretend week in the real screens: a second `AppModel(practice: true)` (in memory, engine answered by `Practice`, its own `cache-practice` folder, emptied at start and end). While `Practice.active`, nothing reaches reminders, widgets, neo-plan or the real saved copies, the Schoology sheet is a pretend dropbox (`PracticeDropbox`; the real page and session are never loaded), and the first-tick status tip is left alone. To Do's look (List/Plan, Week/Urgent first, the month, Plan's folds and tip) is shared with the real app, so practice sets List · Week and puts the student's own back when it ends, or at the next launch if the app was quit mid-practice (`Practice.restoreView`); a View the student changes during practice is kept.
   - **Coach** (`Coach`): its own window above the app and its sheets. It dims everything except a hole around one target and swallows taps outside the hole (not with VoiceOver on). Dewey (`DeweyHello`) bobs beside a bubble with the step and Skip. Screens mark targets with `.coachTarget(key)` (practice only); `tab.N` is found in UIKit. A step moves on when the practice model, a target appearing or disappearing, or a screen's `Coach.note` shows it happened. A "free" step (press-and-hold menus, the back button, a page of switches) drops the dim and lets every tap through, with the bubble at the bottom. A bubble button can run an `action` first (ask for a permission, save a choice), with an `other` button ("Not now") beside it. `-DeweyDemo -DeweyTourStep N` starts practice at step N.
   - **Steps:** *To Do:* hello → tap the lab's circle (Done) → Submit ↗ → pretend dropbox Submit → Done (Dewey checks again; the row turns green) → the green seal explained → the paper worksheet: Turn in or swipe left → Mark turned in → the quiz: Studied → recap → pull the week strip down for the month → push it back up. *Plan:* tap Plan → its three sections → drag Ch. 4 problems by the dots to number 1 → swipe Reading Notes left to Later (or drag it below Later) → + → type "chem lab fri after school" → ↑ adds it → back to To Do. *Grades:* Grades tab → Chemistry → the Goal chip (what it changes) → Try what-if scores → type 20 on the Flame Test lab → the real → what-if bar (Show real keeps the scores; ↺ or leaving clears them). *Classes:* Classes tab → hold English 10 → Take off the shelf → it lies flat (hold to put back) → the student ID button. *Settings (set for real, not pretend):* the gear (opens on Classes) → back to the list → Appearance → Notifications → switch them on, then **Allow** (asks iOS, after saying why) or Not now → back → To Do → School day ("Looks right") → Show in the Calendar app: **Turn on** (asks for Calendar access) or Not now → Finish. Practice's shelf and opened-category settings are removed when it ends.
   - **Tour cards** (Help): reference cards: where everything is, marking work, Plan and your own tasks, turning in, grades and what-if, Settings.

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
