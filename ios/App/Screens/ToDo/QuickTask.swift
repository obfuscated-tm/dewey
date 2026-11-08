import SwiftUI

/// To Do's +: a short sheet over the keyboard. Type the task, with its day, time
/// and class in plain words if you like ("chem lab fri after school", TaskParse);
/// a grey line shows what will be saved. Or pick: one-tap usual picks (Today, Tomorrow,
/// After school, Tonight), then Day, Time, Class and Kind pills for the rest; a pick
/// wins over what was typed. Changing a task later, and its
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
            // The usual picks, one tap each (they toggle). Teal = picked here; what was
            // only typed shows in the grey line above, not on the buttons.
            quickRow(d: d, t: t)
            // Your classes, one tap each (it used the empty band iOS leaves over the keyboard).
            classRow(s: s)
            // Everything else: any day, any time, the kind. One row; two when big text won't fit.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 6) { dayMenu(d: d); timeMenu(t: t); pills(s: s, k: k) }
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) { dayMenu(d: d); timeMenu(t: t) }
                    HStack(spacing: 6) { pills(s: s, k: k) }
                }
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

    @ViewBuilder private func pills(s: String?, k: ItemType) -> some View {
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

    /// Each class as a chip with its colour; tap to pick, tap again to clear. Scrolls sideways.
    private func classRow(s: String?) -> some View {
        let picked = section.flatMap(\.value)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(model.classes) { c in
                    let on = picked == c.sectionId
                    Button { section = on ? nil : .some(c.sectionId) } label: {
                        HStack(spacing: 5) {
                            Circle().fill(model.classColor(c.sectionId)).frame(width: 8, height: 8)
                            Text(c.shortTitle).lineLimit(1)
                        }
                        .font(Theme.footnote.weight(.semibold))
                        .foregroundStyle(on ? Theme.accent : Theme.text)
                        .padding(.horizontal, 10)
                        .frame(minHeight: 32)
                        .background(on ? Theme.accentSoft : Theme.card, in: Capsule())
                        .overlay(Capsule().stroke(on ? Theme.accent.opacity(0.5) : Theme.border, lineWidth: 1))
                        .contentShape(Capsule())
                    }
                    .buttonStyle(PressStyle())
                    .accessibilityLabel("Class: \(c.shortTitle)")
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
        .scrollClipDisabled()
    }

    /// Today · Tomorrow · After school · Tonight: equal buttons, one tap each, tap again to clear.
    private func quickRow(d: Date?, t: TaskParse.Clock?) -> some View {
        let today = cal.startOfDay(for: now)
        let tomorrow = cal.date(byAdding: .day, value: 1, to: today)!
        let pickedDay = day.flatMap(\.value)
        let pickedTime = time.flatMap(\.value)
        func isDay(_ x: Date) -> Bool { pickedDay.map { cal.isDate($0, inSameDayAs: x) } ?? false }
        return HStack(spacing: 6) {
            quick("Today", on: isDay(today)) { day = isDay(today) ? nil : .some(today) }
            quick("Tomorrow", on: isDay(tomorrow)) { day = isDay(tomorrow) ? nil : .some(tomorrow) }
            quick("After school", on: pickedTime == TaskParse.afterSchool) {
                time = pickedTime == TaskParse.afterSchool ? nil : .some(TaskParse.afterSchool)
            }
            quick("Tonight", on: pickedTime == TaskParse.tonight) {
                time = pickedTime == TaskParse.tonight ? nil : .some(TaskParse.tonight)
            }
        }
    }

    private func quick(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(Theme.footnote.weight(.semibold))
                .lineLimit(1).minimumScaleFactor(0.75)
                .foregroundStyle(on ? Theme.accent : Theme.text)
                .frame(maxWidth: .infinity, minHeight: 34)
                .background(on ? Theme.accentSoft : Theme.card, in: RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.tag, style: .continuous)
                    .stroke(on ? Theme.accent.opacity(0.5) : Theme.border, lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    /// The day pill: Today, Tomorrow, any day, no date, or back to what was typed.
    private func dayMenu(d: Date?) -> some View {
        let today = cal.startOfDay(for: now)
        let tomorrow = cal.date(byAdding: .day, value: 1, to: today)!
        let pickedDay = day.flatMap(\.value)
        func dayToggle(_ name: String, _ date: Date) -> some View {
            Toggle(name, isOn: Binding(get: { pickedDay.map { cal.isDate($0, inSameDayAs: date) } ?? false },
                                       set: { on in day = on ? .some(date) : nil }))
        }
        let label: String = day == nil ? "Day" : pickedDay.map(dayLabel) ?? "No date"
        return Menu {
            dayToggle("Today", today)
            dayToggle("Tomorrow", tomorrow)
            Button { picking = .day } label: { Label("Pick a day…", systemImage: "calendar") }
            Section {
                Button { day = Choice.none } label: { Label("No date", systemImage: "calendar.badge.minus") }
                if day != nil { Button { day = nil } label: { Label("Use what I typed", systemImage: "arrow.uturn.backward") } }
            }
        } label: {
            pill(label, icon: "calendar", on: day != nil)
        }
        .accessibilityLabel(d.map { "Day: \(Self.short.string(from: $0))" } ?? "Day: none")
    }

    /// The time pill: before / after school, tonight, any time, or no time.
    private func timeMenu(t: TaskParse.Clock?) -> some View {
        let pickedTime = time.flatMap(\.value)
        func timeToggle(_ name: String, _ c: TaskParse.Clock) -> some View {
            Toggle(name, isOn: Binding(get: { pickedTime == c }, set: { on in time = on ? .some(c) : nil }))
        }
        let label: String = {
            guard time != nil else { return "Time" }
            guard let c = pickedTime else { return "No time" }
            switch c {
            case TaskParse.beforeSchool: return "Before school"
            case TaskParse.afterSchool: return "After school"
            case TaskParse.tonight: return "Tonight"
            default: return clockText(c)
            }
        }()
        return Menu {
            timeToggle("Before school · \(clockText(TaskParse.beforeSchool))", TaskParse.beforeSchool)
            timeToggle("After school · \(clockText(TaskParse.afterSchool))", TaskParse.afterSchool)
            timeToggle("Tonight · 11:59 pm", TaskParse.tonight)
            Button { picking = .time } label: { Label("Pick a time…", systemImage: "clock") }
            Section {
                Button { time = Choice.none } label: { Label("No time", systemImage: "clock.badge.xmark") }
                if time != nil { Button { time = nil } label: { Label("Use what I typed", systemImage: "arrow.uturn.backward") } }
            }
        } label: {
            pill(label, icon: "clock", on: time != nil)
        }
        .accessibilityLabel(t.map { "Time: \(clockText($0))" } ?? "Time: none")
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
