# Tutorial update: plan and brief

> **Built 2026-10-22.** Decisions: a mid-length script (37 steps; tap-a-day,
> fold, edit-your-task and the Inbox read note are lines in other steps or on
> the Plan card), Student ID after the practice on the real account, Calendar
> offered (Turn on / Not now), To Do view mentioned. SCREENS.md (Tour) now
> describes what shipped; this file is the record of how it was planned.

A brief for updating Dewey's iOS tutorial (the welcome cards and the practice
week) to cover the features added on branch `plan-tasks-id`, and to have the
student **set the settings that matter most while they're in the tutorial**,
not leave them for later. Self-contained: paste it into a new chat as the task.

Read first: `CLAUDE.md`, then `docs/ios/IOS-APP.md` (rules), `docs/ios/SCREENS.md`
(every screen, including 1a′ Plan and 1d tasks). Brand rules: `brand/README.md`.

---

## 1. How the tutorial works today

| Part | File | What it is |
|---|---|---|
| Welcome cards | `ios/App/Screens/Tour/TourView.swift` | `mode == .tour`: Welcome, then neo-plan. `mode == .reference` (Settings › Help): Places, Marking, Turning in, Grades, Settings cards. |
| Practice week | `ios/App/Screens/Tour/PracticeView.swift` (steps at ~line 50–126) | A pretend week (`AppModel(practice: true)`, in-memory store, demo engine). Dewey's coach bubble spotlights one target per step. |
| Coach | `ios/App/Screens/Tour/Coach.swift` | `Coach.Step`: `target` (a `.coachTarget("key")` on the view), `text`, `hint` (.tap/.swipeLeft/.none), `button` (bubble button moves on), `done` (closure on `AppModel`: moves on when true), `wide`, `free` (no dimming, every tap goes through, for menus/back buttons), `tab`, `rewind` (for "Take me there"). `Coach.note(flag)` records things the model doesn't hold. |

Current practice steps (24): intro → tick Bio lab → Submit → pretend Schoology →
Done → green seal → paper Turn in → test Studied → "That's To Do!" → Grades tab →
Chemistry → what-if → type 20 → preview note → Classes tab → take English off the
shelf → put it back → gear → back arrow → Appearance → Notifications → end.

**Nothing from `plan-tasks-id` is in the tutorial yet.**

### Facts that constrain what can be done *in* the practice

- **`@AppStorage` settings are real.** The practice shares UserDefaults with the
  real app, so a toggle flipped during the tutorial sticks. Good: that's how the
  student sets things up during the tour. Appearance already works this way.
- **Classes are pretend.** Anything keyed to a real `sectionId` (call numbers,
  hiding classes in Grades, the shelf) set during practice applies to pretend
  classes and is useless afterwards. Teach where it is; don't have them set it.
- **Calendar sync is off in practice** (`CalendarSync.schedule` guards
  `!m.practice`). Turning the toggle on still asks for Calendar access and saves
  the choice, which takes effect on the real week.
- **Student ID: "Fill in from Schoology" in practice returns the demo number
  (`Demo.swift`, `"account"` → 1234567).** Never let the tutorial save that.
  Either type/scan only in the tutorial, or run the ID step after the practice
  ends, against the real engine.
- **Notification permission:** flipping a reminder on asks iOS
  (`ReminderSettings.askIfOn` / `Reminders.requestAuthorization`). The tutorial
  should explain *before* the system prompt why Dewey asks.
- Plan state (`planSection`, `planRank`, `planKeep`) and own tasks in practice
  live in the in-memory store: safe to play with, gone afterwards.

---

## 2. New features to teach (from `plan-tasks-id`)

Priority: **Must** = a tap-along coach step; **Should** = a step or a one-line
tip; **Mention** = a line on a card.

