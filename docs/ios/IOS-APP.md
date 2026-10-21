# iOS app: start here

A native SwiftUI app with Schoology built in, giving the extension's features a phone-first UI: To Do grouped by class with Done / Turn in, grades with comments and rubrics first and what-if second, materials that open natively, and full Schoology as a fallback tab.

**Status (2026-10-10):** M1 done except the live check; every screen of M2–M8 is written and builds, unit tests pass, and the app launches to Sign in in the simulator. Next: Owen signs in once in the simulator, then the live checks below. Keep the "Progress" table at the bottom current.

## Read these first

| File | What it holds | When |
|---|---|---|
| this file | rules, repo layout, milestones in order with "done when" | always |
| [ios/FINDINGS.md](FINDINGS.md) | verified facts: login, free-account limits, how each class turns in, Google Assignments, grades, rubrics | before touching login, submit status, or grades |
| [ios/ENGINE.md](ENGINE.md) | the JS ↔ Swift contract: which extension modules run in the app, engine ops, JSON shapes, fetch rules, neo-plan API | before milestone 1 and any data work |
| [ios/SCREENS.md](SCREENS.md) | every screen: what it shows, data, taps; colours and fonts | before building any screen |
| [ios/design/](design/) | the mockups, one HTML file per screen | to see what a screen looks like |

## Rules for every session

1. **Read-only on Schoology.** The app and any testing you do only GET. Never open quiz-taking pages (`/assessment`, take/start/resume, `/course/{c}/assessments/{id}` unless the student taps it). Never press Submit on a real assignment. Never enter or store Owen's passwords; he signs in himself.
2. **Never copy a parser into Swift.** Schoology parsing lives in `extension/reader/parse/` and runs in the app through the engine. A selector fix goes there, with a test, and fixes the extension too.
3. **Keep the extension working.** After any change under `extension/`, run `node --test test/*.test.js` from that folder (all must pass; `war.test.js` checks the manifest lists every module an overlay imports). Note: `node --test test/` does not work.
4. **Native screens read; changing Schoology happens in the Schoology sheet** (SCREENS.md, "The rule"). Never show "Submitted" unless Schoology's dropbox says so.
5. **One bundle ID forever.** Pick it once in `ios/project.yml` (M2) and never change it: the free account allows 10 new IDs a week.
6. Learned something new about Schoology? Add it to FINDINGS.md in the same change. Finished a milestone? Update "Progress" below.
7. Commit only when Owen asks.

## Repo layout (target)

```
extension/      the Chrome extension (exists). Source of all parsing + grade math.
  engine/engine.js                 NEW (M1): the app engine entry; imports the pure modules
ios/                               NEW (M2)
  project.yml                      XcodeGen spec (committed). Run `xcodegen generate` in ios/.
  Dewey.xcodeproj                  generated; gitignored, never hand-edited
  scripts/build-engine.sh          bundles engine.js → App/Resources/engine.js
  App/
    DeweyApp.swift                 @main, tabs
    Engine/Engine.swift            hidden WKWebView + callAsyncJavaScript (ENGINE.md)
    Engine/Models.swift            Codable structs for engine results
    Web/SchoologyWebView.swift     shared WKWebView wrapper (login, sheet, tab 4)
    Web/SchoologySheet.swift       1c
    Store/                         SwiftData models: ItemState (done, studied, turnedIn, type), caches
    NeoPlan/NeoPlanClient.swift    URLSession client + Keychain token
    Screens/ToDo/, Assignment/, Grades/, Classes/, Settings/
    Resources/engine.js            GENERATED (gitignored or committed, but never hand-edited)
  Tests/                           XCTest: model decoding from saved engine JSON, state logic
```

Build settings: iOS 17+ (SwiftData), `GENERATE_INFOPLIST_FILE = YES`, `INFOPLIST_KEY_UILaunchScreen_Generation = YES`, `INFOPLIST_KEY_NSCameraUsageDescription` set. Simulator builds need no signing; device builds use Owen's Personal Team (he picks it in Xcode once).

