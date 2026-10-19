import SwiftUI
import UserNotifications

/// The tour, shown once after sign-in (and once to anyone already signed in
/// when it shipped), again from Settings → Help → Take the tour: a welcome card,
/// then the guided practice (PracticeView: a pretend week in the real screens,
/// with Dewey pointing the way), then neo-plan for people who also use the
/// desktop extension. Help → Tour cards shows the reference cards instead
/// (where everything is, marking work, turning in, grades and what-if,
/// Settings). The art is drawn with the app's own marks so it matches light and dark.
struct TourView: View {
    static let shownKey = "tourShown"

    enum Mode: String, Identifiable { case tour, cards; var id: String { rawValue } }
    enum Card { case welcome, places, marking, turningIn, grades, settings, neoPlan }

    var mode: Mode = .tour
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage(TourView.shownKey) private var shown = false
    @AppStorage(Theme.appearanceKey) private var appearance = "system"
    @State private var page = 0
    @State private var practicing = false
    @State private var connected = NeoPlanClient().connected

    private var cards: [Card] {
        mode == .tour ? [.welcome, .neoPlan] : [.places, .marking, .turningIn, .grades, .settings]
    }
    private var last: Bool { page == cards.count - 1 }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if mode == .tour {
                    // No swiping past the practice: the button starts it.
                    card(cards[page]).id(page).transition(.opacity)
                } else {
                    TabView(selection: $page) {
                        ForEach(Array(cards.enumerated()), id: \.offset) { i, c in card(c).tag(i) }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
                bottomBar
            }
            .background(Theme.background)
            .toolbar {
                // Always there, so the bar (and the card under it) doesn't jump when Skip goes.
                ToolbarItem(placement: .principal) {
                    Text(mode == .tour ? "Tour" : "\(page + 1) of \(cards.count)")
                        .font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                        .accessibilityHidden(true)
                }
                if !last {
                    ToolbarItem(placement: .cancellationAction) { Button("Skip", action: finish) }
                }
            }
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationBarTitleDisplayMode(.inline)
        }
        .preferredColorScheme(Theme.colorScheme(appearance))
        .tint(Theme.accent)
        .fullScreenCover(isPresented: $practicing) {
            PracticeView {
                page = cards.count - 1
                practicing = false
            }
        }
    }

    @ViewBuilder
    private func card(_ c: Card) -> some View {
        switch c {
        case .welcome: WelcomeCard()
        case .places: PlacesCard()
        case .marking: MarkingCard()
        case .turningIn: TurningInCard()
        case .grades: GradesCard()
        case .settings: SettingsCard()
        case .neoPlan: NeoPlanCard(connected: $connected)
        }
    }

    private var bottomBar: some View {
        VStack(spacing: Theme.Space.l) {
            if mode == .cards {
                HStack(spacing: 7) {
                    ForEach(0..<cards.count, id: \.self) { i in
                        Capsule().fill(i == page ? Theme.accent : Theme.divider)
                            .frame(width: i == page ? 18 : 7, height: 7)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Page \(page + 1) of \(cards.count)")
            }
            Button {
                if cards[page] == .welcome { practicing = true } else if last { finish() } else { go(page + 1) }
            } label: {
                Text(cards[page] == .welcome ? "Start practice" : last ? "Done" : "Next")
                    .font(Theme.headline)
                    .foregroundStyle(Theme.onAccent)
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
        }
        .padding(.horizontal, Theme.Space.xl)
        .padding(.top, Theme.Space.m)
        .padding(.bottom, Theme.Space.s)
    }

    private func go(_ p: Int) {
        if reduceMotion { page = p } else { withAnimation(.easeInOut) { page = p } }
    }

    private func finish() {
        shown = true
        dismiss()
    }
}

// MARK: - Card layout

/// One card: the art in a box at the top, a serif title, a lede, then the details.
private struct TourCard<Art: View, Details: View>: View {
    let title: String
    let lede: String
    @ViewBuilder let art: Art
    @ViewBuilder let details: Details

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                art
                    .frame(maxWidth: .infinity)
                    .padding(Theme.Space.l)
                    .card()
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text(title).font(Theme.display).accessibilityAddTraits(.isHeader)
                    Text(lede).font(Theme.body).foregroundStyle(Theme.secondary)
                }
                details
            }
            .padding(.horizontal, Theme.Space.xl)
            .padding(.top, Theme.Space.s)
            .padding(.bottom, Theme.Space.l)
        }
    }
}

