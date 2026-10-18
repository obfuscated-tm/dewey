import SwiftUI

/// How statuses work: To do → Done → Turned in, what "on Schoology" means, and
/// Studied for tests. Opened from the first-tick tip on To Do and from Settings.
/// Mockup: docs/ios/design/Tutorial.options.html, option C.
struct StatusGuideView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                step(.todo, "To do", "Not finished yet.")
                step(.done, "Done", "You finished it. Nothing is handed in yet. “Done · not submitted yet” means it still needs Submit on Schoology.")
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    step(.turnedIn, "Turned in", "Your teacher has it. Two kinds:")
                    branch("Submitted on Schoology", "the dropbox has your file. Dewey says this only when Schoology does.")
                    branch("Turned in · not on Schoology", "you tapped Turn in because it went in on paper, on a lab site or in class.")
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Why paper work is never “missing”").font(Theme.subhead.weight(.semibold))
                    Text("Schoology only knows about its own dropbox. Paper, lab sites and Google Assignments look “not submitted” there even after you hand them in, and some teachers grade paper even when there's a dropbox. Dewey shows them late until you tap Turn in, never missing.")
                        .font(Theme.footnote).foregroundStyle(Theme.secondary)
                }
                .padding(Theme.Space.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
                step(.studied, "Tests: Studied", "Tests aren't turned in. Tick Studied when you're ready and the night-before reminder stops.")
                step(.undo, "Changed your mind?", "Tap the circle again, or Undo on a turned-in row. Press and hold a row to pick any status.")
            }
            .padding(Theme.Space.l)
        }
        .background(Theme.background)
        .navigationTitle("How statuses work")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func step(_ mark: GuideMark.Kind, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: Theme.Space.m) {
            GuideMark(kind: mark)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Theme.body.weight(.semibold))
                Text(detail).font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }

    private func branch(_ title: String, _ detail: String) -> some View {
        (Text(title).bold().foregroundStyle(Theme.good) + Text(": " + detail).foregroundStyle(Theme.text))
            .font(Theme.footnote)
            .padding(.horizontal, 10).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
            .padding(.leading, 24 + Theme.Space.m)
    }
}

/// The To Do row's marks (CheckMark), drawn without an item.
private struct GuideMark: View {
    enum Kind { case todo, done, turnedIn, studied, undo }
    let kind: Kind

    var body: some View {
        ZStack {
            switch kind {
            case .todo:
                Circle().stroke(Theme.secondary, lineWidth: 1.6)
            case .done, .studied:
                Circle().fill(Theme.accentSoft)
                Circle().strokeBorder(Theme.accent, lineWidth: 2)
                Image(systemName: "checkmark").font(Theme.caption.weight(.bold)).foregroundStyle(Theme.accent)
            case .turnedIn:
                Image(systemName: "checkmark.seal.fill").resizable().scaledToFit()
                    .symbolRenderingMode(.palette).foregroundStyle(Theme.onAccent, Theme.good)
                    .fontWeight(.bold).scaleEffect(1.12)
            case .undo:
                Image(systemName: "arrow.uturn.backward").font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.secondary)
            }
        }
        .frame(width: 24, height: 24)
        .accessibilityHidden(true)
    }
}

/// The one-time tip under the first row a student ticks on To Do: the one
/// thing that matters next, and a link to the full guide.
struct StatusTip: View {
    enum Kind: String { case done, studied, turnedIn }
    let kind: Kind
    let onGuide: () -> Void
    let onClose: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.s) {
            VStack(alignment: .leading, spacing: 6) {
                message.font(Theme.footnote).foregroundStyle(Theme.text)
                Button("How statuses work", action: onGuide)
                    .font(Theme.footnote.weight(.semibold))
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.accent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onClose) {
                Image(systemName: "xmark").font(.footnote.weight(.semibold)).foregroundStyle(Theme.tertiary)
                    .frame(width: 32, height: 32).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-6)
            .accessibilityLabel("Close tip")
        }
        .padding(Theme.Space.m)
        .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
    }

    private var message: Text {
        switch kind {
        case .done:
            Text("Done is just for you. ").bold() + Text("Hand it in next: ")
                + Text("Submit").bold() + Text(" for a Schoology dropbox, ")
                + Text("Turn in").bold() + Text(" for paper or a lab site.")
        case .studied:
            Text("Studied stops the night-before reminder. ").bold() + Text("Tests are never turned in.")
        case .turnedIn:
            Text("Turned in · not on Schoology ").bold()
                + Text("means you handed it in on paper, on a lab site or in class. Dewey says Submitted on Schoology only when Schoology does.")
        }
    }
}
