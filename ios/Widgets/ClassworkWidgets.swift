import ActivityKit
import SwiftUI
import WidgetKit

@main
struct ClassworkWidgets: WidgetBundle {
    var body: some Widget {
        UpNextWidget()
        DueCountWidget()
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
    ], updatedAt: Date(), signedIn: true, accentHex: "#1D5EA8")
}

// ── Shared bits ──────────────────────────────────────────────────────────

enum W {
    static let late = Color(light: "#A63A0B", dark: "#FF8A5C")

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

struct ItemLine: View {
    let item: WidgetItem
    let now: Date
    var accent: Color

    var body: some View {
        let late = (item.due ?? .distantFuture) < now
        HStack(alignment: .top, spacing: 8) {
            RoundedRectangle(cornerRadius: 2).fill(late ? W.late : accent).frame(width: 3)
            VStack(alignment: .leading, spacing: 1) {
                Text(item.title).font(.system(size: 14, weight: item.isTest ? .bold : .semibold)).lineLimit(1)
                HStack(spacing: 4) {
                    Text(item.course).lineLimit(1)
                    Text("·")
                    Text(W.due(item.due, now: now)).foregroundStyle(late ? W.late : .secondary).lineLimit(1).layoutPriority(1)
                }
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}

// ── Up next (Home Screen small / medium / large, Lock Screen) ────────────

struct UpNextWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "UpNext", provider: TodoProvider()) { e in
            UpNextView(entry: e).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Up next")
        .description("Your next due work from the To Do list.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular, .accessoryInline])
    }
}

struct UpNextView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodoEntry

    private var s: WidgetSnapshot { entry.snapshot }
    private var now: Date { entry.date }
    private var accent: Color { Color(hex: s.accentHex) }

    var body: some View {
        switch family {
        case .accessoryInline: inline
        case .accessoryRectangular: rectangular
        case .systemSmall: small
        default: list(max: family == .systemLarge ? 7 : 3)
        }
    }

    private var next: WidgetItem? { s.upcoming(at: now).first ?? s.items.first }

    @ViewBuilder private var inline: some View {
        if let n = next { Text("\(n.title) · \(W.due(n.due, now: now))") } else { Text("Nothing due") }
    }

    @ViewBuilder private var rectangular: some View {
        if let n = next {
            VStack(alignment: .leading, spacing: 0) {
                Text(W.due(n.due, now: now)).font(.system(size: 12, weight: .semibold)).widgetAccentable()
                Text(n.title).font(.system(size: 14, weight: .semibold)).lineLimit(2)
                Text(n.course).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .widgetURL(AppLink.assignment(n.id))
        } else {
            Label(s.signedIn ? "Nothing due" : "Open Classwork to sign in", systemImage: "checkmark.circle")
        }
    }

    private var small: some View {
        let overdue = s.overdue(at: now).count
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "checklist").foregroundStyle(accent)
                Spacer()
                if overdue > 0 {
                    Text("\(overdue) late").font(.system(size: 12, weight: .bold)).foregroundStyle(W.late)
                }
            }
            Spacer(minLength: 0)
            if let n = s.upcoming(at: now).first {
                Text(W.due(n.due, now: now)).font(.system(size: 12, weight: .semibold)).foregroundStyle(accent)
                Text(n.title).font(.system(size: 15, weight: .bold)).lineLimit(3)
                Text(n.course).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
            } else {
                empty
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(s.upcoming(at: now).first.map { AppLink.assignment($0.id) } ?? AppLink.todo)
    }

    private func list(max: Int) -> some View {
        let overdue = s.overdue(at: now)
        let rows = Array((overdue + s.upcoming(at: now)).prefix(max))
        return VStack(alignment: .leading, spacing: family == .systemLarge ? 10 : 7) {
            HStack(spacing: 6) {
                Image(systemName: "checklist").foregroundStyle(accent)
                Text("Up next").font(.system(size: 14, weight: .bold))
                Spacer()
                if !overdue.isEmpty { Text("\(overdue.count) overdue").font(.system(size: 12, weight: .semibold)).foregroundStyle(W.late) }
                Text("\(s.dueToday(at: now).count) today").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
            }
            if rows.isEmpty {
                Spacer(minLength: 0)
                empty
                Spacer(minLength: 0)
            } else {
                ForEach(rows) { it in
                    Link(destination: AppLink.assignment(it.id)) { ItemLine(item: it, now: now, accent: accent) }
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(AppLink.todo)
    }

    private var empty: some View {
        Text(s.signedIn ? "Nothing due. Nice." : "Open Classwork to sign in.")
            .font(.system(size: 14, weight: .medium)).foregroundStyle(.secondary)
    }
}

// ── Due count (Lock Screen circle, small) ────────────────────────────────

struct DueCountWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DueCount", provider: TodoProvider()) { e in
            DueCountView(entry: e).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Due count")
        .description("How much is overdue or due today.")
        .supportedFamilies([.accessoryCircular])
    }
}

struct DueCountView: View {
    let entry: TodoEntry

    var body: some View {
        let s = entry.snapshot
        let late = s.overdue(at: entry.date).count
        let today = s.dueToday(at: entry.date).count
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: -2) {
                Text("\(late + today)").font(.system(size: 22, weight: .bold, design: .rounded)).widgetAccentable()
                Text(late > 0 ? "late" : "today").font(.system(size: 10, weight: .semibold))
            }
        }
        .accessibilityLabel("\(late) overdue, \(today) due today")
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
