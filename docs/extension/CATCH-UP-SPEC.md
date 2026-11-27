# Extension catch-up: one app, not separate add-ons

Settled with Owen on 2026-11-17, after a stress-test of "the extension's features feel disconnected compared to the iOS app". This replaces the 2026-11-01 list where they disagree. [OVERLAY-UI.md](OVERLAY-UI.md) stays the spec for how each piece looks and sits on the page; this file is the plan and the decisions behind it.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Structure | Stay an add-on on Schoology's pages; no separate Dewey page | Owen likes the sidebar and Schoology's look |
| What ties it together | The **To Do sidebar is the spine**: same right-column spot on home, course pages and assignment pages | Every page gets one Dewey anchor instead of scattered pieces |
| Spec | Reconcile one rule at a time; write each choice into both `docs/ios/SCREENS.md` and `OVERLAY-UI.md` | Some iOS parts are better, some extension parts are |
| Late words | "N days late" in red; the Missing pill only where Schoology itself says missing | Plain words; red means late |
| Done but not turned in, past due | Stays red until handed in | It's still owed |
| Sidebar class headings | LATE / DUE TODAY stamp at the right (one per class, most urgent) plus a rule in the class colour, red when the class has late work; no call numbers, no "P1 · N open" | Some character without crowding a narrow column |
| Due line | Full date and time, plus "5 h left" in the last 12 hours of work still owed, and "in N days" on tests | Matches iOS without losing the exact date |
| Assignment page | Status can be marked there, styled like a sidebar row (✓ Done / ↑ Turn in, glyph + word); the due stamp is the one strong detail. Dropbox work still turns in only with Schoology's Submit | The old "Submit only" rule fails for paper, lab sites, Done and Studied |
| Assignment block placement | **Right column** (2026-11-18): a card at the top of Schoology's right column, above its Submit; mockups of both in [design/assignment-block.html](design/assignment-block.html) | Owen left it to Claude: beside Submit, one Dewey column, instructions not pushed down |
| Views | A Plan chip by the To Do title (List ↔ Plan); By class vs Urgent first is set once in Settings | The sidebar never changes layout unless you switch it |
| Search all materials | A box at the top of the sidebar; results over the sidebar, Esc goes back | It's on every page the sidebar is on |
| Search all materials, details | Every class at once; on a course page the box sits above the Materials list (not in the sidebar), only that class's results show, the rest behind one "N more in other classes" row (shown straight away when the class has no match), and the busy line names it. Results grouped by class, two lines each (name, then where it is); each word must start a word ("lab" never finds "Syllabus"); "/" focuses the box; mockup in [design/search-archive.html](design/search-archive.html) | One box for everything, and the class you're in is usually the one you want. Fast: answered from an index read ahead (each class again after 30 minutes), never fetched while you type |
| Archive card | **The shelf** (2026-11-27, draft A in [design/search-archive.html](design/search-archive.html)): each archived class is an upright book in its class colour, taller with more in it; the page's class is pulled out and shown below the shelf ("N items · archived 3 weeks ago" plus a date stamp); each spine carries its last-archived date and fades toward the paper with age, never red; classes not archived yet are dashed outlines; a running archive fills its book; tapping any spine picks that class to archive, from any page. Replaces the "Archived courses" list | Owen's library idea from 11-01; picked over the flat stack |
| Customization | One on/off switch per add-on in Settings (To Do sidebar, grade impact, Materials markers, what-if, …) | Strangers keep only what they want |
| Kept from 11-01 | Plan, Urgent first, search all materials, library-style Archive card | |
| Dropped from 11-01 | Own tasks, Inbox, rubric view, Chrome notifications | |

## Out of scope

More themes (the 17 stay), widgets, Student ID, bell schedule, a separate Dewey page, a per-class turn-in setting, a late count on the toolbar icon, sidebar length.

## Risks

- "Punchy" is how the 11-07 mockups became "too loud": keep it to the stamp.
- Plan and Urgent first are the view changes Owen worried would disrupt the sidebar's flow: check them live, not only in mockups.
- Reconciling rule by rule only holds if each choice lands in both specs.

## Order

1. Sidebar rules: late words, red until turned in, heading stamps and rule, countdown.
2. Assignment page: mockups of both placements → build the one picked.
3. Add-on switches.
4. Urgent first, then Plan.
5. Search all materials.
6. Library-style Archive card.
