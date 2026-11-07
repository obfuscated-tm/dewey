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
1. **Stamps + late-stays-red:** one stamp per class card (LATE red / DUE TODAY ink, most urgent wins, straight); heading rule red only when the class has late work; restore Assignment `DueStamp` exactly as before (main 307e459 has the code); done-but-not-turned-in late work stays red (TodoLogic.showsLate / TodoRow.dueText). Possibly faint warm ruled lines on cards. Update SCREENS.md rules 2/8 to allow these on purpose.
2. **Check a possible date bug:** To Do showed HW 4.3 due Thu Nov 5 while its Assignment screen said Fri Nov 6 (demo data or a real timezone/date bug). Also the Assignment card said "Checking…" twice (place column + status label).
3. **New-task sheet (QuickTask.swift):** problems: two rows of controls in two styles doing the same jobs; you can't see what was parsed; equal-width chips truncate ("Chemis…"); colour means "picked" or "guessed"; fixed 158 pt sheet. Plan: field → grey preview line ("Chem lab · Fri 3:30 pm · Chemistry") → one row of content-sized pills (When = day+time with Today/Tomorrow/After school/Tonight/Pick…, Class, Kind) → ↑ add. Teal = set. Rewrite the tour step that mentions "the row under them". Risk: Tomorrow becomes two taps.
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
