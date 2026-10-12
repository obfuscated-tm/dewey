import ActivityKit
import SwiftUI
import WidgetKit

@main
struct ClassworkWidgets: WidgetBundle {
    var body: some Widget {
        TodoWidget()
        TestsWidget()
        DueActivityWidget()
    }
}

// ── Timeline ─────────────────────────────────────────────────────────────

struct TodoEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

/// Widgets can't reach Schoology; the app reloads them after each refresh.
/// Extra entries at each upcoming due time move rows into "overdue" on time.
struct TodoProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodoEntry { TodoEntry(date: Date(), snapshot: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (TodoEntry) -> Void) {
        let s = SharedStore.load()
        completion(TodoEntry(date: Date(), snapshot: context.isPreview && s.items.isEmpty ? .sample : s))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodoEntry>) -> Void) {
        let s = SharedStore.load()
        let now = Date()
        let cal = Calendar.current
        var dates = [now]
        dates += s.items.compactMap(\.due).filter { $0 > now }.prefix(12)
        if let midnight = cal.nextDate(after: now, matching: DateComponents(hour: 0), matchingPolicy: .nextTime) { dates.append(midnight) }
        let entries = Set(dates).sorted().map { TodoEntry(date: $0, snapshot: s) }
        completion(Timeline(entries: entries, policy: .never))
    }
}

extension WidgetSnapshot {
    static let sample = WidgetSnapshot(items: [
        WidgetItem(id: "1", title: "HW #20 Log Rules", course: "Pre-Calculus H", due: Date().addingTimeInterval(5 * 3600), isTest: false),
        WidgetItem(id: "2", title: "Lab 4 write-up", course: "AP Physics 1", due: Date().addingTimeInterval(30 * 3600), isTest: false),
        WidgetItem(id: "3", title: "Unit 3 Test", course: "US History", due: Date().addingTimeInterval(52 * 3600), isTest: true),
    ], updatedAt: Date(), signedIn: true, accentHex: "#1D5EA8", grades: [
        WidgetGrade(id: "g1", sectionId: "1", title: "Lab 4", course: "AP Physics 1", grade: "18 / 20", day: nil),
        WidgetGrade(id: "g2", sectionId: "2", title: "Quick Write 3", course: "World History", grade: "9 / 10", day: nil),
    ])
}

// ── Shared bits ──────────────────────────────────────────────────────────

enum W {
    static let late = Color(light: "#A63A0B", dark: "#FF8A5C")

    enum Bucket: CaseIterable {
        case late, today, tomorrow, week, later
        var label: String {
            switch self {
            case .late: "LATE"
            case .today: "TODAY"
            case .tomorrow: "TOMORROW"
            case .week: "THIS WEEK"
            case .later: "LATER"
            }
        }
    }

    static func bucket(_ d: Date?, now: Date) -> Bucket {
        guard let d else { return .later }
        if d < now { return .late }
        switch days(d, now: now) {
        case 0: return .today
        case 1: return .tomorrow
        case 2...6: return .week
        default: return .later
        }
    }

    /// Calendar days from today to `d` (0 = today).
    static func days(_ d: Date?, now: Date) -> Int {
        guard let d else { return 99 }
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: d)).day ?? 99
    }

    static func countdown(_ d: Date?, now: Date) -> String {
        switch days(d, now: now) {
        case 0: return "today"
        case 1: return "tomorrow"
        case let n: return "in \(n) days"
        }
    }

    /// A test's day; the time only when it isn't Schoology's 11:59 pm placeholder.
    static func testDay(_ d: Date?, now: Date) -> String {
        guard let d else { return "No date" }
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        let placeholder = c.hour == 23 && c.minute == 59
        let n = days(d, now: now)
        let day = n == 0 ? "Today" : n == 1 ? "Tomorrow" : n < 7 ? d.formatted(.dateTime.weekday(.abbreviated)) : d.formatted(.dateTime.month(.abbreviated).day())
        return placeholder ? day : "\(day) \(d.formatted(date: .omitted, time: .shortened))"
    }

    /// Short due text for a list row: a time today/tomorrow, else a weekday or date.
    static func short(_ d: Date?, now: Date) -> String {
        guard let d else { return "" }
        let n = days(d, now: now)
        if n == 0 || n == 1 { return d.formatted(date: .omitted, time: .shortened) }
        if n > -7 && n < 7 { return d.formatted(.dateTime.weekday(.abbreviated)) }
        return d.formatted(.dateTime.month(.abbreviated).day())
    }

    static var testTag: some View {
        Text("TEST").font(.system(size: 9, weight: .bold)).padding(.horizontal, 3).padding(.vertical, 1)
            .background(Color.primary, in: RoundedRectangle(cornerRadius: 3)).foregroundStyle(Color(UIColor.systemBackground))
    }

    @ViewBuilder static func empty(_ s: WidgetSnapshot) -> some View {
        Text(s.signedIn ? "Nothing due. Nice." : "Open Classwork to sign in.")
            .font(.system(size: 14, weight: .medium)).foregroundStyle(.secondary)
    }

    static func due(_ d: Date?, now: Date) -> String {
        guard let d else { return "No due date" }
        let cal = Calendar.current
        let time = d.formatted(date: .omitted, time: .shortened)
        if cal.isDate(d, inSameDayAs: now) { return d < now ? "Was due \(time)" : "Today \(time)" }
        if let t = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(d, inSameDayAs: t) { return "Tomorrow \(time)" }
        if d < now {
            let days = cal.dateComponents([.day], from: cal.startOfDay(for: d), to: cal.startOfDay(for: now)).day ?? 0
            return "\(days) d late"
        }
        if d.timeIntervalSince(now) < 6 * 86400 { return d.formatted(.dateTime.weekday(.abbreviated).hour().minute()) }
        return d.formatted(.dateTime.month(.abbreviated).day())
    }
}