Build and run from the command line:
```bash
cd ios && xcodegen generate && xcodebuild -scheme Dewey -destination 'platform=iOS Simulator,name=iPhone 17' build
```
In the Claude desktop app, use the iOS Simulator tool (`attach` first, then `launch` the built `.app`) to see and tap it. Owen has to sign in to Schoology in the simulator himself the first time.

## Milestones (in order; don't start one before the previous is done)

### M1 · Engine bundle (extension repo work only)
- Move `isQuizTakingUrl` out of `reader/client.js` into a pure module (client.js re-exports it). Move the pure functions of `overlays/todorows.js` into `overlays/todologic.js` (todorows re-exports; add it to `manifest.json` `web_accessible_resources`).
- Write `extension/engine/engine.js`: the ops and fetch rules in ENGINE.md. Export them on a global `SchoologyEngine`.
- Add a bundler: `esbuild --bundle --format=iife --global-name=SchoologyEngine engine/engine.js --outfile=…`. esbuild is a devDependency in `ios/package.json` (`npm install` in ios/); `ios/scripts/build-engine.sh` runs it, and the Xcode pre-build step calls that script.
- Tests: Node tests for the pure parts of engine.js (URL guard, op → path, result shaping) with fake `fetch`.
- **Done when:** all extension tests pass; the bundle builds; pasting the bundle into the console of a logged-in Schoology tab and running `await SchoologyEngine.todo()`, `gradebook({sectionId})`, `assignment({id})`, `rubric(...)` returns the documented shapes (read-only; US History has rubric rows and Google Assignments to check against).

### M2 · Xcode project, login, engine bridge
- `ios/project.yml`, tabs with placeholder screens, the shared web view wrapper with the login flow and automatic Safari-UA fallback (FINDINGS.md), `Engine.swift`.
- A debug screen (Settings → Developer) that runs each engine op and prints the JSON.
- **Done when:** in the simulator, Owen signs in once; after a relaunch he is still signed in; the debug screen shows real `courses()` and `todo()` results; `error: 'login'` after clearing cookies sends the app to Sign in.

### M3 · Spikes (each answers a question; write the answer into FINDINGS.md)
- **S1 upload:** open Chemistry H's next real "Scan PDF" Submit dialog in the sheet; check Photos / Camera / Files appear and a file attaches. Owen presses Submit only if it's real homework. Also open a US History Google Assignment in the sheet: does Google's frame load?
- **S3 Docs:** a docs.google.com link with `.universalLinksOnly` opens the Docs app with the school account (device only; needs the Docs app).

### M4 · To Do (1a)
- SwiftData `ItemState` (done, studied, turnedIn, type, updatedAt) keyed by Schoology id; the merge of `todo()` with the saved list; type rule; Older group; counts filter; swipe actions; pull to refresh; all 4c states.
- **Done when:** the list matches Schoology's To Do for every class, paper work stays after Schoology drops it, and the state survives relaunch.

### M5 · Assignment (1b) + Schoology sheet (1c)
- The five-way Submission card, Mark as turned in, the sheet with re-check on close.
- **Done when:** a Chemistry dropbox item shows its real state; after submitting in the sheet the row turns green without a manual refresh; AP Bio shows the lab-site button; a Google Assignment shows "Open in Schoology ↗" and never "Submitted".

### M6 · Grades (2a, 2b, 2c)
- Grade math through `math.*`; rubric criteria; comments; what-if mode.
- **Done when:** each class's % matches the Schoology gradebook (or the extension's grades overlay) and the US History rubric items show their criteria.

### M7 · Classes, Materials, file viewer, Schoology tab (3a–3d)

### M8 · Settings, neo-plan sync, reminders (4a, 4b)
- neo-plan is optional; when connected it is the shared copy (newer edit wins; offline taps queue).

## Roadmap after M8 (agreed with Owen 2026-10-11; in order)

### R0 · Device checks (Owen, on the phone)
- Sign in; add the Lock Screen and Home Screen widgets; let a background refresh run (S5). Docs links (S3) and Google Assignments: work (2026-10-11).
- Google Assignment: try **Open Google full screen** with iOS Settings → Dewey → Allow Cross-Website Tracking off, then on.
- **Done when:** each result is written in FINDINGS.md.

