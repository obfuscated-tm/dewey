import Foundation
import SwiftData
import SwiftUI

/// App-wide state: the Schoology session, the engine, the class list, and the
/// To Do merge (Schoology's lists + the student's own saved list).
@MainActor
@Observable
final class AppModel {
    enum Session: Equatable { case checking, signedOut, signedIn }

    var session: Session = .checking {
        // The Lock Screen widgets say "Open Dewey to sign in" while signed out.
        didSet { if session != oldValue, session != .checking { WidgetBridge.publish(self) } }
    }
    let engine: Engine
    /// The tour's pretend week (Practice): in memory, answered locally, no side effects.
    let practice: Bool
    let container: ModelContainer
    var context: ModelContext { container.mainContext }
    let neoPlan = NeoPlanClient()
    let inbox = Inbox()
    /// The scene's model, for Siri intents that run while the app is open (nil in a background launch).
    static weak var live: AppModel?

    var courses: [Course] = []
    var coursesAsOf: Date?

    var todoAsOf: Date?
    var todoError: EngineError?
    var refreshingTodo = false
    /// Bumped whenever ItemState rows change, so lists re-query.
    var revision = 0
    /// Set by a widget / Live Activity link; the To Do tab opens it.
    var deepLinkAssignment: String?

    var toast: String?

    /// Course grades, by section (cached ≤ 30 min).
    var grades: [String: CourseGrade] = [:]
    var gradesAsOf: [String: Date] = [:]
    var gradeErrors: [String: EngineError] = [:]

    init(inMemory: Bool = Demo.on, practice: Bool = false) {
        self.practice = practice
        engine = Engine(practice: practice)
        let config = ModelConfiguration(isStoredInMemoryOnly: inMemory || practice)
        container = try! ModelContainer(for: ItemState.self, CourseState.self, configurations: config)
        if let c = Cache.load("courses", as: [Course].self) {
            courses = c.value
            coursesAsOf = c.savedAt
        }
        if let t = Cache.load("todoAsOf", as: Date.self) { todoAsOf = t.value }
        engine.onSignedOut = { [weak self] in
            guard let self, !self.practice, self.session != .signedOut else { return }
            self.session = .signedOut
            Reminders.shared.sessionDidEnd(notify: true)
        }
    }

    // ── Session ──────────────────────────────────────────────────────────

    /// On launch: try the course list; `login` → Sign in, anything else → in (offline uses saved copies).
    func boot() async {
        if practice {
            engine.start()
            try? await loadCourses()
            session = .signedIn
            return
        }
        // To Do's look, if the app was quit in the middle of the tour's practice.
        if !Practice.active { Practice.restoreView() }
        // Demo rows live only in memory (the saved courses don't), so seed on every launch.
        if Demo.on, (try? context.fetchCount(FetchDescriptor<ItemState>())) == 0 { Demo.seed(self) }
        if let id = Demo.openAssignment { deepLinkAssignment = id }
        engine.start()
        // Saved copies and no sign-out on record: show them now instead of a
        // spinner; a `login` below still sends the app to Sign in.
        if session == .checking, !courses.isEmpty, !Reminders.shared.sessionEnded { session = .signedIn }
        do {
            try await loadCourses()
            session = .signedIn
            Reminders.shared.sessionDidStart()
            await WebConfig.keepSession()
        } catch EngineError.login {
            session = .signedOut
            Reminders.shared.sessionDidEnd(notify: true)
        } catch {
            session = courses.isEmpty && todoAsOf == nil ? .signedOut : .signedIn
        }
    }

    /// The sign-in web view reached a signed-in Schoology page.
    func signedIn() async {
        await WebConfig.keepSession()
        engine.reload()
        session = .signedIn
        Reminders.shared.sessionDidStart()
        if Reminders.shared.signedOutAlert || Reminders.shared.gradeAlerts { _ = await Reminders.shared.requestPermission() }
        try? await loadCourses()
        await forgetOtherAccount()
        await refreshTodo()
    }

    /// A different student signed in on this phone: the last one's rows and marks go.
    func forgetOtherAccount() async {
        guard let a: AccountInfo = try? await engine.call("account"), let user = a.username, !user.isEmpty else { return }
        let key = "signedInUser"
        let was = UserDefaults.standard.string(forKey: key)
        UserDefaults.standard.set(user, forKey: key)
        guard let was, was != user else { return }
        try? context.delete(model: ItemState.self)
        try? context.delete(model: CourseState.self)
        courseStates = [:]
        fetchedItems = nil
        pendingPush = [:]
        save()
    }

