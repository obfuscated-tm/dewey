# Dewey (Schoology toolkit)

- Brand (name, logo files, colors, rules): [brand/README.md](brand/README.md). Only the logo is hand-drawn; UI text stays clean.

- Native iOS app (`ios/`): **read [docs/ios/IOS-APP.md](docs/ios/IOS-APP.md) before any iOS work.** It has the rules (read-only on Schoology, never copy a parser into Swift), the target repo layout, and the milestones in order with "done when" checks, and links the details: [docs/ios/FINDINGS.md](docs/ios/FINDINGS.md) (verified facts), [docs/ios/ENGINE.md](docs/ios/ENGINE.md) (JS ↔ Swift contract), [docs/ios/SCREENS.md](docs/ios/SCREENS.md) (every screen), [docs/ios/design/](docs/ios/design/) (mockups).
- Tests: `npm test` from the repo root runs the extension and MCP suites. Extension only: `node --test test/*.test.js` from `extension/` (`node --test test/` does not work).
