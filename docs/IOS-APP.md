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

## Next steps

1. Refactor the extension: extract a shared `engine.js` (parse + grade math + state), keep the extension working, add tests.
2. Create the real Xcode project (keep one stable bundle ID to save the free-account ID quota), with login + automatic UA fallback.
3. First native screen: To Do (grouped by class, overdue/due-today, Done / Turn in), backed by the engine.
4. Then Grades (Swift Charts + what-if), Assignment, Materials, neo-plan switch.