    func signOut() async {
        await WebConfig.signOut()
        Cache.clear()
        inbox.clear()
        grades = [:]
        gradesAsOf = [:]
        gradeLog = [:]
        courses = []
        session = .signedOut
        Reminders.shared.sessionDidEnd(notify: false)
        engine.reload()
    }

    // ── Courses ──────────────────────────────────────────────────────────

    func loadCourses() async throws {
        let list: [Course] = try await engine.call("courses")
        courses = list
        coursesAsOf = Date()
        Cache.save("courses", list)
    }

    /// Class or group for one course (Settings); redraws everything that sorts by it,
    /// and fetches a new group's updates.
    func setKind(_ c: Course, isClass: Bool) {
        CourseKinds.set(c, isClass: isClass)
        kinds += 1
        revision += 1
        if !isClass { Task { await inbox.refresh(self) } }
    }

    /// Bumped when a course is switched between class and group (Settings).
    var kinds = 0

    var classes: [Course] {
        _ = kinds
        let c = courses.filter(\.isClass)
        return c.isEmpty ? courses : c
    }

    /// The class's colour (Theme.classColors), by its place in the class list.
    func classColor(_ sectionId: String?) -> Color { Theme.classColor(classIndex(sectionId)) }
    /// The same colour, unchanged in dark mode: a book spine on the Classes shelf.
    func spineColor(_ sectionId: String?) -> Color { Theme.spineColor(classIndex(sectionId)) }
    private func classIndex(_ sectionId: String?) -> Int { classes.firstIndex { $0.sectionId == sectionId } ?? 0 }

    func course(_ sectionId: String?) -> Course? {
        guard let sectionId else { return nil }
        return courses.first { $0.sectionId == sectionId }
    }

    /// One row per class, created once and kept: views call this while drawing,
    /// so it must not insert (and so invalidate the view) more than once.
    @ObservationIgnored private var courseStates: [String: CourseState] = [:]

    func courseState(_ sectionId: String) -> CourseState {
        if let c = courseStates[sectionId] { return c }
        let d = FetchDescriptor<CourseState>(predicate: #Predicate { $0.sectionId == sectionId })
        let c = (try? context.fetch(d).first) ?? {
            let n = CourseState(sectionId: sectionId)
            context.insert(n)
            return n
        }()
        courseStates[sectionId] = c
        return c
    }

    // ── To Do ────────────────────────────────────────────────────────────

    func item(_ id: String) -> ItemState? {
        let d = FetchDescriptor<ItemState>(predicate: #Predicate { $0.schoologyId == id })
        return try? context.fetch(d).first
    }

    /// Every saved row, fetched once per `revision` (views call this on every
    /// redraw: the tab badge, each class tile). Rows are live objects, so marks
    /// show at once; only a new row needs a refetch, and `merge` drops the copy.
    @ObservationIgnored private var fetchedItems: (revision: Int, items: [ItemState])?

    func allItems() -> [ItemState] {
        if let f = fetchedItems, f.revision == revision { return f.items }
        let items = (try? context.fetch(FetchDescriptor<ItemState>())) ?? []
        fetchedItems = (revision, items)
        return items
    }

    /// The To Do list: visible items (TodoLogic.isVisible).
    func todoItems() -> [ItemState] {
        _ = revision
        let cal = Calendar.current, now = Date()
        return allItems().filter {
            TodoLogic.isVisible(.init(inSchoology: $0.inSchoology, done: $0.done, turnedIn: $0.turnedIn,
                                      submitted: $0.submitted, graded: $0.graded, dismissed: $0.dismissed,
                                      mine: $0.mine, finishedToday: cal.isDate($0.updatedAt, inSameDayAs: now)))
        }
    }

    /// Open (not finished) overdue work: the tab badge.
    func overdueCount() -> Int {
        let now = Date()
        return todoItems().filter { isOverdue($0, now: now) }.count
    }

    /// Past due, not finished, and not old enough to sit in the Older group.
    func isOverdue(_ it: ItemState, now: Date = Date()) -> Bool {
        (it.due ?? .distantFuture) < now && showsLate(it) && !TodoLogic.isOlder(due: it.due, now: now)
    }

    /// A class's work still to do: late work (as on To Do), then everything due
    /// later that isn't finished, soonest first. Past tests and Older work are left out.
    func openWork(_ sectionId: String, now: Date = Date()) -> [ItemState] {
        todoItems()
            .filter { $0.sectionId == sectionId }
            .filter { ($0.due ?? .distantFuture) < now ? isOverdue($0, now: now) : !isFinished($0) }
            .sorted { ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture) }
    }

    /// Past due counts as late (TodoLogic.showsLate).
    func showsLate(_ it: ItemState) -> Bool {
        TodoLogic.showsLate(type: it.type, isQuiz: it.isQuiz, done: it.type == .exam ? it.studied : it.done, turnedIn: it.turnedIn, submitted: it.submitted, inSchoology: it.inSchoology)
    }

    func isFinished(_ it: ItemState) -> Bool {
        it.submitted || it.turnedIn || (it.type == .exam ? false : it.done)
    }

    /// Pull Schoology's lists, merge, then check a few dropboxes and neo-plan.
    /// `quick`: return once the list is merged (pull to refresh) and let the checks fill in after.
    func refreshTodo(quick: Bool = false) async {
        guard !refreshingTodo else { return }
        refreshingTodo = true
        defer { refreshingTodo = false }
        do {
            if courses.isEmpty { try? await loadCourses() }
            let lists: TodoLists = try await engine.call("todo")
            merge(lists)
            todoError = nil
            todoAsOf = Date()
            Cache.save("todoAsOf", todoAsOf!)
        } catch let e as EngineError {
            todoError = e
        } catch {
            todoError = .js(error.localizedDescription)
        }
        revision += 1
        publishTodo()
        guard todoError == nil else { return }
        if quick { Task { await checkAfterRefresh() } } else { await checkAfterRefresh() }
    }

    @ObservationIgnored private var checking = false

    /// Dropbox checks and neo-plan, after a refresh merged Schoology's lists.
    private func checkAfterRefresh() async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        await checkStatuses()
        await syncNeoPlan()
        revision += 1
        publishTodo()
    }

