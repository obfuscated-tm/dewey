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
            summary(due: due, section: s, kind: k, typed: parsed, typedDay: day == nil && parsed.day != nil)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(0..<5, id: \.self) { n in
                        let date = cal.date(byAdding: .day, value: n, to: cal.startOfDay(for: now))!
                        chip(dayName(n, date), on: d.map { cal.isDate($0, inSameDayAs: date) } ?? false) {
                            toggle(&day, current: d, to: date) { cal.isDate($0, inSameDayAs: $1) }
                        }
                    }
                    let picked = d.map { day in !(0..<5).contains { cal.isDate(day, inSameDayAs: cal.date(byAdding: .day, value: $0, to: cal.startOfDay(for: now))!) } } ?? false
                    chip(picked ? Self.short.string(from: d!) : "Pick", icon: "calendar", on: picked) { picking = .day }
                }
                .padding(.horizontal, 1)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    chip("Before school · \(clockText(TaskParse.beforeSchool, short: true))", on: t == TaskParse.beforeSchool) { toggle(&time, current: t, to: TaskParse.beforeSchool, ==) }
                    chip("After school · \(clockText(TaskParse.afterSchool, short: true))", on: t == TaskParse.afterSchool) { toggle(&time, current: t, to: TaskParse.afterSchool, ==) }
                    chip("Tonight", on: t == TaskParse.tonight) { toggle(&time, current: t, to: TaskParse.tonight, ==) }
                    let other = t.map { ![TaskParse.beforeSchool, TaskParse.afterSchool, TaskParse.tonight].contains($0) } ?? false
                    chip(other ? clockText(t!) : "Time", icon: "clock", on: other) { picking = .time }
                    Divider().frame(height: 20)
                    Menu {
                        Button("No class") { section = Choice.none }
                        ForEach(model.classes) { c in Button(c.shortTitle) { section = .some(c.sectionId) } }
                    } label: {
                        chipLabel(s.flatMap { model.course($0)?.shortTitle } ?? "Class", icon: "graduationcap", on: s != nil)
                    }
                    Menu {
                        ForEach([ItemType.task, .assignment, .exam, .classwork], id: \.self) { t in
                            Button(Self.kindName(t)) { type = t }
                        }
                    } label: {
                        chipLabel(Self.kindName(k), icon: "tag", on: k != .task)
                    }
                }
                .padding(.horizontal, 1)
            }
        }
        .padding(Theme.Space.l)
        .padding(.top, Theme.Space.s)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Theme.background)
        .presentationDetents([.height(150)])
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

    /// One line saying what will be saved, so the typed words never surprise.
    private func summary(due: Date?, section: String?, kind: ItemType, typed: TaskParse.Result, typedDay: Bool) -> some View {
        var parts: [String] = [due.map(Self.dueText) ?? "No date"]
        if let section, let c = model.course(section) { parts.append(c.shortTitle) }
        if kind != .task { parts.append(Self.kindName(kind)) }
        let fromText = typed.day != nil || typed.time != nil || typed.sectionId != nil || typed.type != nil
        return HStack(spacing: 6) {
            Image(systemName: due == nil ? "calendar.badge.minus" : "calendar").foregroundStyle(due == nil ? Theme.tertiary : Theme.accent)
            Text(parts.joined(separator: " · "))
                .foregroundStyle(due == nil ? Theme.secondary : Theme.text)
                .lineLimit(1).minimumScaleFactor(0.85)
            if fromText && !cleanText.isEmpty {
                Text("from what you typed").foregroundStyle(Theme.tertiary).lineLimit(1)
            }
        }
        .font(Theme.footnote.weight(.semibold))
        .accessibilityElement(children: .combine)
    }

    private func chip(_ title: String, icon: String? = nil, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { chipLabel(title, icon: icon, on: on) }
            .buttonStyle(.borderless)
            .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func chipLabel(_ title: String, icon: String?, on: Bool) -> some View {
        HStack(spacing: 4) {
            if let icon { Image(systemName: icon) }
            Text(title).lineLimit(1)
        }
        .font(Theme.footnote.weight(.semibold))
        .foregroundStyle(on ? Theme.accent : Theme.secondary)
        .padding(.horizontal, 11)
        .frame(minHeight: 32)
        .background(on ? Theme.accentSoft : Theme.card, in: Capsule())
        .overlay(Capsule().stroke(on ? Theme.accent.opacity(0.5) : Theme.border, lineWidth: 1))
        .contentShape(Capsule())
    }

    /// A tap on the chip that's on turns it off; on another, picks it.
    private func toggle<T: Equatable>(_ choice: inout Choice<T>?, current: T?, to value: T, _ same: (T, T) -> Bool) {
        if let current, same(current, value) { choice = Choice.none } else { choice = .some(value) }
    }

    private func pickerSheet(_ p: PickWhat, day: Date, time: TaskParse.Clock?) -> some View {
        NavigationStack {
            VStack {
                switch p {
                case .day:
                    DatePicker("Day", selection: Binding(get: { day }, set: { self.day = .some(cal.startOfDay(for: $0)) }),
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
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { picking = nil }.fontWeight(.semibold) } }
        }
        .presentationDetents([p == .day ? .medium : .height(300)])
    }

    private func add(_ parsed: TaskParse.Result, due: Date?, section: String?, kind: ItemType) {
        guard !cleanText.isEmpty else { return }
        _ = model.addTask(title: parsed.title, type: kind, sectionId: section, due: due)
        dismiss()
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
}
