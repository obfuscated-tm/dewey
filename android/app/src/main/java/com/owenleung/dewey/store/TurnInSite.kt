package com.owenleung.dewey.store

import java.net.URI

/**
 * Where a class turns in work that has no Schoology dropbox. The first guess is the first
 * link in the instructions that isn't Schoology, Google or YouTube; once the student opens
 * "Turn in on …" (or picks a site by holding the Turn in row), that site is saved for the
 * class and wins over a reference link listed before it. Works for any school's sites:
 * nothing here knows a hostname. Pure (java.net.URI), so JVM tests run it.
 */
object TurnInSite {
    private val skipped = listOf("schoology.com", "google.com", "youtube.com")
    private val link = Regex("""\]\((https?://[^)\s]+)\)""")

    /** "apcs.tinocs.com" (no www.). */
    fun host(url: String): String? {
        var h = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return null
        if (h.isEmpty()) return null
        if (h.startsWith("www.")) h = h.removePrefix("www.")
        return h
    }

    /** The instructions' outside links, in order. */
    fun links(md: String?): List<String> {
        md ?: return emptyList()
        return link.findAll(md).mapNotNull { m ->
            val u = m.groupValues[1]
            val h = host(u) ?: return@mapNotNull null
            if (skipped.any { h == it || h.endsWith(".$it") }) null else u
        }.toList()
    }

    /** Each outside site once, in order: what the hold menu offers. */
    fun hosts(md: String?): List<String> = links(md).mapNotNull(::host).distinct()

    /**
     * No outside links at all: nothing (paper or in class), even with a saved site.
     * A saved site: the instructions' link on it (the exact page), else the site itself.
     * Otherwise the first outside link.
     */
    fun pick(instructionsMd: String?, saved: String?): String? {
        val ls = links(instructionsMd)
        if (ls.isEmpty()) return null
        if (saved != null) {
            val h = host(saved)
            if (h != null) return ls.firstOrNull { host(it) == h } ?: saved
        }
        return ls.first()
    }
}
