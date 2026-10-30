# Changelog

The extension and the MCP server are versioned separately (see [Versioning and releases](docs/DEVELOPING.md#versioning-and-releases)). Newest first.

Entries before extension 1.10.0 / MCP 1.1.0 were reconstructed from the git history. Apart from MCP 1.0.0 (tag `mcp-v1.0.0`), those versions were never tagged, so they're listed by date.

## 2026-10-18 — Android 0.3

Tag: `android-v0.3` (beta, sideloaded APK)

### Android
- Catches up with the iPhone app's design refresh.
- Guided tour on first launch: Dewey walks you through a pretend week in the real screens, with everything but the next step dimmed. Nothing you tap touches your real Schoology. Settings → Help has the tour and reference cards.
- One look, Dew, with a System / Light / Dark switch (the six themes are gone); serif screen titles.
- A gear on each tab opens that tab's Settings page; Settings is split into pages, with How statuses work and a neo-plan page that explains it before connecting.
- To Do rows are library catalog cards, with a ripple when you mark one done and a one-time tip after your first mark. Classes is a shelf of books.
- Grades: a report card across the top; press and hold a class (or Settings → Grades) to hide it, and hidden classes don't count toward new-grade alerts. A grade change that rounds to nothing shows "+0.00%".
- Inbox: **School updates** from groups such as a class hub. PDFs in a class's materials open.
- "Late" means what Schoology still lists as overdue and you haven't finished: not work marked Done or Turned in, not a past test you marked Studied, not an online quiz you already took (Dewey now reads the quiz's page), and not paper or lab-site work Schoology dropped after its due date. The shelf's LATE ribbon, the tab badge and To Do all use this rule, so the iPhone and Android agree. Same fix on iPhone.
- Settings → Classes (and Settings → Inbox): **Classes and groups**. Dewey guesses which courses are classes from their names (a class ends in "- 12345"); a school that names courses differently can switch any course between Class (a book and grades) and Group (its posts go to School updates). Same on iPhone.
- Tapping a book starts its page with **Open work**: that class's late work and everything due, so you can see what a LATE / TODAY / TMRW ribbon means. It replaces the **Only open work** switch, which kept every folder.

## 2026-10-18 — Extension 1.13.0 · MCP 1.1.1

Tags: `ext-v1.13.0`, `mcp-v1.1.1`

### Extension
- A one-time tip after your first Done, Turn in or Studied, with a full **How statuses work** guide (To do → Done → Turned in, Submitted on Schoology vs turned in elsewhere, why paper or lab-site work isn't missing). Reachable again from Settings → Statuses.
- neo-plan stays out of sight until you connect it: the Welcome card is two steps, the assignment page no longer says "Token needed", and Settings → neo-plan explains what it is before connecting.
- Marking a To Do row done plays a small ripple from the button (off with reduced motion).
- Dewey the drop shows up on the Welcome card and while an archive is running.
- Numbers use Instrument Sans with even-width digits; grade-impact deltas end in % ("+0.24%").

### MCP server
- Renamed to `dewey-mcp` (package, server name, log prefix, user agent). MIT license.

### iPhone (not in a store; build it yourself)
- Guided tour on first launch: Dewey walks you through a pretend week inside the real screens, with everything but the next step dimmed. If you tap somewhere else, **Take me there** brings you back. Settings → Help has the tour and reference cards.
- Grades: press and hold a class (or use Settings → Grades) to hide it; hidden classes don't count toward new-grade alerts.
- Inbox: **School updates** from groups such as a class hub.
- PDFs in a class's materials open (Schoology's own viewer page is resolved to the file).
- A grade change that rounds to nothing shows "+0.00%" in gray instead of nothing.
- One look, Dew, with a System / Light / Dark Appearance switch; serif screen titles; faster start from saved copies; a newer quiz's page shows its facts instead of an error.

## 2026-10-16 — Android 0.2

Tag: `android-v0.2` (beta, sideloaded APK)

### Android
- Catches up with the iPhone app.
- No more false sign-outs: when one page says "signed out", Dewey checks Schoology's course list before sending you to Sign in. An assignment that was removed or made private says so, with **Remove from my list**.
- Inbox: each section is a box you can fold (School starts folded), and each has its own **Show older**.
- Opens faster: saved copies show right away, dropbox checks and gradebooks load 2 at a time, and grades show from the last summary while the rest loads.
- Settings → Look: six themes (Standard with a soft gray dark mode, Midnight, Dew, Frost, Pastel, Paper).
- Done is an accent ring with a check; turned in is a solid green seal.

## 2026-09-30 — Extension 1.12.0

Tag: `ext-v1.12.0`

### Extension
- New work goes into neo-plan by itself: an upcoming (or up to two weeks overdue) assignment or quiz that neo-plan has no item for is added without pressing **Add to neo-plan**, both when a To Do sidebar shows it and on every background sync. Anything you removed from neo-plan stays removed.
- Quizzes go in as tests: a To Do row that links to a quiz (`/assessments/`) is sent as an exam; anything else lets neo-plan decide from the title (quiz, test, exam…), the same rule as its calendar feed. Needs the matching neo-plan update.
- To Do sidebar adds now carry the due date (they were sent with none).
- Assignment page: a **HW · Test · Task · CW** switch on the chip changes the item's type in neo-plan in one tap. A switch neo-plan can't make says why (e.g. "Set a date first"). Needs the matching neo-plan update.

## 2026-09-28 — Extension 1.11.0

Tag: `ext-v1.11.0`

### Extension
- To Do sidebars (home and course): each row keeps its status under the due date and gets stacked buttons at its right: **✓ Done** / **↶ Not done**, then **↑ Turn in** on an open assignment or **↶ Undo** on a turned-in one.
- Grades: the overlay no longer squeezes the title column. Grades sit where plain Schoology puts them; the empty comment column gives up the room instead.
- Content scripts may now ask neo-plan to turn in and put back (only the To Do panels offer it; an assignment page still turns in only through Schoology's Submit).

### Repo
- MIT license.
- README rewritten as a friendly, picture-first guide with screenshots (`docs/images/`); technical detail moved to a collapsible reference at the bottom.
- Repo README and this changelog, written for anyone setting it up (any school's Schoology, neo-plan optional, recommended archive location, grading-scale note).
- Removed the legacy Python downloader (`schoology_downloader.py`, `HOW_TO_RUN.txt`); the extension replaces it.

## 2026-09-27 — Extension 1.10.0 · MCP 1.1.0

Tags: `ext-v1.10.0`, `mcp-v1.1.0`

### Extension
- **Quiz quiet mode:** while any tab is on a Schoology Test/Quiz page, no sync, no MCP bridge, and no archive run. Opening a quiz stops everything as Alt+Shift+K does; it resumes when the quiz tab closes. Overlays are now kept off every `*assessment*` page.
- Find materials across courses, deep live listing, Google Drive folders, faster reconnect to the MCP server.

### MCP server
- `list_materials` / `open_material` read course materials live; `fetch_page` reads public course websites listed in `sites.json`.
- Reads the Algebra 2 textbook site (framesets, image-only pages).
- Live data is preferred over the snapshot and archive.
- The bridge probes a port with `fetch` before opening a WebSocket.

## 2026-09-27 — MCP server 1.0.0

- First version: a local, read-only MCP server so Claude Desktop can read Schoology, live through the extension, with a snapshot cache and archive fallback.

## 2026-09-25 to 2026-09-27 — Extension (unified)

- The Archiver moves into an in-page card; neo-plan settings move into the gear. No side panel.
- Themes and a settings menu; what-ifs, dropping a grade, and percents on the Grades page.
- Our own To Do column on the Schoology home page with type tags and clickable Studied / Done; a course's own To Do in place of Schoology's Upcoming column.
- Overlays on Grades, Materials, and assignment pages that only add to Schoology.
- neo-plan reads Schoology in the background; a neo-plan card on Schoology's pages with Remove / Add back.
- The quiz guard also refuses `/assessments/{id}/take|start|resume` and the dropbox submit form.
- The archiver is split into reader, archive output, and panel, with no behaviour change.

## Before 2026-09-25

- The original Schoology Course Archiver extension and the Python downloader (`schoology_downloader.py`), as they were before the unified extension.
