import Foundation

/// Last good engine results on disk (Application Support/cache), so every screen
/// can show the saved copy offline with "as of N min ago".
enum Cache {
    struct Entry<T: Codable>: Codable {
        var savedAt: Date
        var value: T
    }

    private static let dir: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    private static func file(_ key: String) -> URL {
        dir.appendingPathComponent(key.replacingOccurrences(of: "/", with: "_") + ".json")
    }

    static func save<T: Codable>(_ key: String, _ value: T, at date: Date = Date()) {
        guard let data = try? JSONEncoder().encode(Entry(savedAt: date, value: value)) else { return }
        try? data.write(to: file(key), options: .atomic)
    }

    static func load<T: Codable>(_ key: String, as type: T.Type = T.self) -> Entry<T>? {
        guard let data = try? Data(contentsOf: file(key)) else { return nil }
        return try? JSONDecoder().decode(Entry<T>.self, from: data)
    }

    static func clear() {
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    /// Raw JSON (gradebook rows go back to the engine untouched).
    static func saveRaw(_ key: String, _ data: Data) {
        try? data.write(to: file(key + ".raw"), options: .atomic)
    }

    static func loadRaw(_ key: String) -> (Data, Date)? {
        let f = file(key + ".raw")
        guard let data = try? Data(contentsOf: f),
              let date = (try? FileManager.default.attributesOfItem(atPath: f.path))?[.modificationDate] as? Date
        else { return nil }
        return (data, date)
    }
}

enum Fmt {
    static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    static let isoPlain = ISO8601DateFormatter()

    static func date(_ s: String?) -> Date? {
        guard let s, !s.isEmpty else { return nil }
        return iso.date(from: s) ?? isoPlain.date(from: s)
    }

    /// grademath's 'YYYY-MM-DDTHH:MM' wall-clock date.
    static func wallDate(_ s: String?) -> Date? {
        guard let s else { return nil }
        return (s.count > 10 ? wallDateTime : wallDay).date(from: s)
    }
    // Built once: a DateFormatter is slow to make, and these run per row.
    private static func posix(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = pattern
        return f
    }
    private static let wallDateTime = posix("yyyy-MM-dd'T'HH:mm")
    private static let wallDay = posix("yyyy-MM-dd")

    private static func fmt(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = pattern
        f.amSymbol = "am"
        f.pmSymbol = "pm"
        return f
    }
    static let time = fmt("h:mm a")
    static let weekdayTime = fmt("EEE h:mm a")
    static let dayTime = fmt("EEE MMM d, h:mm a")
    static let header = fmt("EEEE, MMM d")
    static let short = fmt("MMM d")
    static let weekdayCommaTime = fmt("EEE, h:mm a")

    /// "Today, 11:59 pm", "Fri, 8:30 am", "Tue Sep 29, 8:30 am".
    static func due(_ d: Date?, now: Date = Date()) -> String {
        guard let d else { return "No due date" }
        let cal = Calendar.current
        if cal.isDate(d, inSameDayAs: now) { return "Today, \(time.string(from: d))" }
        if let t = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(d, inSameDayAs: t) { return "Tomorrow, \(time.string(from: d))" }
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: d)).day ?? 99
        if days > 0 && days < 7 { return weekdayCommaTime.string(from: d) }
        return dayTime.string(from: d)
    }

    /// "just now", "5 min ago", "2 h ago", "Oct 3".
    static func ago(_ d: Date, now: Date = Date()) -> String {
        let s = now.timeIntervalSince(d)
        if s < 60 { return "just now" }
        if s < 3600 { return "\(Int(s / 60)) min ago" }
        if s < 86400 { return "\(Int(s / 3600)) h ago" }
        return short.string(from: d)
    }

    static func pct(_ p: Double?, digits: Int = 1) -> String {
        guard let p else { return "—" }
        return String(format: "%.\(digits)f%%", p)
    }

    static func num(_ n: Double?) -> String {
        guard let n else { return "—" }
        return n.rounded() == n ? String(Int(n)) : String(format: "%.2f", n).replacingOccurrences(of: #"0+$"#, with: "", options: .regularExpression)
    }

    static func signed(_ d: Double?) -> String {
        guard let d else { return "" }
        return (d < 0 ? "−" : "+") + String(format: "%.2f", abs(d))
    }
}