    private func publishTodo() {
        WidgetBridge.publish(self)
        Reminders.shared.schedule(items: todoItems(), courses: courses)
        CalendarSync.shared.schedule(self)
    }

    func merge(_ lists: TodoLists, now: Date = Date()) {
        var seen = Set<String>()
        let all = allItems()
        var byId = Dictionary(all.map { ($0.schoologyId, $0) }, uniquingKeysWith: { a, _ in a })
        for e in lists.overdue + lists.upcoming {
            seen.insert(e.schoologyId)
            let it = byId[e.schoologyId] ?? {
                let n = ItemState(schoologyId: e.schoologyId, title: e.title)
                context.insert(n)
                byId[e.schoologyId] = n
                fetchedItems = nil
                return n
            }()
            it.title = e.title
            it.sectionId = e.sectionId ?? it.sectionId
            it.courseName = e.course ?? it.courseName
            it.due = Fmt.date(e.due) ?? it.due
            it.url = e.url ?? it.url
            it.graded = e.graded ?? false
            it.inSchoology = true
            it.lastSeen = now
        }
        // The student's own tasks are never on Schoology's lists.
        for it in all where !seen.contains(it.schoologyId) && !it.mine {
            it.inSchoology = false
        }
        prune(all, now: now)
        try? context.save()
    }

    /// Rows nobody will see again: Schoology work it stopped listing months ago, and the
    /// student's own tasks finished months ago. Keeps the saved list from growing all year.
    static let keepDays: Double = 120

    private func prune(_ all: [ItemState], now: Date) {
        let cutoff = now.addingTimeInterval(-Self.keepDays * 86400)
        var gone = false
        for it in all {
            let old = it.mine ? (it.done || it.turnedIn || it.dismissed) && it.updatedAt < cutoff
                              : !it.inSchoology && it.lastSeen < cutoff
            if old { context.delete(it); gone = true }
        }
        if gone { fetchedItems = nil }
    }

    /// status({id}) for up to 10 rows that might have a dropbox, not checked in 30 min.
    /// A quiz is checked on its landing page instead, until it says submitted.
    func checkStatuses(cap: Int = 10) async {
        let cutoff = Date().addingTimeInterval(-30 * 60)
        let candidates = todoItems()
            .filter { !$0.mine && !$0.external && $0.statusState != "no_dropbox" && ($0.statusCheckedAt ?? .distantPast) < cutoff }
            .filter { !$0.isQuiz || !$0.submitted }
            .sorted { ($0.statusCheckedAt ?? .distantPast) < ($1.statusCheckedAt ?? .distantPast) }
            .prefix(cap)
        await twoAtATime(Array(candidates)) { await self.checkStatus($0) }
    }