### R1 · Signed-out handling + clean-ups (built 2026-10-11)
- No paper guessing: Submit ↗ and "Done · not submitted yet" follow the dropbox only (`hasDropbox`). The per-class turn-in setting is gone.
- Schoology signs you out about once a day: one "Signed out of Schoology" notification (Settings switch, on by default), widgets show "Open Dewey to sign in", background refreshes pause until sign-in.
- New-grade alerts have their own switch (no longer tied to evening reminders).
- Google Assignments: "Open Google full screen" in the sheet, plus the Allow Cross-Website Tracking switch.
- New-grade alerts show the class change (`Lab 4: 18 / 20 · 92.1% → 92.6%`); each background refresh also checks 3 gradebooks, in turn.
- **Done when:** on the phone, after Schoology signs you out, one notification arrives and the Lock Screen widget says to sign in; after signing in, both clear.

### R2 · Inbox tab (messages + notifications) (built 2026-10-11)
- Extension first: parsers for `/home/notifications`, the `/messages` list and one message, with Node tests; engine ops for each. Fix the feed parser (cut-off text, date shown as a URL, no post id).
- Tabs: To Do · Grades · Inbox · Classes · Schoology. Sections: New from school (new school messages, until opened), From teachers (unread dot), New in your classes, School (collapsed). Every new message, teacher or school, counts in the tab badge (2026-10-14).
- Each section is a box that folds away (School starts folded; remembered). Its **Show older** reveals 10 more, loading the next page of its own list(s) when needed (no alerts, not cached; pull to refresh goes back to page 1) (2026-10-15).
- Setting "Mark messages read when opened" (on by default; off = show the preview only, never open the message page). Reply / new message happen in the Schoology sheet.
- Notifications for new teacher messages and new materials, each with its own switch.
- **Done when:** the Inbox matches Schoology's messages and notifications pages, and a new teacher message gives one notification after a background refresh.
- Still to check: how Schoology marks an unread row on the messages list (none were unread on 2026-10-11), and the notification on the phone.

### R3 · Smarter due notifications (built 2026-10-11)
- Due in 2 h for dropbox work not yet submitted; an optional morning summary; the night before a test not marked Studied; "Test in N days" on test rows.
- **Done when:** each fires once at the right time in the simulator with a test item, and none fire for finished work.

### R4 · Siri and Shortcuts (built 2026-10-11)
- App Intents in the app target: "What's due tomorrow", "Mark … done", "Open Dewey To Do".
- **Done when:** each phrase works from Siri and the Shortcuts app.

