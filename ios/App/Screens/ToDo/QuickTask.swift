import SwiftUI

/// To Do's +: a short sheet over the keyboard. Type the task, with its day, time
/// and class in plain words if you like ("chem lab fri after school", TaskParse);
/// a grey line shows what will be saved. Or pick on the pills (When, Class, Kind);
/// a pill wins over what was typed. Changing a task later, and its
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
    /// The sheet fits what's in it (bigger text, bigger sheet).
    @State private var height: CGFloat = 150

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
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: Theme.Space.s) {
                TextField("Chem lab fri after school", text: $text)
                    .font(Theme.body)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit { add(parsed, due: due, section: s, kind: k) }
                    .accessibilityLabel("Task")
                    .coachTarget("quick.field")
                    // The tour waits for a task typed with its day, time and class.
                    .onChange(of: parsed.day != nil && parsed.time != nil && parsed.sectionId != nil) { _, all in
                        if all && model.practice { Coach.shared.note("quickParsed") }
                    }
                Button { add(parsed, due: due, section: s, kind: k) } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(Theme.title)
                        .foregroundStyle(cleanText.isEmpty ? Theme.tertiary : Theme.accent)
                }
                .buttonStyle(.borderless)
                .disabled(cleanText.isEmpty)
                .accessibilityLabel("Add task")
            }
            // What will be saved, read from the words and the pills together.
            preview(parsed.title, due: due, section: s, kind: k)
            // One row: When (day and time), Class, Kind. Teal = you picked it here; what was
            // only typed shows in the grey line above, not on the pills.
            // Side by side; stacked when big text won't fit them on one line.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 6) { pills(d: d, t: t, s: s, k: k) }
                VStack(alignment: .leading, spacing: 6) { pills(d: d, t: t, s: s, k: k) }
            }
            .menuOrder(.fixed)
        }
        .padding(Theme.Space.l)
        .padding(.top, Theme.Space.s)
        .fixedSize(horizontal: false, vertical: true)
        .background(GeometryReader { g in Color.clear.preference(key: SheetHeight.self, value: g.size.height) })
        .onPreferenceChange(SheetHeight.self) { h in if h > 0 { height = h } }
        .coachTarget("quick.sheet")
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Theme.background)
        .presentationDetents([.height(height)])
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

    @ViewBuilder private func pills(d: Date?, t: TaskParse.Clock?, s: String?, k: ItemType) -> some View {
        whenMenu(d: d, t: t)
        Menu {
            Picker("Class", selection: Binding(get: { s }, set: { v in section = v.map { .some($0) } ?? Choice.none })) {
                ForEach(model.classes) { c in Text(c.shortTitle).tag(Optional(c.sectionId)) }
                Text("No class").tag(String?.none)
            }
        } label: {
            pill(section.map { $0.value.flatMap { model.course($0)?.shortTitle } ?? "No class" } ?? "Class",
                 icon: "graduationcap", on: section != nil)
        }
        .accessibilityLabel(s.flatMap { model.course($0)?.shortTitle }.map { "Class: \($0)" } ?? "Class")
        Menu {
            Picker("Kind", selection: Binding(get: { k }, set: { type = $0 })) {
                ForEach([ItemType.task, .assignment, .exam, .classwork], id: \.self) { t in Text(Self.kindName(t)).tag(t) }
            }
        } label: {
            pill(type.map(Self.kindName) ?? "Kind", icon: "tag", on: type != nil)
        }
        .accessibilityLabel("Kind: \(Self.kindName(k))")
    }

    /// "Chem lab · Fri · 3:30 pm · Chemistry · Homework": grey, what ↑ will save.
    private func preview(_ title: String, due: Date?, section: String?, kind: ItemType) -> some View {
        let parts = [title.isEmpty ? nil : title,
                     due.map(Self.dueText) ?? (cleanText.isEmpty ? nil : "No date"),
                     section.flatMap { model.course($0)?.shortTitle },
                     kind == .task ? nil : Self.kindName(kind)].compactMap { $0 }
        return Text(parts.isEmpty ? "Add a day, time or class in the words, or below" : parts.joined(separator: " · "))
            .font(Theme.footnote)
            .foregroundStyle(parts.isEmpty ? Theme.tertiary : Theme.secondary)
            .lineLimit(2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel(parts.isEmpty ? "Nothing set yet" : "Will add: \(parts.joined(separator: ", "))")
            .animation(nil, value: parts)
    }

    /// Day and time in one menu: the usual picks are one tap after opening it.
    private func whenMenu(d: Date?, t: TaskParse.Clock?) -> some View {
        let today = cal.startOfDay(for: now)
        let tomorrow = cal.date(byAdding: .day, value: 1, to: today)!
        let pickedDay = day.flatMap(\.value)
        let pickedTime = time.flatMap(\.value)
        func dayToggle(_ name: String, _ date: Date) -> some View {
            Toggle(name, isOn: Binding(get: { pickedDay.map { cal.isDate($0, inSameDayAs: date) } ?? false },
                                       set: { on in day = on ? .some(date) : nil }))
        }
        func timeToggle(_ name: String, _ c: TaskParse.Clock) -> some View {
            Toggle(name, isOn: Binding(get: { pickedTime == c }, set: { on in time = on ? .some(c) : nil }))
        }
        let label: String = {
            if day == nil && time == nil { return "When" }
            let parts = [pickedDay.map(dayLabel), pickedTime.map { clockText($0) }].compactMap { $0 }
            return parts.isEmpty ? "No date" : parts.joined(separator: " · ")
        }()
        return Menu {
            Section("Day") {
                dayToggle("Today", today)
                dayToggle("Tomorrow", tomorrow)
                Button { picking = .day } label: { Label("Pick a day…", systemImage: "calendar") }
            }
            Section("Time") {
                timeToggle("Before school · \(clockText(TaskParse.beforeSchool))", TaskParse.beforeSchool)
                timeToggle("After school · \(clockText(TaskParse.afterSchool))", TaskParse.afterSchool)
                timeToggle("Tonight · 11:59 pm", TaskParse.tonight)
                Button { picking = .time } label: { Label("Pick a time…", systemImage: "clock") }
            }
            Section {
                Button { day = Choice.none; time = Choice.none } label: { Label("No date", systemImage: "calendar.badge.minus") }
                if day != nil || time != nil {
                    Button { day = nil; time = nil } label: { Label("Use what I typed", systemImage: "arrow.uturn.backward") }
                }
            }
        } label: {
            pill(label, icon: "calendar", on: day != nil || time != nil)
        }
        .accessibilityLabel(due(d, t).map { "When: \(Self.dueText($0))" } ?? "When: no date")
    }

    private func due(_ d: Date?, _ t: TaskParse.Clock?) -> Date? { TaskParse.due(day: d, time: t, now: now) }

    /// A pill as wide as what it says. Teal = picked here.
    private func pill(_ title: String, icon: String, on: Bool) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).imageScale(.small)
            Text(title.count > 18 ? title.prefix(17) + "…" : title).lineLimit(1).fixedSize()
            Image(systemName: "chevron.up.chevron.down").imageScale(.small).font(.caption2.weight(.semibold))
                .foregroundStyle(on ? Theme.accent.opacity(0.7) : Theme.tertiary)
        }
        .font(Theme.footnote.weight(.semibold))
        .foregroundStyle(on ? Theme.accent : Theme.secondary)
        .padding(.horizontal, 10)
        .frame(minHeight: 32)

        .background(on ? Theme.accentSoft : Theme.card, in: Capsule())
        .overlay(Capsule().stroke(on ? Theme.accent.opacity(0.5) : Theme.border, lineWidth: 1))
        .contentShape(Capsule())
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
    private static let monthDay = fmt("MMM d")
}

private struct SheetHeight: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
