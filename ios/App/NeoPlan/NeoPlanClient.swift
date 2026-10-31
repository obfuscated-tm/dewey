import Foundation
import Security

/// neo-plan, optional sync (docs/ios/ENGINE.md, "neo-plan"). Same server, token
/// and routes as the extension's outputs/neoplan/api.js buildRequest; if they
/// disagree, that file wins. The token lives in the Keychain.
struct NeoPlanItem: Codable, Hashable {
    var id: String
    var sourceId: String?
    var type: String?
    var work: String?             // todo | ready
    var cleared: Bool?
    var removed: Bool?

    enum CodingKeys: String, CodingKey { case id, sourceId = "source_id", type, work, cleared, removed }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // ids arrive as numbers or strings.
        if let s = try? c.decode(String.self, forKey: .id) { id = s } else { id = String(try c.decode(Int.self, forKey: .id)) }
        if let s = try? c.decodeIfPresent(String.self, forKey: .sourceId) { sourceId = s }
        else if let n = try? c.decodeIfPresent(Int.self, forKey: .sourceId) { sourceId = String(n) }
        type = try c.decodeIfPresent(String.self, forKey: .type)
        work = try c.decodeIfPresent(String.self, forKey: .work)
        cleared = try? c.decodeIfPresent(Bool.self, forKey: .cleared)
        removed = try? c.decodeIfPresent(Bool.self, forKey: .removed)
    }
}

enum NeoPlanError: Error {
    case noToken, network
    /// The status, and neo-plan's `error` word when it sent one ("no_column", "token", …).
    case refused(Int, String?)
}

/// A Schoology section and the neo-plan column it files into (enrich's `courses`).
struct NeoPlanCourse: Decodable, Hashable {
    var sectionId: String
    var classId: String?

    enum CodingKeys: String, CodingKey { case sectionId = "section_id", classId = "class_id" }
}

struct NeoPlanClient {
    static let server = URL(string: "https://neo-plan.vercel.app")!

    var token: String? { Keychain.get("neoplanToken") }
    /// Off during the tour's pretend week, so nothing pretend reaches the board.
    var connected: Bool { !Practice.active && token != nil }

    static func validToken(_ t: String) -> Bool { t.firstMatch(of: /^np_\S{8,}$/) != nil }

    /// "np_••••••••a1b2" (api.js maskToken).
    static func mask(_ t: String?) -> String {
        guard let t, t.count > 7 else { return "" }
        return "\(t.prefix(3))••••••••\(t.suffix(4))"
    }

    func setToken(_ t: String?) {
        if let t { Keychain.set("neoplanToken", t) } else { Keychain.delete("neoplanToken") }
    }

    private func request(_ method: String, _ path: String, body: [String: Any]? = nil,
                         base: String = "api/extension", timeout: TimeInterval = 15) async throws -> Data {
        guard let token else { throw NeoPlanError.noToken }
        var r = URLRequest(url: Self.server.appendingPathComponent(base).appendingPathComponent(path))
        if path.contains("?") {
            r.url = URL(string: Self.server.absoluteString + "/\(base)/" + path)
        }
        r.httpMethod = method
        r.timeoutInterval = timeout
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: r) } catch { throw NeoPlanError.network }
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            struct E: Decodable { var error: String? }
            throw NeoPlanError.refused(status, (try? JSONDecoder().decode(E.self, from: data))?.error)
        }
        return data
    }

    /// Tells neo-plan the student's courses and which work is in which, so it
    /// can file items by course and, for an account that started with no
    /// columns, make a column per course with work (docs/ios/ENGINE.md,
    /// "neo-plan"; api.js `enrich`). Returns the course map as neo-plan has it.
    func enrich(courses: [Course], items: [(schoologyId: String, sectionId: String)], byHand: Bool) async throws -> [NeoPlanCourse] {
        // One malformed entry refuses the whole request, so only digit ids go.
        let digits = { (s: String) in s.firstMatch(of: /^\d{1,20}$/) != nil }
        let courses = courses.filter { digits($0.sectionId) }
        let items = items.filter { digits($0.schoologyId) && digits($0.sectionId) }
        var body: [String: Any] = [
            "fetched_at": Fmt.isoPlain.string(from: Date()),
            "courses": courses.prefix(50).map { ["section_id": $0.sectionId, "title": String($0.title.prefix(500)),
                                                 "section_title": String($0.sectionTitle.prefix(500))] },
            "items": items.prefix(500).map { ["kind": "assignment", "schoology_id": $0.schoologyId, "section_id": $0.sectionId] },
        ]
        if byHand { body["by_hand"] = true }
        let data = try await request("POST", "enrich", body: body, base: "api/import/schoology", timeout: 45)
        struct R: Decodable { var courses: [NeoPlanCourse]? }
        return (try? JSONDecoder().decode(R.self, from: data))?.courses ?? []
    }

    func today() async throws -> Data { try await request("GET", "today") }

    /// ≤ 100 ids per call.
    func items(_ ids: [String]) async throws -> [NeoPlanItem] {
        var out: [NeoPlanItem] = []
        let clean = Array(Set(ids.filter { $0.firstMatch(of: /^\d{1,20}$/) != nil }))
        for start in stride(from: 0, to: clean.count, by: 100) {
            let chunk = clean[start..<min(start + 100, clean.count)]
            let data = try await request("GET", "items?schoology_ids=\(chunk.joined(separator: ","))")
            struct R: Decodable { var items: [NeoPlanItem] }
            out += (try? JSONDecoder().decode(R.self, from: data).items) ?? []
        }
        return out
    }

    func setWork(_ id: String, ready: Bool) async throws {
        _ = try await request("POST", "items/\(id)/work", body: ["work": ready ? "ready" : "todo"])
    }
    func setType(_ id: String, _ type: ItemType) async throws {
        _ = try await request("POST", "items/\(id)/type", body: ["type": type.rawValue])
    }
    func turnIn(_ id: String) async throws { _ = try await request("POST", "items/\(id)/turn-in") }
    func putBack(_ id: String) async throws { _ = try await request("POST", "items/\(id)/put-back") }

    func addItem(schoologyId: String, sectionId: String?, title: String, due: Date?, type: ItemType?) async throws -> NeoPlanItem? {
        var body: [String: Any] = [
            "schoology_id": schoologyId,
            "section_id": sectionId.map { $0 as Any } ?? NSNull(),
            "title": String(title.prefix(500)),
            "due_at": due.map { Fmt.isoPlain.string(from: $0) as Any } ?? NSNull(),
            "source_url": "\(Engine.origin.absoluteString)/assignment/\(schoologyId)",
        ]
        if let type, type == .exam || type == .assignment { body["type"] = type.rawValue }
        let data = try await request("POST", "items", body: body)
        struct R: Decodable { var item: NeoPlanItem? }
        return (try? JSONDecoder().decode(R.self, from: data))?.item ?? (try? JSONDecoder().decode(NeoPlanItem.self, from: data))
    }
}

enum Keychain {
    private static let service = "com.owenleung.classwork"

    static func set(_ key: String, _ value: String) {
        delete(key)
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: key, kSecValueData as String: Data(value.utf8),
                                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        SecItemAdd(q as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: key, kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func delete(_ key: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: key]
        SecItemDelete(q as CFDictionary)
    }
}
