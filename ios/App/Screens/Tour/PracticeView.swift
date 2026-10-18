import SwiftUI

/// The tour's pretend week: the real tabs on a practice model (Practice), with
/// Dewey's coach on top. Ends when the steps are done or the student skips;
/// then the real app republishes its widgets and reminders.
struct PracticeView: View {
    @Environment(AppModel.self) private var real
    let onDone: () -> Void
    @State private var practice: AppModel?

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if let m = practice {
                MainTabs()
                    .environment(m)
                    .modelContainer(m.container)
            } else {
                ProgressView()
            }
        }
        .task {
            guard practice == nil else { return }
            Practice.begin()
            let m = AppModel(practice: true)
            await m.boot()
            await m.refreshTodo()
            practice = m
            // Let the list draw so the first targets have frames.
            try? await Task.sleep(for: .milliseconds(400))
            Coach.shared.start(TourScript.all, model: m, from: Demo.tourStep) { end(m) }
        }
    }

    private func end(_ m: AppModel) {
        m.engine.stop()
        Practice.end()
        WidgetBridge.publish(real)
        Reminders.shared.schedule(items: real.todoItems(), courses: real.courses)
        onDone()
    }
}

/// What Dewey says, step by step. Ids are Practice's pretend week.
@MainActor
enum TourScript {
    // Each step names its tab and how far back "Take me there" rewinds (Coach.recover).
    private static func seen(_ key: String) -> Bool { Coach.shared.frames[key] != nil }

    static let todo: [Coach.Step] = [
        .init(text: "Hi, I'm Dewey! This is a pretend week, so nothing you tap here touches your real Schoology. Let's try what you'll do every day.",
              hint: .none, button: "Let's go"),
        .init(target: "todo.check.\(Practice.lab)",
              text: "You just finished your Bio lab. Tap its circle to mark it Done.",
              done: { $0.item(Practice.lab)?.done == true }, tab: 0),
        .init(target: "todo.action.\(Practice.lab)",
              text: "Done is just for you: your teacher doesn't have it yet. This lab goes in a Schoology dropbox, so tap Submit.",
              done: { _ in seen("dropbox.submit") }, tab: 0),
        .init(target: "dropbox.submit",
              text: "This is a pretend Schoology page. On the real one you'd attach your file here. Tap Submit.",
              done: { _ in Practice.submitted.contains(Practice.lab) }, tab: 0, rewind: 1),
        .init(target: "dropbox.done",
              text: "Sent! Tap Done. When you close Schoology, Dewey checks it again.",
              done: { $0.item(Practice.lab)?.submitted == true }, tab: 0, rewind: 2),
        .init(target: "todo.row.\(Practice.lab)",
              text: "The green seal means Schoology has it. Dewey only says Submitted when Schoology does.",
              hint: .none, button: "Next", wide: true, tab: 0),
        .init(target: "todo.row.\(Practice.worksheet)",
              text: "This worksheet went in on paper, so Schoology never sees it. Tap Turn in, or swipe the row left.",
              hint: .swipeLeft,
              done: { $0.item(Practice.worksheet)?.turnedIn == true }, wide: true, tab: 0),
        .init(target: "todo.check.\(Practice.quiz)",
              text: "Tests are never turned in. Tap the circle once you've studied, and the night-before reminder stops.",
              done: { $0.item(Practice.quiz)?.studied == true }, tab: 0),
        .init(text: "That's To Do! Circle = done for you. Submit or Turn in = handed in. Press and hold any row to pick its status. Now, grades.",
              hint: .none, button: "Next"),
    ]

    static let grades: [Coach.Step] = [
        .init(target: "tab.1",
              text: "Tap Grades to see every class's letter.",
              done: { _ in seen("grades.class.\(Practice.chem)") }, tab: 1),
        .init(target: "grades.class.\(Practice.chem)",
              text: "Every class, and how far it is from an A. Tap Chemistry to see every grade in it.",
              done: { _ in seen("grades.whatif") }, tab: 1),
        .init(target: "grades.whatif",
              text: "What-if lets you try scores before they happen. Tap Try what-if scores.",
              done: { _ in seen("grades.whatifbar") }, tab: 1, rewind: 1),
        .init(target: "grades.score.\(Practice.flameTest)",
              text: "Your Flame Test lab got 18 / 20. Tap the box and type 20 to see what full marks would do.",
              done: { _ in Coach.shared.flags.contains("whatif") }, tab: 1, rewind: 2),
        .init(target: "grades.whatifbar",
              text: "Real → what-if, right here. It's only a preview: nothing is saved, and leaving the class clears it.",
              hint: .none, button: "Next", tab: 1, rewind: 3),
    ]

