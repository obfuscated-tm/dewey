import SwiftUI

/// The right half when nothing is open: something to use, never an empty
/// "Select an item" page.

/// To Do: Plan beside the list (the left stays on List). Order the day, tick
/// work off, tap a row to open it here.
struct PlanPane: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    @State private var sheet: SheetTarget?
    @State private var editing: TaskTarget?

    var body: some View {
        let now = Date()
        ScrollViewReader { proxy in
            List {
                Section {
                    ScreenHeader(kicker: "Your order for today", title: "Plan") {
                        Button { editing = TaskTarget() } label: {
                            Image(systemName: "plus").font(Theme.title.weight(.regular)).foregroundStyle(Theme.accent)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("New task")
                    }
                    .listRowInsets(EdgeInsets(top: Theme.Space.l, leading: 0, bottom: Theme.Space.s, trailing: 0))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                PlanContent(now: now, onOpen: open,
                            onSubmit: { sheet = SheetTarget(url: submitURL($0), assignmentId: $0.schoologyId) },
                            onSet: { model.setProgress($0, $1) },
                            onNewTask: { editing = TaskTarget() }, scroll: proxy)
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(Theme.Space.l)
        .contentMargins(.top, 0, for: .scrollContent)
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
        .sheet(item: $editing) { t in
            if let task = t.task { TaskEditor(task: task) } else { QuickTask(sectionId: t.sectionId) }
        }
        // Leaving Plan (an item opened over it, another tab) lets work finished here go to Done.
        .onDisappear { model.planKeep = [] }
    }

    private func open(_ it: ItemState) {
        if it.mine { editing = TaskTarget(task: it) } else { split.open(AssignmentRoute(id: it.schoologyId, sectionId: it.sectionId)) }
    }

    private func submitURL(_ it: ItemState) -> URL {
        URL(string: "\(Engine.origin.absoluteString)/assignment/\(it.schoologyId)")!
    }
}

/// Grades: the class with the newest grade, open, as if tapped. Picked once the
/// gradebooks are in, and moved while it is still Dewey's pick (a new grade landing
/// later wins); a class the student taps stays. DetailPane watches `pick`; this is
/// what shows until there is one.
struct NewestGradeClass: View {
    var body: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.background)
            .toolbar(.hidden, for: .navigationBar)
    }

    static func pick(_ model: AppModel, hidden hiddenRaw: String) -> Course? {
        let h = GradesLook.hidden(hiddenRaw)
        let classes = model.classes.filter { !h.contains($0.sectionId) }
        if let new = model.newGrades().first(where: { n in classes.contains { $0.sectionId == n.0.sectionId } }) { return new.0 }
        // Until every gradebook has answered, the newest one may still be on its way.
        guard !classes.isEmpty, classes.allSatisfy({ model.grades[$0.sectionId] != nil || model.gradeErrors[$0.sectionId] != nil }) else { return nil }
        let graded = classes.flatMap { c in (model.grades[c.sectionId]?.items ?? []).filter { $0.earned != nil }.map { (c, $0) } }
        return graded.min { model.gradedFirst($0.1, $1.1) }?.0 ?? classes.first
    }
}

/// Inbox: stays quiet, since opening a message marks it read: what's unread, a
/// button for the newest unread message, a new message in the Schoology sheet.
/// Under it, New in your classes (materials and grades), which moves here from the list.
struct InboxPane: View {
    @Environment(AppModel.self) private var model
    @Environment(Split.self) private var split
    @State private var sheet: SheetTarget?
    @State private var shown = 12

    var body: some View {
        let inbox = model.inbox
        let classes = model.classes
        let teachers = inbox.teacherThreads(classes).filter(inbox.isNew).count
        let school = inbox.schoolThreads(classes).filter(inbox.isNew).count
        let newest = inbox.threads.filter(inbox.isNew).max { ($0.when ?? .distantPast) < ($1.when ?? .distantPast) }
        let notices = inbox.classNotices(classes)
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                HStack(alignment: .center, spacing: Theme.Space.l) {
                    Image(newest == nil ? "DeweyCaughtUp" : "DeweyMark").resizable().scaledToFit().frame(width: 56, height: 56)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(newest == nil ? "You're all caught up" : "No message open").font(Theme.title)
                        if newest != nil {
                            Text([teachers > 0 ? "\(teachers) unread from teachers" : nil, school > 0 ? "\(school) new from school" : nil]
                                .compactMap { $0 }.joined(separator: " · "))
                                .font(Theme.subhead).foregroundStyle(Theme.secondary)
                        }
                    }
                }
                HStack(spacing: Theme.Space.m) {
                    if let newest {
                        Button { split.open(newest) } label: {
                            Text("Open newest unread").font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.onAccent).fixedSize()
                                .padding(.horizontal, Theme.Space.l).frame(minHeight: 44)
                                .background(Theme.accent, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                    Button { sheet = SheetTarget(url: Engine.origin.appendingPathComponent("messages/new")) } label: {
                        Text("New message ↗").font(Theme.subhead.weight(.semibold)).foregroundStyle(Theme.accent).fixedSize()
                            .padding(.horizontal, Theme.Space.l).frame(minHeight: 44)
                            .background(Theme.accentSoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("New message in Schoology")
                }

                Text("New in your classes").font(Theme.headline).padding(.top, Theme.Space.s).accessibilityAddTraits(.isHeader)
                VStack(spacing: 0) {
                    if notices.isEmpty {
                        Text(inbox.asOf == nil ? "Loading…" : "Nothing new").font(Theme.subhead).foregroundStyle(Theme.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                    }
                    ForEach(Array(notices.prefix(shown).enumerated()), id: \.element.key) { i, n in
                        NoticeRow(notice: n, divider: i > 0) { sheet = $0 }
                    }
                    if notices.count > shown {
                        Theme.divider.frame(height: 1)
                        Button("Show older") { withAnimation { shown += 12 } }
                            .font(Theme.subhead.weight(.semibold))
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                }
                .card()
            }
            .padding(.horizontal, Theme.Space.xl)
            .padding(.vertical, Theme.Space.l)
        }
        .background(Theme.background)
        .sheet(item: $sheet) { SchoologySheet(target: $0) }
    }
}