| # | Feature | Where | Priority | Notes |
|---|---|---|---|---|
| F1 | **List ↔ Plan switch**: the chip beside the title ("Plan" / "To Do") | To Do header | Must | Plan = your own order for the day. |
| F2 | **Plan's three sections**: To do (numbered), Later, Done | Plan | Must | Work starts where its due date puts it: late, today, tomorrow, tests within 3 days → To do; rest → Later. |
| F3 | **Drag by the dots (grip)**: instant, no hold; across headings into Later/Done | Plan rows | Must | Into Done = marks it done; out of Done = to do again. |
| F4 | **Hold at the screen edge** to keep moving while the list scrolls | Plan | Must (as a tip inside F3) | Easy to miss. |
| F5 | **Swipes in Plan**: left = Later ⇄ To do, right = Done | Plan rows | Should | |
| F6 | **Fold Later / Done** by tapping the heading; folded sections still take drops | Plan | Should | Done starts folded. |
| F7 | **Finished work stays in its row** while Plan is open | Plan | Mention | Reassures that Submit doesn't make it vanish. |
| F8 | **New task (+)**: round button bottom right | To Do (List and Plan) | Must | |
| F9 | **Type it**: "chem lab fri after school" fills day, time, class, kind | New task | Must | Dates/times leave the title; class/kind words stay. |
| F10 | **The four buttons** (day → calendar at once, time, class, kind) and **shortcuts** (Today, Tomorrow, After school, Tonight) | New task | Must (one step) | |
| F11 | **Edit your own task**: tap its row (notes, delete) | To Do / Plan | Should | |
| F12 | **Month**: pull the week strip down (or tap the flat arrow); swipe sideways for months; tap a day = that day + the next | To Do › List | Should | |
| F13 | **Student ID barcode** and its shortcuts: Classes tab button, widget, Control Center button, Siri "Show my student ID in Dewey" | Classes / Settings | Should | See settings S3. |
| F14 | **School day** times behind Before/After school | Settings › To Do | Settings step | See S2. |
| F15 | **Show in the Calendar app** (a separate "Dewey" calendar) | Settings › To Do | Settings step | See S4. |

---

## 3. Settings to have the student set *during* the tutorial

Ranked by how much the app depends on them. "Do" = a coach step where they
actually set it; "Show" = point at it; "Skip" = leave for Help.

| # | Setting | Where | Default | Do / Show / Skip | Why |
|---|---|---|---|---|---|
| S1 | **Notifications**: 2 hours before if not submitted; night before a test; homework the evening before (+ time); morning summary (+ time); alerts for new grades, teacher messages, new materials, signed out | Settings › Notifications | due-soon ON, test-night ON, evening OFF (7 pm), morning OFF (7 am), alerts ON | **Do** | The reminders are most of Dewey's value, and nothing arrives until iOS permission is granted. Ask for permission here, with context, not cold at launch. |
| S2 | **School day**: School starts / School ends | Settings › To Do › School day | 8:00 AM / 3:30 PM (a guess) | **Do** | Dewey can't know the bell schedule (Schoology doesn't have it). Wrong times make "Before/After school" wrong in every new task. |
| S3 | **Student ID** | Settings › Student ID (also Classes) | none | **Do**, but see the practice caveat (§1) | Useful daily (library, attendance). Typing or scanning only; or do it after the practice against the real account. Then point at the widget / Control Center / Siri. |
| S4 | **Show in the Calendar app** | Settings › To Do › iPhone Calendar | OFF | **Do (offer)**: "Want your work in the Calendar app?" Yes → toggle (asks Calendar access); Not now → skip | Some students live in Calendar; others won't want a second calendar. A choice, not a default. |
| S5 | **Appearance**: System / Light / Dark | Settings (top) | System | **Do** (already a step) | Keep. |
| S6 | **To Do view**: Week (by class) or Urgent first | Settings › To Do | Week | **Show** (one line, or a choice step) | Changes the first screen they see every day; cheap to ask. |
| S7 | **Mark messages read when opened** | Settings › Inbox | ON | **Show** | It writes to Schoology (marks read), the one place Dewey changes something there. Worth a sentence. |
| S8 | **Letters across the top** (Grades) | Settings › Grades | ON | Skip | Taste; discoverable. |
| S9 | Classes in Grades / Classes on the shelf / Call numbers | Settings › Grades / Classes / To Do | all shown / auto | **Show only** | Real classes don't exist in practice (§1). The shelf step already teaches hold-to-remove. |
| S10 | neo-plan | Welcome card (already) | off | Keep the existing card | |
| S11 | Login compatibility mode | Settings › Schoology | OFF | Skip | Troubleshooting only. |

---

## 4. Proposed practice script (new and changed steps)

Wording is a draft in Dewey's voice (short, plain, "you"). Keep the existing
steps unless noted. Targets are `.coachTarget` keys that would need adding.

**After "Tests are never turned in…" (step ~8), replace "That's To Do!" with:**

