# Dewey for Schoology (Chrome extension)

Saves a whole Schoology course to `Downloads/Schoology Archive/<Course name>/` so it survives when the course is deleted at the end of the term, and so you can hand the folder to Claude (Cowork) for quizzes and study help.

No cookies to copy: it uses your normal Schoology login in Chrome.

## Install (once)

1. Open `chrome://extensions`
2. Turn on **Developer mode** (top right)
3. Click **Load unpacked** and pick this `extension` folder
4. Pin the extension (puzzle-piece icon → pin "Dewey for Schoology")

After editing any file here, click the ↻ reload button on the extension's card.

## Use

1. Open a course in Schoology and click the **Archive** button next to the overlay switch (bottom left). This opens a card over the page, already pointed at that course.
2. Click **Archive this course**. By default files go straight to `Downloads/Schoology Archive` — no picker, no Save dialogs. If you'd rather pick a folder (so Cowork can point straight at it, say), do that once from the gear's Settings (**Archives save to** → **Choose folder…**, which opens a small Dewey window for Chrome's picker; if Chrome asks, choose **Allow on every visit**). The Welcome card offers the same choice the first time.

Closing the card, or leaving the page, does **not** stop the run — it keeps going in the background. Reopen the card on any Schoology page to see its progress, or to start another course (only one archive runs at a time). Run it again whenever you like (weekly, and definitely before the term ends): only new or changed things are saved. Previously archived courses are listed at the bottom of the card.

## What gets saved

| In Schoology | In the archive |
|---|---|
| Folders | Same folder tree |
| Uploaded files (PDF, slides, images) | The original file, named after its Schoology title |
| Google Docs / Slides / Drawings linked in the course | Exported PDF |
| Google Sheets | .xlsx |
| Google Drive files | The file |
| Assignments | `Title (assignment).md`: instructions, due date, grade, teacher comments + a `Title files/` folder with attachments and **your submissions** (every revision; photos, PDFs, audio/video recordings with their original names) |
| Test/Quiz assignments (older Schoology quizzes) | Every question, the choices, **what you picked, and whether it was correct**, for each finished attempt |
| Quizzes (newer "assessments") | Description, grade, attempts. If the teacher allows reviewing answers: every question with your answers |
| External-tool (LTI) assignments/materials | Title, due date, grade; marked as external (the work lives in another app) |
| Pages, discussions | `.md` text |
| Gradebook | `grades.md` |
| Announcements | `updates.md` |
| Google Forms, Drive folders, YouTube, websites | Listed in `INDEX.md` with their link (can't be downloaded) |

`INDEX.md` at the top of the course folder lists everything, what changed in the last sync, and anything that failed.

## Without neo-plan

Done / Studied, Turn in, Remove and the HW / Test type all work with no neo-plan token: the background worker keeps them in `chrome.storage.local` instead (`outputs/local/marks.js`), under `localMarks:<host>:<Schoology user id>`. The user id is Schoology's own, read from the page that asked (`overlays/ui.js` `pageUserId`: the site navigation's data, else the session-tracker frame), so no extra request is made and it's always whoever is signed in on that page. So Schoology's daily sign-out keeps your marks for the next sign-in, and a different student signing in on the same Chrome gets a list of their own. A page that names no user saves and shows nothing. New To Do work is added to the list on its own, as with neo-plan; anything untouched for 400 days is dropped (3,000 items at most). Submitting with Schoology's own button marks it **Submitted** here too. Missing still needs neo-plan (it comes from neo-plan's sync).

Saving a token switches to neo-plan's copy and moves the local marks onto the board (`outputs/local/port.js`): only the Schoology user on the page the Settings card was opened on (saved from anywhere else: the first user to open a Schoology page within the hour), and only items with a mark of your own (Done / Studied, Turned in, Removed, a changed type). One neo-plan doesn't have is added with its mark; one it has unmarked gets the mark; one it has marked (from the iPhone app, say) keeps neo-plan's. Saving a token again later moves only what was marked since. If the worker stops halfway, the next Schoology page finishes it. The neo-plan page shows how it went under **Connected** for a day.

## neo-plan settings

Settings live only in the overlay: the gear beside the Overlay switch (or the toolbar icon on a Schoology tab) opens the Settings card, with a page each for Appearance, Archive and neo-plan. The neo-plan page (token, the Schoology line, Courses, and Advanced: server and Claude) is `panel/neoplan.html`, framed in the card. The first time the overlay shows, a Welcome card offers the archive folder and the neo-plan token, both optional.

1. In neo-plan, open Settings and make a new extension token (it starts with `np_`).
2. Open Settings (the gear, or the toolbar icon), paste it on the **neo-plan** page, **Connect**. **Advanced → Server** is `neo-plan.vercel.app` or `plan.owen64.me` (the same neo-plan under two names; your token works on both), or `localhost:3000` while developing neo-plan. The neo-plan card keeps you signed in only on the one you picked, since sign-in is per domain.

