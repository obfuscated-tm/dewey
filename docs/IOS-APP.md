# iOS app: findings and plan

Notes for anyone (human or Claude Code session) picking up the native iOS version of this toolkit. Status: **exploration only**, no app code in the repo yet. Findings below were tested on 2026-10-07.

## Goal

A native SwiftUI app that has Schoology built in and gives the extension's features a phone-first UI: To Do grouped by class with Done / Turn in, the Grades graph + what-if + "above an A", Materials status, the assignment status chip, and the neo-plan type switch. Anything not built natively falls back to a full Schoology web view in the same app.

## Constraints (verified)

| Constraint | Consequence |
|---|---|
| FUHSD (`fuhsd.schoology.com`) does **not** give students `/api` keys | No official REST API. The app must use the real web session (cookies) and read pages, exactly as the extension does in Chrome. |
| School login is **Google SSO** | Expected Google to block embedded web views (`disallowed_useragent`). It does **not**, see below. |
| Only a **free Apple Personal Team** (no paid Developer Program) | Builds expire after 7 days (re-run from Xcode); max 3 sideloaded apps per device (user already uses 1); max 10 new bundle IDs per week; no TestFlight / App Store / APNs push; App Groups likely unavailable (so no easy widget data sharing). Simulator has no limits. Local notifications, Keychain, `WKWebView`, `BGAppRefreshTask` all work. |

## Login test result

A throwaway SwiftUI app (one `WKWebView`, persistent `WKWebsiteDataStore.default()`) loaded `https://fuhsd.schoology.com/`, which redirected to Google ("Sign in to continue to Schoology Integration").

- **Google sign-in completed in a plain `WKWebView` with the default user agent**: cookies fully cleared first, then a fresh sign-in. No `disallowed_useragent` block.
- It also works with a Mobile-Safari user agent.
- After login, `fetch('/home', {credentials: 'include'})` run inside the page returned the signed-in home page, so same-origin fetches from the web view carry the session. The full dashboard and To Do column rendered.
- The session lives in `WKWebsiteDataStore.default()`, so it persists across app launches.

### Login design decided

1. Default: plain `WKWebView`, default UA.
2. **Automatic fallback**: if a Google page shows `disallowed_useragent` / "doesn't comply with Google's policies" / "This browser or app may not be secure", switch to a Safari UA, persist that choice, and reload login. Also expose it as a "Login compatibility mode" toggle in Settings.
   ```swift
   // Default WKWebView UA lacks the Version/… Safari/… tokens Google keys on.
   webView.customUserAgent = defaultUA + " Version/26.0 Mobile/15E148 Safari/604.1"
   ```
3. Last resort if Google ever blocks both: a **"Connect iPhone" QR handoff** from the Chrome extension. The extension reads the Schoology session cookie (needs the `cookies` permission) and shows it as a QR code; the app scans it and puts it in its cookie store. Treat the QR code like a password: show it on screen only, never upload it.

Ruled out: `ASWebAuthenticationSession` / `SFSafariViewController` (Google allows them, but their cookies are walled off from the app), the official API (no keys), and a Safari Web Extension passing the session to the app (needs App Groups = paid).

Popups: Google SSO may call `window.open`. Handle `createWebViewWith` by loading the request in the same view.

## Architecture plan

```
SwiftUI screens  ◄─ JSON ─  "engine" (hidden WKWebView, same cookie store)
                            runs the extension's parsers + grade math,
                            fetches schoology.com pages same-origin
"Schoology" tab = visible WKWebView for everything not native yet
```

- Run the parsers in a hidden `WKWebView` (not JavaScriptCore), because they need `DOMParser` / DOM. Return JSON to Swift via `WKScriptMessageHandler` or `callAsyncJavaScript`.
- **Reuse, don't port**: the logic in `schoology_archiver_extension/reader/parse/*.js`, `overlays/grademath.js`, `overlays/asgstate.js` (and likely `matstate.js`, `todorows.js`) is what the app needs. First repo step: split pure logic from page/`chrome.*` code and bundle it into one `engine.js` that both the extension and the app load. Today `reader/parse/materials.js`, `overlays/grademath.js` and `overlays/asgstate.js` reference `document.` or `chrome.`, so check those for coupling first.
- Neo-plan sync: reuse what `panel/np.js` does (it talks to `neo-plan.vercel.app`).
- Notifications: local only (`UNUserNotificationCenter`) scheduled from fetched due dates, refreshed with `BGAppRefreshTask` (best effort).
- Archiving: if ported at all, save to the app's Documents folder so it shows in the Files app. Full-course archive probably stays a desktop feature.
- App Store naming (if ever paid): can't use "Schoology" or its logo as the app's name/branding.

