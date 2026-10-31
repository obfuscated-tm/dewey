# Site research: why it reads as "AI", and what would fix it

Written 2026-10-31, after three rounds of site designs that each still felt off. Part 1 is what we know so far. Part 2 is a short study to check it with real students before another redesign.

---

## Part 1 · Research synthesis (what we have so far)

**Method:** owner's reactions across three design rounds, one friend's comment, and a side-by-side read of bessy.io and gunn.one
**Participants:** 2 (P1 Owen, who built it; P2 a friend). Too few and too close to the project to be findings: treat each theme as a **hypothesis** that Part 2 tests.
**Date:** Oct 2026

### Summary

Each version so far fixed what the last one was blamed for (template look, then too much scrolling) and still felt "AI", so the problem is probably not any single layout choice. The likely cause: none of the versions shows anything only Dewey could show. Concepts like stamps and call numbers came from the *name*, not from a student's real week, so they read as cleverness added on. What's missing is **evidence of a real person and real use**: something hand-made, real class names, the oddly specific problems Dewey solves. On color, P1 wants more green than the new iOS look (warm paper, near-black text, teal only as accent) while still reading "student, but professional".

### Themes (hypotheses)

#### 1 · "AI" means generic, not one bad choice
**Seen in:** P1 in all 3 rounds; P2 about the logo
**Summary:** v1 was the stock template (centered hero, six feature cards, a safety list). The library version was "fun" but long. The three compact drafts were "still AI, no something". The friend said the logo looked "one-shotted by AI", and it was. Every version was polished, symmetrical and safe, with no choice that looked like a person's taste or a mistake someone kept on purpose.
**Evidence:**
- "feels genuinely too AI and doesn't have anything of substance" (P1, v1)
- "fun but… much scrolling" (P1, library version)
- "still… too AI whilst having no 'something' into it" (P1, drafts)
- The logo "looks one-shotted by AI" (P2, in `brand/README.md`)
**Implication:** another layout won't fix this. The page needs things a generator wouldn't make up.

#### 2 · The "something" is specificity
**Seen in:** both references; P1's reaction to them
**Summary:** Bessy has a cow someone clearly drew and a real number ("100,000+ students from all 50 states"). gunn.one makes you pick a color theme before anything else, an odd choice only someone who cares would make. Our pages used made-up classes ("AP Biology", "Chemistry") and generic copy.
**Real Dewey facts no AI would invent:**
- "Not submitted" doesn't mean missing. Some classes grade paper through an empty dropbox, labs go to an outside site, and Google Assignments are invisible to Schoology.
- Dewey goes completely quiet while a quiz is open.
- Alt+Shift+K stops a save mid-run.
- Your real class list: AP Physics 1, AP CS A, Pre-Calc H, World History, World Lit, French.
- "1.76 above an A", the number from the very first screenshot.

**Implication:** demos and copy should use real classes and Dewey's real edge cases, in a student's own words.

#### 3 · A hand-made mark is the missing piece
**Seen in:** P2; both references; the open logo to-do in `brand/README.md`
**Summary:** Bessy's friendliness comes almost entirely from its drawing. Our only "hand-drawn" thing is a placeholder generated as code. The brand to-do (sketch thumbnails, hand-letter "dewey", photograph it) isn't done, and the site can't make up for it.
**Implication:** the hand-drawn logo and lettering are probably worth more than any site layout. Do them first, or at least in parallel.

#### 4 · More green, used the way school stuff uses it
**Seen in:** P1
**Summary:** P1 wants more green than the iOS redesign, which keeps teal for the accent only. Greens are both "school" and "serious" in real objects:
- engineering computation paper (pale green, faint grid, used by engineering and physics students)
- a green banker's lamp in a library
- a green hardcover notebook
- a green-ink grade stamp

Each of these is professional because a real profession uses it, and student because students actually own it. Teal as a "brand color" splashed on buttons is what reads as fake.
**Implication:** try green as the **surface** (paper, cover, desk), not as decoration. The leading candidate: a pale-green engineering-paper background, with ink-dark text and the teal drop.

#### 5 · Professional comes from precision, not polish
**Seen in:** references; P1 wanting "professional without being fake"
**Summary:** gradients, glows and big claims read as fake. Exact numbers, plain sentences, consistent alignment and honest limits ("iPhone isn't out yet", "tested at one school") read as professional.
**Implication:** say exact things ("3.3 above an A", "1 missing · 1 to turn in") and admit what isn't done.

#### 6 · The app already has the identity the site keeps trying to invent
**Seen in:** the iOS app as of Oct 31 (practice data, dark mode)
**Summary:** every library idea the site added on is already in the app, built from real use, and done better:
- **To Do:** each class has its true Dewey Decimal number (512 Algebra 2, 570 AP Biology, 820 English, 460 Spanish, 540 Chemistry, 973 US History). There are "Due today" stamps, and a "1 of 2 done today" ring with the drop.
- **Assignment:** a tilted "Due Oct 29 · 2 days late" stamp over a "Date due / Turn in" card. "Not submitted on Schoology", with the worried drop. Your own To do / Done / Turned in, with the honest line "Turned in means you handed it in, on paper or another site."
- **Grades:** "New 3" with what each score moved ("+0.24%"). A **Report card** with a call number on each class's spine and a big serif letter.
- **Classes:** an actual bookshelf. Each class is a colored spine with a period-and-grade card like a library pocket, and ribbon bookmarks for TODAY, LATE and TMRW.