### R5 · Accessibility and dark mode (built 2026-10-11)
- Dynamic Type up to the largest sizes, VoiceOver labels on every control, a dark mode pass on every screen and widget.
- **Done when:** every screen is usable at the largest text size and with VoiceOver, and nothing is unreadable in dark mode.
- Check: `xcodebuild -scheme A11yAudit -collect-test-diagnostics never test` (Apple's audit on each tab, class grades and a class page, light and dark, largest text; needs the simulator signed in). Colour contrast is `ThemeTests` (every text colour on every surface, light and dark).

### R6 · Student ID (built 2026-10-20)
- The ID card's Code 39 barcode, full brightness; the number from Schoology's username (engine `account()`), the camera or typed; Siri, a widget, a Control Center button and the ID button on Classes open it (FINDINGS.md "Student ID").
- **Done when:** the school's scanner (library, attendance) reads it off the phone.

### R7 · Your own tasks (built 2026-10-20)
- The + on To Do, TaskEditor, `ItemState` rows keyed `mine-…`; in To Do, Plan, widgets, reminders, Siri ("Add a task in Dewey"), never Schoology or neo-plan.
- **Done when:** a task added on the phone shows in To Do, Plan and the widget, survives a relaunch, and leaves when done (at the end of that day).

### R8 · Plan (built 2026-10-20)
- To Do › Plan: three sections, To do (numbered 1…N), Later and Done, placed by due date until the student drags work between them (SCREENS.md 1a′).
- **Done when:** after a week of real use, the order holds across refreshes and new work slots in where expected.

### R9 · Calendar (built 2026-10-20, kept small)
- Month grid from the chevron under the week strip (a day + the next); List / Plan by tapping the small title beside the large one; + floating above the tab bar; Settings › To Do › Show in the Calendar app (a Dewey calendar).
- **Done when:** on the phone, the Dewey calendar matches To Do, and work marked done drops off after a refresh. Note: the month only shows what Schoology's To Do lists (about the next two weeks); a fuller month would need a Schoology calendar reader.

### Later / dropped
- Later: Focus filter, GPA, offline classes, distribution (TestFlight needs a paid account), the Android port of R6–R9.
- Dropped: a native class Updates feed (feeds are nearly empty; the Inbox covers it), a "paper" setting.

## Progress

| Milestone | State | Notes |
|---|---|---|
| Research + design | done | FINDINGS.md, SCREENS.md, design/ |
| Parser gaps found in research | done | rubric ids + `rubric.js`, Google Assignment detection (verified on live pages 2026-10-10) |
| M1 Engine bundle | done | engine.js + guard.js + todologic.js, `test/engine.test.js`; extension tests 200/200; bundle 68 KB, no `chrome.` refs. Live check 2026-10-11 on the Developer screen: `courses()` returned the class list; todo/assignment/gradebook/rubric run live behind their screens. |
| M2 Xcode project + login | done | `ios/` builds for the simulator (Xcode 27, iOS 27 sim); bundle ID `com.owenleung.classwork`; launches to Sign in. Checked 2026-10-11: still signed in after a relaunch; an expired session goes to Sign in. Build with default DerivedData: a build folder under ~/Documents fails codesign ("resource fork … detritus"). |
| M3 Spikes S1, S3 | done | S1 (2026-10-11): upload dialog opens with Photos / Camera / Files; Google Assignments work on the phone with the R1 fixes. S3 (2026-10-11): Docs links work on the phone. S2 rubric source and S4 Google Assignment markup are done |
| M4–M8 | built, tested live in the simulator | Checked live 2026-10-10: To Do, Assignment, PDF attachment viewer, Submit sheet (opened, never pressed). Fixed: session cookie lost on relaunch, Submit pill truncating due dates. Earlier:  To Do, Assignment + sheet, Grades (2a–2c, what-if, plan a test), Classes/Materials/file viewer/Schoology tab, Settings (neo-plan, reminders, Developer; per-class turn-in removed in R1). 10 XCTest cases pass (decoding, TodoLogic, routes, widget data). Owen's calls: one-tap status like the extension (circle = Done, pill = Turn in / Submit ↗ / Undo; long-press for all three), grade categories start collapsed (Expand all; remembered per class), Appearance setting (System / Light / Dark), Schoology always in its desktop layout. |
| R6–R9 Student ID, own tasks, Plan, calendar | built 2026-10-20, checked in the simulator (demo) | 11 new XCTest cases (PlanTests, CalendarAndIDTests); extension `account.test.js`. Simulator checks: Plan drag within and across sections (grip drags at once, hold elsewhere; into Done marks it done; drops into folded Later and Done), Later / To do by swipe, New task, month grid, ID card and its barcode decoded by Vision. Not yet on the phone: scanner at school, camera scan, Calendar app, Control Center button, Siri. |
| Widgets + Live Activity | built, checked in the simulator 2026-10-11; needs a look on the phone | `DeweyWidgets` extension: **To Do** (small/medium/large + Lock Screen rectangle; list grouped Late/Today/Tomorrow/This week/Later; on the Home Screen a tap on the circle marks it done, or a test studied, in Dewey only, never Schoology, via `ToggleDoneIntent` and the App Group `widgetMarks` queue the app applies), **Tests and grades** (small: next test countdown + studied; medium: next 3 tests + latest 3 graded items; Lock Screen rectangle and "3d TEST" circle; test time shown only when it isn't 11:59 pm), and a due-time countdown Live Activity with a Dynamic Island, started from the Assignment screen (work due within 12 h). No inline (above-the-clock) or late-count widgets, by Owen's call. Data: `Shared/WidgetData.swift` through the App Group; `App/Widgets/WidgetBridge.swift` writes it after each refresh or mark. Links: `classwork://assignment/{id}`. |