/// A symbol and a line of text, for the details under a card.
private struct Point: View {
    let symbol: String
    let title: String
    let detail: String
    @ScaledMetric(relativeTo: .subheadline) private var iconWidth: CGFloat = 24

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Space.m) {
            Image(systemName: symbol)
                .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.accent)
                .frame(width: iconWidth)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Theme.subhead.weight(.semibold))
                Text(detail).font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A pill like the To Do row's Submit ↗ / Turn in.
private struct Pill: View {
    let text: String
    var green = false
    var body: some View {
        Text(text)
            .font(Theme.footnote.weight(.semibold))
            .padding(.horizontal, 11).padding(.vertical, 5)
            .foregroundStyle(green ? Theme.good : Theme.accent)
            .background(green ? Theme.goodSoft : Theme.accentSoft, in: Capsule())
    }
}

// MARK: - 1 · Welcome

private struct WelcomeCard: View {
    var body: some View {
        TourCard(title: "Welcome to Dewey",
                 lede: "Your Schoology, easier to read: what's due, your grades and your classes in one place.") {
            ZStack {
                ForEach(0..<4, id: \.self) { i in
                    Circle().stroke(Theme.accent.opacity(0.25 + 0.18 * Double(3 - i)), lineWidth: 2.5)
                        .frame(width: 60 + CGFloat(i) * 34, height: 60 + CGFloat(i) * 34)
                }
                DropShape().fill(Theme.accent).frame(width: 30, height: 40)
            }
            .frame(height: 170)
            .accessibilityHidden(true)
        } details: {
            Point(symbol: "eye", title: "Dewey only reads Schoology",
                  detail: "Submitting, quizzes and posts happen on Schoology's own page inside the app, so nothing changes there unless you do it.")
            Point(symbol: "theatermasks", title: "Practice with a pretend week",
                  detail: "Dewey walks you through marking work and handing it in, in the real screens. Nothing you tap reaches Schoology.")
            Point(symbol: "arrow.uturn.backward", title: "Skip any time",
                  detail: "The tour and the reference cards are in Settings → Help.")
        }
    }
}

// MARK: - 2 · Where everything is

private struct PlacesCard: View {
    private let tabs: [(String, String, String)] = [
        ("checklist", "To Do", "Everything due, by class. Start here."),
        ("chart.bar", "Grades", "Every class's letter, new scores, what-if."),
        ("tray", "Inbox", "Messages from teachers, new materials and grades."),
        ("square.grid.2x2", "Classes", "Your classes on a shelf: materials, files, search."),
        ("globe", "Schoology", "The full website, for anything else."),
    ]

    var body: some View {
        TourCard(title: "Where everything is",
                 lede: "Five tabs along the bottom.") {
            VStack(spacing: 0) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { i, t in
                    if i > 0 { Divider().overlay(Theme.divider) }
                    HStack(spacing: Theme.Space.m) {
                        Image(systemName: t.0)
                            .font(Theme.subhead.weight(.semibold)).foregroundStyle(.white)
                            .frame(width: 32, height: 32)
                            .background(Theme.spineColor(i), in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(t.1).font(Theme.subhead.weight(.semibold))
                            Text(t.2).font(Theme.footnote).foregroundStyle(Theme.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, Theme.Space.s)
                    .accessibilityElement(children: .combine)
                }
            }
        } details: {
            Point(symbol: "gearshape", title: "The gear by each title",
                  detail: "Opens Settings on that tab's own page, with everything else one tap back.")
            Point(symbol: "arrow.down", title: "Pull down to refresh",
                  detail: "Offline, you see the last saved copy and how old it is.")
        }
    }
}

// MARK: - 3 · Marking work (try it)

private struct MarkingCard: View {
    enum Step { case todo, done, turnedIn }
    @State private var hw = Step.todo
    @State private var studied = false

    var body: some View {
        TourCard(title: "Marking work",
                 lede: "Try it: tap a circle, then Turn in.") {
            VStack(spacing: 0) {
                hwRow
                Divider().overlay(Theme.divider).padding(.vertical, Theme.Space.s)
                testRow
            }
        } details: {
            Point(symbol: "circle", title: "Circle = Done, just for you",
                  detail: "Done doesn't hand anything in. Submit or Turn in does.")
            Point(symbol: "checkmark.seal.fill", title: "Green seal = turned in",
                  detail: "“Submitted on Schoology” only when Schoology's dropbox has it. “Not on Schoology” when you marked paper or lab-site work.")
            Point(symbol: "hand.draw", title: "Swipe and hold",
                  detail: "Swipe a row left for Mark turned in, right for Done. Press and hold for every status.")
            NavigationLink { StatusGuideView() } label: {
                Text("How statuses work").font(Theme.subhead.weight(.semibold))
            }
            .padding(.leading, 24 + Theme.Space.m)
        }
    }

