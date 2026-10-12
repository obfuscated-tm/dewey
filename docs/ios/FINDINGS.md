# iOS app: verified findings

What was tested on the real FUHSD Schoology, and what it means for the app. Everything here was checked live (2026-10-07 unless noted). Treat it as fact until a test says otherwise; when you find something that contradicts it, fix this file in the same change.

Start at [../IOS-APP.md](../IOS-APP.md) for the build plan.

## Constraints

| Constraint | Consequence |
|---|---|
| FUHSD (`fuhsd.schoology.com`) does **not** give students `/api` keys | No official REST API. The app uses the real web session (cookies) and reads pages, exactly as the extension does in Chrome. |
| School login is **Google SSO** | Expected Google to block embedded web views (`disallowed_useragent`). It does **not**: see below. |
| Only a **free Apple Personal Team** (no paid Developer Program) | Builds expire after 7 days (re-run from Xcode); max 3 sideloaded apps per device (Owen already uses 1); max 10 new bundle IDs per week, so keep **one** bundle ID; no TestFlight / App Store / APNs push; App Groups **do** sign on the free team (checked 2026-10-10: `group.com.owenleung.classwork` is in both the app's and the widget extension's signed entitlements), so widgets share data through it. The widget extension adds a second bundle ID, `com.owenleung.classwork.widgets`; never change it either. The simulator has no limits. Local notifications, Keychain, `WKWebView` and `BGAppRefreshTask` all work. |
| Tooling on this Mac | Xcode 16+, XcodeGen 2.46.0 (`brew install xcodegen`), Node 25. esbuild 0.28 via `ios/package.json`. Xcode 27 with iOS 26.2 / 27.0 simulators. Build outside ~/Documents (default DerivedData): codesign rejects the extended attributes files get there. |

## Login (tested with a throwaway app)

One `WKWebView` with the persistent `WKWebsiteDataStore.default()` loaded `https://fuhsd.schoology.com/`, which redirected to Google ("Sign in to continue to Schoology Integration").

- **Google sign-in completed in a plain `WKWebView` with the default user agent**, with cookies fully cleared first. No `disallowed_useragent` block. It also works with a Mobile Safari user agent.
- After login, `fetch('/home', {credentials: 'include'})` run inside the page returned the signed-in home page. Same-origin fetches from a web view on the Schoology origin carry the session.
- The session lives in `WKWebsiteDataStore.default()`, **but Schoology's login cookies are session-only** (no expiry; checked 2026-10-10: after a quit and relaunch the app's `Library/Cookies` was empty and the engine got the login page). WebKit drops session cookies when the process ends. The app gives the `*.schoology.com` session cookies a 30-day expiry after sign-in, on each launch and on going to the background (`WebConfig.keepSession`), so they are written to the app's own cookie file. Schoology still decides when the session really ends: in normal use (browser or app) it signs you out about once a day (Owen, 2026-10-11), and the longer cookie expiry doesn't change that. So the app sends one "Signed out of Schoology" notification (Settings switch), the widgets show "Open Classwork to sign in", and background refreshes stop until the next sign-in.
- Google SSO may call `window.open`: handle `WKUIDelegate.webView(_:createWebViewWith:for:windowFeatures:)` by loading the request in the same view and returning nil.

Login design (decided):
1. Plain `WKWebView`, default UA.
2. Automatic fallback: if a Google page shows `disallowed_useragent`, "doesn't comply with Google's policies" or "This browser or app may not be secure", switch to a Safari UA, save that choice, and reload. Also a "Login compatibility mode" toggle in Settings.
   ```swift
   // The default WKWebView UA lacks the Version/… Safari/… tokens Google keys on.
   webView.customUserAgent = defaultUA + " Version/26.0 Mobile/15E148 Safari/604.1"
   ```
3. Last resort if Google ever blocks both: a "Connect iPhone" QR handoff from the Chrome extension (it reads the Schoology session cookie, needs the `cookies` permission, shows it as a QR code; the app puts it in its cookie store). Not built; only if needed. Show it on screen only, never upload it.

Ruled out: `ASWebAuthenticationSession` / `SFSafariViewController` (their cookies are walled off from the app), the official API (no keys), a Safari Web Extension handing the session to the app (App Groups turned out to work on the free team, but the login cookies are session-only and the in-app login works, so it is not needed).

## Schoology's pages on a phone