    /// Runs `body` over `list`, two at a time: the engine lets 2 requests run at once.
    func twoAtATime<T>(_ list: [T], _ body: @escaping @MainActor (T) async -> Void) async {
        await withTaskGroup(of: Void.self) { group in
            for (i, x) in list.enumerated() {
                if i >= 2 { await group.next() }
                group.addTask { @MainActor in await body(x) }
            }
        }
    }

    @discardableResult
    func checkStatus(_ it: ItemState) async -> SubmissionStatus? {
        if it.isQuiz { await checkQuiz(it); return nil }
        guard let s: SubmissionStatus = try? await engine.call("status", ["id": it.schoologyId]) else { return nil }
        let was = it.statusState
        it.statusState = s.quiz == true ? "unknown" : s.state
        it.statusLate = s.late
        it.statusCheckedAt = Date()
        if s.state == "submitted" && was != "submitted", let np = it.neoPlanId {
            try? await neoPlan.turnIn(np)
        }
        try? context.save()
        revision += 1
        WidgetBridge.publish(self)
        return s
    }

    /// A quiz's own status op is refused (it redirects), but its landing page
    /// says whether an attempt was handed in. Taken = submitted, so it isn't late.
    func checkQuiz(_ it: ItemState) async {
        guard let a: Assignment = try? await engine.call("assignment", ["id": it.schoologyId]) else { return }
        it.statusCheckedAt = Date()
        if let q = a.quiz { noteQuiz(it, q) }
        try? context.save()
    }

    /// Records a quiz landing page's state on the row (submitted, or still to take).
    func noteQuiz(_ it: ItemState, _ q: QuizInfo) {
        let state = q.state == "submitted" ? "submitted" : "unknown"
        guard state != it.statusState else { return }
        let was = it.statusState
        it.statusState = state
        if state == "submitted" && was != "submitted", let np = it.neoPlanId {
            Task { try? await neoPlan.turnIn(np) }
        }
        revision += 1
        WidgetBridge.publish(self)
    }

    // ── The student's marks ──────────────────────────────────────────────

    func toggleDone(_ it: ItemState) {
        if it.type == .exam { it.studied.toggle() } else { it.done.toggle() }
        it.touch()
        save()
        pushNeoPlan(it, .work(it.type == .exam ? it.studied : it.done))
    }

    func setTurnedIn(_ it: ItemState, _ on: Bool) {
        it.turnedIn = on
        if on { it.done = true }
        it.touch()
        save()
        pushNeoPlan(it, .turnedIn(on))
    }

    enum Progress: String, CaseIterable { case todo, done, turnedIn
        var label: String { switch self { case .todo: "To do"; case .done: "Done"; case .turnedIn: "Turned in" } }
    }

    func progress(_ it: ItemState) -> Progress {
        it.turnedIn ? .turnedIn : (it.type == .exam ? it.studied : it.done) ? .done : .todo
    }

    /// Done / Studied taps from the Home Screen widget, made while the app wasn't looking.
    func applyWidgetMarks() {
        for (id, on) in SharedStore.takeWidgetMarks() {
            guard let it = item(id), !it.turnedIn else { continue }
            setProgress(it, on ? .done : .todo)
        }
    }

    /// One switch for the student's own state: to do → done (worked on) → turned in (handed in).
    func setProgress(_ it: ItemState, _ p: Progress) {
        let was = progress(it)
        guard p != was else { return }
        if was == .turnedIn { setTurnedIn(it, false) }
        let ready = p != .todo
        if (it.type == .exam ? it.studied : it.done) != ready { toggleDone(it) }
        if p == .turnedIn { setTurnedIn(it, true) }
    }

    func setType(_ it: ItemState, _ t: ItemType) {
        it.type = t
        it.touch()
        save()
        pushNeoPlan(it, .type(t.rawValue))
    }

    func dismiss(_ it: ItemState) {
        it.dismissed = true
        it.touch()
        save()
    }

    func save() {
        try? context.save()
        revision += 1
        WidgetBridge.publish(self)
        Reminders.shared.schedule(items: todoItems(), courses: courses)
        CalendarSync.shared.schedule(self)
    }

    // ── The student's own tasks ──────────────────────────────────────────

