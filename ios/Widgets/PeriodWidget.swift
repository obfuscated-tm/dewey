import SwiftUI
import WidgetKit

// "Now in class": the period in session with the time left, and what's next.
// Everything comes from the bell schedule typed in Settings → Schedule
// (BellSchedule); nothing here reaches Schoology. The countdown is a system timer
// that ticks on its own; one timeline entry a minute redraws the ring and bar.

struct PeriodEntry: TimelineEntry {
    let date: Date
    let schedule: BellSchedule
    var moment: BellSchedule.Moment { schedule.moment(at: date) }
}

struct PeriodProvider: TimelineProvider {
    func placeholder(in context: Context) -> PeriodEntry { PeriodEntry(date: Date(), schedule: BellStore.widgetSchedule()) }

    func getSnapshot(in context: Context, completion: @escaping (PeriodEntry) -> Void) {
        completion(PeriodEntry(date: Date(), schedule: BellStore.widgetSchedule()))
    }

    /// One entry a minute through the school day (from an hour before the first bell), so
    /// "12 min" is always right without a ticking seconds clock; a new timeline at midnight.
    func getTimeline(in context: Context, completion: @escaping (Timeline<PeriodEntry>) -> Void) {
        let s = BellStore.widgetSchedule(), now = Date(), cal = Calendar.current
        let completion = { (dates: [Date]) in
            // A widget test ends sooner than midnight: back to the real schedule then.
            let next = BellStore.testUntil ?? cal.startOfDay(for: now).addingTimeInterval(86_400)
            completion(Timeline(entries: dates.map { PeriodEntry(date: $0, schedule: s) }, policy: .after(next)))
        }
        let m = s.moment(at: now)
        guard let start = m.dayStart, let end = m.dayEnd, now < end,
              let minute = cal.nextDate(after: now, matching: DateComponents(second: 0), matchingPolicy: .nextTime) else {
            return completion([now])
        }
        var dates = [now], t = max(minute, start.addingTimeInterval(-3600))
        while t <= end { dates.append(t); t.addTimeInterval(60) }
        completion(dates)
    }
}