The token is kept in the extension and only the background worker sends it to neo-plan; a Schoology page never sees it, even with Settings framed right on it.

## neo-plan and Schoology

With a token saved, the extension also reads Schoology for neo-plan in the background: every 15 minutes while Chrome is open, when a Schoology page loads (at most every 5 minutes), and from **Check now** in Settings. It only reads (GET): the course list, the home page's upcoming and overdue lists, upcoming events, the gradebook of each course that is mapped to a neo-plan column, and the submission status of assignments neo-plan still has open (at most 40 per run, oldest first; a page that redirects, such as a newer quiz, is never followed). neo-plan then files items into the right column, clears work Schoology shows as submitted, and flags what the gradebook marks Missing. It never creates items on its own. A run never overlaps an Archive; an Archive started during a run waits a few seconds for it.

- **Settings → Schoology**: when it last read Schoology, **Check now**, and one line if something went wrong (not signed in, token refused, archive running).
- **Settings → Courses**: every Schoology section and the neo-plan column it files into. Ones neo-plan matched by name are marked `auto`; pick another (or None) to override.
- **On an assignment page**, a one-line chip under Schoology's due date. Not submitted yet: the class, **Missing** if it is, what a zero would cost your course grade ("A zero costs −1.26"), and **Remove from neo-plan**. After you submit with Schoology's own button: **Submitted**, **Cleared in neo-plan**, and **Late by N days** if it was. Graded: how much it moved your course grade and where it ranks in its category ("2nd lowest of 5 in Tests & Quizzes"). Not in neo-plan: **Add to neo-plan**; removed: **Removed · Add back**. There's no Turn in button here: when the page shows your submission as made, neo-plan is told right away and clears it. (The To Do panels have one, below.)
- **On an assignment page**, while it's ungraded, a **What if I get [ ] / pts** box in Schoology's sidebar, under its Grade box, that shows your course grade with that score (never saved). Below the instructions, **Also in <folder>** lists the folder's other items with the same markers as the Materials page; files and pages say **In your archive** when the archive has them. To find the folder it reads the course's folders from the top until it turns up (kept for 10 minutes, shared with the Materials page).
- **On a course's Materials page (and its folders)**: a strip above the list with **Only open work** (hides everything but missing, done-not-submitted and not-done work, and the folders holding them), how many are open in the course, and when the course was last archived with **Sync now** (opens the Archive card for this course and starts it right away). Each folder shows what's open in it ("1 missing · 1 to turn in · 1 to do", or "All turned in") and its scored % (points earned / points graded in it). Each assignment shows one marker on the right: its score when graded, **Submitted**, **Done, not submitted**, its due date when not done, **Missing**, or **+ Add to list**. To read the folders and scores it opens each folder of the course and its gradebook once (kept for 10 minutes).
- **On the Schoology home page**, our own To Do column in place of Schoology's, grouped by class in the Course Dashboard's order. Each row is the title, the full date (`Tue Sep 29, 8:30 am`; overdue in red with `· 11 d overdue`), and its status below (○ **To do**, ● **Done, not submitted**, ✓ **Turned in**, **Missing**, or **+ Add to list**). On the right, stacked buttons with a glyph and a short word: **✓ Done** / **↶ Not done** (**Studied** / **Not studied** for a test), then **↑ Turn in** for an open assignment or **↶ Undo** for one you've turned in (it goes back to done, not turned in). These are the only Turn in buttons the extension adds. A thin rotated tag on the left says what it is in neo-plan: black **TEST**, outlined **HW** / **CW** / **TASK** / **MEET**, dashed and empty when it isn't in neo-plan yet.
- **On a course's own pages (Materials, Updates, …)**, the same To Do panel (status and buttons included) in place of that course's own Upcoming column — just this course's overdue and upcoming, oldest overdue first then soonest upcoming, with no class grouping and no cap (there's only one class here).
- **On Schoology's own To Do and Upcoming lists when ours hasn't loaded (the "N more overdue" popup too), and the list on Home → Assignments (Upcoming, Recent, Missing)**: the same marker as on Materials, on the title's line, colour-coded: green **Submitted** / **Turned in** / **Finished**; blue **Done, not submitted** (and **Studied** for a test); **Not studied** for a test (a square, as in neo-plan); grey **To do**; red **Missing**; **+ Add to list**, or **Removed · Add back** for one you removed. Click **Not studied** / **Studied** or **To do** / **Done, not submitted** to flip it in neo-plan (here and on Materials); it changes at once and flips back if neo-plan refuses. A row Schoology already shows a grade for gets nothing. A removed assignment stays off neo-plan when Schoology is read again. Taking one off neo-plan is on its assignment page. (The Assignments list has no links, so it's matched to its assignments through the list's own data, `/v2/events/…`.)
- **On a course's Grades page**, inside Schoology's own table: a **Graph** of your course grade over the term; how far above the A cutoff you are; per category, how low it can drop and keep the A; and the impact of each graded item on your course grade. Press **What-if** to try scores: every row gets a score box (blank keeps the real score), **Leave out** drops an item, **+ Upcoming test** adds one that isn't in the gradebook yet and says what you'd need on it for an A, and a bar at the bottom of the window shows your real grade → the what-if one. **Show real** goes back without losing what you typed; nothing is saved past this page view. The math follows Schoology's own category percents, so an item your teacher set to count for nothing is left out (marked **not counted**). The added column takes its room from Schoology's (usually empty) comment column, so titles and grades stay where plain Schoology puts them. If Schoology shows no weight for a category, type it next to the category's name (saved per course). Pick a **Goal** (A down to C-) per class and the cutoff line and the "need" numbers follow it; it starts at A, 93%. On the all-classes Grades page, **Hide** takes a class off the page.
- **The neo-plan button**, bottom right of every Schoology page except assignments and tests: opens neo-plan itself in a card over the page, with Schoology dimmed behind it. Close it with **×**, **Esc**, or a click outside the card; it keeps its place until you leave the page. **Open in a tab** is at the top of the card.

