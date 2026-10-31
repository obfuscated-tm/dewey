# Paper and ink (the 2026-10-31 refresh)

Owen's decisions from the design canvas (artifact "Dewey refresh", boards To Do, Grades, Inbox, Assignment, before / after light / after dark). The goal: stop looking like a generic AI-made app (mint everywhere, capsules, tinted pills) and look like a library card catalog instead. The canvas source is the `.dc.html` drafts; this file is what was decided.

## Whole app

- **Palette:** warm paper (`#F6F4EE`, cards `#FFFDF8`) and near-black ink (`#1B1A17`); dark mode is warm charcoal (`#121211`, cards `#1B1A18`), not green-black. Values live in `Palette.dew` (Theme.swift).
- **Teal is for the main action only** (Submit on Schoology, a screen's one primary button). Links, toggles and the selected tab are ink (`.tint(Theme.text)`).
- **Corners:** cards 8 pt, buttons and tags 6 pt. No capsules for actions.
- **Red rule** (`Theme.catalogRule`) under a card's heading, like an index card. Pale blue ruled lines between rows.
- **Keep the containers.** Sections stay as cards; the refresh changes their look, not how much is on screen.
- **Globe = Schoology.** Anywhere that opens Schoology uses the globe symbol, so it reads the same everywhere.
- Tabs stay To Do, Grades, Inbox, Classes, Schoology.

## To Do

- Title is the To Do / Plan switch: the screen you're on in the big serif with a red rule under it; the other beside it, smaller (title2 serif) and grey, on the same baseline. Tap to swap. No tinted chip.
- New task: a floating button above the tab bar with the word "New task" and a plus, in ink (not a bare teal circle), so people can see how to add a task.
- The one-tap action stays at the right end of the due line, as before (the title keeps the full width).
- Turn in and Submit must never look alike: **Turn in** is outlined ink with a check (stays in Dewey: paper or in-person work); **Submit** is filled teal with the globe and ↗ (opens Schoology's dropbox). `PillButton.turnIn` / `.submit`.
- The type bar (HW / TEST) fills the row's height under the checkbox.

## Grades

- Keep the recent-grades list at the top (what's new is the most useful part).
- Kicker: the grading period exactly as Schoology names it ("26-27 T1"), never reformatted.
- Report card rows: the class's Dewey number as a vertical spine label with a stripe in the class colour (saves width); class name; second line = period (only if found) · the latest graded item and its score; on the right the percentage with the latest change under it (green up, late-orange down), then the letter with the base letter and the +/− in fixed columns so letters line up.
- No "X over / under" line on the list (it belongs on the class page).
- Period: `Course.periodLabel` is "P3" when the section title says P3 / Per 3 / Period 3, else empty, and an empty period is simply left out.

## Inbox

- Each section is a card again (New from school, From teachers, New in your classes) with its title, count and a collapse chevron, red rule under the heading.
- An "Earlier messages" card at the bottom opens past (read) messages.

## Assignment

- Keep the due stamp and the date card. Status as one To do / Done / Turned in control. The only teal on the screen is "Submit on Schoology".
