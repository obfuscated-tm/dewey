import SwiftUI

/// To Do's +: a short sheet over the keyboard. Type the task, with its day, time
/// and class in plain words if you like ("chem lab fri after school", TaskParse);
/// or tap a chip. A chip wins over what was typed. Changing a task later, and its
/// notes, is the full TaskEditor (tap its row).
struct QuickTask: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// The class, when added from a class's card.
    var sectionId: String?

    @State private var text = ""
    /// What a chip set; nil = what was typed decides.
    @State private var day: Choice<Date>?
    @State private var time: Choice<TaskParse.Clock>?
    @State private var section: Choice<String>?
    @State private var type: ItemType?
    @State private var picking: PickWhat?
    @FocusState private var focused: Bool

    /// A chip's choice: a value, or deliberately none.
    enum Choice<T: Equatable>: Equatable { case none, some(T)
        var value: T? { if case .some(let v) = self { return v } else { return nil } }
    }
    enum PickWhat: Identifiable { case day, time
        var id: Self { self }
    }

    private let now = Date()
    private var cal: Calendar { .current }

    var body: some View {
        let parsed = TaskParse.parse(text, now: now, classes: model.classes.map { .init(sectionId: $0.sectionId, title: $0.title) })
        let d = day.map(\.value) ?? parsed.day
        let t = time.map(\.value) ?? parsed.time
        let s = section.map(\.value) ?? parsed.sectionId
        let k = type ?? parsed.type ?? .task
        let due = TaskParse.due(day: d, time: t, now: now)
        VStack(alignment: .leading, spacing: Theme.Space.m) {
            HStack(alignment: .center, spacing: Theme.Space.s) {
                TextField("Chem lab fri after school", text: $text)
                    .font(Theme.body)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit { add(parsed, due: due, section: s, kind: k) }
                    .accessibilityLabel("Task")
                Button { add(parsed, due: due, section: s, kind: k) } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(Theme.title)
                        .foregroundStyle(cleanText.isEmpty ? Theme.tertiary : Theme.accent)
                }
                .buttonStyle(.borderless)
                .disabled(cleanText.isEmpty)
                .accessibilityLabel("Add task")
            }
            // Day, time, class, kind: four small menus, all on screen. Each shows what's chosen.
            HStack(spacing: 6) {
                // Straight to the calendar (Today and Tomorrow are one tap on the row below).
                Button { picking = .day } label: {
                    chipLabel(d.map(dayLabel) ?? "Day", icon: "calendar", on: d != nil)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(d.map { "Due \(Self.menuDay.string(from: $0))" } ?? "Due day")
                Menu {
                    Picker("Time", selection: Binding(get: { t }, set: { v in time = v.map { .some($0) } ?? Choice.none })) {
                        Text("Before school · \(clockText(TaskParse.beforeSchool))").tag(Optional(TaskParse.beforeSchool))
                        Text("After school · \(clockText(TaskParse.afterSchool))").tag(Optional(TaskParse.afterSchool))
                        Text("Tonight · 11:59 pm").tag(Optional(TaskParse.tonight))
                        Text("No time").tag(TaskParse.Clock?.none)
                    }
                    Button { picking = .time } label: { Label("Pick a time…", systemImage: "clock") }
                } label: {
                    chipLabel(t.map { clockText($0, short: true) } ?? "Time", icon: "clock", on: t != nil)
                }
                Menu {
                    Picker("Class", selection: Binding(get: { s }, set: { v in section = v.map { .some($0) } ?? Choice.none })) {
                        ForEach(model.classes) { c in Text(c.shortTitle).tag(Optional(c.sectionId)) }
                        Text("No class").tag(String?.none)
                    }
                } label: {
                    chipLabel(s.flatMap { model.course($0)?.shortTitle } ?? "Class", icon: "graduationcap", on: s != nil)
                }
                Menu {
                    Picker("Kind", selection: Binding(get: { k }, set: { type = $0 })) {
                        ForEach([ItemType.task, .assignment, .exam, .classwork], id: \.self) { t in Text(Self.kindName(t)).tag(t) }
                    }
                } label: {
                    chipLabel(Self.kindName(k), icon: "tag", on: k != .task)
                }
                .accessibilityLabel("Kind: \(Self.kindName(k))")
            }
            .menuOrder(.fixed)
            .lineLimit(1)
            // One tap for the usual picks; tap one that's on to clear it.
            HStack(spacing: 6) {
                let today = cal.startOfDay(for: now)
                let tomorrow = cal.date(byAdding: .day, value: 1, to: today)!
                quick("Today", on: d.map { cal.isDate($0, inSameDayAs: today) } ?? false) {
                    day = (d.map { cal.isDate($0, inSameDayAs: today) } ?? false) ? Choice.none : .some(today)
                }
                quick("Tomorrow", on: d.map { cal.isDate($0, inSameDayAs: tomorrow) } ?? false) {
                    day = (d.map { cal.isDate($0, inSameDayAs: tomorrow) } ?? false) ? Choice.none : .some(tomorrow)
                }
                quick("After school", on: t == TaskParse.afterSchool) {
                    time = t == TaskParse.afterSchool ? Choice.none : .some(TaskParse.afterSchool)
                }
                quick("Tonight", on: t == TaskParse.tonight) {
                    time = t == TaskParse.tonight ? Choice.none : .some(TaskParse.tonight)
                }
            }
        }
        .padding(Theme.Space.l)
        .padding(.top, Theme.Space.s)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Theme.background)
        .presentationDetents([.height(158)])
        .presentationDragIndicator(.visible)
        .onAppear {
            if let sectionId { section = .some(sectionId) }
            focused = true
        }
        .sheet(item: $picking) { p in
            pickerSheet(p, day: d ?? cal.startOfDay(for: now), time: t)
        }
    }

    private var cleanText: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    // ── Pieces ───────────────────────────────────────────────────────────

    /// A menu button: the four share the row's width equally.
    private func chipLabel(_ title: String, icon: String?, on: Bool) -> some View {
        HStack(spacing: 4) {
            if let icon { Image(systemName: icon).imageScale(.small) }
            Text(title).lineLimit(1).minimumScaleFactor(0.8)
        }
        .font(Theme.footnote.weight(.semibold))
        .foregroundStyle(on ? Theme.accent : Theme.secondary)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, minHeight: 32)
        .background(on ? Theme.accentSoft : Theme.card, in: Capsule())
        .overlay(Capsule().stroke(on ? Theme.accent.opacity(0.5) : Theme.border, lineWidth: 1))
        .contentShape(Capsule())
    }

    /// A shortcut on the second row: plain text, tinted when it's what's set.
    private func quick(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(Theme.footnote.weight(.semibold))
                .lineLimit(1).minimumScaleFactor(0.8)
                .foregroundStyle(on ? Theme.accent : Theme.text)
                .frame(maxWidth: .infinity, minHeight: 30)
                .background(on ? Theme.accentSoft : Theme.divider.opacity(0.5), in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func pickerSheet(_ p: PickWhat, day: Date, time: TaskParse.Clock?) -> some View {
        NavigationStack {
            VStack {
                switch p {
                case .day:
                    // Picking a day closes the calendar.
                    DatePicker("Day", selection: Binding(get: { day }, set: { self.day = .some(cal.startOfDay(for: $0)); picking = nil }),
                               in: cal.startOfDay(for: now)..., displayedComponents: .date)
                        .datePickerStyle(.graphical)
                case .time:
                    let base = cal.date(bySettingHour: time?.hour ?? 16, minute: time?.minute ?? 0, second: 0, of: now)!
                    DatePicker("Time", selection: Binding(get: { base }, set: {
                        let c = cal.dateComponents([.hour, .minute], from: $0)
                        self.time = .some(.init(hour: c.hour ?? 16, minute: c.minute ?? 0))
                    }), displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                        .labelsHidden()
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Theme.Space.l)
            .navigationTitle(p == .day ? "Due day" : "Due time")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if p == .day {
                    ToolbarItem(placement: .cancellationAction) { Button("No date") { self.day = Choice.none; picking = nil } }
                }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { picking = nil }.fontWeight(.semibold) }
            }
        }
        .presentationDetents([p == .day ? .medium : .height(300)])
    }

    private func add(_ parsed: TaskParse.Result, due: Date?, section: String?, kind: ItemType) {
        guard !cleanText.isEmpty else { return }
        _ = model.addTask(title: parsed.title, type: kind, sectionId: section, due: due)
        dismiss()
    }

    /// The day button: "Today", "Tmrw", "Fri", or "Oct 30" further out.
    private func dayLabel(_ d: Date) -> String {
        let n = cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: d)).day ?? 0
        if n == 0 { return "Today" }
        if n == 1 { return "Tmrw" }
        return n < 7 ? Self.weekday.string(from: d) : Self.monthDay.string(from: d)
    }

    private func dayName(_ n: Int, _ date: Date) -> String {
        n == 0 ? "Today" : n == 1 ? "Tomorrow" : Self.weekday.string(from: date)
    }

    /// "3:30 pm", or "3:30" for a chip that already says before or after school.
    private func clockText(_ c: TaskParse.Clock, short: Bool = false) -> String {
        (short ? Self.bare : Self.time).string(from: cal.date(bySettingHour: c.hour, minute: c.minute, second: 0, of: now)!)
    }

    static func kindName(_ t: ItemType) -> String {
        t == .task ? "Task" : t == .assignment ? "Homework" : t == .exam ? "Test" : "Classwork"
    }

    /// "Fri, Oct 23 · 3:30 pm", or just the day when it's due by the end of it.
    static func dueText(_ d: Date) -> String {
        let day = short.string(from: d)
        return TaskEditor.isEndOfDay(d) ? day : "\(day) · \(time.string(from: d))"
    }

    private static func fmt(_ p: String) -> DateFormatter {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US"); f.dateFormat = p
        f.amSymbol = "am"; f.pmSymbol = "pm"; return f
    }
    private static let weekday = fmt("EEE")
    private static let short = fmt("EEE, MMM d")
    private static let time = fmt("h:mm a")
    private static let bare = fmt("h:mm")
    private static let menuDay = fmt("EEEE, MMM d")
    private static let monthDay = fmt("MMM d")
}
