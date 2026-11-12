# Overlay UI: grades, materials, assignment pages

Agreed with Owen on 2026-09-26. The reference render is `docs/extension/overlay-demo.html`
(open it in a browser). This file is the spec: where the demo and this file
disagree, this file wins.

## Principle

The overlay **supplements Schoology; it never replaces it.** Schoology's own
tables, buttons and layout stay as they are. The extension only adds small
elements next to them, and only information Schoology doesn't already show.
Never repeat what's on the page (course %, category %, due date, score,
instructions).

- On an assignment page, turning work in happens **only with Schoology's own
  Submit button**; submit detection (already built) clears the item in neo-plan.
  The one exception is the To Do sidebars (home and a course's): each row keeps
  its status words under the due date ("○ To do", "● Done, not submitted",
  "✓ Turned in", Missing), and has buttons stacked at its right, a glyph and a
  short word each: **✓ Done** / **↶ Not done** (a test: Studied / Not studied),
  then **↑ Turn in** on an open assignment (neo-plan's turn-in; assignments
  only), or **↶ Undo** on a turned-in one (put back: done, not turned in).
- Everything the extension adds sits in its own shadow DOM (as now) and uses the
  overlay's own look (below), not neo-plan's DESIGN.md.
- **Overlay switch.** One on/off setting, kept in `chrome.storage.local` and applied on
  every Schoology page. Toggled by a small switch placed on the page, and by
  Alt+S. Off means every added element is hidden and Schoology looks untouched.

## Look (the overlay's own, themeable)

| Token | Value | Use |
|---|---|---|
| `--accent` | `#1D5EA8` (per theme, see below) | everything the extension adds that isn't a state: links, what-if inputs, projections |
| `--accent-soft` | `#E7EFF8` (per theme) | filled what-if input, a row with a what-if score |
| `--ink` / `--dim` / `--faint` | `#1F2429` / `#5E6670` / `#8A929B` | text |
| `--line` / `--sunk` | `#DCE0E5` / `#F6F7F9` | borders, strips |
| `--bad` / `--bad-soft` | `#B8430F` / `#FBEDE5` | Missing, negative impact |
| `--good` / `--good-soft` | `#23794A` / `#E6F3EC` | Submitted, positive impact |

Type: Instrument Sans for text and numbers; numbers use tabular digits (`font-variant-numeric: tabular-nums`) so columns line up. No monospace in the UI (it reads as a dev tool); JetBrains Mono is bundled only for the archive log.
Bundle both as local `.woff2` (400/500/600), not a Google Fonts link. Titles only (card names, settings and Welcome page headings, the To Do heading) use `--serif`, the system serif (New York on a Mac, like the iOS app's display style; Georgia elsewhere), bold. 8px radius on cards and buttons, 6px on chips and tags (iOS `Theme.Radius`). No shadows.

Class colours (`--class-1` … `--class-8`) are the iOS app's `Theme.classColors`. The home To Do colours a class by its place among the Course Dashboard's classes, as iOS does.

The values above are the non-Dew themes'. The Dew themes set every token to the iOS app's paper-and-ink palette (`Palette.dew`); every other theme changes only `--accent` and `--accent-soft` (`overlays/theme.js`), and a dark one gets its dark look from inverting the whole page (below), not from swapping these tokens.

## Archive button and settings

Beside the Overlay switch (bottom left, shown only while the overlay is on) sit two
square buttons: **Archive**, then a gear.

The Archive button (`overlays/switch.js`) opens `overlays/archive.js`'s card —
dimmed page, inset card, ×/Esc/click-outside closes, closed shadow root via
`createHost`, same pattern as `overlays/launcher.js`. The card is only a UI: the
archive job itself runs in the extension's offscreen document (it needs
`DOMParser`, same as the neo-plan sync's `offscreen/parse.js`; `offscreen/archive.js`
shares that one document, routed by message `target`), owned by `background.js` as
a single job at a time, mutually exclusive with the neo-plan sync. Closing the card
or navigating away from Schoology does not stop the job — reopening the card on any
Schoology page (even a different course, even a different tab) shows the live
status, count and log, pushed over a `chrome.runtime.connect({ name: 'archive-card'
})` port and mirrored in `chrome.storage.session` so a freshly opened card catches
up immediately. On a course page (`/course/(\d+)`) the card knows its `courseId`
from the URL; the course name comes from the page (falling back to `Course
123`). The card's own message contract: `archiveStart {courseId, options}` (the
course's host comes from `sender.url`, not anything the page claims),
`archiveStop`, `archiveState`. Alt+Shift+K / `killEverything()` stops a running
archive job too.

Because the offscreen document only has `chrome.runtime`, everything else
(`chrome.downloads.*`, `chrome.scripting.executeScript`, `chrome.tabs.*`,
`chrome.storage.*`) is proxied to the service worker through
`outputs/archive/chrome-bridge.js`: the same exported functions take a "direct"
branch when called from the service worker and a `chrome.runtime.sendMessage`
proxy branch otherwise, so `background.js`'s `archiveProxy` handler is just an
allowlist (`PROXY_OPS`) of those same functions, accepted only from the offscreen
document's own URL.

Files default to the Downloads folder, as before (no picker). A custom folder
(File System Access handle, kept in IndexedDB — `outputs/archive/folder.js`) can be
chosen from Settings, the Welcome card or the Archive card's "Change". Chrome
refuses the folder picker on a Schoology page and in a frame on one, so each of
those sends `archiveFolderPick` and `background.js` opens `panel/folder.html` in a
small popup window (one at a time): the only Dewey page outside the overlay. It
picks the folder, or re-allows one that lost permission (that needs a click too),
writes `archiveFolderAt` to `chrome.storage.local`, and closes; every open card
listens for that key and redraws. "Use Downloads" is `archiveFolderClear`. The
offscreen document reads the same IndexedDB handle (same extension origin) and
uses it only if `queryPermission({ mode: 'readwrite' })` is already `'granted'` —
there is no user gesture in an offscreen document to call `requestPermission`, so
a folder that has lost permission just falls back to Downloads with one log line,
not a prompt.

### Settings

There is no settings tab: settings live only in the overlay. The gear opens the
Settings card (`overlays/settings.js`), and so does the toolbar icon on a
Schoology tab (`background.js` messages the tab `openSettings`; on any other tab
it opens Schoology). The card shares its shell with the Welcome card
(`overlays/dialog.js`: dimmed page, ×/Esc/click-outside, Tab trap, the same look
as the Archive card). A sidebar picks one page at a time (Appearance, Archive,
neo-plan; arrow keys move between them; tabs along the top below 600px wide),
with **Welcome tour** at its foot. Each page is a title, one line on what it's
for, then grouped rows: title and description on the left, the control on the
right. Only the page scrolls: the card has a fixed height and the neo-plan frame
always grows to fit (`scrolling="no"`, height clamped to 40–4000px).

1. **Appearance** — the theme grid (below) and a "Percent beside scores" switch.
2. **Archive** — one row: Downloads or the chosen folder, a line on what that
   means, and Choose/Change/Allow again plus Use Downloads (above).
3. **neo-plan** — an `<iframe>` of the extension's own `panel/neoplan.html`, not
   inline controls: a Schoology page must never see the neo-plan token. Rows in
   the same style: **Account** (Connect / Connected + Disconnect, and the
   Schoology last-read line with Check now), **Courses** (each section and the
   neo-plan column it files into) and a collapsed **Advanced** (the server, and
   whether Claude's MCP server is connected). The frame is created the first
   time the page shows and then kept. It posts `{ type: 'np-frame-height',
   height }` to the parent whenever its size changes; `dialog.js` accepts that
   only from `e.source === frame.contentWindow` and the extension's own origin.
   The frame is *not* re-inverted under a dark theme (`.npframe { filter: none }`
   outranks the host's plain `iframe` re-invert rule): it wears the overlay's
   tokens and fonts and paints no surface of its own, so it goes dark along with
   the card. `test/neoplan-frame.html` draws it with a fake `chrome`.

`test/settings-card.html` shows both cards outside the extension.

### Welcome

The first time the overlay shows on a Schoology page, `switch.js` opens the
Welcome card (`overlays/intro.js`) once, unless `introSeen` is set in
`chrome.storage.local`. Three steps with dots and Back/Next: what Dewey adds and
where its three buttons are; where archives go (optional, same controls as
Settings); connect neo-plan (optional, the frame with `?view=connect`: the token
only). **Skip setup**, ×, Esc and **Done** all close it and set `introSeen`;
Settings → **Welcome tour** opens it again.

Theme and Grades are one setting object in `chrome.storage.local`
(`ui.js`: `SETTINGS_KEY`, `getSettings`/`onSettings`/`setSetting`), applied on every
open Schoology tab like the Overlay switch itself.

`overlays/theme.js` owns the theme list (`THEMES`, an ordered object of key →
`{ name, dark, header, headerText?, page, accent, accentSoft }`). Every colour is
the colour as seen, dark themes included; the code pre-inverts for dark themes.
`header` is one colour or gradient stops; `page` is the backdrop behind
Schoology's white cards and past the page's edges):

- `dew` — follows the computer's light or dark mode (`prefers-color-scheme`, followed live): it stands for `dewLight` or `dewDark` (`themeOf`). Both carry `tokens`, every overlay colour from the iOS app's `Palette.dew`, as seen.
- `dewLight` — deep teal header (`#0F6E56`), paper page (`#F6F4EE`), teal accent.
- `dewDark` — dark, with a softer, warmer invert than the dark themes below (`invert(1) contrast(0.87) hue-rotate(180deg) sepia(0.05)`): Schoology's white comes out the iOS background (`#121211`) and its black text warm off-white, not `#000` and `#FFF`. Pine header (`#04342C`), the iOS dark palette (accent `#5DCAA5`, shown a little softer: the filter can't reach that saturated a teal). Colours are painted through `softPre()`, and photos get the contrast undone as well as the invert.
- `schoology` (default) — no page changes at all. `header` is `null`; `accent`/
  `accentSoft` are the overlay's original blue.
- `ocean`, `forest`, `plum`, `crimson`, `graphite` — light: a coloured header bar
  (white text and icons), a lightly tinted page, a matching accent.
- `sunset`, `aurora`, `sakura`, `matcha`, `rainbow` — light, with gradient headers
  (rainbow's text gets a shadow to stay readable on its yellow).
- `dark`, `midnight`, `dracula`, `nord`, `synthwave`, `ember` — dark: the whole
  page inverted ("smart invert", below), each with its own header and page tint.

**Page styling.** `themeCss(key)` (pure, testable in node) returns the CSS for one
`<style id="np-theme">` appended to `document.documentElement` — additive, present
only while the overlay is on and the theme isn't `schoology`, removed the instant
either stops being true. It targets Schoology's header with structural selectors,
not its (hashed, versioned) class names. Checked on fuhsd.schoology.com
(2026-09-27): `#header` is the district's colour strip, `#header > header` the
white bar, then `nav > ul > li`, each li holding its `<a>`/`<button>` directly or
inside a `<div>` (Courses, Groups, Apps and the user menu, which have their own
white background). So: `#header, #header > header, #header > header nav` get the
bar colour; `nav > ul > li > :is(a, button)` and `nav > ul > li > div > :is(a,
button)` go transparent with the header text colour and a subtle hover, and their
`svg` icons (drawn in fixed fills, not currentColor) are flattened to that colour
with a filter. Dropdown panels sit deeper and are left alone.

**Dark themes ("smart invert").** Schoology's markup is large and changes often, so
rather than reskin it, dark themes invert the whole page: `html { filter: invert(1)
hue-rotate(180deg) }`. A filter on the root element is exempt from the rule
that a filtered ancestor becomes the containing block for `position: fixed`
(Filter Effects spec), so the switch bar, the launcher and the Archiver card stay
put while the page scrolls — checked in Chromium on `test/theme-page.html`, 3000px
tall, at several scroll positions. Keep the filter on `html`: moving it to `body`
or a wrapper would lose that exemption.
html's background paints the canvas: inside the window it's filtered like the
page, but the rubber-band area past the page's edges isn't, so no single colour is
right for both. html and body get the theme's `page` colour pre-inverted, and dark
themes set `overscroll-behavior: none` so the light bounce never shows. Real media would look like a film negative under that, so
a second rule re-inverts it: `img, video, iframe, canvas, picture, svg image, embed,
object, [style*="background-image"]` — except a blanket `img` would double-invert
one nested in a `picture` (the `picture` selector already re-inverts it once), so
the image part of that selector is `:not(picture) > img`.

A colour we want the *page* to display under that filter — the header, its text —
has to be written pre-inverted, since the filter runs on top of it. `preInvert(hex)`
(pure) computes the exact pre-image by running the same filter math forward: invert
per channel, then the CSS hue-rotate(180deg) matrix from the Filter Effects spec.
The combined operation is its own inverse, so `preInvert` and "apply the filter" are
the same function.

Our own hosts get inverted right along with the page (they're real DOM, just in a
shadow root), which is exactly what makes their light tokens (ink, surface…) come
out dark; `accent`/`accentSoft` are given as seen, so they're pre-inverted for
dark themes like the page's colours (`paint(hex, dark)`). Inverted as-is, white
surfaces come out pure black, harsh against the theme's page, so dark themes also
set `--surface` (the page mixed 7% towards white), `--sunk` (the page) and
`--line` (16% towards white), pre-inverted: our cards sit a step above the page. The settings menu's
swatches, and the grades page's what-if bar (an inline style on Schoology's cell),
are painted the same way.
Every `createHost` root gets a second `<style data-np-theme>` (kept in sync by
`ui.js`, which learns the current theme's CSS from a small hook `theme.js`
registers — no import cycle) holding `:host { --accent; --accent-soft }` and, in
dark themes only, the same media re-invert rule — a page stylesheet can't reach
into a shadow root, so without this an `<img>`/`<iframe>` inside one (the
neo-plan launcher's card) would come out negative.

**Early apply.** A second content script, `overlays/theme-early.js`
(`document_start`, same matches as `boot.js`), dynamic-imports `theme.js` and calls
its `start()` as soon as possible, so a dark theme doesn't flash white before
`boot.js` and the rest of the overlay load at `document_idle`. `start()` waits for
both the Overlay switch (`onOverlay`) and the settings (`onSettings`) before
touching the page.

**Caveats.** The header selectors are written against Schoology Plus's reference
CSS and a hand-built stand-in page (`test/theme-page.html`), not real Schoology
markup — if `#header > header` changes shape, the header colouring quietly does
nothing (additive-by-default: worst case is a plain white/black header, never a
broken page). Smart invert is a blunt instrument: anything Schoology draws as a
CSS gradient, a background-image on a non-`img` element without an inline `style`
attribute, or canvas-drawn UI may invert incorrectly or not re-invert at all; we
accept that trade for not having to track Schoology's markup.

## Grade math

- Category % = points earned / points possible for graded items in that category.
- Course % = Σ(weight × category %) / Σ(weights of categories that have grades).
- **Weights must come from the gradebook page.** Find where Schoology shows
  them (category row text or the page's JSON) and record it in
  `docs/schoology-endpoints.md`. If a course has no weights, fall back to total
  points and let Owen type weights in (saved per course).
- Items with no grade ("—") are left out, not counted as zero.
- Impact of an item = course % with it − course % without it.
- History = replay graded items in due-date order, recomputing after each day.
  What-ifs, planned items and drops (below) are hypothetical or a page-view-only
  decision; history is the real term, so it ignores all three.
- **What-if** (`grademath.js` opts.whatIf) covers ungraded ("—") rows and also
  overrides a graded item's score (and its points, if given).
- **Drop** (opts.drop, an array or `Set` of item ids) leaves an item out of the
  math entirely, as if it weren't in the gradebook: `grade`/`impact`/
  `categoryFloor`/`needFor` all respect it and only `history` doesn't. The
  overlay calls it "Leave out".
- **Items Schoology doesn't count.** A teacher can make an item count for
  nothing (or half, or double). A student's gradebook shows no sign of it: the
  row reads "7 / 7" like any other, and nothing in the row's markup, the page
  settings or the assignment page says so (checked 2026-11-12 on AP Physics 1,
  where Quizzes added up to 98 / 100 but Schoology showed 97.67% = 84 / 86).
  The only trace is the category's own percent, so `periodsFromRows` runs
  `calibrate` on each category: if the sum of its items doesn't give the
  percent Schoology shows, it looks for the fewest graded items (up to 4)
  that, left out, do, and marks them `uncounted` ('sure' when only those items
  fit; 'guess' when others with the same score would fit too, since the totals
  are right either way). All the math skips an uncounted item, what-ifs on it
  included. If no such set exists, or sets with different totals fit, the
  category's earned points are scaled to Schoology's percent (`cat.scale`)
  instead. Either way the category and course percent match Schoology's.

## 1. Grades page (inline in Schoology's table)

The first cut of this had every control always on screen (a dashed input on
every ungraded row, a permanent "+ Plan…" row under each category): too loud.
The second hid them all until a row was hovered: quiet, but nobody could find
them. Now what-if is a **mode**, as in the iOS app (docs/ios/SCREENS.md 2b):
off, the page shows facts only; one visible What-if button turns it on.

Always:

- **Course row:** a small sparkline button ("Hide graph" / "Graph"; open by default) that toggles a row under it
  with a step chart of the replayed history: y axis around the grade range, a
  dashed A-cutoff line, hover crosshair + tooltip (date, %, item that changed
  it), and a dashed projection while what-if mode has changes. Beside it the
  **What-if** button (a pill; filled and reading "Show real" while the mode is
  on, "What-if · N changes" when scores are waiting). Right column: "X.XX
  above A", or "Doesn't match: NN.NN%" when the math and Schoology's course
  percent still disagree.
- **Category rows:** "weight N%" beside the name, only when Schoology shows no
  (N%) itself; right column "A holds down to NN.N%" (lowest this category can
  drop, the others unchanged, and still keep the A).
- **Graded item rows:** a small percent beside Schoology's own score (tabular
  digits, faint; Settings → "Percent beside scores" hides it) and the impact
  tag in the right column. An item Schoology leaves out of the category (Grade
  math, above) says "not counted" there instead ("may not count" for a guess;
  the tooltip says why) and gets no score box.

In what-if mode:

- **Every item row** gets a score box in its grade cell. On a graded row the
  placeholder is the real score and blank keeps it; typing overrides the item,
  the percent beside it and its impact tag follow, and Esc clears it. Beside
  the box, a faint **Leave out** drops the item from the math: Schoology's
  score in that row is struck through and faded and the right column offers
  **Count it**. A "—" row also gets a points box, since it doesn't say what
  it's out of.
- **Category rows:** "→ NN.N%" beside Schoology's percent when it changed, and
  **+ Upcoming test** (or assignment) beside the name, which opens a row of our
  own under the category: name / worth / score / "need X/pts for an A" (or
  "any score keeps an A" / "out of reach with this alone") and Remove.
- **The bar**, pinned to the bottom of the window so it stays in view far down
  a long gradebook: "What-if", "N changes" (or "Type a score on any row"),
  real → what-if letter and percent, the difference, **Reset** (clears every
  score, left-out item and planned score) and **Show real**.
- **Show real** (the bar's button or the pill) switches the mode off without
  clearing anything: every number on the page is real again and the pill reads
  "What-if · N changes". Only one course is in what-if mode at a time on
  /grades/grades.
- **Row marker:** a changed row gets a 3px inset left bar on its first cell,
  only while the overlay is on.
- What-if scores, left-out items and planned items are per-page-view only.
  Never saved.

## 2. Materials page and folders

- A one-line strip above the list: "Only open work" checkbox, "N open in this
  course", and on the right "Archive saved <when> · Sync now".
- **Folder rows**, right side: a short status ("1 missing · 1 to turn in · 1 to do",
  or "All turned in"), then the folder's scored % (points earned / graded points in
  it) in tabular digits. Folders keep opening the Schoology way (their own page).
- **Assignment rows**, right side (one marker, replaces today's ○ ✓ × markers):
  - graded → score `10 / 10`
  - submitted, not graded → "Submitted"
  - neo-plan done, not submitted → filled circle + "Done, not submitted"
  - not done → empty circle + short due date
  - missing → red "Missing" pill + empty circle
  - not in neo-plan → "+ Add to neo-plan"
- "Only open work" hides everything except missing / done-not-submitted / not-done
  rows and folders that contain them. On a Materials root page, that requires reading
  folder contents (reuse the reader's folder parsing, cached).

## 3. Assignment page

- **Chip** (one line, under Schoology's due date), replacing the current chip:
  - not submitted: class dot + name · Missing (if missing) · "A zero costs −N.NN" ·
    "Remove from neo-plan". No Turn in button.
  - after a Schoology submit: "Submitted" (green) · "Cleared in neo-plan" · "Late by N days" if late.
  - graded: "Graded" · "Moved your course grade ±N.NN" · "Nth lowest of M in <category>".
  - not in neo-plan: "Add to neo-plan"; removed: "Removed · Add back" (as now).
- **Sidebar**, under Schoology's Grade box, only when ungraded: "What if I get [ ] / pts" →
  "Course grade NN.NN% ±delta".
- No "Also in <folder name>" list below the instructions (removed 2026-09-27:
  Owen found it cluttered the page without adding much).
