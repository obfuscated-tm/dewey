import Foundation

/// Quick add (To Do's +): reads a day, a time, a class and a kind out of what the
/// student types, e.g. "chem lab fri after school" → Fri · 3:30 pm · Chemistry H.
/// The date and time words leave the title; the class and kind words stay in it
/// ("Chem lab" is still the task). Pure, so tests can run it (TaskParseTests).
enum TaskParse {
    /// A time of day.
    struct Clock: Equatable, Hashable {
        var hour: Int
        var minute: Int
    }

    /// The school's own words for a time. FUHSD's first period starts around 8:30.
    static let beforeSchool = Clock(hour: 8, minute: 0)
    static let afterSchool = Clock(hour: 15, minute: 30)
    /// Due tonight: 11:59 pm, like Schoology's "due that day".
    static let tonight = Clock(hour: 23, minute: 59)

    struct ClassName {
        var sectionId: String
        var title: String
    }

    struct Result: Equatable {
        /// What's left once the date and time words are out (the typed text if nothing is left).
        var title: String
        /// The day, at its start.
        var day: Date?
        var time: Clock?
        var sectionId: String?
        var type: ItemType?
    }

    static func parse(_ text: String, now: Date, classes: [ClassName] = [], calendar cal: Calendar = .current) -> Result {
        let words = text.split(separator: " ", omittingEmptySubsequences: true).map(String.init)
        let low = words.map { $0.lowercased().trimmingCharacters(in: .punctuationCharacters.subtracting(CharacterSet(charactersIn: ":/"))) }
        var used = Set<Int>()
        var day: Date?
        var time: Clock?
        let today = cal.startOfDay(for: now)
        func plus(_ n: Int) -> Date { cal.date(byAdding: .day, value: n, to: today)! }

        var i = 0
        while i < low.count {
            let w = low[i]
            let next = i + 1 < low.count ? low[i + 1] : nil
            // "on fri", "by tmrw", "due 10/24", "at 8am": the little word goes too, when it leads into one.
            func take(_ n: Int) {
                for k in i..<(i + n) { used.insert(k) }
                if i > 0, ["on", "by", "due", "at", "for"].contains(low[i - 1]), !used.contains(i - 1) { used.insert(i - 1) }
                i += n
            }
            if day == nil, ["today", "tdy"].contains(w) { day = today; take(1); continue }
            if day == nil, w == "tonight" { day = today; time = time ?? tonight; take(1); continue }
            if day == nil, ["tomorrow", "tmrw", "tmr", "tmw", "tomorow"].contains(w) { day = plus(1); take(1); continue }
            if day == nil, w == "next", let n = next, let wd = weekday(n, original: words[i + 1]) {
                // "next tue": that day in next week's calendar week (on a Thursday, the coming Tuesday).
                let u = upcoming(wd, from: today, cal)
                let sameWeek = cal.isDate(u, equalTo: today, toGranularity: .weekOfYear)
                day = sameWeek ? cal.date(byAdding: .day, value: 7, to: u)! : u; take(2); continue
            }
            if day == nil, let wd = weekday(w, original: words[i]) { day = upcoming(wd, from: today, cal); take(1); continue }
            if day == nil, w == "in", let n = next, i + 2 < low.count, ["day", "days"].contains(low[i + 2]), let k = Int(n) {
                day = plus(k); take(3); continue
            }
            if day == nil, w == "in", next == "a" || next == "1", i + 2 < low.count, low[i + 2] == "week" { day = plus(7); take(3); continue }
            if day == nil, let d = slashDate(w, today: today, cal) { day = d; take(1); continue }
            if day == nil, let m = month(w), let n = next, let d = Int(n), let date = monthDay(m, d, today: today, cal) {
                day = date; take(2); continue
            }
            if time == nil, w == "before" || w == "after", next == "school" { time = w == "before" ? beforeSchool : afterSchool; take(2); continue }
            if time == nil, w == "noon" { time = Clock(hour: 12, minute: 0); take(1); continue }
            if time == nil, w == "midnight" { time = tonight; take(1); continue }
            if time == nil, let c = clock(w, next: next) {
                time = c.clock
                take(c.words)
                continue
            }
            // "at 3": a bare number counts only after "at".
            if time == nil, w == "at", let n = next, let h = Int(n), (1...12).contains(h) {
                time = Clock(hour: h <= 6 ? h + 12 : h, minute: 0); used.insert(i); used.insert(i + 1); i += 2; continue
            }
            i += 1
        }

        let kept = words.enumerated().filter { !used.contains($0.offset) }.map(\.element)
        var title = kept.joined(separator: " ").trimmingCharacters(in: .whitespaces)
        if title.isEmpty { title = text.trimmingCharacters(in: .whitespaces) }
        return Result(title: title, day: day, time: time,
                      sectionId: matchClass(low, classes), type: kind(low))
    }