    private var hwRow: some View {
        HStack(alignment: .center, spacing: Theme.Space.m) {
            Button {
                withAnimation(.snappy) { hw = hw == .todo ? .done : .todo }
            } label: {
                GuideMark(kind: hw == .todo ? .todo : hw == .done ? .done : .turnedIn)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-10)
            .accessibilityLabel(hw == .todo ? "Mark done" : "Mark not done")
            VStack(alignment: .leading, spacing: 2) {
                Text("Enzyme lab report").font(Theme.body)
                Text(hwStatus).font(Theme.footnote.weight(.medium))
                    .foregroundStyle(hw == .turnedIn ? Theme.good : hw == .done ? Theme.accent : Theme.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            switch hw {
            case .todo: EmptyView()
            case .done:
                Button { withAnimation(.snappy) { hw = .turnedIn } } label: { Pill(text: "Turn in", green: true) }
                    .buttonStyle(.plain)
            case .turnedIn:
                Button { withAnimation(.snappy) { hw = .todo } } label: { Pill(text: "Undo") }
                    .buttonStyle(.plain)
            }
        }
    }

    private var hwStatus: String {
        switch hw {
        case .todo: "HW · due tomorrow"
        case .done: "Done · not handed in yet"
        case .turnedIn: "Turned in · not on Schoology"
        }
    }

    private var testRow: some View {
        HStack(alignment: .center, spacing: Theme.Space.m) {
            Button { withAnimation(.snappy) { studied.toggle() } } label: {
                GuideMark(kind: studied ? .studied : .todo)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-10)
            .accessibilityLabel(studied ? "Mark not studied" : "Mark studied")
            VStack(alignment: .leading, spacing: 2) {
                Text("Unit 3 test").font(Theme.body)
                Text(studied ? "Studied · no night-before reminder" : "TEST · Friday · not studied")
                    .font(Theme.footnote.weight(.medium))
                    .foregroundStyle(studied ? Theme.accent : Theme.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - 4 · Turning in

private struct TurningInCard: View {
    var body: some View {
        TourCard(title: "Turning in",
                 lede: "Every class turns in its own way. Dewey shows the right button.") {
            VStack(alignment: .leading, spacing: Theme.Space.m) {
                way("Schoology dropbox", Pill(text: "Submit ↗"))
                way("Lab site", Pill(text: "Lab site ↗"))
                way("Paper or in class", Pill(text: "Mark turned in", green: true))
                way("Quiz", Pill(text: "Take on Schoology ↗"))
            }
        } details: {
            Point(symbol: "arrow.up.right.square", title: "↗ opens Schoology's real page",
                  detail: "Upload there and tap Done. Dewey checks again, and when Schoology has it the row turns green.")
            Point(symbol: "doc.text", title: "Paper work is never “missing”",
                  detail: "Schoology only knows its own dropbox. Paper work shows late until you mark it turned in.")
            Point(symbol: "chevron.right", title: "› stays in the app, ↗ goes to Schoology or a website",
                  detail: "Tap a row for the assignment: instructions, files and how it's turned in.")
        }
    }

    private func way(_ title: String, _ pill: Pill) -> some View {
        HStack {
            Text(title).font(Theme.subhead).foregroundStyle(Theme.text)
            Spacer()
            pill
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - 5 · Grades and what-if

private struct GradesCard: View {
    var body: some View {
        TourCard(title: "Grades and what-if",
                 lede: "Your real grade first. Then try out scores before they happen.") {
            VStack(spacing: Theme.Space.m) {
                HStack(alignment: .firstTextBaseline, spacing: Theme.Space.m) {
                    Text("B+").font(Theme.display).foregroundStyle(Theme.classColor(2))
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Chemistry").font(Theme.subhead.weight(.semibold))
                        Text("88.4% · 1.6% to an A−").font(Theme.footnote.monospacedDigit()).foregroundStyle(Theme.secondary)
                    }
                    Spacer()
                }
                Text("What if I get __ / 20?")
                    .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.accent)
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .overlay(Capsule().strokeBorder(Theme.accent, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Chemistry, B plus, 88.4 percent. What if I get a score out of 20?")
        } details: {
            Point(symbol: "sparkles", title: "New grades first",
                  detail: "New scores sit at the top until you've seen them. Tap a class for every item, its effect on your grade, comments and rubrics.")
            Point(symbol: "slider.horizontal.3", title: "What-if",
                  detail: "In a class, tap What-if (top right) and type a score for anything not graded yet to see your new grade.")
            Point(symbol: "target", title: "Plan an upcoming test",
                  detail: "Also in What-if: the score you need on a test to reach a grade.")
            Point(symbol: "lock", title: "Just a preview",
                  detail: "Leaving the class clears it. Nothing is saved or sent anywhere.")
        }
    }
}

// MARK: - 6 · Settings

private struct SettingsCard: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Theme.appearanceKey) private var appearance = "system"
    @State private var allowed: Bool?

    var body: some View {
        TourCard(title: "Settings",
                 lede: "Set these two now, or later from the gear on To Do, Grades, Inbox or Classes.") {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("Appearance").font(Theme.subhead.weight(.semibold))
                    Picker("Appearance", selection: $appearance) {
                        Text("System").tag("system")
                        Text("Light").tag("light")
                        Text("Dark").tag("dark")
                    }
                    .pickerStyle(.segmented)
                }
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("Reminders").font(Theme.subhead.weight(.semibold))
                    if allowed == true {
                        Label("Notifications are on", systemImage: "checkmark.circle.fill")
                            .font(Theme.subhead).foregroundStyle(Theme.good)
                    } else {
                        Button {
                            Task {
                                allowed = await Reminders.shared.requestPermission()
                                Reminders.shared.schedule(items: model.todoItems(), courses: model.courses)
                            }
                        } label: {
                            Label("Allow notifications", systemImage: "bell")
                                .font(Theme.subhead.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 36)
                        }
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.capsule)
                        if allowed == false {
                            Text("Turned off in iOS Settings → Dewey → Notifications.")
                                .font(Theme.footnote).foregroundStyle(Theme.secondary)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } details: {
            Point(symbol: "bell.badge", title: "Notifications",
                  detail: "2 hours before work that isn't submitted, the night before a test, new grades and messages. Pick which in Settings → Notifications.")
            Point(symbol: "checklist", title: "To Do view",
                  detail: "Week (by class, under the week strip) or Urgent first (one list: late, today, this week, later).")
            Point(symbol: "books.vertical", title: "Your classes",
                  detail: "Press and hold a book on the Classes shelf to take it off (study hall, advisory). Press and hold a class heading on To Do to change its call number.")
        }
        .task {
            let s = await UNUserNotificationCenter.current().notificationSettings()
            switch s.authorizationStatus {
            case .authorized, .provisional, .ephemeral: allowed = true
            case .denied: allowed = false
            default: allowed = nil
            }
        }
    }
}

// MARK: - 7 · neo-plan

private struct NeoPlanCard: View {
    @Binding var connected: Bool

    var body: some View {
        TourCard(title: "Using neo-plan?",
                 lede: "If you also use the Dewey extension on a computer with neo-plan, connect it here and both stay in step.") {
            HStack(alignment: .top, spacing: Theme.Space.s) {
                ForEach(0..<3, id: \.self) { col in
                    VStack(spacing: 6) {
                        Theme.spineColor(col).frame(height: 6).clipShape(Capsule())
                        ForEach(0..<(3 - col), id: \.self) { _ in
                            RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous)
                                .fill(Theme.background).frame(height: 26)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 110, alignment: .top)
            .accessibilityHidden(true)
        } details: {
            Point(symbol: "arrow.triangle.2.circlepath", title: "What syncs",
                  detail: "New work, Done, Studied, Turned in and the type (HW, Test, Task, CW). Never your password, grades or messages.")
            Point(symbol: "person.crop.circle.badge.questionmark", title: "Optional",
                  detail: "Dewey works fully without it. No neo-plan account? Skip this.")
            if connected {
                Label("neo-plan is connected", systemImage: "checkmark.circle.fill")
                    .font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.good)
            } else {
                NavigationLink { NeoPlanView(connected: $connected) } label: {
                    Text("Connect neo-plan")
                        .font(Theme.subhead.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                Text("Not using it? Tap Done. It's always in Settings → Help → neo-plan.")
                    .font(Theme.footnote).foregroundStyle(Theme.secondary)
            }
        }
    }
}
