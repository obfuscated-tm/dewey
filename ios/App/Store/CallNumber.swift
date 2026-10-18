import Foundation

/// Dewey Decimal call numbers for classes, printed on the To Do catalog cards.
///
/// Careful on purpose, because class names are often odd ("Algebra 2_Trig",
/// "IM 2", "Freshman Seminar"): a precise number only for a clear subject word,
/// the main class (500, 900 …) when only the area is clear, and nothing at all
/// otherwise. A wrong number is worse than none. The student can set any class
/// by hand (long-press its card, or Settings → To Do → Call numbers); "None"
/// hides it.
enum CallNumber {
    /// Hand-set numbers: "sectionId=570;sectionId2=" ("" = none). Missing = automatic.
    static let key = "callNumbers"

    /// Dewey's ten main classes: the choices in the picker.
    static let mains: [(number: String, name: String)] = [
        ("000", "Computers and general"), ("100", "Philosophy and psychology"), ("200", "Religion"),
        ("300", "Social sciences"), ("400", "Languages"), ("500", "Science and math"),
        ("600", "Technology and health"), ("700", "Arts and sports"), ("800", "Literature"),
        ("900", "History and geography"),
    ]

    /// Not classes in the library sense: never numbered.
    private static let skip = #"advisory|homeroom|home room|study hall|tutorial|office hours?|hub|lunch|seminar|counsel(ing|or)|tutoring|flex|ssr"#

    /// First match wins, so the specific comes before the general ("computer science"
    /// before "science", "spanish literature" before "spanish"). Whole words, any case.
    private static let rules: [(String, String)] = [
        // Computing
        (#"comp(uter)?\.? ?sci(ence)?|computer|computing|programming|coding|cs[ -]?(a|p|principles)?|app dev(elopment)?"#, "005"),
        // Social sciences, before plain "science"
        (#"political science|gov(ernment)?|civics|politics"#, "320"),
        (#"econ(omics)?|macro(economics)?|micro(economics)?"#, "330"),
        (#"psych(ology)?"#, "150"),
        (#"sociology"#, "301"),
        (#"ethnic studies"#, "305"),
        (#"law|criminal justice"#, "340"),
        (#"social (studies|science)"#, "300"),
        (#"philosophy|ethics|theory of knowledge|tok"#, "100"),
        (#"religion|religious|theology|bible|scripture"#, "200"),
        // Literature and writing, before the languages
        (#"spanish lit(erature)?"#, "860"),
        (#"french lit(erature)?"#, "840"),
        (#"american lit(erature)?"#, "810"),
        (#"(english )?lang(uage)? (and|&) comp(osition)?|ap lang|composition|rhetoric|creative writing|writing"#, "808"),
        (#"journalism|yearbook|newspaper"#, "070"),
        (#"eld|esl|ell|english language development"#, "420"),
        (#"english|ela|language arts|lit(erature)?|ap lit"#, "820"),
        // Languages
        (#"spanish|español"#, "460"),
        (#"french"#, "440"),
        (#"german"#, "430"),
        (#"italian"#, "450"),
        (#"latin"#, "470"),
        (#"chinese|mandarin|japanese|korean"#, "495"),
        (#"arabic|hebrew"#, "492"),
        (#"asl|sign language"#, "419"),
        // Math
        (#"algebra"#, "512"),
        (#"geometry"#, "516"),
        (#"calc(ulus)?|pre-?calc(ulus)?|math analysis|analysis"#, "515"),
        (#"stat(istic)?s"#, "519"),
        (#"math(ematics)?|integrated math|im|trig(onometry)?"#, "510"),
        // Science
        (#"biotech(nology)?"#, "660"),
        (#"anatomy|physiology|medical|biomed(ical)?"#, "612"),
        (#"bio(logy)?|life science"#, "570"),
        (#"chem(istry)?"#, "540"),
        (#"physics"#, "530"),
        (#"astronomy"#, "520"),
        (#"earth (and space )?science|geology"#, "550"),
        (#"environmental|ecology"#, "577"),
        (#"health"#, "613"),
        (#"science|stem"#, "500"),
        // Technology and health
        (#"robotics"#, "629"),
        (#"engineering|engr|pltw"#, "620"),
        (#"culinary|cooking|foods?|nutrition"#, "641"),
        (#"business|marketing|accounting|finance|entrepreneur(ship)?"#, "650"),
        // Arts and sports
        (#"p\.?e\.?|physical education|fitness|weight (training|lifting)|athletics|sports?"#, "796"),
        (#"band|orchestra|choir|chorus|music|jazz|piano|guitar"#, "780"),
        (#"theat(er|re)|drama|dance"#, "792"),
        (#"film|video|media arts?"#, "791"),
        (#"photo(graphy)?"#, "770"),
        (#"ceramics|pottery"#, "738"),
        (#"design"#, "745"),
        (#"art|drawing|painting|studio"#, "700"),
        // History and geography
        (#"u\.?s\.? history|united states history|american history|apush"#, "973"),
        (#"world history|world civ(ilizations?)?"#, "909"),
        (#"european history|euro"#, "940"),
        (#"geography|human geo"#, "910"),
        (#"history"#, "900"),
    ]

    private static func matches(_ pattern: String, _ text: String) -> Bool {
        text.range(of: #"(?i)(?<![\p{L}\p{N}])(?:"# + pattern + #")(?![\p{L}])"#, options: .regularExpression) != nil
    }

    /// The automatic number for a class name, or nil when it isn't clear.
    static func guess(_ title: String) -> String? {
        // "Algebra 2_Trig" → "Algebra 2 Trig": an underscore isn't a word break otherwise.
        let t = title.replacingOccurrences(of: "_", with: " ")
        if matches(skip, t) { return nil }
        return rules.first { matches($0.0, t) }?.1
    }

    static func overrides(_ raw: String) -> [String: String] {
        var out: [String: String] = [:]
        for pair in raw.split(separator: ";") {
            let kv = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            if kv.count == 2 { out[String(kv[0])] = String(kv[1]) }
        }
        return out
    }

    /// `raw` with this class set to `value` (nil = automatic again, "" = none).
    static func setting(_ raw: String, _ sectionId: String, _ value: String?) -> String {
        var o = overrides(raw)
        o[sectionId] = value
        return o.keys.sorted().map { "\($0)=\(o[$0]!)" }.joined(separator: ";")
    }

    /// What the card prints: the hand-set number, else the guess.
    static func number(for course: Course, raw: String) -> String? {
        if let o = overrides(raw)[course.sectionId] { return o.isEmpty ? nil : o }
        return guess(course.shortTitle)
    }
}
