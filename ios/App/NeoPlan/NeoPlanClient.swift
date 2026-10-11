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
    case noToken, refused(Int), network
}

struct NeoPlanClient {
    static let server = URL(string: "https://neo-plan.vercel.app")!

    var token: String? { Keychain.get("neoplanToken") }
    var connected: Bool { token != nil }

    static func validToken(_ t: String) -> Bool { t.firstMatch(of: /^np_\S{8,}$/) != nil }

    /// "np_••••••••a1b2" (api.js maskToken).
    static func mask(_ t: String?) -> String {
        guard let t, t.count > 7 else { return "" }
        return "\(t.prefix(3))••••••••\(t.suffix(4))"
    }

    func setToken(_ t: String?) {
        if let t { Keychain.set("neoplanToken", t) } else { Keychain.delete("neoplanToken") }
    }

    private func request(_ method: String, _ path: String, body: [String: Any]? = nil) async throws -> Data {
        guard let token else { throw NeoPlanError.noToken }
        var r = URLRequest(url: Self.server.appendingPathComponent("api/extension").appendingPathComponent(path))
        if path.contains("?") {
            r.url = URL(string: Self.server.absoluteString + "/api/extension/" + path)
        }
        r.httpMethod = method
        r.timeoutInterval = 15
        r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: r) } catch { throw NeoPlanError.network }
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw NeoPlanError.refused(status) }
        return data
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