1. *(button)* "That's your list. Circle = done for you. Submit or Turn in = handed in. Press and hold a row to pick its status."
2. `todo.month` (the week strip) — "Pull the days down to see the whole month. Try it." `done`: month open. *(F12)*
3. *(button)* "Tap any day to see it and the next. Push it back up when you're done." *(or fold into 2)*
4. `todo.planChip` — "Now your own order for today. Tap Plan." `done`: mode == .plan. *(F1)*
5. *(button, no target or the To do heading)* "Plan has three parts. **To do** is numbered: what you'll do today, in order. **Later** is for another day. **Done** fills up as you finish. New work lands by its due date." *(F2)*
6. `plan.grip.<item>` — "Drag a row by its dots to move it up or down. Put the Chem worksheet first." `done`: that item rank 1. *(F3)*
7. `plan.grip.<item>` — "Not today? Drag it below **Later**, or swipe it left." `done`: item in Later. Tip in the bubble: "Hold near the top or bottom of the screen to keep going." *(F3, F4, F5)*
8. *(button)* "Tap Later or Done to fold them away. Finished something here? It stays put until you leave Plan." *(F6, F7)*
9. `todo.add` — "Something that isn't on Schoology? Tap +." `done`: sheet open. *(F8)*
10. `quick.field` — "Type it like you'd say it: **chem lab fri after school**." `done`: parsed day + time + class present. *(F9)*
11. *(button)* "Dewey filled in the day, time and class. The buttons change them; the row below is one tap for the usual ones. Tap ↑ to add it." `done`: task added. *(F10)*
12. *(button)* "Tap your own task any time to change it or add notes." *(F11)*
13. `todo.planChip` — "Tap To Do to go back to everything." then continue to Grades as today.

**Grades, Classes: unchanged.** In the Classes stretch add:

14. `classes.idButton` — "Your student ID lives here, as a barcode for the library and attendance. You'll set it up in a moment." *(F13; no setup in practice: demo ID)*

**Settings stretch: replace "Appearance" and "Notifications" steps with a short setup:**

15. (existing) gear → back arrow.
16. (existing) Appearance.
17. `settings.notifications` — "Reminders are why most people keep Dewey. Tap Notifications." *(S1)*
18. *(free)* "Turn on the ones you want. iPhone will ask once if Dewey can send notifications: say Allow, or none of these arrive." `done`: any toggle changed or a button "Done choosing". Before the system prompt: show the reason first.
19. `settings.todo` — "Tap To Do." → `settings.schoolDay` "When does your school day start and end? 'Before school' and 'After school' in new tasks use these." *(S2)* `done`: button "Looks right".
20. `settings.calendar` — "Want your work in the iPhone Calendar app too? It goes in its own Dewey calendar, which you can hide any time." Buttons: **Turn on** / **Not now**. *(S4)*
21. *(optional)* Inbox: "Opening a message in Dewey marks it read on Schoology. You can change that here." *(S7)*
22. End: "That's the tour! One last thing…" → leave the practice → **Student ID setup on the real account** (`StudentIDSetup`, Fill in from Schoology works for real), with Skip. Then point at the widget / Control Center / "Hey Siri, show my student ID in Dewey". *(S3, F13)*

Count: about +14 steps (24 → ~38). If that's too long, cut 3, 8, 12, 21 to card lines (→ ~34).

### Reference cards (Settings › Help › tutorial, `mode == .reference`)

- **Places**: add "List and Plan (the chip by To Do's title)" and "+ adds your own task".
- **Marking**: add a line for Plan's grip/swipes.
- **New card "Plan and your own tasks"** between Marking and Turning in: three sections, drag by the dots, hold at the edge, type a task in words.
- **Settings card**: "To Do view" point → mention School day and Show in the Calendar app; add Student ID.

---

## 5. Practice data needed

The practice week (`Demo.swift` / practice engine) should have, so Plan looks real:
- at least one item that lands in **Later** (due 3+ days out, not a test),
- one item **done today** (so Done isn't empty when unfolded),
- the Chem worksheet and Bio lab already used by earlier steps,
- no own tasks at the start (step 10 makes one).

Check what the demo already has before adding (demo mode in the simulator:
`-DeweyDemo`; practice uses the same data via `AppModel(practice: true)`).

---

## 6. Open decisions (ask before building)

1. Full script (~38 steps) or the cut version (~34)?
2. Student ID: set up **after** the practice on the real account (recommended),
   or skip it in the tutorial and only point at it?
3. Calendar: offer it in the tutorial (recommended) or leave it to Settings?
4. Should "To Do view" (Week / Urgent first) be a choice step, or a mention?

---

## 7. Done when

- Each new step's `done` condition fires in the simulator, and "Take me there"
  (`rewind`, `tab`) lands on the right screen for every new step.
- After finishing the tutorial: notification permission was asked with context
  first; School day, Calendar choice and Appearance are saved; no demo student
  ID was saved (Keychain `studentId` empty unless the student set a real one).
- Skip at any step leaves the app in its normal state (practice store gone, real
  settings as set so far).
- VoiceOver can complete every new step (accessibility actions exist for Plan
  moves: Move up/down, Move to To do/Later).
- iOS tests pass (`xcodebuild test`); docs updated: `SCREENS.md` (tutorial
  section), `IOS-APP.md` progress.
- Android: note in `docs/android/ANDROID-APP.md` that the tutorial changed, for
  the port.