Schoology has no phone layout: no viewport meta tag, a fixed 1200 px page (`#sidebar-left` 180 px floated left, `#main-content-wrapper` with `margin-left: 180px`, `#center-wrapper`/`#center-inner` 978 px, `#right-column` on assignment pages holding "Submit Assignment"). WebKit shows it at 980 px, so the text is small. The app tried a phone fit (a device-width viewport plus CSS that un-floats those columns) and dropped it on Owen's call: the app's web views always show Schoology's desktop layout, like the extension does, and the student pinches to zoom. Opening an `/assignment/{id}` link for a quiz redirects to `/course/{c}/assessments/{id}` (the quiz landing page): don't use assignment links from the home page for testing.

## How each class turns work in

| Class | How work is turned in | What the app shows |
|---|---|---|
| AP Physics 1 | "Scan PDF" homework through the Schoology **dropbox** (`submission.state: submitted`), but not always: "HW 10/9 APCR FRQ" (2026-10) has no dropbox ("Do work on paper and bring to class") | Submit ↗ and real Submitted status when there is a dropbox; otherwise Mark as turned in |
| AP CS A | **No dropboxes.** Labs go to apcs.tinocs.com (linked from the instructions) | "Turn in on lab site ↗" + Mark as turned in |
| Pre-Calc H | Has dropboxes but **grades paper** (HW #13 graded 5/5 with the dropbox `not_submitted`) | Grey "Not used by this class" card + Mark as turned in |
| World History | **Google Assignments** (Quick Writes, Haitian Revolution Document Packet) and **Schoology rubrics** | "Open in Schoology ↗" + Mark as turned in; rubric criteria on Graded work |
| World Lit, French | Mostly paper / no dropbox. French quizzes are `/course/{id}/assessments/{id}` | Mark as turned in; quizzes only open on a tap |

Rules that follow:
- **"Not submitted" does not mean missing.** Schoology's overdue list holds only unsubmitted dropbox work and quizzes. Paper work drops off that list once past due, so the app keeps its own list until the student ticks it.
- Learn "paper class" per course: graded items whose dropbox is `not_submitted` → that class grades paper.
- **Submit ↗** only when the item has a dropbox. If the instructions link an outside submit page (AP CS), the button opens that page. Otherwise only **Mark as turned in**.
- Bad due dates exist (French "Interro: PC ou imparfait" due in 2025, a teacher typo). Overdue items older than 60 days go under "Older".

## Google Assignments (spike S4, done)

- The served page has `lti-app-grading` on `<body>` and `<iframe id="external-tool-iframe" src="/external_tool/{id}/launch/iframe">`, with a `.s-lti-gradable-launch-wrapper`. The final URL is `/assignment/{id}/info`. Some have instructions text (Haitian packet), some don't (Quick Write).
- `parseAssignment` flags these as `isExternalTool`; the MCP returns `type: 'external_tool'`. Verified on the live pages: Quick Write and Haitian packet → true; Annotations, Physics HW 10/2, AP CS Lab 8.2, Pre-Calc HW #18 → false.
- **Schoology never says whether you turned one in.** That lives inside Google's cross-site frame (it even drew at height 0 in desktop Chrome). The app shows "Open in Schoology ↗" + manual mark and never infers "submitted".
- In the app's sheet (2026-10-11, Haitian Revolution Document Packet) the Google frame shows Google's "Allow Google access to your necessary cookies". Tapping **Allow cookies** worked once (Owen). Later opens showed "Can't access your Google Account … allowing cookie access to proceed" with no prompt, so WebKit doesn't keep the storage-access grant.
- Fixes in the app (work on Owen's phone, 2026-10-11): (1) `NSCrossWebsiteTrackingUsageDescription` in Info.plist, which adds an **Allow Cross-Website Tracking** switch under iOS Settings → Classwork. Turning it on lifts WebKit's third-party cookie block for the app's web views. (2) An **Open Google full screen** button in the sheet for Google Assignments. It loads the page's biggest frame as the page itself, so Google is first-party and uses its own cookies. Checked in the simulator 2026-10-11: it loads `assignments.google.com` full screen with Owen's file, Add files / Create and Google's Submit (nothing pressed).

## Grades

- **Comments** are real and sometimes long (a World Lit paragraph; "added 0.5 to factoring problem"; "score (104.45) capped at 100"). `parseGrades` returns them in `comment`.
- A grade cell can hold an **exception word after a dash**: `—Collected`. "Missing" probably looks the same; parse the word.
- Scores to handle: extra credit over 100% (`25 / 24`), `0 / 0` items (never divide by 0), raw and curved copies of one test. Skip the empty "(no grading period)" block.
- **Scales differ by course.** Physics tops out at "A (100%)", others show A+. World History shows "A- (92%)", so its A line is above 92. Use 93 (`A_CUTOFF` in `overlays/grademath.js`) as approximate, narrow it with letter/% pairs seen in that course, let the user set it.
- Dropbox comments include system lines ("Revision 1 submitted"). Filter them out.

## Rubrics (spike S2, done)

- Schoology rubrics are used in World History: the Enlightenment In-Class Essay and "The Enlightenment (Robinson) Annotations" (4.6 / 5).
- In the **served** gradebook HTML the grade cell holds an empty placeholder `<div tpl-id="s_grades_rubric_grading_launch_btn_N" class="… s-grades-rubric-grading-launch-btn">`. Its ids are in the page's Drupal settings: `theme_js[tplId].args = ["rubricId", "enrollmentId", itemId, …]`. Once Schoology's JS renders it, a `<span rubric-id enrollment-id item-id total-rubric-points>` carries the same ids.
- The rubric is two JSON GETs (what the popup loads):
  - `/iapi/grades/rubric/{rubricId}` → `body.rows[{ id, title, max_points, columns[{ pts, description }] }]`
  - `/iapi/grades/rubric_grade_info/{rubricId}/{itemId}/{enrollmentId}` → `body.gradeInfo{ [rowId]: { grade, comment } }`, `overrideGrade` (`gradeInfo` is `[]` when nothing is graded)
- `parseGrades` returns `rubric: { rubricId, enrollmentId }` on those rows; `reader/parse/rubric.js` turns the two responses into criteria (tests in `test/rubric.test.js`). Verified live: Enlightenment Diagnostic Rubric 10/10 (Claim 3/3, Evidence Selection 3/3, Analysis 4/4, every level matched); Annotation Skills Check 4.6/5.
- Many "rubrics" are just attached Google Docs or PDFs (French "UTILISEZ la RUBRIC", "French Revolution Essay AS + Rubric"). List those from the assignment's attachments.

## Uploading (spike S1, done in the simulator 2026-10-11)

Checked on Pre-Calc HW #20's real dialog in the app's Schoology sheet, without attaching a file or pressing Submit: **Submit Assignment** opens Schoology's Upload popup (Upload / Create / Resources tabs, a file field, a comment box, **Submit** and **Cancel** side by side). Tapping the file icon brings up the iOS menu **Photo Library / Take Photo or Video / Choose Files** with no extra code. `NSCameraUsageDescription` is set. Not checked: the camera itself (the simulator has none) and an actual attach + Submit, which only Owen does, on real homework.

Testing rule: never press Submit, Resubmit or Unsubmit, never pick a file in the dialog (Schoology may upload on pick), and close with the sheet's **Done**, not the dialog's Cancel (it sits next to Submit).

## Google Docs (spike S3, done on the phone 2026-10-11: works)

Plan: `UIApplication.shared.open(url, options: [.universalLinksOnly: true])` so the Docs app takes a docs.google.com link if installed; if it returns false, open the in-app web view, which is already signed in to Google from the school login. Confirm the Docs app opens it with the school account.

## Other decisions

- **Notifications:** local only (`UNUserNotificationCenter`), scheduled from fetched due dates; refreshed with `BGAppRefreshTask` (best effort). No push on a free account.
- **Archiving:** if ported at all, save to the app's Documents folder so it shows in the Files app. The full-course archive stays a desktop feature.
- **Naming:** the app can't use "Schoology" or its logo as its name or icon.
- **Project file without Xcode's UI:** use XcodeGen. Fallback that worked for the login test: a hand-written `project.pbxproj` with `objectVersion = 77` and a `PBXFileSystemSynchronizedRootGroup` (Xcode 16+), which compiles every Swift file in the folder with no per-file entries; `xcodebuild -list` autogenerates the scheme.

## Freshness (decided)

- A full refresh is about 15 requests: 1 course list, 2 home lists, 6 gradebooks, 6 feeds, plus 1 per unsubmitted dropbox item, plus 2 per rubric opened.
- To Do: its 3 requests on open and on pull to refresh. A class's gradebook: when opened, at most every 30 min otherwise. A dropbox: only for its own row. The feed: only on Updates.
- New-grade alerts compare against the last saved gradebook; iOS background refresh is best effort.
