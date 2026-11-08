# iOS design review, Nov 2026 (full notes)

Everything from the design conversation of 2026-11-06 → 07, so a fresh session can pick it up. Branch: `ios-calm-design` (worktree `.claude/worktrees/ios-calm-design`). The Design rules that came out of it live in [SCREENS.md](SCREENS.md) → "Design rules".

## 1. Where it started
- Owen: the iOS app "feels too complex for the average person… clunky like an AI app"; the website lacks "oomph" (colour grading, fun web stuff); the extension hasn't been updated in a while.
- First plan given: (1) simplify iOS, (2) site motion + colour pass, (3) release extension 1.15 then the catch-up "yes" items. Extension facts: 1.14.0 shipped Nov 1 (Dew brand); Done/Studied/Turn in without neo-plan committed Nov 3 but not released.
- Owen: he has grown with the app and wants to keep most features; the problem is it doesn't feel clean.

## 2. Diagnosis (why it felt like an AI app)
Measured in code on main (307e459): ~33 `.sheet`, 25 `.toolbar`, 35 coach targets, 67 distinct SF Symbols, 38 long explanatory strings, 19 `tracking()` (spaced caps), 32 `.card()`, red used both for late (29 uses) and as decoration (8 `catalogRule`), To Do used 9 text styles plus a fixed 10 pt label (below Apple's 11 pt minimum).
Root cause: **AI-assisted building only adds** (a line, label, colour, setting per prompt) and nothing gets removed.
Specific tells found: spaced all-caps kickers, everything in cards, a chip/badge for every state, icons beside labels, too many colours with jobs, red rules making everything look urgent, captions explaining the UI, " · " joined fact strings, a big tour + coach marks, settings instead of decisions, hidden custom gestures, 5 ways to mark done, springy animation everywhere, screens designed in isolation (To Do custom header vs Classes native title), rare edge cases always on screen, coloured band on one side of a rounded card.

## 3. Research (sources)
- Apple HIG Motion: motion purposeful; avoid motion on frequent interactions; brief feedback; don't make people wait; respect Reduce Motion.
- Apple HIG Color: limited palette; "avoid using the same color to mean different things"; don't rely on colour alone.
- Apple HIG Typography: few typefaces; 11 pt minimum; use built-in text styles (Dynamic Type); New York serif + SF is fine.
- Apple HIG Sheets: brief tasks; grabber for resizable sheets; Cancel leading / Done trailing.
- NN/g Animation duration: ~100 ms simple feedback, 200–300 ms view changes, exits shorter; "far more common for animations to be too long than too short"; ease-out entrances.
- NN/g Minimalist design: remove what doesn't support tasks; not the same as visual minimalism; dense can be fine if everything is needed.
- NN/g Visual hierarchy: ~3 sizes, ≤2 largest elements, red for warnings, "if everything is contrasted, then nothing stands out", all caps flattens hierarchy, containers only when spacing isn't enough, squint test.
- NN/g Progressive disclosure: ≤2 levels; several routes to the same option rarely help.
- Emil Kowalski "You don't need animations": frequency decides; UI animation under 300 ms.
- "AI slop" community lists (weak evidence): colored side border on rounded cards, beige + serif/sans mix, badges everywhere. Fix: write exact design rules into the project.
- Owen's reel ("20 ways to make beautiful AI animations"): mostly not needed for Dewey. Dewey's motion was already mostly fine (.snappy 0.18–0.25 s, Reduce Motion handled). Useful bits: press scale 0.97, no bounce on everyday UI, rubber band past bounds, exits shorter, zoom into detail (iOS 18). Rejected: staggering To Do rows (seen daily).
- Quick-add research: Apple Reminders (one quick toolbar above keyboard; date menu has Today/Tomorrow/Weekend/Custom; NL date suggestion in QuickType bar), Todoist (recognized words highlighted in the field, tap to un-highlight; one row of property buttons), Things 3 (plain entry; details in their own pop-ups).

## 4. What was built on `ios-calm-design` (commit ab807f7, builds, 50/50 unit tests pass, checked in simulator with `-DeweyDemo`)
- Theme: `catalogRule` = ink (was red); `catalogLine` = divider (was pale blue); `ribbon` font = caption2 (was fixed 10 pt); TypeBar uses it with @ScaledMetric; `PressStyle` (0.97 scale + dim, 100 ms, Reduce Motion aware) on PillButton/WideButton; Submit pill outlined, no globe.
- ScreenHeader kicker: sentence case, subhead (was spaced caps).
- To Do: no done-today ripple ring; class card heading = class-colour dot + name over a thin divider (no period, no "N open", no DUE TODAY stamp); call numbers behind `CallNumber.showKey` ("showCallNumbers", default off) with a toggle in Settings → To Do → Call numbers; status line only for Submitted / Submitted late / Turned in / Google Assignment / task notes; test due line drops "not studied"; Submit/Turn in button only when done, late, or due by end of tomorrow; Undo button removed (circle does it); swipe-right Done removed; status list removed from hold menu; "Older" header drops "over N days late"; month pull uses .snappy and a rubber band; mode switch .snappy.
- Tour text updated (no ripple / hold-to-pick-status).
- Assignment: catalog line sentence case "Class · Category"; no stamp, no class band, no DATE DUE/TURN IN headers, no mascot; due line shows "· N days late" in red; "Checked…" only if > 1 h old; trimmed captions (kept "Schoology doesn't tell Dewey if it's turned in" for Google Assignments); status = native segmented Picker; "Grade"/"Type" labels sentence case; bottom button no globe. `DueStamp` struct deleted.
- Grades: class % to 2 decimals on Report card (matches class screen); no period on rows; call number on spine only when shown. Graph caption "Drag across…" removed; what-if rows don't repeat "Real x/y"; what-if bar "What-if" sentence case, no "preview only"; GradeDetail headings sentence case.
- Wide sidebar "Classes", NeoPlan headings sentence case.
- SCREENS.md: Design rules section (11 rules) + updated 1a/1b/2a text; CLAUDE.md points to the rules.
- Not done: Android port, app-wide container reduction, Settings regrouping, Quick task chips, tour not re-tested. Plan numbers kept on purpose.
- Not pushed to main. Push command when approved: `git push origin ios-calm-design:main`.

## 5. Owen's pushback and decisions
- Sideways type bar: keep it (he finds it useful). My held view: only the black TEST block is scanned; grey HW/CW/TASK text adds little. His call: keep.
- Decimals: keep precision (I conceded: 89.46 vs 89.54 decides a letter; the real bug was inconsistency).
- Wants blunt pushback ("be mean"); hold positions unless he brings new facts (memory: push-back-bluntly). Self-audit: I flipped twice without new facts (type bar, Assignment stamp).
- After seeing the build: cards feel empty/basic; misses the stamps and the red bar; app lost its Dewey branding.
- Q&A answers (2026-11-07):
  - Adds tasks rarely; uses both typing and tapping; no home for "random stuff".
  - Random stuff = bring-to-school items, personal/non-school, quick notes. Decided scope: **school + bring-to-school only**; bring items are **ordinary tasks**, no new feature. Personal/notes stay in Reminders/Notes.
  - Own tasks placement: unsure.
  - Scans To Do **by class card** (Week view).
  - Card stamps: **LATE + DUE TODAY only**, at most one per card (no TMRW). Red rule under the heading only when the class has late work.
  - Assignment stamp: **exactly like before** (big, tilted, overlapping the card top). (I'd have made it smaller; his call.)
  - Done-but-late work: **stays red until turned in**.
  - Call numbers: **off by default**.
  - Audience: just Owen now, but design arguments are decided for **future strangers**.
  - First thing wanted on open: what's due today / late.
  - Uses To Do and Grades daily; **keep all five tabs** anyway.
  - **Do first: stamps + late-stays-red.** Then the new-task sheet.

## 6. Next work, in order
1. ✓ (built 2026-11-07, §11) **Stamps + late-stays-red:** one stamp per class card (LATE red / DUE TODAY ink, most urgent wins, straight); heading rule red only when the class has late work; restore Assignment `DueStamp` exactly as before (main 307e459 has the code); done-but-not-turned-in late work stays red (TodoLogic.showsLate / TodoRow.dueText). Possibly faint warm ruled lines on cards. Update SCREENS.md rules 2/8 to allow these on purpose.
2. ✓ (2026-11-07, §11: demo only) **Check a possible date bug:** To Do showed HW 4.3 due Thu Nov 5 while its Assignment screen said Fri Nov 6 (demo data or a real timezone/date bug). Also the Assignment card said "Checking…" twice (place column + status label).
3. ✓ **New-task sheet (QuickTask.swift):** problems: two rows of controls in two styles doing the same jobs; you can't see what was parsed; equal-width chips truncate ("Chemis…"); colour means "picked" or "guessed"; fixed 158 pt sheet. Plan: field → grey preview line ("Chem lab · Fri 3:30 pm · Chemistry") → one row of content-sized pills (When = day+time with Today/Tomorrow/After school/Tonight/Pick…, Class, Kind) → ↑ add. Teal = set. Rewrite the tour step that mentions "the row under them". Risk: Tomorrow becomes two taps.
4. Later ideas not decided: website motion/colour pass; extension 1.15 release + catch-up items; Android port of this pass; Settings regrouping.

## 7. The full 46-item cleanup list (given before building; ✓ = done on the branch, ↩ = reversed by Owen)
App-wide: 1 one loud thing per screen ✓ · 2 cut explanation text ✓ (main screens) · 3 cut " · " joined facts ✓ (mostly) · 4 library theme in 2 places not 10 ✓ then partly ↩ (stamps return) · 5 cut icons ~67→25 (partly: globe) · 6 one button style ✓ (outlined pills) · 7 two corner sizes (not done) · 8 cut shadows/tints (not done).
To Do: 9 call numbers off ✓ · 10 cut ripple ring ✓ · 11 cut date kicker (kept, sentence case) · 12 To Do/Plan switch as a segmented control (not done) · 13 type bar grey labels plain (Owen: keep) · 14 cut status lines ✓ · 15 cut "not studied" ✓ · 16 row action only when due soon ✓ · 17 cut "P1 · 1 open" ✓ · 18 cut DUE TODAY stamps ✓ then ↩ (LATE/DUE TODAY one per card) · 19 Older header ✓.
Plan: 20 cut numbers (kept on purpose) · 21 quieter class name (not done) · 22 no filled Submit ✓.
Assignment: 23 stamp ✓ then ↩ (back exactly as before) · 24 table headers ✓ · 25 mascot ✓ · 26 "Checked just now" ✓ · 27 segmented status ✓ · 28 "Only you see this" ✓.
Grades: 29 spine call numbers ✓ · 30 move period ✓ (last score kept) · 31 delta only when new (not done; Owen likes the numbers) · 32 new-dot vs delta (not done).
Class grades: 33 "94.72%"→1 decimal ↩ (Owen: keep 2 decimals everywhere ✓) · 34 "Unit 2 Test" label (not done) · 35 graph caption ✓ · 36 "A holds down to" (kept) · 37 "30% of grade" (not done).
What-if: 38 "Real 20/20" ✓ · 39 ⋯ column → long-press (not done) · 40 header simplify (partly).
Shelf: 41 keep ✓ · 42 one ribbon style (not done; font fixed).
Quick task: 43 chip icons · 44 two chip rows → see §6.3.
Tabs/Settings: 45 Schoology tab behind Classes (Owen: keep all five) · 46 regroup Settings (not done).
"If you only do five": 2, 9, 14, 16, 23.

## 8. Vibecoding advice given (keep using)
- Habit: after every feature, a pass whose only job is deleting. One in, one out.
- Checks: **squint test**; **side-by-side** with Apple Reminders/Calendar; **10-second friend test** ("what is this for?" per element); **prompt with limits** ("no new colours, no new explanatory text, no new settings, remove something if you add something").
- Keep-vs-cut test for any element: do you act on it? is it said once? is it consistent across screens? is the personality concentrated (shelf, serif letters) or smeared everywhere?
- Dense-on-purpose is fine (Stocks, scoreboards); dense-by-accident reads as AI.
- Reel's motion list mapped: missing #4 press scale (added), #20 zoom into detail (iOS 18, not done), #18 symbol crossfade on the checkbox (not done), #19 rubber band (added); wrong #13 bounce (fixed), #15 exits shorter; already good #8 ripple, #10 month grid from 0.94, #14 pull follows finger; skip #5, #7.

## 9. Devil's-advocate format (Owen shared a post, 2026-11-07)
For a new idea: (1) viability 1–10 with one line why, (2) the 3 strongest reasons it fails, (3) what facts would prove me wrong / what I don't know, (4) go / fix / drop plus the first thing to test. Hold position without new facts; update and say what changed with new facts. (The post's "18% vs 9%" Anthropic stat was not verified.)
Applied to "bring stamps back": 7/10; risks: stamps spread, TMRW noise on busy weeks, tilt gimmicky on many cards; verdict fix-then-go (LATE + DUE TODAY only, straight on cards, tilt only on Assignment); test: 3 school days, do you read a stamp before the rows?
Applied to the new-task sheet: 8/10, fix; test: add 5 real tasks, count parse corrections.
Owen also likes being interrogated with multiple-choice questions (AskUserQuestion) to critique ideas.

## 10. Website and extension notes (from the first answer)
- Site (`site/src`): only 7 transition/animation rules; ideas: hero gradient/glow + grain, ease-out scroll entrances with 30 ms stagger, press scale, phone frame cycling the `img/app/` screenshots, no bounce.
- Extension: release what's done as 1.15, then the "yes" items in memory extension-catch-up-plan (what-if, new grades badge, goals, notifications, own tasks, rubric view, hide a class).

## 11. Built 2026-11-07: stamps + late-stays-red, the date check
- **Late stays red until handed in.** `TodoLogic.showsLate` no longer lets Done clear late; only submitted or marked turned in does (your own tasks: done still clears, nothing to hand in). One `TodoLogic.showsLate(_ item:)` replaces the three copies of the long call. `AppModel.isPending` (not finished, or late) keeps done-but-late work with the late work: To Do's Late filter and week-strip count, Urgent first's Late section, sort order. The tab badge already used `isOverdue`, so it now counts these too.
- **Card stamps** (Week view): `TodoLogic.cardStamp` (pure, `testCardStamp`) picks one: LATE if any row is late, else DUE TODAY if owed work is due later today; nothing for tomorrow. `CardStampView`: outlined caps, caption heavy, straight, at the heading's right end (lines up down the list, so it scans as a column). Red rule under the heading only with late work, else the divider. Urgent first shows no stamps (its sections already say Late / Today), but its Late group gets the red rule.
- **Assignment stamp:** `DueStamp` and `stamp(_:due:)` copied back unchanged from main 307e459, same placement (offset -4, -44 over the card's top right; inside the card at accessibility sizes) and the 36 pt top padding. Not restored: the class band, DATE DUE / TURN IN headers, mascot. The due line went back to plain ink: with the stamp saying "1 DAY LATE", the red "· 1 day late" after the date said it twice.
- **"Checking…" twice:** the place column is left out while the status is unknown; only "Checking Schoology…" shows.
- **HW 4.3 Thu Nov 5 vs Fri Nov 6: demo only, not a real bug.** Demo dates were relative to `Date()` on every call; the review session crossed midnight, so To Do (loaded on the 6th) and the Assignment (opened on the 7th) were a day apart. Fix: `Demo.today` fixed at launch. The real app can't do this: Schoology's assignment page gives its due date as words (`reader/parse/assignment.js`), `Fmt.date` (ISO) returns nil, and 1b falls back to To Do's own date.
- SCREENS.md: rules 2 and 8 allow the card rule and the stamps on purpose; 1a, 1b and 3b's "Late" updated; stamps added to the brand-character list.
- Checked: build, 51/51 unit tests, simulator screenshots with `-DeweyDemo` (To Do: Algebra 2 LATE + red rule with HW 4.3 done and still "1 day late", AP Biology DUE TODAY; Assignment: HW 4.3 "DUE NOV 6 / 1 DAY LATE", both screens Fri Nov 6).
- Left alone on purpose: **Plan** still files done-but-late work under Done (Plan is your own order, and Done there means you did it); **widgets** still leave out every done row, so their late count can be one lower than the tab badge. Say if either should follow the new rule.

## 12. Assignment page rework (2026-11-07)
- **Owen:** make the page cleaner by merging statuses and giving the instructions more weight; they were sandwiched between cards. Also wanted a wow factor.
- **Research:** Google Classroom (instructions are the page, "Your work" at the bottom), Canvas Student (status + one big Submit; "Try Again" after submitting confused students), Things 3 (neutral page, colour only for meaning), Flighty (wow comes from live, exact data, not decoration), iOS 18 zoom transition.
- **Owen's picks:** status → the bottom bar circle; Type and What-if → ⋯ menu; wow → stamp slam, live countdown, class-colour header wash, plus attachment thumbnails and the ripple on the circle. Zoom: he asked how it looks, so it's built as its own commit with a recording; one revert drops it.
- **My pushback, recorded:** the wash is the weakest pick (warm class colours vs "red means late"), so it's 7% and should be dropped if it muddies a LATE stamp on the phone. The countdown ticks per minute, never per second. Three moments is the ceiling; more undoes the cleanup. Hiding What-if in ⋯ costs discoverability before tests.
- **Built:** one header card (stamp, due + countdown, one merged status line, grade row), the old "where it's turned in" column removed (the status line says it), instructions unboxed at body size, `AttachmentCard` with QuickLook thumbnails (first 4 files; demo mode makes a one-page PDF so they show), bottom bar = circle + main action (paper work's main action is now Mark turned in), ⋯ menu (Open in Schoology, Type, What-if, Lock Screen countdown), `SlammingStamp`, zoom from To Do rows.
- **Bug found while recording:** the page said "Checking Schoology…" until its own status check answered, though the saved row already knew (no dropbox). It now starts from the saved status.
- **Checked:** build, 51/51 unit tests, a recording driven by a throwaway UI test (zoom open, Mark turned in → MARKED TURNED IN slam, badge 2 → 1, back, Lab 2.2 with "in 11 h 56 min"). The simulator tool's taps don't register on this machine, so UI tests drive recordings.
- **Not done / to check on the phone:** how the slam and heavy haptic feel in the hand; the wash against every class colour in light mode; thumbnails on real Schoology files (they download each file when the page opens, at most 4).
- **Wash dropped (same day):** in light mode the Spanish header (raspberry wash + red stamp + red status) read as one late block, as predicted. Owen said "build until you're done" without deciding, so I took my recommendation as its own commit; revert it to bring the wash back.

## 13. New-task sheet (2026-11-07)

Done per §6 step 3. Field, then a grey line with what ↑ will add, then one row of content-sized pills: When (day and time in one menu), Class, Kind. Teal only for what you picked on a pill; parsed values live in the grey line, so teal no longer means "picked or guessed". The sheet measures itself instead of a fixed 158 pt. The tour step now points at the grey line and the pills.

Trade-off kept on purpose: Tomorrow / After school / Tonight are now two taps (open When, pick) instead of one. Typing "tmrw" is still zero taps, and the old second row was the thing that made two rows of controls do the same jobs. If it hurts in use, the fix is a Tomorrow pill that appears only while When is unset, not a second row.

## 14. To Do: the old catalog card back (2026-11-07)

Owen, on the real app: To Do "seems very basic and AI… doesn't provoke Dewey, unlike the past version which, although a bit messy, felt more homey". Compared side by side (main 307e459 vs the branch, real data, light mode; the first look was in dark mode by mistake). Restored: call numbers on by default, "P1 · N open", the heading rule (now in the class colour, red only with late work, so red still means late), pale blue index-card row lines everywhere they were, small-caps date, title underline (accent, not red), Submit filled with the globe, Turn in / Undo on every row, the status lines. Kept from the calm pass: stamps, late-stays-red, zoom, new-task sheet. Lesson: the calm pass removed character along with clutter; the catalog card was character.

Also tried and scrapped the same day (mockups only, never built): web-trend Grades (bento, glass, big type, grain) and library details (date-due slip, Inbox NEW tabs, library card in Settings). Owen: generic, too loud, then "worse".


## 15. Assignment: the checkout card back (2026-11-07)

Owen: the Assignment page "has no hierarchy, looks bland / empty card". Side by side with main (307e459), the calm header was a run of same-weight lines with nothing labelled. He asked for almost everything back, smaller, keeping the PDF preview and the bottom bar (no "Your status" card). Built: class-colour band with the stamp on it, small-caps catalog line, DATE DUE / TURN IN slip, status with icon and its why, the worried mascot on overdue work, the "Instructions" heading. All inside one card, so the stamp's "forehead" stays small. The catalog line now stops at the stamp's measured width (a fixed gap let SPAN 2 · … · TAREAS run under a 2 DAYS LATE stamp). "Outside Schoology" became "Off Schoology" to fit the column; the status says "not turned in yet" and the note says how, instead of both saying "hand it in".

Type: the menu on the HW tag (chevron, inline picker) was "clunky and hidden". Replaced by all four type tags in a row under the title, the current one solid ink; one tap switches. Checked with a throwaway UI test: tapping TEST selects it, and the stamp goes from 2 DAYS LATE to DUE (tests aren't late).

Then the loose tags didn't read as changeable either. Now: a TYPE label + the system segmented control (Owen picked it from four options); a segmented track is iOS's standard "pick one" sign.

Then: no TYPE label, and the picked segment coloured like its tag (Owen). The system control can't colour one segment, so `TypeTags` is a custom outlined track with a sliding fill: soft grey for HW/CW/TASK, ink for TEST.

## 16. Bringing back what the calm pass hid (2026-11-07)

Owen reviewed a list of everything removed or changed since main and asked for:
- Assignment: What-if and the Lock Screen countdown back on the page, under the card (they had moved into ⋯). The countdown shows "Countdown from <time>" dimmed before its 12-hour window, instead of being invisible. The "Only you see your circle…" line is back.
- New-task sheet: separate Day and Time pills, the one-tap Today / Tomorrow / After school / Tonight row back, and class chips (one tap, class colour) in place of the Class pill. He also asked to use or remove the empty band over the keyboard: it's iOS's spacing between a sheet and the keyboard (a smaller detent, removing the home-indicator inset and ignoring the bottom safe area all left it unchanged), so the class row uses the sheet's height instead.
- Grades graph: a tiny "Drag for any day" hint in the card's corner.
Design rule 6 now allows one tiny line that stops a wrong guess.