extension Color {
    init(hex: String) {
        let v = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255, opacity: 1)
    }

    init(light: String, dark: String) {
        let l = UIColor(Color(hex: light)), d = UIColor(Color(hex: dark))
        self.init(UIColor { $0.userInterfaceStyle == .dark ? d : l })
    }
}

// ── To Do (Home Screen small / medium / large, Lock Screen rectangle) ──

struct TodoWidget: Widget {
    var body: some WidgetConfiguration {
        // Kind kept from the old "Up next" so placed widgets carry over.
        StaticConfiguration(kind: "UpNext", provider: TodoProvider()) { e in
            TodoWidgetView(entry: e).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("To Do")
        .description("Your To Do list. Tap a circle to mark it done in Classwork (not on Schoology).")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

struct TodoWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodoEntry

    private var s: WidgetSnapshot { entry.snapshot }
    private var now: Date { entry.date }
    private var accent: Color { Color(hex: s.accentHex) }

    var body: some View {
        switch family {
        case .accessoryRectangular: rectangular
        case .systemSmall: small
        default: list(max: family == .systemLarge ? 10 : 3)
        }
    }

    private var rows: [WidgetItem] { s.overdue(at: now) + s.upcoming(at: now) }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if !s.signedIn {
                Label("Open Classwork to sign in", systemImage: "person.crop.circle.badge.exclamationmark")
            } else if s.upcoming(at: now).isEmpty {
                Label("Nothing due", systemImage: "checkmark.circle")
            } else {
                ForEach(s.upcoming(at: now).prefix(2)) { it in
                    Text(it.title).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    Text(W.due(it.due, now: now)).font(.system(size: 12)).foregroundStyle(.secondary).widgetAccentable()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(AppLink.todo)
    }

    private var small: some View {
        let late = s.overdue(at: now).count
        let next = s.upcoming(at: now).first
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: "checklist").foregroundStyle(accent)
                Spacer()
                if late > 0 { Text("\(late) late").font(.system(size: 12, weight: .bold)).foregroundStyle(W.late) }
            }
            Spacer(minLength: 0)
            if let n = next {
                Text(W.due(n.due, now: now)).font(.system(size: 12, weight: .semibold)).foregroundStyle(accent)
                Text(n.title).font(.system(size: 15, weight: .bold)).lineLimit(3)
                Text(n.course).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            } else {
                W.empty(s)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(next.map { AppLink.assignment($0.id) } ?? AppLink.todo)
    }

    private func list(max: Int) -> some View {
        let all = rows
        let shown = Array(all.prefix(max))
        let groups = Dictionary(grouping: shown) { W.bucket($0.due, now: now) }
        let late = s.overdue(at: now).count
        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text("To Do").font(.system(size: 14, weight: .bold))
                Spacer()
                if late > 0 { Text("\(late) late").font(.system(size: 12, weight: .semibold)).foregroundStyle(W.late) }
                let today = s.dueToday(at: now).count
                if today > 0 { Text("\(today) today").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary) }
            }
            if shown.isEmpty {
                Spacer(minLength: 0)
                W.empty(s)
                Spacer(minLength: 0)
            } else {
                ForEach(W.Bucket.allCases, id: \.self) { b in
                    if let items = groups[b] {
                        Text(b.label).font(.system(size: 10, weight: .semibold)).foregroundStyle(.secondary).padding(.top, 2)
                        ForEach(items) { TodoRow(item: $0, now: now, wide: family == .systemLarge) }
                    }
                }
                if all.count > shown.count {
                    Text("+\(all.count - shown.count) more").font(.system(size: 11)).foregroundStyle(.secondary).padding(.leading, 26)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(AppLink.todo)
    }
}

/// One To Do row: the circle marks it, the rest opens it.
struct TodoRow: View {
    let item: WidgetItem
    let now: Date
    let wide: Bool

    var body: some View {
        let late = (item.due ?? .distantFuture) < now
        let mark = item.isTest ? (item.isMarked ? "checkmark.square.fill" : "square") : (item.isMarked ? "checkmark.circle.fill" : "circle")
        HStack(spacing: 8) {
            Button(intent: ToggleDoneIntent(id: item.id)) {
                Image(systemName: mark).font(.system(size: 17)).foregroundStyle(item.isMarked ? Color.accentColor : .secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item.isMarked ? "Mark \(item.title) not done" : (item.isTest ? "Mark \(item.title) studied" : "Mark \(item.title) done"))
            Link(destination: AppLink.assignment(item.id)) {
                HStack(spacing: 6) {
                    Text(item.title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        .strikethrough(item.isMarked && !item.isTest).foregroundStyle(item.isMarked && !item.isTest ? .secondary : .primary)
                    if item.isTest { W.testTag }
                    Spacer(minLength: 4)
                    if wide { Text(item.course).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1) }
                    Text(W.short(item.due, now: now)).font(.system(size: 11).monospacedDigit())
                        .foregroundStyle(late ? W.late : .secondary).lineLimit(1)
                }
            }
        }
    }
}

// ── Tests and grades (Home Screen small / medium, Lock Screen) ────────────

struct TestsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Tests", provider: TodoProvider()) { e in
            TestsWidgetView(entry: e).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Tests and grades")
        .description("Your next tests, with a countdown, and your latest grades.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
    }
}

struct TestsWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodoEntry

    private var s: WidgetSnapshot { entry.snapshot }
    private var now: Date { entry.date }
    private var accent: Color { Color(hex: s.accentHex) }
    private var tests: [WidgetItem] { s.tests(at: now) }

    var body: some View {
        switch family {
        case .accessoryCircular: circular
        case .accessoryRectangular: rectangular
        case .systemSmall: small
        default: medium
        }
    }

    private var circular: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: -2) {
                if let t = tests.first {
                    let d = W.days(t.due, now: now)
                    Text(d == 0 ? "Today" : "\(d)d").font(.system(size: d == 0 ? 13 : 20, weight: .bold, design: .rounded)).widgetAccentable()
                    Text("TEST").font(.system(size: 9, weight: .semibold))
                } else {
                    Text("—").font(.system(size: 18, weight: .bold))
                    Text("TEST").font(.system(size: 9, weight: .semibold))
                }
            }
        }
        .accessibilityLabel(tests.first.map { "Next test \(W.countdown($0.due, now: now)): \($0.title)" } ?? "No tests coming up")
        .widgetURL(tests.first.map { AppLink.assignment($0.id) } ?? AppLink.todo)
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let t = tests.first {
                Text("Test \(W.testDay(t.due, now: now)) · \(W.countdown(t.due, now: now))")
                    .font(.system(size: 12, weight: .semibold)).widgetAccentable().lineLimit(1)
                Text(t.title).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                Text("\(t.course) · \(t.isMarked ? "studied" : "not studied")").font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            } else {
                Label(s.signedIn ? "No tests coming up" : "Open Classwork to sign in", systemImage: "pencil.and.list.clipboard")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(tests.first.map { AppLink.assignment($0.id) } ?? AppLink.todo)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let t = tests.first {
                Text("TEST · \(W.countdown(t.due, now: now).uppercased())").font(.system(size: 11, weight: .bold)).foregroundStyle(accent)
                Spacer(minLength: 0)
                Text(t.title).font(.system(size: 15, weight: .bold)).lineLimit(3)
                Text("\(t.course) · \(W.testDay(t.due, now: now))").font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(2)
                Text(t.isMarked ? "Studied" : "Not studied").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(t.isMarked ? accent : .secondary)
            } else {
                Image(systemName: "pencil.and.list.clipboard").foregroundStyle(accent)
                Spacer(minLength: 0)
                Text(s.signedIn ? "No tests coming up" : "Open Classwork to sign in.").font(.system(size: 14, weight: .medium)).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(tests.first.map { AppLink.assignment($0.id) } ?? AppLink.todo)
    }

    private var medium: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text("Tests").font(.system(size: 13, weight: .bold))
                if tests.isEmpty {
                    Text(s.signedIn ? "None coming up" : "Sign in to Classwork").font(.system(size: 12)).foregroundStyle(.secondary)
                }
                ForEach(tests.prefix(3)) { t in
                    Link(destination: AppLink.assignment(t.id)) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text(t.title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                            Text("\(W.testDay(t.due, now: now)) · \(W.countdown(t.due, now: now))")
                                .font(.system(size: 11)).foregroundStyle(W.days(t.due, now: now) <= 1 ? accent : .secondary).lineLimit(1)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Rectangle().fill(.separator).frame(width: 0.5)
            VStack(alignment: .leading, spacing: 5) {
                Text("Latest grades").font(.system(size: 13, weight: .bold))
                let grades = s.grades ?? []
                if grades.isEmpty {
                    Text("Shows up after the app checks your grades.").font(.system(size: 12)).foregroundStyle(.secondary)
                }
                ForEach(grades) { g in
                    VStack(alignment: .leading, spacing: 0) {
                        Text(g.title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        Text("\(g.grade) · \(g.course)").font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(AppLink.todo)
    }
}

// ── Live Activity: a countdown to one due time ──────────────────────────

struct DueActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DueActivityAttributes.self) { ctx in
            DueLockScreenView(ctx: ctx)
                .activityBackgroundTint(nil)
                .widgetURL(AppLink.assignment(ctx.attributes.id))
        } dynamicIsland: { ctx in
            let accent = Color(hex: ctx.attributes.accentHex)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: ctx.attributes.isTest ? "pencil.and.list.clipboard" : "checklist")
                        .font(.title2).foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Countdown(state: ctx.state, start: ctx.attributes.startedAt).font(.system(size: 20, weight: .bold).monospacedDigit())
                        .frame(maxWidth: 90, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(ctx.attributes.title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text(ctx.attributes.course).lineLimit(1)
                        Spacer()
                        Text("Due \(ctx.state.due.formatted(date: .omitted, time: .shortened))")
                    }
                    .font(.system(size: 13)).foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: ctx.state.finished ? "checkmark.circle.fill" : "checklist").foregroundStyle(accent)
            } compactTrailing: {
                Countdown(state: ctx.state, start: ctx.attributes.startedAt).font(.system(size: 14, weight: .semibold).monospacedDigit())
                    .frame(maxWidth: 56)
            } minimal: {
                Image(systemName: ctx.state.finished ? "checkmark.circle.fill" : "checklist").foregroundStyle(accent)
            }
            .widgetURL(AppLink.assignment(ctx.attributes.id))
            .keylineTint(accent)
        }
    }
}

