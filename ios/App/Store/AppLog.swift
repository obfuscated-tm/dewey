import Foundation

/// The last 60 things the app did with its screens (launch, leaving and coming back, layout,
/// the Schoology sheet and assignment pages opening and closing), shown on Settings → About →
/// Developer, so a screen that vanished on the phone can be traced afterwards. Kept across launches.
enum AppLog {
    private static let key = "appLog"
    private static let fmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM d HH:mm:ss"
        return f
    }()

    static func note(_ s: String) {
        var all = lines
        all.append("\(fmt.string(from: Date()))  \(s)")
        UserDefaults.standard.set(Array(all.suffix(60)), forKey: key)
    }

    static var lines: [String] { UserDefaults.standard.stringArray(forKey: key) ?? [] }
    static func clear() { UserDefaults.standard.removeObject(forKey: key) }
}
