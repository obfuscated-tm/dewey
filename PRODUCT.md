# Dewey: product context

Written for design work (Impeccable). Facts come from README.md, brand/README.md, site/README.md and the iOS docs; the rules for screens are in docs/ios/SCREENS.md.

## What it is

Dewey is a free toolkit that makes Schoology make sense: a Chrome extension (the main product), iPhone and Android apps in beta, and an optional Claude add-on. It puts everything due on one list, shows your grade to the decimal with what-if scores, and saves whole classes to a folder you own before the term ends. It only reads Schoology; it never submits or changes anything there.

## Who it's for

High school students on any school's Schoology. Today it's mostly used by the person who made it, but design calls are made for **future strangers**: a first-time visitor's clarity beats the maker's taste.

## Who makes it

Owen, a high school student. Not a company; not affiliated with Schoology or PowerSchool. No server, no accounts, no analytics, no ads. That honesty is part of the pitch.

## Surfaces

| Surface | Mode | Notes |
|---|---|---|
| dewey.owen64.me home (`site/src/index.html`) | Persuade | "Green cover" design: a green book cover, a library due-date card you can tick off, a feature switcher, install steps |
| `/web` (`site/src/web.html`) | Persuade | A live mockup of the web app, sign-in locked until Schoology approves OAuth |
| Privacy, Terms, 404 (`site/src/style.css`) | Read | Plain legal reading; must stay true (docs/LEGAL.md) |
| Extension, iOS, Android apps | Operate | See docs/ios/IOS-APP.md and SCREENS.md |

## Personality

A school library, not a startup: paper, pine-green covers, ink date stamps, Dewey Decimal call numbers. Calm, a little homey, made by a person. Plain words ("due", not puns) in the product UI; puns only in marketing.

## Anti-references

- Generic SaaS landing pages: bento grids, glassmorphism, gradient text, grain-and-glow heroes, giant type for its own sake. Owen rejected these as "generic, too loud, not Dewey".
- Over-calming: stripping the library details (call numbers, stamps, ruled cards) removed the character. Ask before removing a Dewey-specific detail.
- Anything that looks one-shotted by AI. The logo is a placeholder for a hand-drawn one; only the logo is hand-drawn, and UI text stays clean.

## Rules that carry over

- Red means late. Nothing else uses the stamp red as decoration.
- No spaced capitals in app UI (the site's stamps and the date-due card are the exceptions, as in the app).
- No web fonts, no third parties on the site.
