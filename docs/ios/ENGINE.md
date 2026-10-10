# iOS app: the engine (JS ↔ Swift contract)

The app does not re-implement Schoology parsing in Swift. It runs the extension's own parsers inside a hidden `WKWebView` (they need `DOMParser`, so JavaScriptCore alone won't do) and gets plain JSON back. This file is the contract between that JS and the Swift side. Build plan: [../IOS-APP.md](../IOS-APP.md).

## Why this shape

- The parsers in `schoology_archiver_extension/reader/parse/` are already pure: `(document | text) → plain data`, no `chrome.*`. The extension's offscreen page (`offscreen/parse.js`) already uses them this way: it receives `(kind, text, url)` and returns `{ data } | { login: true }`. **The app engine is that same idea, plus fetching.**
- One copy of the parsing logic: a fix to a selector fixes the extension, the MCP and the app together. Never copy a selector into Swift.

## Pieces

```
schoology_archiver_extension/engine/engine.js   entry point (new, milestone 1). Imports the pure modules below.
            │  bundled to one classic script (esbuild --bundle --format=iife --global-name=SchoologyEngine)
            ▼
ios/App/Resources/engine.js                    GENERATED, never hand-edited, rebuilt by ios/scripts/build-engine.sh
            │  injected as a WKUserScript (atDocumentEnd, main frame only)
            ▼
hidden WKWebView on https://fuhsd.schoology.com   same WKWebsiteDataStore.default() as every other web view
            │  callAsyncJavaScript("return await SchoologyEngine.<op>(args)", arguments: …)
            ▼
Engine.swift (actor)  →  Codable structs  →  SwiftData cache  →  SwiftUI
```

Why a classic bundle and not ES modules: `WKWebView` won't load `import` graphs from `file://`, and the engine has to sit on the Schoology origin anyway so `fetch(..., {credentials: 'include'})` carries the cookies (verified in the login test). Injecting one script into a Schoology page sidesteps both.

