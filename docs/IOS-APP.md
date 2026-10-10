# iOS app: start here

A native SwiftUI app with Schoology built in, giving the extension's features a phone-first UI: To Do grouped by class with Done / Turn in, grades with comments and rubrics first and what-if second, materials that open natively, and full Schoology as a fallback tab.

**Status (2026-10-10):** design and research done, **no app code yet**. Next is milestone 1. Keep the "Progress" table at the bottom current.

## Read these first

| File | What it holds | When |
|---|---|---|
| this file | rules, repo layout, milestones in order with "done when" | always |
| [ios/FINDINGS.md](ios/FINDINGS.md) | verified facts: login, free-account limits, how each class turns in, Google Assignments, grades, rubrics | before touching login, submit status, or grades |
| [ios/ENGINE.md](ios/ENGINE.md) | the JS ↔ Swift contract: which extension modules run in the app, engine ops, JSON shapes, fetch rules, neo-plan API | before milestone 1 and any data work |
| [ios/SCREENS.md](ios/SCREENS.md) | every screen: what it shows, data, taps; colours and fonts | before building any screen |
| [ios/design/](ios/design/) | the mockups, one HTML file per screen | to see what a screen looks like |

## Rules for every session

1. **Read-only on Schoology.** The app and any testing you do only GET. Never open quiz-taking pages (`/assessment`, take/start/resume, `/course/{c}/assessments/{id}` unless the student taps it). Never press Submit on a real assignment. Never enter or store Owen's passwords; he signs in himself.
2. **Never copy a parser into Swift.** Schoology parsing lives in `schoology_archiver_extension/reader/parse/` and runs in the app through the engine. A selector fix goes there, with a test, and fixes the extension too.
3. **Keep the extension working.** After any change under `schoology_archiver_extension/`, run `node --test test/*.test.js` from that folder (all must pass; `war.test.js` checks the manifest lists every module an overlay imports). Note: `node --test test/` does not work.
4. **Native screens read; changing Schoology happens in the Schoology sheet** (SCREENS.md, "The rule"). Never show "Submitted" unless Schoology's dropbox says so.
5. **One bundle ID forever.** Pick it once in `ios/project.yml` (M2) and never change it: the free account allows 10 new IDs a week.
6. Learned something new about Schoology? Add it to FINDINGS.md in the same change. Finished a milestone? Update "Progress" below.
7. Commit only when Owen asks.

## Repo layout (target)

```
schoology_archiver_extension/      the Chrome extension (exists). Source of all parsing + grade math.
  engine/engine.js                 NEW (M1): the app engine entry; imports the pure modules
ios/                               NEW (M2)
  project.yml                      XcodeGen spec (committed). Run `xcodegen generate` in ios/.
  SchoolApp.xcodeproj              generated; gitignored, never hand-edited
  scripts/build-engine.sh          bundles engine.js → App/Resources/engine.js
  App/
    SchoolApp.swift                @main, tabs
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
cd ios && xcodegen generate && xcodebuild -scheme SchoolApp -destination 'platform=iOS Simulator,name=iPhone 17' build
```
In the Claude desktop app, use the iOS Simulator tool (`attach` first, then `launch` the built `.app`) to see and tap it. Owen has to sign in to Schoology in the simulator himself the first time.

## Milestones (in order; don't start one before the previous is done)

### M1 · Engine bundle (extension repo work only)
- Move `isQuizTakingUrl` out of `reader/client.js` into a pure module (client.js re-exports it). Move the pure functions of `overlays/todorows.js` into `overlays/todologic.js` (todorows re-exports; add it to `manifest.json` `web_accessible_resources`).
- Write `schoology_archiver_extension/engine/engine.js`: the ops and fetch rules in ENGINE.md. Export them on a global `SchoologyEngine`.
- Add a bundler: `esbuild --bundle --format=iife --global-name=SchoologyEngine engine/engine.js --outfile=…`. esbuild isn't installed yet; ask Owen before installing it (`brew install esbuild` or a devDependency in a new `ios/package.json`).
- Tests: Node tests for the pure parts of engine.js (URL guard, op → path, result shaping) with fake `fetch`.
- **Done when:** all extension tests pass; the bundle builds; pasting the bundle into the console of a logged-in Schoology tab and running `await SchoologyEngine.todo()`, `gradebook({sectionId})`, `assignment({id})`, `rubric(...)` returns the documented shapes (read-only; World History has rubric rows and Google Assignments to check against).

### M2 · Xcode project, login, engine bridge
- `ios/project.yml`, tabs with placeholder screens, the shared web view wrapper with the login flow and automatic Safari-UA fallback (FINDINGS.md), `Engine.swift`.
- A debug screen (Settings → Developer) that runs each engine op and prints the JSON.
- **Done when:** in the simulator, Owen signs in once; after a relaunch he is still signed in; the debug screen shows real `courses()` and `todo()` results; `error: 'login'` after clearing cookies sends the app to Sign in.

### M3 · Spikes (each answers a question; write the answer into FINDINGS.md)
- **S1 upload:** open AP Physics's next real "Scan PDF" Submit dialog in the sheet; check Photos / Camera / Files appear and a file attaches. Owen presses Submit only if it's real homework. Also open a World History Google Assignment in the sheet: does Google's frame load?
- **S3 Docs:** a docs.google.com link with `.universalLinksOnly` opens the Docs app with the school account (device only; needs the Docs app).

### M4 · To Do (1a)
- SwiftData `ItemState` (done, studied, turnedIn, type, updatedAt) keyed by Schoology id; the merge of `todo()` with the saved list; type rule; Older group; counts filter; swipe actions; pull to refresh; all 4c states.
- **Done when:** the list matches Schoology's To Do for every class, paper work stays after Schoology drops it, and the state survives relaunch.

### M5 · Assignment (1b) + Schoology sheet (1c)
- The five-way Submission card, Mark as turned in, the sheet with re-check on close.
- **Done when:** a Physics dropbox item shows its real state; after submitting in the sheet the row turns green without a manual refresh; AP CS shows the lab-site button; a Google Assignment shows "Open in Schoology ↗" and never "Submitted".

### M6 · Grades (2a, 2b, 2c)
- Grade math through `math.*`; rubric criteria; comments; what-if mode.
- **Done when:** each class's % matches the Schoology gradebook (or the extension's grades overlay) and the World History rubric items show their criteria.

### M7 · Classes, Materials, file viewer, Schoology tab (3a–3d)

### M8 · Settings, neo-plan sync, reminders (4a, 4b)
- neo-plan is optional; when connected it is the shared copy (newer edit wins; offline taps queue).

## Progress

| Milestone | State | Notes |
|---|---|---|
| Research + design | done | FINDINGS.md, SCREENS.md, design/ |
| Parser gaps found in research | done | rubric ids + `rubric.js`, Google Assignment detection (verified on live pages 2026-10-10) |
| M1 Engine bundle | not started | |
| M2 Xcode project + login | not started | a throwaway login test app proved the approach; nothing kept |
| M3 Spikes S1, S3 | not started | S2 rubric source and S4 Google Assignment markup are done |
| M4–M8 | not started | |