## Building without Xcode's UI

**XcodeGen 2.46.0 is installed** (`brew install xcodegen`, 2026-10-07). For the real app, prefer a `project.yml` + `xcodegen generate` and keep the generated `.xcodeproj` out of git, or committed but never hand-edited. Fallback used for the login test, before XcodeGen was installed: a hand-written `project.pbxproj` with `objectVersion = 77` and a `PBXFileSystemSynchronizedRootGroup` (Xcode 16+) works: every Swift file in the folder is compiled automatically, with no per-file entries. Use `GENERATE_INFOPLIST_FILE = YES` and `INFOPLIST_KEY_UILaunchScreen_Generation = YES`. `xcodebuild -list` autogenerates the scheme. Simulator builds need no signing team; device builds need the user's Personal Team set in Xcode.

## UI plan (2026-10-07)

Mockups: [iOS App Screens canvas](https://claude.ai/artifact/GSX8QmamsY6nkjpdjckhue) (private to Owen). 13 screens in 4 rows, each with a note under it saying what it shows, where its data comes from, and what each tap does. Row 5 lists the open holes. Look: the extension overlay's tokens (accent `#1D5EA8`, Instrument Sans, JetBrains Mono for numbers), 44pt minimum touch targets.

**The rule.** Native screens *read* Schoology and keep the student's *own* state (done, studied, marked turned in, neo-plan type, what-if). Anything that *changes* Schoology (submit, take a quiz, post, Google Assignments / external tools) happens on Schoology's real page in a reusable in-app **Schoology sheet** (WKWebView, same cookies), which watches for "Submitted" and then clears the item in neo-plan, the same as the extension's submit detection.

**Two kinds of "turned in", never mixed up:** *Submit on Schoology ↗* (solid blue, opens the sheet; a filled green check once Schoology shows a submission) vs *Mark as turned in* (quiet, neo-plan only, for paper / elsewhere; an outline green check). `›` opens a native screen, `↗` opens Schoology or a website.

Tabs: **To Do · Grades · Classes · Schoology** (full site). Screens: To Do, Assignment, Schoology sheet, Grades, Course grades (facts first; what-if is a mode behind a button), Graded work (score, teacher comments, rubric, submitted files), Classes, Materials (every item says where it opens), File viewer (PDFKit/QuickLook: Find, Mark up, Save to Files, Share), Schoology tab (with an "Open in app" pill), First launch, Settings, Shared states (first load, offline, signed out, couldn't-read-page fallback, neo-plan not connected, nothing due).

Data already parsed by the extension: gradebook rows include a `comment` column (`reader/parse/grades.js`); the submission page gives grade, comments, revisions and files (`parseDropbox`); `parseAssignment` flags quizzes (`isQuiz`; never open `/assessment`) and external tools (`isExternalTool`).

### Holes: what was found (checked live, 2026-10-07)

**Turning in**
- Only some work has a Schoology dropbox. AP Physics "Scan PDF" homework uses it (`submission.state: submitted`). AP CS has none: labs are turned in on apcs.tinocs.com (linked from the instructions). Most other work is paper (`no_dropbox`).
- A dropbox doesn't mean the class uses it. Pre-Calc HW #13 is graded 5/5 with the dropbox `not_submitted`. Learn per class: graded items with nothing submitted mean "paper class", and show its dropbox card in grey, not red.
- Schoology's overdue list holds unsubmitted dropbox work and quizzes only. Paper work drops off once it's past due, so the app has to keep its own list. Pre-Calc HW #18 shows as overdue, though it's probably on paper.
- So **Submit ↗** shows only when the item has a dropbox. If the instructions link a submit page (AP CS), the button opens that page instead. Otherwise the item offers only **Mark as turned in**.
- **Google Assignments exist in World History** (the Quick Writes, "Submit response in Schoology"; the Haitian Revolution Document Packet). **The parser can't see them**: the page reads as no instructions, no attachments and `no_dropbox`, and `isExternalTool` misses it, because the Google part loads in an embedded app. Until their markup is known (spike S4), treat any assignment with an empty body as "Open in Schoology ↗" with a manual mark. Whether Schoology ever shows them as submitted is unknown.
- Quizzes come as `/course/{id}/assessments/{id}` in the overdue list. Never open them without a tap.
- Bad due dates exist: French "Interro: PC ou imparfait" is due in 2025 (teacher typo). Put overdue items older than 60 days under "Older".
- Upload: an iOS `WKWebView` shows the Photos / Camera / Files menu for `<input type=file>` with no extra code. Add `NSCameraUsageDescription`, or "Take Photo" crashes. It still needs one test on the real Submit dialog (spike S1).

**Grades**
- Comments are real and sometimes long (a World Lit paragraph; "added 0.5 to factoring problem"; "score (104.45) capped at 100").
- A grade cell can hold an exception word after a dash: `—Collected`. "Missing" will probably look the same, so parse that word rather than looking for a separate marker.
- Scores to handle: extra credit over 100% (`25 / 24`), `0 / 0` items, raw and curved copies of the same test. Skip the empty "(no grading period)" block.
- Grading scales differ by course. Physics tops out at "A (100%)" while the others show A+. World History has a category at "A- (92%)", so its A line is above 92. Keep 93 as an approximate line, narrow it with the letter/% pairs seen in that course, and let the user set it.
- Rubrics: **Schoology rubrics are used in World History.** "The Enlightenment (Robinson) Annotations" (4.6 / 5, id 8562611325) is the item `parseGrades` was written around. `parseGrades` deletes the `.s-grades-rubric-grading-launch-btn` button to read the score and never reports it, so the MCP shows no rubric. Fix: return `rubric: true` on the row. The criteria still load from wherever that button points (spike S2). Other "rubrics" are attached Google Docs or PDFs (French "UTILISEZ la RUBRIC", "French Revolution Essay AS + Rubric"): list them from the attachments.
- The dropbox comments include system lines ("Revision 1 submitted"). Filter them out.

**Your own state (decided)**
- Done, Studied, Turned in and type live locally (SwiftData). The app works with no neo-plan. Connecting neo-plan is fully supported: same token and ops as the extension, and once connected it is the shared copy, so the app, the extension and the website all stay in sync. When the extension also edited an item, the newer edit wins. Taps made offline are queued.
- HW vs Test without neo-plan: from the gradebook category (Tests, Quiz, Tests/Quizzes, Summatives) and title words (Exam, Quiz, Interro). neo-plan's type wins when connected.

**Freshness (decided)**
- A full refresh is about 15 requests: 1 course list, 2 home lists, 6 gradebooks, 6 feeds, plus 1 per unsubmitted dropbox item.
- To Do loads its 3 requests on open and on pull to refresh. A class's gradebook loads when you open it, or at most every 30 min. A dropbox is checked only for its row. The feed loads only on Updates.
- New-grade alerts compare against the last saved gradebook; iOS background refresh is best effort.

**Google Docs (decided, to confirm)**
- Open with `UIApplication.open(url, options: [.universalLinksOnly: true])` so the Docs app takes it if installed. If not, use the in-app web view, which is already signed in to Google from the school login (spike S3).

**Still open**
- Spikes S1 (upload), S2 (rubric source: tap the Enlightenment Annotations rubric button and log the request), S3 (Docs app), S4 (Google Assignment markup and submitted state on the Quick Write page). S2 and S4 can also be done read-only in Chrome on the desktop before the app exists.
- Parser breakage: the couldn't-read fallback on every screen, plus the shared, tested `engine.js`.
- Not designed: Updates feed, messages, calendar, dark mode, Dynamic Type, VoiceOver actions for swipes, iPad, app name and icon. Saving a whole class offline is out of v1.

## Next steps

1. Refactor the extension: extract a shared `engine.js` (parse + grade math + state), keep the extension working, add tests.
2. Create the real Xcode project (keep one stable bundle ID to save the free-account ID quota), with login + automatic UA fallback.
3. Spikes: S1 file upload in the Schoology sheet (Physics dropbox), S2 rubric source (log the gradebook rubric button's request), S3 Docs app via universal link, S4 Google Assignment markup.
4. First native screen: To Do (grouped by class, overdue/due-today, Done, Submit ↗ / Mark turned in), backed by the engine.
5. Then Assignment + Schoology sheet, Grades, Materials + file viewer, Settings.