    static let classes: [Coach.Step] = [
        .init(target: "tab.3",
              text: "Tap Classes. Each class is a book on your shelf.",
              done: { _ in seen("shelf.book.\(Practice.english)") || seen("shelf.flat.any") }, tab: 3),
        .init(target: "shelf.book.\(Practice.english)",
              text: "Not a real class, like study hall? Press and hold English 10, then tap Take off the shelf. (A tap opens its materials.)",
              done: { _ in seen("shelf.flat.any") },
              free: true, tab: 3),
        .init(target: "shelf.flat.any",
              text: "It lies flat under the shelf now. Press and hold it again to put it back.",
              hint: .none, button: "Next", tab: 3),
    ]

    static let settings: [Coach.Step] = [
        .init(target: "gear.classes",
              text: "Last stop: the gear. It opens Settings for the tab you're on. Tap it.",
              done: { _ in seen("settings.page.classes") }, tab: 3),
        .init(target: "settings.sheet",
              text: "These are the Classes settings. Tap the back arrow at the top left for the rest of Settings.",
              hint: .none,
              done: { _ in !seen("settings.page.classes") && seen("settings.appearance") },
              free: true, tab: 3, rewind: 1),
        .init(target: "settings.appearance",
              text: "Appearance: System, Light or Dark. Change it any time.",
              hint: .none, button: "Next", wide: true, tab: 3, rewind: 2),
        .init(target: "settings.notifications",
              text: "Notifications: reminders before work is due, the night before a test, and new grades. Pick which ones here.",
              hint: .none, button: "Next", wide: true, tab: 3, rewind: 3),
        .init(text: "That's the tour! Your real week is waiting. You can take this again from Settings → Help.",
              hint: .none, button: "Finish"),
    ]

    static var all: [Coach.Step] { todo + grades + classes + settings }
}

/// The Schoology sheet during practice: a pretend dropbox, so the real page
/// (and the student's real session) is never loaded.
struct PracticeDropbox: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let target: SheetTarget
    @State private var sent = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Space.l) {
                    Label("Pretend page · nothing is sent", systemImage: "theatermasks")
                        .font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Theme.accentSoft, in: Capsule())
                    Text(title).font(Theme.title)
                    VStack(alignment: .leading, spacing: Theme.Space.m) {
                        Text("Submit Assignment").font(Theme.headline)
                        HStack(spacing: Theme.Space.s) {
                            Image(systemName: "doc.fill").foregroundStyle(Theme.secondary)
                            Text("my-lab-report.pdf").font(Theme.subhead)
                            Spacer()
                            Text("Attached").font(Theme.footnote).foregroundStyle(Theme.secondary)
                        }
                        .padding(Theme.Space.m)
                        .background(Theme.background, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                        if sent {
                            Label("Submitted", systemImage: "checkmark.seal.fill")
                                .font(Theme.headline).foregroundStyle(Theme.good)
                            Button { dismiss() } label: {
                                Text("Done").font(Theme.headline).frame(maxWidth: .infinity, minHeight: 44)
                            }
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                            .coachTarget("dropbox.done")
                        } else {
                            Button {
                                if let id = target.assignmentId { Practice.submitted.insert(id) }
                                withAnimation { sent = true }
                                Coach.shared.check()
                            } label: {
                                Text("Submit").font(Theme.headline).foregroundStyle(Theme.onAccent)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                            .coachTarget("dropbox.submit")
                        }
                    }
                    .padding(Theme.Space.l)
                    .card()
                }
                .padding(Theme.Space.l)
            }
            .background(Theme.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() }.fontWeight(.semibold) }
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text("Schoology").font(Theme.subhead.weight(.semibold))
                        Text("pretend page").font(Theme.caption).foregroundStyle(Theme.secondary)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .tint(Theme.accent)
        .onDisappear {
            guard let id = target.assignmentId else { return }
            Task { await model.recheckAfterSheet(id) }
        }
    }

    private var title: String { target.assignmentId.flatMap { model.item($0)?.title } ?? "Assignment" }
}