    /// Adds a task of the student's own (TaskEditor, Siri). Never sent anywhere.
    @discardableResult
    func addTask(title: String, type: ItemType = .task, sectionId: String? = nil, due: Date? = nil, notes: String? = nil) -> ItemState {
        let it = ItemState(mineTitle: title)
        it.typeOverride = type.rawValue
        it.sectionId = sectionId
        it.courseName = course(sectionId)?.shortTitle
        it.due = due
        it.notes = notes
        context.insert(it)
        fetchedItems = nil
        save()
        return it
    }

    func updateTask(_ it: ItemState, title: String, type: ItemType, sectionId: String?, due: Date?, notes: String?) {
        it.title = title
        it.typeOverride = type.rawValue
        it.sectionId = sectionId
        it.courseName = course(sectionId)?.shortTitle
        it.due = due
        it.notes = notes
        it.touch()
        save()
    }

    func deleteTask(_ it: ItemState) {
        guard it.mine else { return }
        context.delete(it)
        fetchedItems = nil
        save()
    }

    // ── Plan ─────────────────────────────────────────────────────────────

    struct PlanLists {
        var todo: [ItemState]
        var later: [ItemState]
        /// Finished today.
        var done: [ItemState]
        var rows: [PlanLogic.Row] {
            PlanLogic.rows(todo: todo.map(\.schoologyId), later: later.map(\.schoologyId), done: done.map(\.schoologyId))
        }
    }

    /// For a plan row: finished, as Plan counts it (a test you studied for is done).
    func planFinished(_ it: ItemState) -> Bool { isFinished(it) || (it.type == .exam && it.studied) }

    /// Every open item in To do or Later (PlanLogic), plus what was finished today.
    /// Work finished while Plan is open (a tick, a swipe, Submit): it stays where it
    /// was, ticked, instead of dropping into Done at the bottom, until Plan closes.
    var planKeep: Set<String> = []

    func planLists(now: Date = Date()) -> PlanLists {
        let cal = Calendar.current
        var todo: [ItemState] = [], later: [ItemState] = [], done: [ItemState] = []
        for it in todoItems() {
            let kept = planKeep.contains(it.schoologyId)
            if planFinished(it) && !kept {
                if cal.isDate(it.updatedAt, inSameDayAs: now) { done.append(it) }
                continue
            }
            let late = isOverdue(it, now: now)
            if !kept {
                if TodoLogic.isOlder(due: it.due, now: now) { continue }
                // A past test that isn't an online quiz isn't work any more (TodoLogic.showsLate).
                if (it.due ?? .distantFuture) < now && !late && !it.mine { continue }
            }
            let auto = PlanLogic.autoBucket(due: it.due, late: late, isTest: it.type == .exam, mine: it.mine, now: now)
            let b = it.planSection.flatMap(PlanLogic.Bucket.init(rawValue:)) ?? auto
            if b == .later { later.append(it) } else { todo.append(it) }
        }
        func ordered(_ list: [ItemState]) -> [ItemState] {
            let byId = Dictionary(list.map { ($0.schoologyId, $0) }, uniquingKeysWith: { a, _ in a })
            return PlanLogic.order(list.map { .init(id: $0.schoologyId, rank: $0.planRank, due: $0.due) }).compactMap { byId[$0.id] }
        }
        return PlanLists(todo: ordered(todo), later: ordered(later), done: done.sorted { $0.updatedAt < $1.updatedAt })
    }

    /// Saves a drag (PlanLogic.moved): every item's place, and for the item that
    /// changed section, that section. Into Done = done; out of Done = to do again.
    func applyPlan(_ placed: [String: (PlanLogic.Bucket, Double)], moved id: String?, now: Date = Date()) {
        let lists = planLists(now: now)
        let was = Dictionary(lists.todo.map { ($0.schoologyId, PlanLogic.Bucket.todo) } + lists.later.map { ($0.schoologyId, .later) }
                             + lists.done.map { ($0.schoologyId, .done) }, uniquingKeysWith: { a, _ in a })
        for it in lists.todo + lists.later + lists.done {
            guard let (b, rank) = placed[it.schoologyId] else { continue }
            if b != .done { it.planRank = rank }
            guard it.schoologyId == id, let from = was[it.schoologyId], from != b else { continue }
            switch b {
            case .done:
                planKeep.remove(it.schoologyId)
                setProgress(it, .done)
            case .todo, .later:
                if from == .done { setProgress(it, .todo) }
                // By due date it would be here anyway: leave it to follow its due date.
                let auto = PlanLogic.autoBucket(due: it.due, late: isOverdue(it, now: now), isTest: it.type == .exam, mine: it.mine, now: now)
                it.planSection = b == auto ? nil : b.rawValue
            }
        }
        save()
    }

