package com.owenleung.dewey.store

import com.owenleung.dewey.engine.Course

/**
 * Dewey Decimal call numbers for classes, printed on the To Do catalog cards
 * (ios/App/Store/CallNumber.swift, the same rules).
 *
 * Careful on purpose, because class names are often odd ("Algebra 2_Trig",
 * "IM 2", "Freshman Seminar"): a precise number only for a clear subject word,
 * the main class (500, 900 …) when only the area is clear, and nothing at all
 * otherwise. A wrong number is worse than none. The student can set any class
 * by hand (long-press its card, or Settings → To Do → Call numbers); "None"
 * hides it.
 */
object CallNumber {
    /** Hand-set numbers: "sectionId=570;sectionId2=" ("" = none). Missing = automatic. */
    const val KEY = "callNumbers"

    /** Dewey's ten main classes: the choices in the picker. */
    val mains = listOf(
        "000" to "Computers and general", "100" to "Philosophy and psychology", "200" to "Religion",
        "300" to "Social sciences", "400" to "Languages", "500" to "Science and math",
        "600" to "Technology and health", "700" to "Arts and sports", "800" to "Literature",
        "900" to "History and geography",
    )

    /** Not classes in the library sense: never numbered. */
    private const val SKIP = """advisory|homeroom|home room|study hall|tutorial|office hours?|hub|lunch|seminar|counsel(ing|or)|tutoring|flex|ssr"""

    /**
     * First match wins, so the specific comes before the general ("computer science"
     * before "science", "spanish literature" before "spanish"). Whole words, any case.
     */
    private val rules: List<Pair<String, String>> = listOf(
        // Computing
        """comp(uter)?\.? ?sci(ence)?|computer|computing|programming|coding|cs[ -]?(a|p|principles)?|app dev(elopment)?""" to "005",
        // Social sciences, before plain "science"
        """political science|gov(ernment)?|civics|politics""" to "320",
        """econ(omics)?|macro(economics)?|micro(economics)?""" to "330",
        """psych(ology)?""" to "150",
        """sociology""" to "301",
        """ethnic studies""" to "305",
        """law|criminal justice""" to "340",
        """social (studies|science)""" to "300",
        """philosophy|ethics|theory of knowledge|tok""" to "100",
        """religion|religious|theology|bible|scripture""" to "200",
        // Literature and writing, before the languages
        """spanish lit(erature)?""" to "860",
        """french lit(erature)?""" to "840",
        """american lit(erature)?""" to "810",
        """(english )?lang(uage)? (and|&) comp(osition)?|ap lang|composition|rhetoric|creative writing|writing""" to "808",
        """journalism|yearbook|newspaper""" to "070",
        """eld|esl|ell|english language development""" to "420",
        """english|ela|language arts|lit(erature)?|ap lit""" to "820",
        // Languages
        """spanish|español""" to "460",
        """french""" to "440",
        """german""" to "430",
        """italian""" to "450",
        """latin""" to "470",
        """chinese|mandarin|japanese|korean""" to "495",
        """arabic|hebrew""" to "492",
        """asl|sign language""" to "419",
        // Math
        """algebra""" to "512",
        """geometry""" to "516",
        """calc(ulus)?|pre-?calc(ulus)?|math analysis|analysis""" to "515",
        """stat(istic)?s""" to "519",
        """math(ematics)?|integrated math|im|trig(onometry)?""" to "510",
        // Science
        """biotech(nology)?""" to "660",
        """anatomy|physiology|medical|biomed(ical)?""" to "612",
        """bio(logy)?|life science""" to "570",
        """chem(istry)?""" to "540",
        """physics""" to "530",
        """astronomy""" to "520",
        """earth (and space )?science|geology""" to "550",
        """environmental|ecology""" to "577",
        """health""" to "613",
        """science|stem""" to "500",
        // Technology and health
        """robotics""" to "629",
        """engineering|engr|pltw""" to "620",
        """culinary|cooking|foods?|nutrition""" to "641",
        """business|marketing|accounting|finance|entrepreneur(ship)?""" to "650",
        // Arts and sports
        """p\.?e\.?|physical education|fitness|weight (training|lifting)|athletics|sports?""" to "796",
        """band|orchestra|choir|chorus|music|jazz|piano|guitar""" to "780",
        """theat(er|re)|drama|dance""" to "792",
        """film|video|media arts?""" to "791",
        """photo(graphy)?""" to "770",
        """ceramics|pottery""" to "738",
        """design""" to "745",
        """art|drawing|painting|studio""" to "700",
        // History and geography
        """u\.?s\.? history|united states history|american history|apush""" to "973",
        """world history|world civ(ilizations?)?""" to "909",
        """european history|euro""" to "940",
        """geography|human geo""" to "910",
        """history""" to "900",
    )

    private val compiled = HashMap<String, Regex>()

    private fun matches(pattern: String, text: String): Boolean =
        compiled.getOrPut(pattern) { Regex("""(?i)(?<![\p{L}\p{N}])(?:$pattern)(?![\p{L}])""") }.containsMatchIn(text)

    /** The automatic number for a class name, or null when it isn't clear. */
    fun guess(title: String): String? {
        // "Algebra 2_Trig" → "Algebra 2 Trig": an underscore isn't a word break otherwise.
        val t = title.replace("_", " ")
        if (matches(SKIP, t)) return null
        return rules.firstOrNull { matches(it.first, t) }?.second
    }

    fun overrides(raw: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in (raw ?: "").split(";")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotEmpty()) out[kv[0]] = kv[1]
        }
        return out
    }

    /** `raw` with this class set to `value` (null = automatic again, "" = none). */
    fun setting(raw: String?, sectionId: String, value: String?): String {
        val o = overrides(raw).toMutableMap()
        if (value == null) o.remove(sectionId) else o[sectionId] = value
        return o.keys.sorted().joinToString(";") { "$it=${o[it]}" }
    }

    /** What the card prints: the hand-set number, else the guess. */
    fun number(course: Course, raw: String?): String? {
        overrides(raw)[course.sectionId]?.let { return it.ifEmpty { null } }
        return guess(course.shortTitle)
    }

    fun set(sectionId: String, value: String?) = Prefs.set(KEY, setting(Prefs.string(KEY), sectionId, value))
}
