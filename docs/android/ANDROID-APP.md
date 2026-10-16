# Android app: start here

A native Kotlin + Jetpack Compose port of the iOS app (`ios/`). Same screens, same engine, same rules. The iOS docs stay the source of truth for *what* each screen does ([SCREENS.md](../ios/SCREENS.md)), the engine contract ([ENGINE.md](../ios/ENGINE.md)) and Schoology facts ([FINDINGS.md](../ios/FINDINGS.md)). This file covers what is Android-specific.

**Status (2026-10-15):** every iOS screen and feature is ported and builds (`assembleDebug`). JVM unit tests pass. On a Pixel 8 emulator (API 36) the app launches to Sign in, and the sign-in web view reaches Google's "Sign in with Google" page with no "disallowed user agent" refusal. Nothing past sign-in has been checked yet; see "Not verified yet".

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
    ui/                            Compose screens, one package per tab (todo, assignment, grades, classes, inbox, schoology, settings)
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

Deep links: `dewey://todo`, `dewey://grades`, `dewey://inbox`, `dewey://assignment/{id}`, `dewey://message/{id}`.

## Android-only facts

- **Google refuses sign-in in a WebView whose user agent has `; wv` or `Version/x.y`.** Login compatibility mode (Settings → Sign-in) removes both and is **on by default** on Android. The `X-Requested-With` header is also stripped (androidx.webkit allow-list) where the WebView supports it. On the emulator (API 36), Google's sign-in page loads with this user agent. Nobody has finished a sign-in yet.
- **The engine's WebView must sit in a window.** A WebView in no window counts as a background tab. Chrome then runs its timers at most once a second, and once a minute after 5 minutes. engine.js waits on a timer (`setTimeout`) before every request, so the app crawled after sign-in (Owen, 2026-10-15). `Engine.attach` puts the view in MainActivity's content view at 1×1 px and alpha 0. Background refreshes still run with no window, so they can be slow. `adb logcat -s DeweyEngine` logs each op's time and each page the engine loads.
- Google Assignments run in a google.com frame, so the WebView accepts third-party cookies.
- On Owen's Mac the emulator window can go blank with the default GPU mode ("Failed to make display surface context current" in its log). Use `-gpu swiftshader_indirect`. Shut it down with `adb emu kill`, not by killing the process, so the WebView's cookies get written first.
- `avdmanager` from Homebrew's commandlinetools warns "Could not load devices.xml" but still creates the AVD.
- In the Claude Code sandbox, `adb` can't reach its daemon. Run adb commands outside the sandbox.

## Not verified yet (do these after Owen signs in on the emulator or a phone)

Done 2026-10-15 on the emulator: Google sign-in finishes; engine.js runs on the JSON page `/iapi/course/active`, so the `/home` fallback isn't needed; the session survives the app restarting (WebView keeps Schoology's session cookies, unlike WebKit); To Do, assignment, gradebook, messages and notifications ops each take 0.2–1.2 s.

Still to check:

4. Every engine op in Settings → Developer returns data like it does on iOS.
5. Reminders fire at the right time, including after a reboot. Widgets update and the tick works. The countdown notification shows.
6. The background refresh runs and sends grade, message and signed-out alerts.
7. Uploads in the Schoology sheet open the file picker and camera. Don't submit anything; just check that the picker opens.

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
| Widgets, background refresh, reminders, countdown | done, not run live |
| Unit tests | 10 pass |