struct PeriodWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Period", provider: PeriodProvider()) { e in
            PeriodWidgetView(entry: e).containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Now in class")
        .description("The class you're in, a timer to the bell, and what's next.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct PeriodWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PeriodEntry

    private var m: BellSchedule.Moment { entry.moment }
    private var accent: Color { Accent.color(hex: SharedStore.load().accentHex) }

    var body: some View {
        Group {
            switch family {
            case .accessoryInline: inline
            case .accessoryCircular: circular
            case .accessoryRectangular: rectangular
            case .systemMedium: medium
            default: small
            }
        }
        .widgetURL(AppLink.todo)
    }

    // ── Reading the moment ───────────────────────────────────────────────

    private func color(_ b: BellSchedule.Block) -> Color {
        b.period.color.map { Accent.color(hex: PeriodColors.hex($0)) } ?? accent
    }

    private func clock(_ d: Date) -> String { d.formatted(date: .omitted, time: .shortened) }

    /// Whole minutes from now to `d`, rounded up: 9:40:20 to 9:50 is 10.
    private func minutes(to d: Date) -> Int { max(0, Int((d.timeIntervalSince(entry.date) / 60).rounded(.up))) }

    private func progress(_ b: BellSchedule.Block) -> Double { b.progress(at: entry.date) }

    /// The next class, when it's close enough to count down to (within the hour).
    private var soon: BellSchedule.Block? {
        guard m.now == nil, let n = m.next, minutes(to: n.start) <= 60 else { return nil }
        return n
    }

    /// The line for when nothing is on.
    private var idle: String {
        if entry.schedule.isEmpty { return "Set up in Settings" }
        if m.dayEnd == nil { return "No school today" }
        if let n = m.next { return "\(n.title) at \(clock(n.start))" }
        return "Done for today"
    }

    // ── Home Screen ──────────────────────────────────────────────────────

    /// The ticking countdown, worded the same in every size: "12:03 left" in class, "in 4:10" before one.
    /// iOS keeps it running.
    private func timer(_ b: BellSchedule.Block, size: CGFloat) -> some View {
        let big = Font.system(size: size, weight: .bold, design: .rounded)
        let small = Font.system(size: 13, weight: .semibold)
        let t = countdown(b).font(big).monospacedDigit()
        return (m.now != nil ? t + Text(" left").font(small).foregroundColor(.secondary) : Text("in ").font(small).foregroundColor(.secondary) + t)
            .lineLimit(1).minimumScaleFactor(0.6)
    }

    /// The ring fills as the class goes (empty while waiting for one), redrawn each minute.
    private func ring(_ value: Double, _ tint: Color, width: CGFloat) -> some View {
        ZStack {
            Circle().stroke(.secondary.opacity(0.25), lineWidth: width)
            Circle().trim(from: 0, to: value).stroke(tint, style: StrokeStyle(lineWidth: width, lineCap: .round)).rotationEffect(.degrees(-90))
        }
    }

    private func line(_ parts: [String]) -> String { parts.filter { !$0.isEmpty }.joined(separator: " · ") }

    /// 2×2, laid out like the To Do widget: a small ring where its icon sits and the room
    /// across from it, then the timer, the class and what comes after it at the bottom.
    @ViewBuilder
    private var small: some View {
        if let b = m.now ?? soon {
            let inClass = m.now != nil
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .top) {
                    // As heavy as the timer below it: 46pt across, a 7pt line.
                    ring(inClass ? progress(b) : 0, color(b), width: 7).frame(width: 46, height: 46).padding(3.5)
                    Spacer()
                    if !b.period.room.isEmpty {
                        Text(b.period.room).font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 8)
                timer(b, size: 26)
                Text(b.title).font(.system(size: 15, weight: .bold)).foregroundStyle(color(b)).lineLimit(2)
                if inClass, let n = m.next {
                    Text("then \(n.title)").font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
        } else {
            Text(idle).font(.system(size: 16, weight: .bold)).lineLimit(3)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    /// 4×2: now on the left (timer, bar, and the bell's clock time beside it); on the right the next
    /// class, then the rest of today with their start times.
    @ViewBuilder
    private var medium: some View {
        let current = m.now ?? soon
        let upcoming = entry.schedule.blocks(on: entry.date).filter { $0.start > entry.date && $0.start != soon?.start }
        if let b = current {
            let inClass = m.now != nil
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(b.title).font(.system(size: 16, weight: .bold)).foregroundStyle(color(b)).lineLimit(2).minimumScaleFactor(0.8)
                    Text(line([b.period.room, inClass ? "ends \(clock(b.end))" : "starts \(clock(b.start))"]))
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 2)
                    timer(b, size: 30)
                    ProgressView(value: inClass ? progress(b) : 0).tint(color(b))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                if let n = upcoming.first {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(inClass ? "Next" : "Then").font(.system(size: 12)).foregroundStyle(.secondary)
                        Text(n.title).font(.system(size: 15, weight: .bold)).foregroundStyle(color(n)).lineLimit(1)
                        Text(line([clock(n.start), n.period.room])).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                        let later = upcoming.dropFirst()
                        if !later.isEmpty {
                            Divider().padding(.vertical, 5)
                            ForEach(later.prefix(2)) { l in
                                HStack(alignment: .firstTextBaseline, spacing: 6) {
                                    Text(clock(l.start)).font(.system(size: 12)).monospacedDigit().foregroundStyle(.secondary)
                                    Text(l.title).font(.system(size: 13, weight: .semibold)).foregroundStyle(color(l)).lineLimit(1)
                                }
                            }
                            if later.count > 2 {
                                Text("\(later.count - 2) more").font(.system(size: 12)).foregroundStyle(.secondary)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            }
            .accessibilityElement(children: .combine)
        } else {
            Text(idle).font(.system(size: 16, weight: .bold)).lineLimit(3)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    // ── Lock Screen ──────────────────────────────────────────────────────

    private func countdown(_ b: BellSchedule.Block) -> Text {
        Text(timerInterval: entry.date...max(entry.date, m.now != nil ? b.end : b.start), countsDown: true)
    }

    @ViewBuilder
    private var circular: some View {
        if let b = m.now ?? soon {
            ZStack {
                AccessoryWidgetBackground()
                ring(m.now != nil ? progress(b) : 0, .primary, width: 4).padding(3)
                countdown(b).font(.system(size: 12, weight: .semibold)).monospacedDigit()
                    .multilineTextAlignment(.center).minimumScaleFactor(0.6).frame(width: 40)
            }
            .accessibilityLabel(b.title)
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "bell").font(.title3.weight(.semibold))
            }
            .accessibilityLabel(idle)
        }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let b = m.now ?? soon {
                Text(b.title).font(.system(size: 14, weight: .semibold)).widgetAccentable().lineLimit(1)
                (m.now != nil ? countdown(b) + Text(" left · \(clock(b.end))") : Text("in ") + countdown(b) + Text(" · \(clock(b.start))"))
                    .font(.system(size: 13)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.8)
                if m.now != nil, let n = m.next {
                    Text("Next \(n.title)").font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                } else if !b.period.room.isEmpty {
                    Text(b.period.room).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                }
            } else {
                Text(idle).font(.system(size: 14, weight: .semibold)).widgetAccentable().lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var inline: some View {
        if let b = m.now {
            Text("\(b.title) · ") + countdown(b)
        } else if let n = soon {
            Text("\(n.title) in ") + countdown(n)
        } else {
            Text(idle)
        }
    }
}

/// The class colours, as the app's Theme.classColors (the widget target can't see Theme).
enum PeriodColors {
    static let all = ["#1D5EA8", "#23794A", "#6A4BA8", "#A8324A", "#9A5B0C", "#16707A", "#5E6670", "#8A4B1D"]
    static func hex(_ i: Int) -> String { all[((i % all.count) + all.count) % all.count] }
}