/// Counts down to the due time; "Done" once marked, "Due now" after.
struct Countdown: View {
    let state: DueActivityAttributes.ContentState
    let start: Date

    var body: some View {
        if state.finished {
            Text("Done")
        } else if state.due > Date() {
            Text(timerInterval: min(start, state.due)...state.due, countsDown: true).multilineTextAlignment(.trailing)
        } else {
            Text("Due now").foregroundStyle(W.late)
        }
    }
}

struct DueLockScreenView: View {
    let ctx: ActivityViewContext<DueActivityAttributes>

    var body: some View {
        let a = ctx.attributes, accent = Color(hex: a.accentHex)
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Label(a.course, systemImage: a.isTest ? "pencil.and.list.clipboard" : "checklist")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(accent).lineLimit(1)
                Spacer()
                Text("Due \(ctx.state.due.formatted(date: .omitted, time: .shortened))")
                    .font(.system(size: 13)).foregroundStyle(.secondary)
            }
            HStack(alignment: .firstTextBaseline) {
                Text(a.title).font(.system(size: 17, weight: .bold)).lineLimit(2)
                Spacer()
                Countdown(state: ctx.state, start: ctx.attributes.startedAt).font(.system(size: 24, weight: .bold).monospacedDigit())
                    .frame(maxWidth: 110, alignment: .trailing)
            }
            if !ctx.state.finished, ctx.state.due > Date() {
                ProgressView(timerInterval: min(a.startedAt, ctx.state.due)...ctx.state.due, countsDown: true) { EmptyView() } currentValueLabel: { EmptyView() }
                    .tint(accent)
            }
        }
        .padding(16)
    }
}