    // ── neo-plan ─────────────────────────────────────────────────────────

    /// One change to send to neo-plan.
    enum NeoPlanOp: Codable, Equatable {
        case work(Bool), turnedIn(Bool), type(String)
        var kind: Int { switch self { case .work: 0; case .turnedIn: 1; case .type: 2 } }

        func run(_ np: NeoPlanClient, _ id: String) async throws {
            switch self {
            case .work(let ready): try await np.setWork(id, ready: ready)
            case .turnedIn(true): try await np.turnIn(id)
            case .turnedIn(false): try await np.putBack(id)
            case .type(let t): if let ty = ItemType(rawValue: t) { try await np.setType(id, ty) }
            }
        }
    }

    /// neo-plan writes that failed (offline), by Schoology id, the newest of each kind;
    /// saved, so a quit doesn't lose them, and retried on refresh.
    private var pendingPush: [String: [NeoPlanOp]] = Cache.load("neoplanPending", as: [String: [NeoPlanOp]].self)?.value ?? [:] {
        didSet { Cache.save("neoplanPending", pendingPush) }
    }

    private func queueNeoPlan(_ sid: String, _ op: NeoPlanOp) {
        pendingPush[sid] = (pendingPush[sid] ?? []).filter { $0.kind != op.kind } + [op]
    }

    private func pushNeoPlan(_ it: ItemState, _ op: NeoPlanOp) {
        // neo-plan's extension API only takes Schoology items: the student's own tasks stay here.
        guard neoPlan.connected, !it.mine else { return }
        let sid = it.schoologyId
        // Behind earlier failed writes: keep the order.
        if pendingPush[sid] != nil { queueNeoPlan(sid, op); return }
        Task {
            guard let id = await neoPlanId(for: sid) else { queueNeoPlan(sid, op); return }
            do { try await op.run(neoPlan, id) } catch { queueNeoPlan(sid, op) }
        }
    }

    private func neoPlanId(for sid: String) async -> String? {
        guard let it = item(sid) else { return nil }
        if let id = it.neoPlanId { return id }
        if let found = try? await neoPlan.items([sid]).first?.id {
            it.neoPlanId = found
            return found
        }
        if let added = try? await neoPlan.addItem(schoologyId: sid, sectionId: it.sectionId, title: it.title,
                                                   due: it.due, type: it.isQuiz ? .exam : nil) {
            it.neoPlanId = added.id
            return added.id
        }
        return nil
    }

    /// neo-plan is the shared copy: the newer edit wins. Pulls its state for every listed item.
    func syncNeoPlan() async {
        guard neoPlan.connected else { return }
        for (sid, ops) in pendingPush {
            guard let id = await neoPlanId(for: sid) else { continue }
            var left = ops
            while let op = left.first, (try? await op.run(neoPlan, id)) != nil { left.removeFirst() }
            pendingPush[sid] = left.isEmpty ? nil : left
        }
        let items = todoItems().filter { !$0.mine }
        guard let got = try? await neoPlan.items(items.map(\.schoologyId)) else { return }
        let bySource = Dictionary(got.compactMap { g in g.sourceId.map { ($0, g) } }, uniquingKeysWith: { a, _ in a })
        let lastSync = Cache.load("neoplanSyncedAt", as: Date.self)?.value ?? .distantPast
        for it in items {
            guard let g = bySource[it.schoologyId] else { continue }
            it.neoPlanId = g.id
            // Local edits since the last sync win; otherwise neo-plan's state.
            if it.updatedAt > lastSync && pendingPush[it.schoologyId] == nil { continue }
            if let w = g.work {
                if it.type == .exam { it.studied = w == "ready" } else { it.done = w == "ready" }
            }
            if g.cleared == true && !it.submitted { it.turnedIn = true }
            if let t = g.type, let ty = ItemType(rawValue: t) { it.typeOverride = ty.rawValue }
            if g.removed == true { it.dismissed = true }
        }
        Cache.save("neoplanSyncedAt", Date())
        save()
    }

    // ── Grades ───────────────────────────────────────────────────────────