The site's stamps were a copy of something it never showed. The app is the "something".
**Implication:** the site shouldn't invent a concept. It should be a calm frame around the real app: big, sharp screenshots of the bookshelf, the report card and the late stamp, with the copy pointing at real details. Green fits as the frame (a deep library green), with the app's warm paper and class colors on top.

### Insights → opportunities

| Insight | Opportunity | Impact | Effort |
|---|---|---|---|
| The app already has a real, well-made identity | Build the site around big, real app screenshots (shelf, report card, late stamp) on a deep library-green frame | High | Low |
| Generic = "AI" | Use real class names and Dewey's real edge cases in every demo and line of copy | High | Low |
| The hand-drawn mark is missing | Finish the brand README's sketch + lettering to-do; put it on the site | High | Med |
| Green should be a surface | Prototype an engineering-paper page in more green; compare with live | High | Low |
| Precision reads professional | Rewrite copy with exact numbers and honest limits | Med | Low |
| Length hurt the library version | Keep any direction to ≤2 laptop screens | Med | Low |

### Who the site is for (to confirm)

| Segment | What they're like | What they need from the site | Size |
|---|---|---|---|
| Friend-referred student, on a phone | Heard about it at school, opens the link on an iPhone | Know in 5 seconds what it is and that it's legit; most can't install anything yet (no iPhone app) | Likely most |
| Laptop student who'll install | Has Chrome, willing to use Developer mode | Clear steps, reassurance that it's safe | Smaller |
| Parent or teacher checking it out | Asks "is this allowed / safe?" | Who made it, what it reads, Privacy and Terms | Few, high stakes |

### Recommendations

1. **High:** run Part 2 before another redesign. Three rounds of guessing came from the same two opinions.
2. **High:** do the hand-drawn logo and lettering (brand README to-do) whatever the test says.
3. **High:** next prototype uses an engineering-paper green surface, real classes and real edge cases. Test it against the live site and draft B.
4. **Medium:** decide whether hand-written margin notes are allowed on the site. The brand rule today is "only the logo is hand-drawn".

### Open questions

- When students say "AI-looking", what are they actually reacting to: color, type, copy, or pictures?
- Do students trust a site more or less when it says it's made by a student?
- On a phone, do students want to try a demo, or just get the app?
- Does green paper read as "school" or as "old"?

---

## Part 2 · Research plan (one lunch period, about a week)

### Goal

Find out what makes a student call the Dewey site "AI" or "legit", and which direction reads as **student but professional**.

### Method

Short in-person sessions (about 10 minutes each), on a phone, since that's how a friend's link gets opened.

- **Who:** 6–8 students. At most 3 who already use Dewey; the rest have never seen it (current users are friends and will be kind). Mix grades, and include at least 2 who don't care about apps.
- **What to show:** live site, draft B, and one new green prototype, as phone screenshots. Change the order for each person.

### Session guide

**1 · Warm-up (1 min)**
"I'm testing a website, not you. There are no wrong answers, and you can't hurt my feelings."

**2 · Context (2 min)**
- "How do you check what's due right now?"
- "Have you ever used an app or site for Schoology or grades? Which one? Why did you keep it or stop?"

**3 · Five-second test (3 min, once per version)**
Show it for **5 seconds**, then hide it. Ask in this order, and don't lead:
1. "What was that for?"
2. "Who do you think made it?" Listen for "a student", "a company", "AI".
3. "Would you put in your school login? Why or why not?"
4. "Pick 3 words from this card" (shuffle the order every time):
   > trustworthy · generic · made by a student · corporate · AI-made · fun · cringe · clean · school-y · sketchy · real · boring · professional · fake

**4 · Compare (2 min)**
Lay out all versions. "Which one would you send to a friend? What about it?" Then: "What's the most fake-looking thing on any of these?" Have them point at it.

**5 · Wrap-up (1 min)**
"Anything that would make you trust it more?" Thank them.

### What to write down

One row per person per version:

| Person | Uses Dewey? | Version | "What is it?" (their words) | "Who made it?" | Would log in? | 3 words | Pointed at as fake |
|---|---|---|---|---|---|---|---|

### How to read the results

- **"AI" problem fixed:** fewer than 2 of 8 say "AI" or "company" when asked who made it, and "generic" or "fake" are rarely picked.
- **Winning direction:** chosen most often in "send to a friend", and picks up "real", "trustworthy" or "made by a student" without "cringe".
- **Fake-looking things:** anything pointed at by 2 or more people gets cut.

Bring the filled-in sheet back to Claude and run `/design:research-synthesis` on it for the next version.

### Watch out for

- Friends will be nice. Weigh the people who have never seen Dewey more.
- Don't say "AI" before they do.
- Five seconds really means five seconds: first impressions are the point.
