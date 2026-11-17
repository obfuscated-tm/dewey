import SwiftUI
import WidgetKit

/// Settings → Schedule: the bell schedule behind the "Now in class" widget
/// (BellSchedule). Day types the student makes and names (each a list of periods
/// with times), which type each weekday uses, and what each period is (class,
/// room, teacher). Nothing is pre-made; everything is looked up by id, so a day
/// type deleted while it's open just isn't there any more.
struct ScheduleSettings: View {
    @Environment(AppModel.self) private var model
    @State private var schedule = BellStore.load()
    /// The day type just added, opened straight away to be named.
    @State private var opened: UUID?

    private static let weekdays: [(Int, String)] = [(2, "Monday"), (3, "Tuesday"), (4, "Wednesday"), (5, "Thursday"), (6, "Friday")]

    var body: some View {
        DewForm {
            if !schedule.isEmpty {
                Section {
                    Picker("Today", selection: todayChoice) {
                        Text(usualName).tag(TodayChoice.usual)
                        ForEach(schedule.dayTypes.filter { $0.id != usualID }) { Text(Self.title($0)).tag(TodayChoice.type($0.id)) }
                        if usualID != nil { Text("No school").tag(TodayChoice.none) }
                    }
                }
            }
            Section("Day types") {
                ForEach(schedule.dayTypes) { type in
                    NavigationLink {
                        DayTypeEditor(schedule: $schedule, id: type.id, classes: model.classes)
                    } label: {
                        LabeledContent(Self.title(type), value: Self.summary(schedule.slots(in: type.id)))
                    }
                }
                Button("Add a day type") { opened = schedule.addDayType(weekdays: Self.weekdays.map(\.0)) }
                NavigationLink("Use my school's schedule") {
                    SchoolPicker(replacing: !schedule.dayTypes.isEmpty) { template in
                        // The school's day types and week; the student's classes, rooms and teachers stay.
                        var new = template.schedule
                        new.periods = schedule.periods
                        schedule = new
                        Self.fill(&schedule, from: model.classes)
                    }
                }
            }
            if !schedule.dayTypes.isEmpty {
                Section("Week") {
                    ForEach(Self.weekdays, id: \.0) { day, name in
                        Picker(name, selection: weekBinding(day)) {
                            Text("No school").tag(UUID?.none)
                            ForEach(schedule.dayTypes) { Text(Self.title($0)).tag(UUID?.some($0.id)) }
                        }
                    }
                }
            }
            if !schedule.periodNames.isEmpty {
                Section("Periods") {
                    ForEach(schedule.periodNames, id: \.self) { name in
                        NavigationLink {
                            PeriodEditor(name: name, period: periodBinding(name), classes: model.classes) { schedule.remove(period: name) }
                        } label: {
                            LabeledContent(name, value: periodSummary(name))
                        }
                    }
                }
            }
        }
        .navigationTitle("Schedule")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $opened) { id in
            DayTypeEditor(schedule: $schedule, id: id, classes: model.classes)
        }
        .onAppear {
            Self.fill(&schedule, from: model.classes)
        }
        .onChange(of: schedule) { _, new in
            BellStore.save(new)
            WidgetCenter.shared.reloadTimelines(ofKind: "Period")
        }
    }

    static func title(_ t: BellSchedule.DayType) -> String { t.name.isEmpty ? "Day type" : t.name }

    // ── Today ────────────────────────────────────────────────────────────

    private enum TodayChoice: Hashable {
        case usual, none
        case type(UUID)
    }

    private var usualID: UUID? { schedule.week[Calendar.current.component(.weekday, from: Date())] }
    private var usualName: String { schedule.dayTypes.first { $0.id == usualID }.map(Self.title) ?? "No school" }

    private var todayChoice: Binding<TodayChoice> {
        let key = BellSchedule.dayKey(Date())
        return Binding(
            get: {
                guard let o = schedule.today, o.day == key else { return .usual }
                return o.dayType.map { .type($0) } ?? .none
            },
            set: {
                switch $0 {
                case .usual: schedule.today = nil
                case .none: schedule.today = .init(day: key, dayType: nil)
                case .type(let id): schedule.today = .init(day: key, dayType: id)
                }
            })
    }

    // ── Rows ─────────────────────────────────────────────────────────────

    static func summary(_ slots: [BellSchedule.Slot]) -> String {
        guard let first = slots.first, let last = slots.map(\.end).max() else { return "No periods" }
        return "\(BellTime.text(first.start)) – \(BellTime.text(last))"
    }

    private func periodSummary(_ name: String) -> String {
        let p = schedule.periods[name] ?? .init()
        return [p.title, p.room].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private func weekBinding(_ day: Int) -> Binding<UUID?> {
        Binding(get: { schedule.week[day] }, set: { schedule.week[day] = $0 })
    }

    private func periodBinding(_ name: String) -> Binding<BellSchedule.Period> {
        Binding(get: { schedule.periods[name] ?? .init() }, set: { schedule.periods[name] = $0 })
    }

    /// The periods Schoology names for the student's classes ("P1"…), in order.
    static func classPeriods(_ classes: [Course]) -> [String] {
        Array(Set(classes.map(\.periodLabel).filter { !$0.isEmpty })).sorted { $0.localizedStandardCompare($1) == .orderedAscending }
    }

    // ── Classes from Schoology ───────────────────────────────────────────

    /// A period nobody has described yet takes its class from Schoology: "P3" → the class whose section says P3.
    static func fill(_ schedule: inout BellSchedule, from classes: [Course]) {
        let names = Set(schedule.dayTypes.flatMap { $0.slots.map(\.name) })
        for (i, c) in classes.enumerated() where names.contains(c.periodLabel) && schedule.periods[c.periodLabel] == nil {
            schedule.periods[c.periodLabel] = .init(title: c.shortTitle, teacher: teacher(c.sectionTitle), color: i)
        }
    }

    /// "1404: GarciaJ p4 T1" → "GarciaJ"; "" when the section doesn't name anyone.
    static func teacher(_ sectionTitle: String) -> String {
        guard let m = sectionTitle.firstMatch(of: /:\s*([A-Za-z][A-Za-z'’\-]+)/) else { return "" }
        let name = String(m.1)
        return name.firstMatch(of: /(?i)^p(?:er(?:iod)?)?\d*$/) == nil ? name : ""
    }
}

/// Minutes after midnight ↔ the clock.
enum BellTime {
    static func date(_ minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date()) ?? Date()
    }
    static func minutes(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
    static func text(_ minutes: Int) -> String { date(minutes).formatted(date: .omitted, time: .shortened) }
}

/// One day type: its name and its periods in time order, each a name and two times.
private struct DayTypeEditor: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var schedule: BellSchedule
    let id: UUID
    let classes: [Course]

    private var type: BellSchedule.DayType? { schedule.dayTypes.first { $0.id == id } }
    private var slots: [BellSchedule.Slot] { schedule.slots(in: id) }

    var body: some View {
        DewForm {
            if type != nil {
                Section {
                    TextField("Name", text: Binding(get: { type?.name ?? "" }, set: { schedule.name(dayType: id, $0) }))
                }
                if slots.isEmpty {
                    Section {
                        if !classPeriods.isEmpty { Button("Add my classes") { addClasses() } }
                        ForEach(copyable) { other in
                            Button("Same as \(ScheduleSettings.title(other))") { copy(from: other.id) }
                        }
                    }
                }
                Section {
                    ForEach(slots) { slot in
                        HStack(spacing: Theme.Space.s) {
                            TextField("Period", text: Binding(get: { schedule.slot(slot.id)?.name ?? "" }, set: { schedule.rename(slot: slot.id, to: $0) }))
                                .frame(minWidth: 56)
                                .accessibilityLabel("Period name")
                            Spacer(minLength: 0)
                            DatePicker("Starts", selection: time(slot.id, \.start), displayedComponents: .hourAndMinute).labelsHidden()
                            DatePicker("Ends", selection: time(slot.id, \.end), displayedComponents: .hourAndMinute).labelsHidden()
                        }
                    }
                    .onDelete { offsets in
                        let ids = offsets.map { slots[$0].id }
                        ids.forEach { schedule.remove(slot: $0) }
                    }
                    Button("Add a period") { add(BellSchedule.nextName(in: slots)) }
                    Button("Add lunch or a break") { add("Lunch") }
                }
                Section {
                    Button("Delete this day type", role: .destructive) {
                        schedule.remove(dayType: id)
                        dismiss()
                    }
                }
            }
        }
        .navigationTitle(type.map(ScheduleSettings.title) ?? "")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func time(_ slot: UUID, _ key: WritableKeyPath<BellSchedule.Slot, Int>) -> Binding<Date> {
        Binding(get: { BellTime.date(schedule.slot(slot)?[keyPath: key] ?? 0) },
                set: { new in schedule.update(slot: slot) { $0[keyPath: key] = BellTime.minutes(new) } })
    }

    private func add(_ name: String) {
        schedule.setSlots(slots + [BellSchedule.nextSlot(after: slots, name: name)], in: id)
        ScheduleSettings.fill(&schedule, from: classes)
    }

    private var classPeriods: [String] { ScheduleSettings.classPeriods(classes) }

    /// A period for every class Schoology names one for, back to back from 8:00, to adjust.
    private func addClasses() {
        var new: [BellSchedule.Slot] = []
        for name in classPeriods { new.append(BellSchedule.nextSlot(after: new, name: name)) }
        schedule.setSlots(new, in: id)
        ScheduleSettings.fill(&schedule, from: classes)
    }

    /// The other day types that have periods, to start this one from.
    private var copyable: [BellSchedule.DayType] { schedule.dayTypes.filter { $0.id != id && !$0.slots.isEmpty } }

    private func copy(from other: UUID) {
        schedule.setSlots(schedule.slots(in: other).map { .init(name: $0.name, start: $0.start, end: $0.end) }, in: id)
    }
}

/// What a period is: the class (one of the student's, or typed), the room, the teacher.
private struct PeriodEditor: View {
    @Environment(\.dismiss) private var dismiss
    let name: String
    @Binding var period: BellSchedule.Period
    let classes: [Course]
    let onDelete: () -> Void
    /// "Something else" picked: the class is typed.
    @State private var typing = false

    private enum Choice: Hashable { case none, course(Int), other }

    private var choice: Binding<Choice> {
        Binding(
            get: {
                if typing { return .other }
                if let i = classes.firstIndex(where: { $0.shortTitle == period.title }) { return .course(i) }
                return period.title.isEmpty ? .none : .other
            },
            set: {
                switch $0 {
                case .none: typing = false; period = .init(room: period.room)
                case .other: typing = true; period.color = nil
                case .course(let i):
                    typing = false
                    period = .init(title: classes[i].shortTitle, room: period.room,
                                   teacher: ScheduleSettings.teacher(classes[i].sectionTitle), color: i)
                }
            })
    }

    var body: some View {
        DewForm {
            Section {
                Picker("Class", selection: choice) {
                    Text("None").tag(Choice.none)
                    ForEach(Array(classes.enumerated()), id: \.offset) { i, c in Text(c.shortTitle).tag(Choice.course(i)) }
                    Text("Something else").tag(Choice.other)
                }
                if choice.wrappedValue == .other {
                    TextField("Class name", text: $period.title)
                }
                TextField("Room", text: $period.room)
                TextField("Teacher", text: $period.teacher)
            }
            Section {
                Button("Delete \(name)", role: .destructive) {
                    dismiss()
                    onDelete()
                }
            }
        }
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Schools whose bell schedule Dewey knows, by district. Picking one replaces the day
/// types and the week, after asking when there are day types already.
private struct SchoolPicker: View {
    @Environment(\.dismiss) private var dismiss
    let replacing: Bool
    let onPick: (BellTemplate) -> Void
    @State private var pending: BellTemplate?

    private var districts: [String] { Array(Set(BellTemplate.all.map(\.district))).sorted() }

    var body: some View {
        DewForm {
            ForEach(districts, id: \.self) { district in
                Section(district) {
                    ForEach(BellTemplate.all.filter { $0.district == district }) { t in
                        Button {
                            if replacing { pending = t } else { use(t) }
                        } label: {
                            row(t)
                        }
                    }
                }
            }
        }
        .navigationTitle("Schools")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Replace your day types with \(pending?.school ?? "")'s?", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }), titleVisibility: .visible) {
            Button("Replace", role: .destructive) { if let t = pending { use(t) } }
        } message: {
            Text("Your classes, rooms and teachers stay.")
        }
    }

    private func use(_ t: BellTemplate) {
        onPick(t)
        dismiss()
    }

    private func row(_ t: BellTemplate) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(t.school).foregroundStyle(.primary)
            Text("\(t.year) · \(t.days.count) day types").font(.subheadline).foregroundStyle(.secondary)
        }
    }
}

#if DEBUG
/// Xcode builds only: a pretend half hour (BellSchedule.test) so the widget can be watched
/// through a bell, a passing period and lunch without waiting for school.
struct WidgetTestButton: View {
    @State private var until = BellStore.testUntil

    var body: some View {
        Button(until == nil ? "Test the widget for 30 minutes" : "Stop the widget test") {
            if until == nil {
                let now = Date(), end = Calendar.current.date(byAdding: .minute, value: 31, to: now) ?? now
                BellStore.startTest(.test(from: BellStore.load(), at: now), until: end)
                until = end
            } else {
                BellStore.stopTest()
                until = nil
            }
            WidgetCenter.shared.reloadTimelines(ofKind: "Period")
        }
        .onAppear { until = BellStore.testUntil }
    }
}
#endif