    func gradeRows(_ sectionId: String) -> Data? { Cache.loadRaw("grades-\(sectionId)")?.0 }

    /// The course's gradebook (cached ≤ 30 min unless `force`) → courseGrade.
    func loadGrades(_ sectionId: String, force: Bool = false) async {
        if grades[sectionId] == nil, let cached = Cache.loadRaw("grades-\(sectionId)") {
            gradesAsOf[sectionId] = cached.1
            // The saved summary shows at once; working it out again waits for the engine page.
            if let s = Cache.load(Self.summaryKey(sectionId), as: CourseGrade.self), s.savedAt >= cached.1.addingTimeInterval(-5) {
                grades[sectionId] = s.value
            } else if let g = try? await summarize(rows: cached.0) {
                grades[sectionId] = g
            } else if let s = Cache.load(Self.anySummaryKey(sectionId), as: CourseGrade.self) {
                // Offline, or the engine page won't load: an older build's summary beats nothing.
                grades[sectionId] = s.value
            }
        }
        if !force, let at = gradesAsOf[sectionId], Date().timeIntervalSince(at) < 30 * 60, grades[sectionId] != nil { return }
        do {
            let raw = try await engine.callRaw("gradebook", ["sectionId": sectionId])
            Cache.saveRaw("grades-\(sectionId)", raw)
            let g = try await summarize(rows: raw)
            Cache.save(Self.summaryKey(sectionId), g)
            Cache.save(Self.anySummaryKey(sectionId), g)
            noteNewGrades(sectionId: sectionId, old: grades[sectionId], new: g)
            grades[sectionId] = g
            gradesAsOf[sectionId] = Date()
            gradeErrors[sectionId] = nil
            learnFromGrades(sectionId: sectionId, g)
        } catch let e as EngineError {
            gradeErrors[sectionId] = e
        } catch {
            gradeErrors[sectionId] = .js(error.localizedDescription)
        }
    }

    /// The last summary whatever the build: the offline fallback.
    nonisolated static func anySummaryKey(_ sectionId: String) -> String { "gradeSummaryLast-\(sectionId)" }

