import SwiftUI

/// A task of the student's own: add one (To Do's +), or change or delete one
/// (tap its row). Lives on the phone only: never on Schoology or neo-plan.
struct TaskEditor: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// nil = a new task.
    var task: ItemState?
    /// A new task's class, when added from a class's card.
    var sectionId: String?

    @State private var title = ""
    @State private var type: ItemType = .task
    @State private var section: String = ""
    @State private var hasDue = false
    @State private var due = TaskEditor.defaultDue()
    @State private var hasTime = false
    @State private var notes = ""
    @State private var confirmDelete = false
    @FocusState private var titleFocused: Bool

    var body: some View {
        NavigationStack {
            DewForm {
                Section {
                    TextField("What do you need to do?", text: $title, axis: .vertical)
                        .font(Theme.body)
                        .focused($titleFocused)
                        .submitLabel(.done)
                        .accessibilityLabel("Task")
                    Picker("Kind", selection: $type) {
                        ForEach([ItemType.task, .assignment, .exam, .classwork], id: \.self) { t in
                            Text(t == .task ? "Task" : t == .assignment ? "Homework" : t == .exam ? "Test" : "Classwork").tag(t)
                        }
                    }
                    Picker("Class", selection: $section) {
                        Text("None").tag("")
                        ForEach(model.classes) { c in Text(c.shortTitle).tag(c.sectionId) }
                    }
                }
                Section {
                    Toggle("Due date", isOn: $hasDue.animation())
                    if hasDue {
                        DatePicker("Day", selection: $due, displayedComponents: .date)
                        Toggle("At a time", isOn: $hasTime.animation())
                        if hasTime { DatePicker("Time", selection: $due, displayedComponents: .hourAndMinute) }
                    }
                } footer: {
                    Text(hasDue ? (hasTime ? "Late once that time passes." : "Due by the end of the day.")
                                : "No date: it stays at the end of your Plan until you move it.")
                }
                Section {
                    TextField("Notes", text: $notes, axis: .vertical).lineLimit(2...6)
                }
                if task != nil {
                    Section {
                        Button("Delete task", role: .destructive) { confirmDelete = true }
                    }
                } else {
                    Section {} footer: {
                        Text("Only on this phone. Nothing is added to Schoology.")
                    }
                }
            }
            .navigationTitle(task == nil ? "New task" : "Task")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(task == nil ? "Add" : "Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(cleanTitle.isEmpty)
                }
            }
            .confirmationDialog("Delete this task?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let task { model.deleteTask(task) }
                    dismiss()
                }
            }
        }
        .onAppear(perform: load)
    }

    private var cleanTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func load() {
        guard let task else {
            section = sectionId ?? ""
            titleFocused = true
            return
        }
        title = task.title
        type = task.type
        section = task.sectionId ?? ""
        notes = task.notes ?? ""
        if let d = task.due {
            hasDue = true
            due = d
            hasTime = !Self.isEndOfDay(d)
        }
    }

    private func save() {
        let when: Date? = hasDue ? (hasTime ? due : Self.endOfDay(due)) : nil
        let n = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        if let task {
            model.updateTask(task, title: cleanTitle, type: type, sectionId: section.isEmpty ? nil : section, due: when, notes: n.isEmpty ? nil : n)
        } else {
            model.addTask(title: cleanTitle, type: type, sectionId: section.isEmpty ? nil : section, due: when, notes: n.isEmpty ? nil : n)
        }
        dismiss()
    }

    /// Tomorrow, so the time picker starts somewhere useful.
    static func defaultDue() -> Date {
        let cal = Calendar.current
        let tomorrow = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: Date()))!
        return cal.date(bySettingHour: 16, minute: 0, second: 0, of: tomorrow)!
    }

    /// No time: 11:59 pm, like Schoology's own "due that day".
    static func endOfDay(_ d: Date) -> Date {
        Calendar.current.date(bySettingHour: 23, minute: 59, second: 0, of: d)!
    }

    static func isEndOfDay(_ d: Date) -> Bool {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return c.hour == 23 && c.minute == 59
    }
}

/// The student's own task, opened somewhere other than To Do (a widget, a
/// reminder, a class page): what it is, its status, and Edit.
struct MineTaskPage: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var editing = false

    private var it: ItemState? { _ = model.revision; return model.item(id) }

    var body: some View {
        ScrollView {
            if let it {
                VStack(alignment: .leading, spacing: Theme.Space.l) {
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        HStack(spacing: Theme.Space.s) {
                            TypeTag(type: it.type)
                            Text(model.course(it.sectionId)?.shortTitle ?? "My task")
                                .font(Theme.footnote.weight(.semibold)).foregroundStyle(Theme.secondary)
                        }
                        Text(it.title).font(Theme.title).foregroundStyle(Theme.text)
                        Text(Fmt.due(it.due)).font(Theme.subhead).foregroundStyle(Theme.secondary)
                    }
                    ProgressChips(current: model.progress(it), isExam: it.type == .exam) { model.setProgress(it, $0) }
                    if let n = it.notes {
                        Text(n).font(Theme.body).foregroundStyle(Theme.text)
                            .padding(Theme.Space.l).frame(maxWidth: .infinity, alignment: .leading).card()
                    }
                    Text("Your own task: only on this phone, never on Schoology.")
                        .font(Theme.footnote).foregroundStyle(Theme.tertiary)
                }
                .padding(Theme.Space.l)
            } else {
                EmptyState(icon: "trash", title: "Task deleted", detail: nil)
                    .padding(.top, 60)
            }
        }
        .background(Theme.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if it != nil { ToolbarItem(placement: .primaryAction) { Button("Edit") { editing = true } } }
        }
        .sheet(isPresented: $editing) { TaskEditor(task: it) }
    }
}