Remove and Add back need neo-plan's side (`neo-plan/docs/EXTENSION-CONTRACT-5.md`); until it has it they say **neo-plan needs an update**. Staying signed in to neo-plan inside the card needs the same update (its sign-in cookie has to allow being inside another site's page); until then the card shows neo-plan's sign-in, and **Open in a tab** works.

The overlays only read the page and draw in their own isolated elements: they never click, submit or change anything of Schoology's (the Grades page adds rows and a column of its own to the gradebook, and tints a row with a what-if score; **Only open work** hides Materials rows until it's unticked), and they never run on dropbox-submit pages or while you take a quiz.

**Quiz pages.** A newer quiz's start page (Start Attempt and Previous Attempts) gets the assignment chip and your theme; an old-style quiz's attempts and answer review get your theme. The moment you start an attempt, everything comes off that page and the background goes quiet (no sync, no Claude bridge, no Archive) until the quiz tab is gone. Schoology runs a newer quiz's start page and its attempt on the same URL, so the extension goes by the page: it counts as the start page only while it shows Schoology's start page and no answer field.

## Stopping (kill switch)

Any of these stops a run **immediately**: page loads are aborted, file writes in progress are discarded (no half-written files) and quiz-review tabs are closed.

- **■ Stop now** button in the Archive card while it runs
- **Alt+Shift+K** from any tab (change it at `chrome://extensions/shortcuts`)

Unlike before, closing the Archive card (or its **Esc**/×/click-outside) does **not**
stop the run — the job lives in the extension's offscreen document, not the card, so
it keeps going until it finishes, errors, or is stopped by one of the two above.

Everything saved before the stop is remembered, so the next run continues where it left off. Only one archive can run at a time, across all Chrome windows.

## View only: quizzes are never started

The archiver only reads. It never requests a quiz-taking page (it refuses `/assignment/…/assessment` outright) and never presses Start, Resume, Continue or Submit. To read answer reviews on newer quizzes it opens the quiz page in a background tab and clicks only links whose whole text is exactly **View** in your Previous Attempts table, the same as clicking View yourself.

## Edits and deletions

- **Teacher replaced or edited a file:** the new version is saved next to the old one as `name (updated 2026-10-01).pdf`. Nothing is overwritten.
- **Teacher removed something:** your copy stays, listed under "Removed from Schoology" in `INDEX.md`.
- **You deleted a file from the archive:** it's downloaded again next run.
- **"Re-download everything"** ignores the history and fetches it all again.

Change tracking is stored in the extension (and copied to `_archive/manifest.json`). If you remove the extension, that history is lost and the next run starts fresh, but the files you already have stay put.

## Using it with Claude (Cowork)

Point Cowork at your archive folder's `<Course>/` subfolder and start with something like:

> Read INDEX.md first. Then quiz me on Unit 2 using the lecture slides and guided notes. Don't show me answer keys until I've answered.

## Code layout

