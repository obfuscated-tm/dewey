# Schoology Toolkit

- Native iOS app (planned; design done, no app code yet): **read [docs/IOS-APP.md](docs/IOS-APP.md) before any iOS work.** It has the rules (read-only on Schoology, never copy a parser into Swift), the target repo layout, and the milestones in order with "done when" checks, and links the details: [docs/ios/FINDINGS.md](docs/ios/FINDINGS.md) (verified facts), [docs/ios/ENGINE.md](docs/ios/ENGINE.md) (JS ↔ Swift contract), [docs/ios/SCREENS.md](docs/ios/SCREENS.md) (every screen), [docs/ios/design/](docs/ios/design/) (mockups).
- Extension tests: `node --test test/*.test.js` from `schoology_archiver_extension/` (`node --test test/` does not work).