    /// Per build, so new grade math never shows an old summary.
    nonisolated static func summaryKey(_ sectionId: String) -> String {
        "gradeSummary-\(sectionId)-\(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0")"
    }

    func summarize(rows: Data, whatIf: [String: [String: Double]] = [:], extra: [[String: Any]] = [],
                   drop: [String] = [], weights: [String: Double] = [:]) async throws -> CourseGrade {
        let rowsObj = try JSONSerialization.jsonObject(with: rows)
        var opts: [String: Any] = ["whatIf": whatIf, "extra": extra, "drop": drop]
        if !weights.isEmpty { opts["weights"] = weights }
        return try await engine.call("courseGrade", ["rows": rowsObj, "opts": opts])
    }

    func plan(sectionId: String, category: String, possible: Double, target: Double) async -> PlanResult? {
        guard let raw = gradeRows(sectionId), let rows = try? JSONSerialization.jsonObject(with: raw) else { return nil }
        return try? await engine.call("planTest", ["rows": rows, "category": category, "possible": possible, "target": target])
    }

    /// The gradebook category gives an item its type.
    private func learnFromGrades(sectionId: String, _ g: CourseGrade) {
        var changed = false
        let byId = Dictionary(allItems().map { ($0.schoologyId, $0) }, uniquingKeysWith: { a, _ in a })
        for gi in g.items {
            guard let it = byId[gi.id] else { continue }
            if it.categoryHint != gi.category { it.categoryHint = gi.category; changed = true }
        }
        if changed { save() }
    }

    /// When each score first appeared (nil = already there before the app was
    /// watching) and whether the student has looked at it. Saved, so "new"
    /// survives a relaunch and covers scores found by background refresh.
    struct GradeLogEntry: Codable {
        var earned: Double?
        var at: Date?
        var seen: Bool
    }
    var gradeLog: [String: GradeLogEntry] = Cache.load("gradeLog", as: [String: GradeLogEntry].self)?.value ?? [:]
    /// "New" wears off after a week even if never opened.
    nonisolated static let newGradeDays: Double = 7

    func isNewGrade(_ id: String) -> Bool { gradeLog[id].map { Self.isNew($0) } ?? false }

    nonisolated static func isNew(_ e: GradeLogEntry, now: Date = Date()) -> Bool {
        guard !e.seen, let at = e.at else { return false }
        return now.timeIntervalSince(at) < newGradeDays * 86400
    }

    /// Unseen new scores, newest first.
    /// The classes Grades shows: every class but the ones hidden there (Settings → Grades).
    var gradeClasses: [Course] {
        let hidden = GradesLook.hidden(UserDefaults.standard.string(forKey: GradesLook.hiddenKey) ?? "")
        return classes.filter { !hidden.contains($0.sectionId) }
    }

    func newGrades() -> [(Course, CourseGrade.Item)] {
        gradeClasses.flatMap { c in (grades[c.sectionId]?.items ?? []).filter { isNewGrade($0.id) }.map { (c, $0) } }
            .sorted { gradedFirst($0.1, $1.1) }
    }

    /// Unseen new scores in one class, or everywhere (from the saved log, so the
    /// tab badge is right before any gradebook has loaded this launch).
    func newGradeCount(_ sectionId: String? = nil) -> Int {
        guard let sectionId else {
            // Hidden classes don't count (their items are known once a gradebook is saved).
            let hidden = GradesLook.hidden(UserDefaults.standard.string(forKey: GradesLook.hiddenKey) ?? "")
            let skip = Set(hidden.flatMap { (grades[$0]?.items ?? []).map(\.id) })
            return gradeLog.keys.filter { isNewGrade($0) && !skip.contains($0) }.count
        }
        return (grades[sectionId]?.items ?? []).filter { isNewGrade($0.id) }.count
    }

    /// Order: when the score appeared, then due date (both newest first).
    func gradedFirst(_ a: CourseGrade.Item, _ b: CourseGrade.Item) -> Bool {
        let x = gradeLog[a.id]?.at, y = gradeLog[b.id]?.at
        if x != y { return (x ?? .distantPast) > (y ?? .distantPast) }
        return (a.due ?? "") > (b.due ?? "")
    }

    func markGradesSeen(_ ids: some Sequence<String>) {
        var changed = false
        for id in ids where gradeLog[id]?.seen == false { gradeLog[id]?.seen = true; changed = true }
        if changed { Cache.save("gradeLog", gradeLog) }
    }

    func markAllGradesSeen() { markGradesSeen(gradeLog.keys) }

    /// Logs scores that changed since the last saved gradebook; the first
    /// gradebook for a class only records what is there, as already seen.
    private func noteNewGrades(sectionId: String, old: CourseGrade?, new: CourseGrade) {
        let fresh = Self.logGrades(&gradeLog, old: old?.items, new: new.items)
        Cache.save("gradeLog", gradeLog)
        let hidden = GradesLook.hidden(UserDefaults.standard.string(forKey: GradesLook.hiddenKey) ?? "")
        if !fresh.isEmpty, !hidden.contains(sectionId), let c = course(sectionId) {
            Reminders.shared.newGrades(course: c.shortTitle, items: fresh, from: old?.pct, to: new.pct)
        }
    }

    /// Returns the scores that are new (changed since `old`) and logs them unseen.
    nonisolated static func logGrades(_ log: inout [String: GradeLogEntry], old: [CourseGrade.Item]?,
                                      new: [CourseGrade.Item], now: Date = Date()) -> [CourseGrade.Item] {
        let before = Dictionary((old ?? []).map { ($0.id, $0.earned) }, uniquingKeysWith: { a, _ in a })
        var fresh: [CourseGrade.Item] = []
        for it in new where it.earned != nil {
            if let e = log[it.id], e.earned == it.earned { continue }
            let changed = old != nil && before[it.id] != it.earned
            log[it.id] = GradeLogEntry(earned: it.earned, at: changed ? now : log[it.id]?.at, seen: !changed)
            if changed { fresh.append(it) }
        }
        return fresh
    }

    /// Background refresh: a few gradebooks per run, in turn, so new-grade
    /// alerts come without opening the app (a run only gets ~30 s).
    func refreshSomeGrades(count: Int = 3) async {
        let list = classes.map(\.sectionId)
        guard !list.isEmpty else { return }
        let key = "gradeRotation"
        let start = UserDefaults.standard.integer(forKey: key) % list.count
        let picked = (0..<min(count, list.count)).map { list[(start + $0) % list.count] }
        await twoAtATime(picked) { await self.loadGrades($0) }
        UserDefaults.standard.set(start + min(count, list.count), forKey: key)
    }

    func showToast(_ s: String) {
        toast = s
        Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if toast == s { toast = nil }
        }
    }
}
