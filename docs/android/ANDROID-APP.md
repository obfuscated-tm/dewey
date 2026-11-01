# Android app: start here

A native Kotlin + Jetpack Compose port of the iOS app (`ios/`). Same screens, same engine, same rules. The iOS docs stay the source of truth for *what* each screen does ([SCREENS.md](../ios/SCREENS.md)), the engine contract ([ENGINE.md](../ios/ENGINE.md)) and Schoology facts ([FINDINGS.md](../ios/FINDINGS.md)). This file covers what is Android-specific.

**Status (2026-10-15):** every iOS screen and feature is ported and builds (`assembleDebug`). JVM unit tests pass. On a Pixel 8 emulator (API 36) the app launches to Sign in, and the sign-in web view reaches Google's "Sign in with Google" page with no "disallowed user agent" refusal. Nothing past sign-in has been checked yet; see "Not verified yet".

**Caught up with iOS (2026-10-16):** the iOS changes up to `acd01cc` are ported: no sign-out on a single op's `login` (the engine asks `/iapi/course/active` first; a 403 shows "removed or made private" with "Remove from my list"), foldable Inbox boxes with "Show older", faster loading (saved copies on launch, dropbox checks and gradebooks 2 at a time, saved grade summaries per build, no re-adding an unchanged set of reminders), the six themes in Settings → Look, and the accent-ring done vs green-seal turned-in marks. They build and pass unit tests, but haven't been run on the emulator. iOS's Debug-only demo mode (`-DeweyDemo`) is not ported.

**Caught up with design-refresh (2026-10-18):** the iOS changes since Android 0.2 are ported: the Dew palette (one theme, so the six themes are gone) with serif titles, a gear on each tab that opens that tab's Settings page, Settings as pages, the Grades report card, hidden classes in Grades (left out of new-grade counts and alerts), To Do as catalog cards with call numbers and the done ripple, the status tip and status guide, the Assignment checkout card, Classes as a shelf of books, Inbox "School updates", course PDFs resolved through the `material` op, "+0.00%" for a grade that doesn't move, and the guided tour (below). Unit tests pass. On the emulator, signed out, the whole tour was run start to finish; the rest was built but not run live.

## Rules (same as iOS)

1. **Read-only on Schoology.** GET only. Never open quiz-taking pages. Never press Submit. Never enter or store Owen's passwords; he signs in himself.
2. **Never copy a parser into Kotlin.** All parsing runs in `extension/engine/engine.js` inside a hidden WebView. A selector fix goes in `extension/`, with a test.
3. **Keep the extension working:** `npm test` from the repo root.
4. **One application ID forever:** `com.owenleung.dewey`.
5. Learned something about Schoology? Add it to [FINDINGS.md](../ios/FINDINGS.md). Learned something Android-only? Add it here.
6. Commit only when Owen asks.

## Build and run

You need JDK 21 and the Android SDK. Gradle comes from the wrapper (8.14.3).

```bash
cd android && ./gradlew assembleDebug
```

```bash
cd android && ./gradlew testDebugUnitTest
```

- `android/local.properties` (gitignored) holds `sdk.dir`. On Owen's Mac it is `/opt/homebrew/share/android-commandlinetools`, and `JAVA_HOME` is `/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home`.
- `preBuild` runs `bundleEngine`, which runs `android/scripts/build-engine.sh`. That script bundles `extension/engine/engine.js` with esbuild from `ios/node_modules` (`cd ios && npm install` once). If esbuild isn't installed, it copies the iOS bundle `ios/App/Resources/engine.js`. The output goes in `app/build/generated/engine/assets/` and is never committed.
- **Emulator:** the AVD is `dewey` (Pixel 8 size, `system-images;android-36;google_apis;arm64-v8a`).

```bash
$ANDROID_HOME/emulator/emulator -avd dewey -gpu swiftshader_indirect
```

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

- **A real phone:** turn on USB debugging, plug the phone in, then run `./gradlew installDebug`. Release builds are signed with the debug key for now, so there is no Play Store or keystore setup yet.

## Layout