    /// The due date from a day and a time. A time alone is today, or tomorrow once
    /// it has passed; a day alone is by the end of it; neither is no date.
    static func due(day: Date?, time: Clock?, now: Date, calendar cal: Calendar = .current) -> Date? {
        guard day != nil || time != nil else { return nil }
        let t = time ?? tonight
        var d = day ?? cal.startOfDay(for: now)
        var at = cal.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: d)!
        if day == nil && at <= now {
            d = cal.date(byAdding: .day, value: 1, to: d)!
            at = cal.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: d)!
        }
        return at
    }

    // ── Pieces ───────────────────────────────────────────────────────────

    /// 1 = Sunday … 7 = Saturday. All-caps "SAT" is the test, not Saturday.
    private static func weekday(_ w: String, original: String) -> Int? {
        let names: [(Int, [String])] = [
            (1, ["sun", "sunday"]), (2, ["mon", "monday"]), (3, ["tue", "tues", "tuesday"]),
            (4, ["wed", "weds", "wednesday"]), (5, ["thu", "thur", "thurs", "thursday"]),
            (6, ["fri", "friday"]), (7, ["sat", "saturday"]),
        ]
        if original.hasPrefix("SAT") { return nil }
        return names.first { $0.1.contains(w) }?.0
    }

    /// The next such weekday, today included ("quiz fri" on a Friday is today's).
    private static func upcoming(_ wd: Int, from today: Date, _ cal: Calendar) -> Date {
        let now = cal.component(.weekday, from: today)
        return cal.date(byAdding: .day, value: (wd - now + 7) % 7, to: today)!
    }

    private static func month(_ w: String) -> Int? {
        let names = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
        let full = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"]
        if let i = full.firstIndex(of: w) { return i + 1 }
        if w == "sept" { return 9 }
        if let i = names.firstIndex(of: w) { return i + 1 }
        return nil
    }

    /// A month and day, this year, or next year if it has passed.
    private static func monthDay(_ m: Int, _ d: Int, today: Date, _ cal: Calendar) -> Date? {
        guard (1...31).contains(d) else { return nil }
        var c = cal.dateComponents([.year], from: today)
        c.month = m; c.day = d
        guard var date = cal.date(from: c), cal.component(.day, from: date) == d else { return nil }
        if date < today { date = cal.date(byAdding: .year, value: 1, to: date)! }
        return date
    }

    /// "10/24" or "10/24/26".
    private static func slashDate(_ w: String, today: Date, _ cal: Calendar) -> Date? {
        let p = w.split(separator: "/").compactMap { Int($0) }
        guard p.count >= 2, p.count == w.split(separator: "/").count, (1...12).contains(p[0]) else { return nil }
        if p.count == 3 {
            var c = DateComponents(); c.year = p[2] < 100 ? 2000 + p[2] : p[2]; c.month = p[0]; c.day = p[1]
            return cal.date(from: c)
        }
        return monthDay(p[0], p[1], today: today, cal)
    }

    /// "8am", "8:30", "3:30pm", "8 am". Without am or pm, 1 to 6 o'clock is the afternoon.
    private static func clock(_ w: String, next: String?) -> (clock: Clock, words: Int)? {
        guard let m = w.wholeMatch(of: /(\d{1,2})(?::(\d{2}))?(am|pm|a|p)?/) else { return nil }
        guard let h0 = Int(m.1), (0...23).contains(h0) else { return nil }
        let minute = m.2.flatMap { Int($0) } ?? 0
        guard minute < 60 else { return nil }
        var mark = m.3.map(String.init)
        var words = 1
        if mark == nil, let n = next, ["am", "pm"].contains(n) { mark = n; words = 2 }
        // A bare number ("2 problems") isn't a time; it needs a colon or am/pm.
        guard mark != nil || m.2 != nil else { return nil }
        var h = h0
        if let mark {
            guard (1...12).contains(h0) else { return nil }
            if mark.hasPrefix("p") && h < 12 { h += 12 }
            if mark.hasPrefix("a") && h == 12 { h = 0 }
        } else if (1...6).contains(h) {
            h += 12
        }
        return (Clock(hour: h, minute: minute), words)
    }

    private static func kind(_ low: [String]) -> ItemType? {
        if low.contains(where: { ["test", "quiz", "exam", "midterm", "final", "finals"].contains($0) }) { return .exam }
        if low.contains(where: { ["hw", "homework"].contains($0) }) { return .assignment }
        return nil
    }

    /// Words that start many class names or are just part of the task.
    private static let notClasses: Set<String> = [
        "the", "and", "for", "with", "study", "studies", "read", "reading", "write", "writing", "work",
        "lab", "test", "quiz", "review", "finish", "notes", "project", "practice", "unit", "chapter",
    ]

    /// A class named by the start of one of its words ("chem", "bio", "precalc", "spanish"),
    /// when it fits exactly one class.
    private static func matchClass(_ low: [String], _ classes: [ClassName]) -> String? {
        let keys = classes.map { c -> (String, [String]) in
            let t = Course.short(c.title).lowercased()
            let joined = t.replacingOccurrences(of: "-", with: "")
            let parts = t.split(whereSeparator: { !$0.isLetter }).map(String.init)
                + joined.split(whereSeparator: { !$0.isLetter }).map(String.init)
            return (c.sectionId, parts.filter { $0.count >= 4 })
        }
        for w in low where w.count >= 3 && !notClasses.contains(w) && w.allSatisfy(\.isLetter) {
            let hits = Set(keys.filter { $0.1.contains { $0.hasPrefix(w) } }.map(\.0))
            if hits.count == 1 { return hits.first }
        }
        return nil
    }
}
