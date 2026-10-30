<picture>
  <source media="(prefers-color-scheme: dark)" srcset="brand/logo/dewey-horizontal-dark.svg">
  <img src="brand/logo/dewey-horizontal.svg" alt="Dewey" height="64">
</picture>

Dewey makes Schoology make sense. It's a free Chrome extension that shows what's due, your grade to the decimal, and saves every class before the term deletes it. iPhone and Android apps are in beta.

**[Download for Chrome](https://github.com/obfuscated-tm/dewey/releases/latest/download/dewey-extension.zip)** · [Install steps](#install) · [Phone apps](#iphone-and-android-beta) · [Is it safe?](#is-it-safe)

<img src="docs/images/hero.png" alt="Dewey on Schoology: the To Do list grouped by class, a Grades page showing 1.76 above an A, and the one-click Archive card" width="900">

## What it does

- **To Do**, grouped by class, with the real due time, overdue work marked "2 d late", and Done / Turn in buttons on each row.
- **Grades**, with a graph of the term, how far above an A you are, how low each category can drop, and what each score did to your grade. Type a score you might get ("what if I get a 38/50?") and watch it change.
- **Materials**, where every folder shows what's still open ("1 missing · 1 to turn in") and your score.
- **Archive.** Schoology deletes classes when the term ends. Dewey saves slides, handouts, Google Docs (as PDFs), your own submissions, grades and announcements to a folder you own.
- **Claude**, optionally. Ask "quiz me on Unit 3" and it reads your actual slides and notes.

<img src="docs/images/grades.png" alt="The Grades page with the course graph, distance above an A, and each score's impact" width="100%">

<img src="docs/images/materials.png" alt="Materials page with per-folder status and scores" width="100%">

<img src="docs/images/assignment.png" alt="Assignment page with a Missing chip and a what-if box" width="100%">

## Install

You need Google Chrome and a Schoology student login. No coding.

1. [Download the ZIP](https://github.com/obfuscated-tm/dewey/releases/latest/download/dewey-extension.zip) and unzip it. Move the `dewey-extension` folder somewhere it can stay, like Documents. If you delete the folder, Chrome removes the extension.
2. Go to `chrome://extensions`, turn on **Developer mode** (top right), click **Load unpacked** and pick the `dewey-extension` folder.
3. Pin **Dewey for Schoology** from the puzzle-piece menu, then open Schoology. The Welcome card walks you through the rest.

Chrome may show a "Disable developer mode extensions" bubble. That's normal for extensions installed from a folder; close it.

**Updating:** download the new ZIP, replace the old folder (same place, same name), then click ↻ on Dewey in `chrome://extensions` and refresh Schoology. Your settings and saved classes stay. To hear about new versions, click **Watch → Custom → Releases** at the top of this page.

**From source:** `git clone https://github.com/obfuscated-tm/dewey.git`, then Load unpacked the `extension` folder inside it. Update with `git pull` and ↻. The [Claude add-on](#study-with-claude) needs this way.

## Save your classes

Open a class, click **Archive** in the bottom-left corner, then **Archive this course**. It runs in the background while you keep browsing. The first time, Dewey asks where to save; make a folder like `Documents/Schoology Archive` (otherwise it goes to `Downloads/Schoology Archive`).

Re-archive every week or so, and once more before the term ends. It only grabs what's new, so it's quick.

Each class becomes a normal folder:

```text
Schoology Archive/
└── AP Biology/
    ├── INDEX.md                     start here: a list of everything
    ├── grades.md                    your gradebook
    ├── updates.md                   teacher announcements
    └── Unit 1 - Cells/
        ├── Lecture Slides.pdf       Google Slides, saved as a PDF
        ├── Guided Notes.pdf
        ├── Lab 1 (assignment).md    instructions, due date, grade, comments
        └── Lab 1 files/
            └── My submission.pdf    what you turned in
```

Files are saved as they are. Google Docs, Slides and Drawings become PDFs, and Sheets become Excel files. Assignments keep their instructions, grade, comments and everything you submitted. Quizzes are saved only if your teacher lets you review them. Links, videos and Forms are listed in `INDEX.md`. If a teacher updates a file, the new one is saved next to the old one. If they delete something, your copy stays.

## iPhone and Android (beta)

<img src="docs/images/ios-preview.png" alt="Dewey for iPhone: To Do, Grades, the Classes shelf with LATE / TODAY / TMRW ribbons, and a class's page starting with its open work" width="100%">

The same things, on your phone: To Do with a week strip, Grades with what-if scores, your classes as a shelf of books, an Inbox for announcements, reminders and widgets, and a countdown to your next test. Paper work you've ticked off stops showing as late. A first-launch tour walks you through a pretend week without touching your real Schoology.

Neither app is in a store yet. On Android, [download the latest APK](https://github.com/obfuscated-tm/dewey/releases?q=android&expanded=true) and open it on your phone. On iPhone, you build it yourself (below). Both only read Schoology; anything that changes it, like submitting, happens on Schoology's own page inside the app.

<details>
<summary><b>Build the iPhone app</b></summary>
<br>

You need a Mac, [Xcode](https://apps.apple.com/app/xcode/id497799835) (iOS 17+ SDK), [Node.js](https://nodejs.org), and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
git clone https://github.com/obfuscated-tm/dewey.git
```

```bash
cd dewey/ios && npm install && xcodegen generate
```

```bash
open Dewey.xcodeproj
```

For the simulator, pick an iPhone and press ▶; no Apple account needed. For your own iPhone, replace every `DEVELOPMENT_TEAM` in [`ios/project.yml`](ios/project.yml) with your team ID and `com.owenleung` in the bundle IDs with something of yours, run `xcodegen generate` again, plug in your phone and press ▶. A free Apple account works, but the app has to be re-installed every 7 days.

The design, screens and plan are in [`docs/ios/`](docs/ios/IOS-APP.md).

</details>

<details>
<summary><b>Build the Android app</b></summary>
<br>

You need [JDK 21](https://adoptium.net) and the [Android SDK](https://developer.android.com/studio) (Android Studio installs it). The phone needs Android 8.0 or newer.

```bash
git clone https://github.com/obfuscated-tm/dewey.git
```

```bash
cd dewey/android && ./gradlew assembleDebug
```

On your own phone, turn on USB debugging, plug it in, and run `./gradlew installDebug` from `android/`. For the emulator, open `android` in Android Studio and press ▶, or `adb install` the APK in `app/build/outputs/apk/debug/`.

If you change anything in `extension/engine/`, run `npm install` in `ios/` once so the build re-bundles it. Android notes are in [`docs/android/`](docs/android/ANDROID-APP.md).

</details>

## Study with Claude

Connect Dewey to [Claude Desktop](https://claude.ai/download) and ask things like "what do I have due this week?", "what's pulling my Chemistry grade down?", or "quiz me on Unit 3, one question at a time, don't show answers until I try." It reads your real slides, notes and gradebook. It works best as a study buddy, not a homework machine. Follow your school's rules on AI.

<details>
<summary><b>Set it up (about 5 minutes)</b></summary>
<br>

This needs the [source download](#install), since the ZIP only has the extension.

1. Install [Node.js](https://nodejs.org) (LTS).
2. In Terminal (Mac) or Command Prompt (Windows), go into the `dewey` folder and run:

   ```bash
   cd mcp && npm install
   ```

3. In Claude Desktop, open **Settings → Developer → Edit Config** and paste this, with the paths changed to where your folders are:

   ```json
   {
     "mcpServers": {
       "schoology": {
         "command": "node",
         "args": ["/Users/you/Documents/dewey/mcp/server.js"],
         "env": { "SCHOOLOGY_ARCHIVE": "/Users/you/Documents/Schoology Archive" }
       }
     }
   }
   ```

4. Quit and reopen Claude Desktop, then ask "What's my Schoology status?"

Claude gets live answers while Chrome is open. When Chrome is closed, it uses your saved classes and tells you how old they are. If Claude can't start it, replace `"node"` with Node's full path (on a Mac, run `which node`).

</details>

## Is it safe?

Dewey only reads Schoology. It can't submit, post or change anything. It never opens or starts a quiz, and it goes completely quiet while you have one open. Nothing is uploaded anywhere: saved classes are ordinary files in a folder you chose, and it uses the login Chrome already has, so there are no passwords.

To hide everything, use the switch in Schoology's bottom-left corner. To stop a save instantly, press **Alt+Shift+K**. Every line is here, [MIT licensed](LICENSE).

## Troubleshooting

| Problem | Try this |
|---|---|
| **Load unpacked** says "Manifest file is missing" | You picked the wrong folder. Pick the one with `manifest.json` directly inside it (`dewey-extension`, or `extension` in the source download). |
| I don't see Dewey on Schoology | In `chrome://extensions`, click ↻ on Dewey, then refresh Schoology. Make sure the switch in the bottom-left is on. |
| Everything disappeared while I'm taking a quiz | That's on purpose. It comes back after you close the quiz. |
| A save stopped halfway | Open the card and start it again. It picks up where it left off. If Schoology logged you out, log back in first. |
| Chrome keeps asking where to save files | In Chrome's settings, turn off **Ask where to save each file before downloading**. |
| A Google Doc was saved as a link, not a file | It isn't shared with you, or it's too big. The reason is in `INDEX.md`. |
| The **Choose folder** button does nothing | Click Dewey's icon in Chrome's toolbar and choose it there, not from inside Schoology. |
| My letter grades look wrong | Your school probably uses a different grading scale. See [Change the grading scale](docs/DEVELOPING.md#change-the-grading-scale). |
| I see "Add to neo-plan" or "neo-plan needs an update" | You can ignore those. They're for an optional planner app (see [neo-plan](docs/DEVELOPING.md#neo-plan-optional-planner)). |
| Claude doesn't see Schoology | Quit and reopen Claude Desktop. Check the path in the config. Try replacing `"node"` with the full path from `which node`. |
| Claude says it's using saved (old) data | Open Chrome with Schoology, and make sure no quiz is open. |
| Claude can't find my saved classes | Set `SCHOOLOGY_ARCHIVE` in the config to your archive folder (see [Settings](docs/DEVELOPING.md#settings-environment-variables)). |

Quiz questions are only saved when your teacher lets you review them. Your change history lives in the extension, so if you remove it, the next save starts fresh (your files stay).

Still stuck? [Open an issue](https://github.com/obfuscated-tm/dewey/issues) and describe what happened. Dewey will be very sorry.

## Developing

How it works, the MCP tools, tests, project layout and releases are in [docs/DEVELOPING.md](docs/DEVELOPING.md).

[MIT](LICENSE). Free to use, change and share. Keep the copyright notice. No warranty.
