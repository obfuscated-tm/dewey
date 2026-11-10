import Foundation

/// The one short status line under an assignment or quiz row in Materials. Exactly one
/// fact, first match wins: the score, else "Late · Oct 24", else Submitted / Turned in,
/// else "Due …". The check mark beside it already says done-but-not-turned-in, so that
/// never appears here.
enum MaterialStatus {
    struct Line: Equatable {
        var text: String
        var late = false
    }

    static func line(score: String?, late: Bool, due: Date?, submitted: Bool, turnedIn: Bool, now: Date = Date()) -> Line? {
        if let score, !score.isEmpty { return Line(text: score) }
        if late, let due { return Line(text: "Late · " + Fmt.short.string(from: due), late: true) }
        if submitted { return Line(text: "Submitted") }
        if turnedIn { return Line(text: "Turned in") }
        if let due { return Line(text: "Due " + Fmt.due(due, now: now)) }
        return nil
    }
}
