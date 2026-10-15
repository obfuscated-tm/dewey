import Foundation

/// R2 · The Inbox: Schoology's messages list and /home/notifications, sorted
/// into From teachers, New in your classes and School. Only list pages are
/// fetched here; a thread is opened (and so marked read on Schoology) only by
/// MessageView, and only with "Mark messages read when opened" on.
@MainActor
@Observable
final class Inbox {
    static let markReadKey = "markMessagesRead"
    /// Message ids the student opened in the app (no dot after that).
    private static let seenKey = "inboxSeen"
    /// Ids and keys a refresh has already seen: an alert is only for something new.
    private static let knownMessagesKey = "inboxKnownMessages"
    private static let knownNoticesKey = "inboxKnownNotices"
    /// When the Inbox first loaded: older messages never get a "new" dot of the app's own.
    private static let since = "inboxSince"

    var threads: [MessageThread] = []
    var notices: [Notice] = []
    var asOf: Date?
    var error: EngineError?
    var loading = false
    private(set) var seen: Set<String>

    init() {
        if let c = Cache.load("inbox-messages", as: [MessageThread].self) { threads = c.value; asOf = c.savedAt }
        if let c = Cache.load("inbox-notices", as: [Notice].self) { notices = c.value }
        seen = Set(UserDefaults.standard.stringArray(forKey: Self.seenKey) ?? [])
    }

    static var markRead: Bool { UserDefaults.standard.object(forKey: markReadKey) as? Bool ?? true }

    // ── Sorting ──────────────────────────────────────────────────────────

    enum Kind { case teacher, school }

    /// A direct message, or one from a class's group ("[AP Comp Sci A - 2350: McLeodT p2 T1] …"), is from a
    /// teacher; anything from a hub or other group ("[CHS Hub: Gr10] …") is School.
    nonisolated static func kind(group: String?, classes: [Course]) -> Kind {
        guard let g = group?.trimmingCharacters(in: .whitespaces), !g.isEmpty else { return .teacher }
        let isClass = classes.contains { c in
            g == "\(c.title): \(c.sectionTitle)" || g == c.title || g.hasPrefix("\(c.title):")
        }
        return isClass ? .teacher : .school
    }

    func teacherThreads(_ classes: [Course]) -> [MessageThread] { threads.filter { Self.kind(group: $0.group, classes: classes) == .teacher } }
    func schoolThreads(_ classes: [Course]) -> [MessageThread] { threads.filter { Self.kind(group: $0.group, classes: classes) == .school } }

    /// Posted materials and new grades in the student's classes (grade rows carry no course).
    func classNotices(_ classes: [Course]) -> [Notice] {
        let ids = Set(classes.map(\.sectionId))
        return notices.filter { $0.type == "grade" || ($0.sectionId.map(ids.contains) ?? false) }
    }

    func otherNotices(_ classes: [Course]) -> [Notice] {
        let shown = Set(classNotices(classes).map(\.key))
        return notices.filter { !shown.contains($0.key) }
    }

    /// The dot: unread on Schoology, or arrived since the Inbox first loaded, and not opened in the app.
    func isNew(_ t: MessageThread) -> Bool {
        guard !seen.contains(t.id) else { return false }
        if t.unread == true { return true }
        guard let since = UserDefaults.standard.object(forKey: Self.since) as? Date, let d = t.when else { return false }
        return d > since
    }

    /// The tab badge: new messages, from teachers and from school (other School notices aren't counted).
    func newCount(_ classes: [Course]) -> Int { threads.filter(isNew).count }

    func markSeen(_ id: String) {
        guard !seen.contains(id) else { return }
        seen.insert(id)
        UserDefaults.standard.set(Array(seen.suffix(500)), forKey: Self.seenKey)
    }

    // ── Refresh ──────────────────────────────────────────────────────────

    /// First page of each list. `notify`: alert for teacher messages and class
    /// materials not seen by an earlier refresh (background runs only; the first
    /// refresh ever just remembers what's there).
    func refresh(_ m: AppModel, notify: Bool = false) async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let list: MessageList = try await m.engine.call("messages", ["page": 0])
            let nl: NoticeList = try await m.engine.call("notifications", ["page": 0])
            if UserDefaults.standard.object(forKey: Self.since) == nil { UserDefaults.standard.set(Date(), forKey: Self.since) }
            threads = list.threads
            notices = nl.items
            asOf = Date()
            error = nil
            Cache.save("inbox-messages", threads)
            Cache.save("inbox-notices", notices)
            let classes = m.classes
            let newMessages = Self.unknown(teacherThreads(classes).map(\.id), key: Self.knownMessagesKey)
            let newNotices = Self.unknown(classNotices(classes).filter { $0.type == "material" }.map(\.key), key: Self.knownNoticesKey)
            if notify {
                Reminders.shared.newMessages(teacherThreads(classes).filter { newMessages.contains($0.id) && isNew($0) })
                Reminders.shared.newMaterials(notices.filter { newNotices.contains($0.key) })
            }
        } catch let e as EngineError {
            error = e
        } catch {
            self.error = .js(error.localizedDescription)
        }
    }

    /// The ids in `ids` no earlier refresh saw; remembers them all. None on the very first run.
    private static func unknown(_ ids: [String], key: String) -> Set<String> {
        let d = UserDefaults.standard
        let first = d.object(forKey: key) == nil
        var known = d.stringArray(forKey: key) ?? []
        let fresh = ids.filter { !known.contains($0) }
        known.append(contentsOf: fresh)
        d.set(Array(known.suffix(300)), forKey: key)
        return first ? [] : Set(fresh)
    }

    func clear() {
        threads = []
        notices = []
        asOf = nil
    }
}
