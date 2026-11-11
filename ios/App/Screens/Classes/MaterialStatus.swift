import Foundation

/// The one short status line under an assignment or quiz row in Materials: the date,
/// then one fact. "Late · Oct 24"; else "Oct 24 · 18 / 20"; else "Oct 24 · Submitted" or
/// "Oct 24 · Turned in"; else "Due …" (just the date once it has passed). With no date,
/// the fact alone. The check mark beside it already says done-but-not-turned-in, so that
/// never appears here.
enum MaterialStatus {
    struct Line: Equatable {
        var text: String
        var late = false
    }

    static func line(score: String?, late: Bool, due: Date?, submitted: Bool, turnedIn: Bool, now: Date = Date()) -> Line? {
        let day = due.map { Fmt.short.string(from: $0) }
        let hasScore = !(score ?? "").isEmpty
        if !hasScore, late, let day { return Line(text: "Late · " + day, late: true) }
        let fact: String? = hasScore ? score : submitted ? "Submitted" : turnedIn ? "Turned in" : nil
        if let fact { return Line(text: day.map { $0 + " · " + fact } ?? fact) }
        guard let due, let day else { return nil }
        return Line(text: due < now ? day : "Due " + Fmt.due(due, now: now))
    }
}