What page the hidden view loads: something small on the Schoology origin. Try `https://fuhsd.schoology.com/iapi/course/active` (a JSON response, so it's light); if WebKit won't run user scripts there, use `/home`. If the load ends on Google or `/login`, the session is gone: show Sign in (4a). Verify this in milestone 2.

## Modules the bundle uses (all pure today unless noted)

| Module | What the app uses from it |
|---|---|
| `reader/parse/sync.js` | `parseKind` (`courses`, `homeList`, `events`, `gradebook`, `status`), `parseCourses`, `parseEventsJson`, `isAssessmentUrl`, `parseDueText` |
| `reader/parse/mcp.js` | `parseMcpKind` (`gradesFull`, `assignmentFull`, `dropboxFull`, `materialsFull`, `materialFull`, `updatesFeed`) |
| `reader/parse/rubric.js` | `rubricPaths`, `parseRubric` |
| `reader/parse/grades.js`, `assignment.js`, `materials.js`, `feed.js` | through `parseMcpKind` |
| `overlays/grademath.js` | `periodsFromRows`, `currentPeriod`, `grade`, `impact`, `history`, `categoryFloor`, `needFor`, `letter`, `parseScore`, `A_CUTOFF` |
| `overlays/asgstate.js` | `chipState`, `zeroCost`, `whatIfGrade`, `rankIn`, `lateDays` |
| `overlays/matstate.js` | `rowState`, `folderTotals`, `summaryParts` |
| `overlays/todorows.js` | `annotateItems`, `sortDueItems`, `typeFor`, `tagFor`, `formatDue`, `formatOverdue`. **Coupled:** it imports `overlays/ui.js` for `buildRow`. Milestone 1 moves the pure functions to `overlays/todologic.js` (todorows re-exports them so nothing else changes). |
| `reader/client.js` | `isQuizTakingUrl`. **Coupled:** the file imports the Chrome tab bridge. Milestone 1 moves `isQuizTakingUrl` to a pure module (e.g. `reader/guard.js`); client.js re-exports it. |
| `reader/ratelimit.js` | `isRateLimited` (pure function). Don't use its `Limiter`/`acquireSlot` (shared extension state); the engine has its own small queue. |
| `outputs/neoplan/api.js` | `buildRequest` (pure: op → `{ url, init }`). The rest uses `chrome.storage`. Swift sends the request itself (token in Keychain), see "neo-plan" below. |

## Engine API (what `engine.js` exports)

Every op is `async`, takes one plain object, and returns one of:

```js
{ ok: true, data, url, fetchedAt }       // url = final URL after redirects; fetchedAt = ISO
{ ok: false, error: 'login' }            // Schoology sent its sign-in page / Google
{ ok: false, error: 'quiz' }             // refused: quiz-taking page or /assessments/ (never followed)
{ ok: false, error: 'ratelimit' }        // 429 or Schoology's "slow down" page
{ ok: false, error: 'parse', message }   // a parser threw: the screen shows its "couldn't read this page" fallback
{ ok: false, error: 'http', status }
```

| Op | Request(s) | Parser | `data` |
|---|---|---|---|
| `courses()` | `GET /iapi/course/active` | `parseKind('courses')` | `[{ section_id, title, section_title }]` in Course Dashboard order |
| `todo()` | `GET /home/upcoming_submissions_ajax`, `GET /home/overdue_submissions_ajax`, `GET /home/upcoming_ajax` | `parseKind('homeList')` ×2, `parseKind('events')` | `{ upcoming: [{ schoology_id, realm, title, due? }], overdue: [...], events: [{ schoology_id, realm }] }`. Map `realm` → section with `sectionsByRealm(courses)`. |
| `gradebook({ sectionId })` | `GET /course/{id}/student_grades` | `parseMcpKind('gradesFull')` | rows `[{ level: course\|period\|category\|item, title, due, grade, comment, weight?, id?, rubric?: { rubricId, enrollmentId } }]`. Feed to `periodsFromRows` for grade math. |
| `assignment({ id })` | `GET /assignment/{id}` | `parseMcpKind('assignmentFull')` | `{ title, type: 'assignment'\|'quiz'\|'external_tool', due, instructions_md, attachments: [{ url, title }], submission: { state, late? } \| null, dropboxUrl }` |
| `status({ id })` | `GET /assignment/{id}/info` with **`redirect: 'manual'`** | `parseKind('status')` | `{ state: 'submitted'\|'not_submitted'\|'no_dropbox'\|'unknown', late?, quiz? }`. A redirect means a quiz: return `{ state: 'unknown', quiz: true }` without following it. |
| `dropbox({ url })` | `GET {dropboxUrl}` | `parseMcpKind('dropboxFull')` | `{ grade, comments }` (drop system lines like "Revision 1 submitted") |
| `rubric({ rubricId, enrollmentId, itemId })` | the two GETs from `rubricPaths()` | `parseRubric` | `{ title, total, earned, criteria: [{ title, max, earned, level, comment }] }` |
| `materials({ sectionId, folderId? })` | `GET /course/{id}/materials[?f={folderId}]` | `parseMcpKind('materialsFull')` | folder rows (see `reader/parse/mcp.js` `materialsRows`) |
| `material({ path })` | `GET {path}` (only the shapes `outputs/mcp/ops.js` allows: `/course/{c}/materials/gp/{id}`, `/course/{c}/materials/link/view/{id}`, `/page/{id}`, `/discussion/{id}`, `/assignment/{id}`) | `parseMcpKind('materialFull')` | `{ url, kind, title, body_md, target, files, links }` |
| `feed({ sectionId, page })` | `GET /course/{id}/feed?page={n}` | `parseMcpKind('updatesFeed')` | `[{ author, date, body_md }]` |
| `math.*` | none | grademath / asgstate / matstate / todologic | pass-through to the pure functions, e.g. `math.grade({ course, opts })`, `math.impact({ course, id, opts })` |

`outputs/mcp/ops.js` already does almost exactly these fetch+parse pairs for the MCP. Read it before writing `engine.js`; copy its request shapes, not its Chrome plumbing.

## Fetch rules (the engine's `get`, ported from `reader/client.js` `getText`)

1. **Refuse before requesting** any URL where `isQuizTakingUrl(url)` or `isAssessmentUrl(url)` is true → `error: 'quiz'`. Never open `/assessment`, take/start/resume, or `/dropbox/submit`.
2. `fetch(url, { credentials: 'include', redirect })`. Final host not `fuhsd.schoology.com`, or path starts with `/login`, or the page has `form#s-user-login-form, #login-container` → `error: 'login'`.
3. `isRateLimited(status, text)` → back off (wait, retry up to 3 times), then `error: 'ratelimit'`. HTTP 5xx → retry with 3 s, 6 s waits.
4. At most 2 requests in flight, at least 300 ms apart. The app is one student's phone; never hammer Schoology.
5. **GET only.** The engine never POSTs to Schoology. Anything that changes Schoology happens in the visible Schoology sheet, by the student.

## Swift side

```swift
actor Engine {
    // Hidden WKWebView, not in the view hierarchy (keep a strong ref; give it a 1×1 frame
    // in a hidden window if WebKit stops running JS when detached).
    func call<T: Decodable>(_ op: String, _ args: [String: Any] = [:]) async throws -> T
}
// callAsyncJavaScript("return await SchoologyEngine[op](args)", arguments: ["op": op, "args": args],
//                     in: nil, contentWorld: .page)
// Result is a JSON string (have engine.js JSON.stringify its result) → JSONDecoder.
```

- One engine for the whole app. If the hidden page navigates away or the process dies (`webViewWebContentProcessDidTerminate`), reload it and re-inject.
- Decode into Codable structs that mirror the tables above. Unknown fields are ignored; missing optional fields are fine.
- `error: 'login'` from any call → app-wide signed-out state (4c), not a per-screen error.

## neo-plan (optional sync)

Same server, token and ops as the extension (`outputs/neoplan/api.js`). Base `https://neo-plan.vercel.app`, header `Authorization: Bearer np_…`. Token lives in Keychain. Swift calls it with `URLSession` (no web view needed, no CORS since it's not a browser).

| Op | Request |
|---|---|
| today | `GET /api/extension/today` |
| items | `GET /api/extension/items?schoology_ids=1,2,3` (≤100) |
| work (done / studied) | `POST /api/extension/items/{id}/work` `{ "work": "todo" \| "ready" }` |
| setType | `POST /api/extension/items/{id}/type` `{ "type": "assignment" \| "exam" \| "classwork" \| "task" }` |
| turnIn / putBack | `POST /api/extension/items/{id}/turn-in` / `put-back` |
| remove / restore | `POST /api/extension/items/{id}/remove` / `restore` |
| addItem | `POST /api/extension/items` `{ schoology_id, section_id, title, due_at, source_url, type? }` |
| courses / setCourse | `GET /api/extension/courses`, `PUT /api/extension/courses/{section_id}` `{ class_id }` |

`buildRequest()` in that file is the source of truth for bodies and validation; if it and this table disagree, it wins. Without neo-plan, the same state lives in SwiftData only.

## Keeping the extension working

The engine entry must not change any extension behaviour. Milestone 1 is done when `node --test test/*.test.js` (from `schoology_archiver_extension/`) passes, `war.test.js` still passes (every module an overlay imports is listed in `manifest.json` `web_accessible_resources`), and the bundle builds.