- `manifest.json`, `background.js` (archive + sync run lock, kill switch, sync schedule, message routing and the `archiveProxy` allowlist), `util.js` (shared helpers)
- `reader/`: everything that reads Schoology. `client.js` fetches pages (and refuses quiz-taking URLs); `parse/` turns pages into data (`materials`, `assignment`, `quiz`, `grades`, `feed`); `md.js` converts HTML to Markdown.
- `outputs/archive/`: the archive run (`archiver.js`), file writing and change tracking (`saver.js`), the chosen folder (`folder.js`), Google exports (`google.js`), pure job-state helpers used by both `background.js` and the card (`state.js`), and `chrome-bridge.js` — the proxy every chrome API the offscreen document can't call directly (downloads, tabs, scripting, storage) goes through, taking a direct path in the service worker and a `chrome.runtime.sendMessage` path in the offscreen document.
- `reader/parse/sync.js`: courses, home lists, events, gradebook rows, submission status and the assignment page, for the sync and the overlays.
- `sync/`: `sync.js` is one run (Schoology reads → one enrich → a Snapshot in storage); `runner.js` is when it may run (lock, schedule, kill switch) and submit detection.
- `offscreen/`: the one offscreen document (the background worker has no DOMParser), shared by both jobs and routed by message `target`. `parse.js` is the sync's page parsing; `archive.js` runs the Archiver itself (started/stopped by `background.js`, everything else proxied through `chrome-bridge.js`); `ensure.js` is the shared "create it if it doesn't exist yet" helper.
- `overlays/`: content scripts on Schoology pages. `boot.js` loads `assignment.js`, `home.js`, `course.js` (Materials, and every course page's own To Do panel) or `grades.js` (a course's Grades page) as a module, and `launcher.js` (the neo-plan button) everywhere but assignments and tests; `todorows.js` is the To Do panel's shared look and pure logic (the row grid, the rotated type tag, due-date formatting, the overdue/upcoming sort) that `todo.js` (home page, grouped by class) and `coursetodo.js` (one course's own panel, flat) both build on; `marks.js` is the row marker `home.js` and `course.js` share; `materials.js` is the Materials strip, folder totals and row markers, and `matstate.js` what they say (pure); `archive.js` is the Archive card (dimmed page, inset card; `switch.js`'s Archive button and `materials.js`'s Sync now both open it); `test/materials-page.html` is its stand-in page; `test/home-page.html` and `test/course-page.html` are the To Do panels' stand-ins; `test/archive-card.html` fakes `chrome.runtime` to look at the Archive card idle and running; `ui.js` is their shadow-DOM styling. `grademath.js` is the grades overlay's math (pure; tests in `test/`, run `node --test` from this folder). `test/grades-page.html` is a stand-in gradebook to look at `grades.js` outside Schoology (serve this folder with `python3 -m http.server` and open it).
- `outputs/neoplan/api.js`: every call to neo-plan (token, server, fetch). Only the background worker imports it; content scripts ask it by message (content scripts may only read items, add, turn in, put back, remove and restore one, and open the neo-plan window).
- `panel/sync-settings.js`: the Schoology line and Courses in Settings.
- `overlays/settings.js`, `overlays/intro.js`, `overlays/dialog.js`: the Settings card, the Welcome card, and the card shell, neo-plan frame and archive-folder helpers they share.
- `panel/`: `neoplan.html`/`neoplan.js`/`neoplan.css` are the neo-plan section framed in the Settings card (`?view=settings`) and the Welcome card (`?view=connect`); `sync-settings.js` is the Schoology line and Courses within it, `np.js` is its message helper, `folder.html`/`folder.js` is the small window that runs Chrome's folder picker, and `today-format.js` is dates shared with the overlays' To Do panels.

## Known limits

- **Quizzes:** questions and answers are saved only where the teacher allows reviewing them. Otherwise the file has the description, grade and attempts. Unfinished or never-taken quizzes are left alone.
- **Google files you can't open** (not shared with you) are listed as links with the error.
- **Very large Drive files** hit Google's "can't scan for viruses" page and are listed as links instead.
- If a file can't be fetched directly (rare), the archiver falls back to a normal Chrome download for it, which goes to `Downloads/Schoology Archive` and can show a Save dialog if "Ask where to save each file" is on.
- **Announcements (`updates.md`)** haven't been tested against a course that has posts yet (none of the current courses have any).
- **Discussions, media albums, SCORM/web packages** exist in Schoology but none of the current courses use them. They're saved as page text as a fallback; untested.
- Schoology API keys are disabled for students in this district, so the archiver reads the normal pages.

## Claude (MCP)

`../mcp/` is a local, read-only MCP server that lets Claude Desktop read your Schoology: to-do, grades, assignments, announcements, materials, and every archived file (PDFs as text, plus search). While Chrome is open the extension answers it live, through your login; otherwise it answers from the last background read and the archive folder, and says how old the answer is. **Settings → Claude** shows whether it's connected. Setup and the connection details: `../mcp/README.md` and `../docs/mcp/MCP-BRIDGE.md`.
