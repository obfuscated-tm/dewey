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
    /// Updates posted in the student's groups (hubs, counseling, advisory), newest first.
    var posts: [GroupPost] = []
    var asOf: Date?
    var error: EngineError?
    var loading = false
    /// The last page loaded of each list: 0 until a box's "Show older" asks for more.
    private(set) var messagePage = 0
    private(set) var noticePage = 0
    private(set) var hasOlderMessages = true
    private(set) var hasOlderNotices = true
    private(set) var loadingOlderMessages = false
    private(set) var loadingOlderNotices = false
    private(set) var seen: Set<String>

    init() {
        if let c = Cache.load("inbox-messages", as: [MessageThread].self) { threads = c.value; asOf = c.savedAt }
        if let c = Cache.load("inbox-notices", as: [Notice].self) { notices = c.value }
        if let c = Cache.load("inbox-posts", as: [GroupPost].self) { posts = c.value }
        seen = Set(UserDefaults.standard.stringArray(forKey: Self.seenKey) ?? [])
    }

    static var markRead: Bool { UserDefaults.standard.object(forKey: markReadKey) as? Bool ?? true }

    // ── Sorting ──────────────────────────────────────────────────────────

    enum Kind { case teacher, school }

    /// A direct message, or one from a class's group ("[AP Biology - 1101: RiveraM p2 T1] …"), is from a
    /// teacher; anything from a hub or other group ("[School Hub: Gr10] …") is School.
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
            async let messages: MessageList = m.engine.call("messages", ["page": 0])
            async let notifications: NoticeList = m.engine.call("notifications", ["page": 0])
            let (list, nl) = try await (messages, notifications)
            if UserDefaults.standard.object(forKey: Self.since) == nil { UserDefaults.standard.set(Date(), forKey: Self.since) }
            threads = list.threads
            notices = nl.items
            messagePage = 0
            noticePage = 0
            hasOlderMessages = list.hasMore ?? !list.threads.isEmpty
            hasOlderNotices = nl.hasMore ?? !nl.items.isEmpty
            asOf = Date()
            error = nil
            Cache.save("inbox-messages", threads)
            Cache.save("inbox-notices", notices)
            await refreshPosts(m)
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

    /// The first page of each group's updates (a group that fails keeps its last posts).
    /// Hub posts never reach /home/notifications or the messages list, so they're read here.
    private func refreshPosts(_ m: AppModel) async {
        let classIds = Set(m.courses.filter(\.isClass).map(\.sectionId))
        let groups = m.courses.filter { !classIds.contains($0.sectionId) }
        var got: [String: [GroupPost]] = Dictionary(grouping: posts, by: \.sectionId)
        // A few groups: one at a time, so a slow hub never holds up the messages.
        for g in groups {
            guard let list: [FeedPost] = try? await m.engine.call("feed", ["sectionId": g.sectionId, "page": 0]) else { continue }
            got[g.sectionId] = list.map { GroupPost(group: g.shortTitle, sectionId: g.sectionId, post: $0) }
        }
        let ids = Set(groups.map(\.sectionId))
        posts = got.filter { ids.contains($0.key) }.values.flatMap { $0 }
            .sorted { ($0.when ?? .distantPast) > ($1.when ?? .distantPast) }
        Cache.save("inbox-posts", posts)
    }

    /// The next page of the messages list, added under what's there. No alerts,
    /// and not cached: the next refresh goes back to the first page.
    func loadOlderMessages(_ m: AppModel) async {
        guard !loading, !loadingOlderMessages, hasOlderMessages else { return }
        loadingOlderMessages = true
        defer { loadingOlderMessages = false }
        do {
            let list: MessageList = try await m.engine.call("messages", ["page": messagePage + 1])
            let ids = Set(threads.map(\.id))
            let fresh = list.threads.filter { !ids.contains($0.id) }
            threads += fresh
            messagePage += 1
            hasOlderMessages = !fresh.isEmpty && (list.hasMore ?? true)
            error = nil
        } catch {
            self.error = error as? EngineError ?? .js(error.localizedDescription)
        }
    }

    /// The next page of /home/notifications, the same way.
    func loadOlderNotices(_ m: AppModel) async {
        guard !loading, !loadingOlderNotices, hasOlderNotices else { return }
        loadingOlderNotices = true
        defer { loadingOlderNotices = false }
        do {
            let nl: NoticeList = try await m.engine.call("notifications", ["page": noticePage + 1])
            let keys = Set(notices.map(\.key))
            let fresh = nl.items.filter { !keys.contains($0.key) }
            notices += fresh
            noticePage += 1
            hasOlderNotices = !fresh.isEmpty && (nl.hasMore ?? true)
            error = nil
        } catch {
            self.error = error as? EngineError ?? .js(error.localizedDescription)
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
        posts = []
        asOf = nil
        messagePage = 0
        noticePage = 0
        hasOlderMessages = true
        hasOlderNotices = true
    }
}

/// One update from a group's feed, with the group it came from.
struct GroupPost: Codable, Hashable, Identifiable {
    var group: String
    var sectionId: String
    var post: FeedPost
    var id: String { "\(sectionId)-\(post.id ?? post.date ?? post.bodyMd ?? "")" }
    var when: Date? { Fmt.date(post.time) }
}