```
android/
  settings.gradle.kts, build.gradle.kts, gradle/wrapper/   Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21
  scripts/build-engine.sh          engine.js → build/generated/engine/assets
  app/build.gradle.kts             compileSdk 36, targetSdk 35, minSdk 26
  app/src/main/java/com/owenleung/dewey/
    DeweyApp.kt                    Application; RefreshWorker (WorkManager, every 2 h)
    MainActivity.kt                Compose host, deep links, notification permission, file chooser
    AppModel.kt                    the one app state (mirrors iOS AppModel)
    engine/Engine.kt, Models.kt    hidden WebView + JavascriptInterface bridge; kotlinx.serialization models
    web/WebConfig.kt, WebController.kt   WebView settings, user agent, cookies, native-route links
    store/                         ItemStore, Prefs/Cache/Secrets, Reminders, Inbox, Fmt
    neoplan/NeoPlanClient.kt       neo-plan API, token in Android Keystore
    widget/                        Glance widgets (To Do, Tests) + WidgetBridge + countdown notification
    engine/Practice.kt             the tour's pretend week: courses, items, gradebooks, and an answer for every op
    ui/                            Compose screens, one package per tab (todo, assignment, grades, classes, inbox, schoology, settings)
    ui/tour/                       TourScreen (cards), PracticeScreen (the pretend week + step script), Coach (overlay)
  app/src/test/                    JVM unit tests (TodoLogic, Reminders.plan, grade alert text, Inbox.kind, camelCase keys, user agent)
```

## How iOS maps to Android

| iOS | Android |
|---|---|
| WKWebView + `callAsyncJavaScript` | hidden `WebView` on `/iapi/course/active` (falls back to `/home`). `engine.js` is evaluated after `onPageFinished`, and results come back through `DeweyBridge.resolve(id, json)` into a `CompletableDeferred` |
| `JSONDecoder.convertFromSnakeCase` | `EngineJson.camel` renames keys, then kotlinx.serialization decodes |
| WKWebsiteDataStore.default | the app-wide `CookieManager`, flushed when the app goes to the background |
| UserDefaults / Keychain | SharedPreferences `dewey` / AES-GCM key in Android Keystore |
| UNUserNotificationCenter | AlarmManager (exact when allowed) + `ReminderReceiver`; `BootReceiver` reschedules after a restart. Channels: due, grades, inbox, account, countdown |
| BGAppRefreshTask | WorkManager `RefreshWorker`, every 2 h when online |
| WidgetKit | Glance `TodoWidget` and `TestsWidget`; ticking an item calls `AppModel.setProgress` |
| Live Activity (test countdown) | an ongoing notification with a chronometer counting down |
| Swipe actions / context menus | long-press menu on a To Do row |
| Siri / App Shortcuts | static launcher shortcuts (To Do, Grades) + `dewey://` deep links |
| QuickLook | built-in viewer: PdfRenderer for PDFs, images inline, "Open with…" and Share through a FileProvider |
| Sheets | full-screen Compose dialogs (Schoology sheet, file viewer, what-if) |
| PracticeView + Coach (tour) | `PracticeScreen` runs the real `MainScreen` on `AppModel.practice(ctx)`. Its `Engine(practice = true)` answers from `Practice`, and only the pure ops run engine.js, in its own web view on a blank page at the Schoology origin (no network, works signed out). `ItemStore.inMemory()`, `cache-practice`, and guards in Reminders, WidgetBridge, NeoPlanClient and RefreshWorker keep it off everything real. The coach overlay is in the same window as the screens: targets report `boundsInRoot` through `Modifier.coachTarget(key)`, and four blocker boxes swallow taps around the hole. So in practice the Schoology sheet is `PracticeDropbox`, drawn in the window rather than in a Dialog. |

Deep links: `dewey://todo`, `dewey://grades`, `dewey://inbox`, `dewey://assignment/{id}`, `dewey://message/{id}`.

## Done ripple

Dewey's one signature motion, on iOS (`ios/App/Screens/Shared/DoneRipple.swift`) and in the extension (`extension/overlays/ripple.js`): when a To Do checkbox turns on (Done, or Studied for a test), two thin rings in the theme accent spread out from it, like the ripples around the logo's drop. On Android it's `Modifier.doneRipple` in `ui/Drop.kt`, on the checkbox in `ui/todo/ToDoScreen.kt`:

- An outer ring grows from the checkbox's size to about 2.15×, its opacity 0.55 → 0 and stroke 2 → 1 dp; an inner ring starts 22% later and grows to about 1.7×, 0.4 → 0. About 420 ms in all, ease-out.
- Plays only when the box turns on, never on undo, never when a list first loads (key it on the change, e.g. `LaunchedEffect` on a counter bumped in the tap handler that sets done). Drawn behind the checkbox (`Modifier.drawBehind` + one `Animatable`), not clipped by the row.
- Off when the system has animations turned off (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`, Android's Reduce Motion).

**Caught up with iOS (2026-10-31):** the iOS changes since `e517c70` (through `4551b5f`) are ported: Plan (To do / Later / Done, drag by the grip, swipe, fold, school-day aware), the student's own tasks (quick add that reads "chem lab fri after school", the task editor), the month calendar pulled down from the week strip, the floating +, Student ID (barcode card, Settings page with Fill in from Schoology and the Code 39 scanner, a launcher shortcut `dewey://id`, the button on Classes), the "Dewey" calendar in the phone's Calendar app, School day times, the paper-and-ink look, the report card rows (spine labels, period only when a section names one), a goal per class (Goal chip, graph line, planner, "You need"), what-if that keeps its scores ("Show real" / "Back to what-if · N changes", planner counts them), Inbox index cards, the Assignment page taking its class from the link (and opening own tasks), refresh on return after 10 minutes, saved neo-plan writes replayed in order, a different student signing in clearing the last one's rows, pruning after 120 days, and the tour (month, Plan, new-task, goal and student-ID steps, Settings set for real with Allow / Not now, the Student ID card after practice, the Plan reference card). The engine's 20 s request timeout comes free with engine.js. Unit tests (iOS's PlanTests, TaskParseTests and CalendarAndIDTests ported) and `npm test` pass. On the emulator the tour was run through To Do, the month, Plan (grip drag, swipe to Later), quick add and Grades' Goal chip; the rest (Settings steps, Student ID scan, Calendar writes) was built but not run live.

Not ported: iOS's wide layout for iPad / Duo (WideShell): the activity stays portrait. The swipe-left hint on the tour's Later step is plain text (Android's coach has no swipe hint mark).

## Android-only facts

- **Google refuses sign-in in a WebView whose user agent has `; wv` or `Version/x.y`.** Login compatibility mode (Settings → Sign-in) removes both and is **on by default** on Android. The `X-Requested-With` header is also stripped (androidx.webkit allow-list) where the WebView supports it. On the emulator (API 36), Google's sign-in page loads with this user agent. Nobody has finished a sign-in yet.
- **The engine's WebView must sit in a window.** A WebView in no window counts as a background tab. Chrome then runs its timers at most once a second, and once a minute after 5 minutes. engine.js waits on a timer (`setTimeout`) before every request, so the app crawled after sign-in (Owen, 2026-10-15). `Engine.attach` puts the view in MainActivity's content view at 1×1 px and alpha 0. Background refreshes still run with no window, so they can be slow. `adb logcat -s DeweyEngine` logs each op's time and each page the engine loads.
- Google Assignments run in a google.com frame, so the WebView accepts third-party cookies.
- On Owen's Mac the emulator window can go blank with the default GPU mode ("Failed to make display surface context current" in its log). Use `-gpu swiftshader_indirect`. Shut it down with `adb emu kill`, not by killing the process, so the WebView's cookies get written first.
- `avdmanager` from Homebrew's commandlinetools warns "Could not load devices.xml" but still creates the AVD.
- In the Claude Code sandbox, `adb` can't reach its daemon. Run adb commands outside the sandbox.
- Debug builds open the tour from adb, signed in or not: `adb shell am start -n com.owenleung.dewey/.MainActivity --es tour tour` (or `cards`).
- `ItemState` is mutable and passed around as the same instance. With Compose's strong skipping, a composable that takes one is skipped when its parent recomposes, so it must read `model.revision` to redraw after a mark.
- Building inside `~/Documents` can fail at dex merging with "defined multiple times … 2.dex": iCloud makes " 2" copies of files in `app/build`. Build with the build directory outside Documents (a Gradle init script that sets `layout.buildDirectory`).

## Not verified yet (do these after Owen signs in on the emulator or a phone)

Done 2026-10-15 on the emulator: Google sign-in finishes; engine.js runs on the JSON page `/iapi/course/active`, so the `/home` fallback isn't needed; the session survives the app restarting (WebView keeps Schoology's session cookies, unlike WebKit); To Do, assignment, gradebook, messages and notifications ops each take 0.2–1.2 s.

Also done 2026-10-15 on the emulator:
- **Reminders** are scheduled from the To Do list and fire. Settings → Developer → "Test reminder in 1 minute" goes through the same AlarmManager → `ReminderReceiver` → notification path; it posted 3 min after the tap. They're rescheduled after a reboot (`BootReceiver`, about 50 s after boot on the emulator) and after an app update (`MY_PACKAGE_REPLACED`).
- **Widgets:** both show up in the launcher's widget list. The To Do widget shows real data. Ticking a circle or box from the home screen marks it in Dewey and the widget redraws without opening the app.
- **The countdown** notification shows (ongoing, silent, counting down to the due time) and goes away when switched off.
- **Background refresh** with the app closed (Developer → "Background refresh in 30 seconds", a one-off run of `RefreshWorker`): To Do, grades, messages and notifications in 4.2 s, still signed in.
- **Uploads:** Developer → "Test file picker" opens a local page with a file input in the Schoology sheet; nothing is sent anywhere. Files (a .txt from Downloads) and Camera (a photo) both reach the page.

Fixed while checking:
- Without "Alarms & reminders" allowed (new installs on Android 12+ start without it), `setAndAllowWhileIdle` gave each reminder up to an hour of slack. Now it's `setWindow` with 10 minutes, and Settings has an "Allow exact reminder times" row that opens Android's page for it.
- The widgets read their snapshot once per Glance session, so a tick saved but didn't redraw. They now collect `WidgetSnapshot.latest`.
- The file chooser used the page's `createIntent()` (GET_CONTENT), which on Android 16 offered only Photos & videos, so PDFs and documents couldn't be picked. Now it's OPEN_DOCUMENT (the Files picker) plus Camera.

Still to check:

1. Every engine op in Settings → Developer returns data like it does on iOS.
2. Grade, message, material and signed-out **alerts** with real new data. The refresh runs, and posting uses the same `Reminders.post` that the test reminder exercised, but nothing new arrived during the test.
3. An upload on a real Schoology dropbox (Owen, by hand; the app never presses Submit).
4. Exact reminder times once "Alarms & reminders" is allowed (the code path is the one the app used before; not run on the emulator).

## Progress

| Area | State |
|---|---|
| Toolchain, Gradle project, engine bundling | done |
| Engine bridge, models | done, not run live |
| Sign in | done; Google page loads on emulator |
| To Do, Assignment, Schoology sheet | done, not run live |
| Grades (list, course, detail, what-if, planner, history chart) | done, not run live |
| Classes, Materials, file viewer | done, not run live |
| Inbox, messages | done, not run live |
| Schoology tab | done, not run live |
| Settings, Developer, neo-plan | done, not run live |
| Widgets, background refresh, reminders, countdown | done, checked on emulator |
| Design refresh (Dew palette, settings pages, catalog cards, shelf, status guide) | done, seen in the tour on the emulator |
| Guided tour, practice mode, tour cards | done, run start to finish on emulator (signed out) |
| Plan, own tasks, quick add, month, Student ID, Calendar, goals (2026-10-31) | done; Plan, quick add, month and Goal run in the tour on the emulator |
| Tour update from iOS: month, Plan and new-task steps, settings set during the tour (notifications, school day, Calendar), Student ID card after practice, Plan reference card | done; run on the emulator up to Grades |
| Wide layout (iPad / Duo on iOS) | not ported |
| Unit tests | pass (Dew contrast, call numbers, logic, Plan, quick add, calendar events, Code 39, student ID, neo-plan ops) |
