package com.owenleung.dewey.store

import java.util.Date

/**
 * Plan (To Do › Plan): the student's open work in three sections, To do,
 * Later and Done, in their own order. Pure, so tests can run it (PlanTest;
 * ios/App/Store/Plan.swift).
 *
 * - Where an item goes until the student moves it (by due date): To do for
 *   late work, work due today or tomorrow, a test in the next 3 days, and an
 *   own task with no date; Later for the rest. Done holds what was finished today.
 * - Dragging a row into another section keeps it there (`planSection`);
 *   dragging it into Done marks it done, out of Done marks it to do.
 * - Order inside a section: the student's ranks. Work not placed yet is
 *   slotted in by due date: before the first placed item due later than it,
 *   so something due tomorrow never lands under work placed last week.
 */
/** A collapsed section's items aren't rows: they're held by section, so a drop on a collapsed heading lands at the top of that section. */
typealias PlanHidden = Map<PlanLogic.Bucket, List<String>>

object PlanLogic {
    enum class Bucket(val raw: String, val title: String) {
        TODO("todo", "To do"), LATER("later", "Later"), DONE("done", "Done");

        companion object {
            fun of(raw: String?): Bucket? = entries.firstOrNull { it.raw == raw }
        }
    }

    /** Where an item goes when the student hasn't put it anywhere. */
    fun autoBucket(due: Date?, late: Boolean, isTest: Boolean, mine: Boolean, now: Date): Bucket {
        if (late) return Bucket.TODO
        if (due == null) return if (mine) Bucket.TODO else Bucket.LATER
        val days = if (isTest) 4 else 2   // a test: today + the next 3 days, so there's time to study
        return if (due.before(Fmt.addDays(Fmt.startOfDay(now), days))) Bucket.TODO else Bucket.LATER
    }

    data class Entry(val id: String, val rank: Double?, val due: Long?)

    /** One section's order: placed items by rank, each unplaced one slotted in by due date. */
    fun order(entries: List<Entry>): List<Entry> {
        val byDue = compareBy<Entry> { it.due ?: Long.MAX_VALUE }.thenBy { it.id }
        val out = entries.filter { it.rank != null }.sortedWith(compareBy<Entry> { it.rank!! }.then(byDue)).toMutableList()
        for (u in entries.filter { it.rank == null }.sortedWith(byDue)) {
            val due = u.due ?: Long.MAX_VALUE
            val i = out.indexOfFirst { it.rank != null && (it.due ?: Long.MAX_VALUE) > due }.takeIf { it >= 0 } ?: out.size
            out.add(i, u)
        }
        return out
    }

    /** The list as shown: each section's heading, then its items. Headings are rows too, so dragging an item past one moves it into that section. */
    sealed class Row {
        data class Header(val bucket: Bucket) : Row()
        data class Item(val id: String) : Row()
    }

    fun rows(todo: List<String>, later: List<String>, done: List<String>): List<Row> =
        listOf(Row.Header(Bucket.TODO)) + todo.map(Row::Item) + Row.Header(Bucket.LATER) + later.map(Row::Item) +
            Row.Header(Bucket.DONE) + done.map(Row::Item)

    data class Place(val bucket: Bucket, val rank: Double)

    /** Where every item ends up after a drag of the row at `from` to before the row at `to` (1…). An item dropped above the first heading is in To do. */
    fun moved(rows: List<Row>, from: Int, to: Int, hidden: PlanHidden = emptyMap()): Map<String, Place> {
        val list = rows.toMutableList()
        val r = list.removeAt(from)
        val at = if (from < to) to - 1 else to
        list.add(at.coerceIn(0, list.size), r)
        return place(expand(list, hidden))
    }

    /** One item to another section (a swipe, TalkBack), at the top of it. */
    fun moved(rows: List<Row>, id: String, to: Bucket, hidden: PlanHidden = emptyMap()): Map<String, Place> {
        val list = rows.filter { it != Row.Item(id) }.toMutableList()
        val h = list.indexOf(Row.Header(to)).takeIf { it >= 0 } ?: (list.size - 1)
        list.add(h + 1, Row.Item(id))
        return place(expand(list, hidden))
    }

    /** One item up or down a row (TalkBack, the grip), crossing a heading if it's next. Never above the first heading. */
    fun nudging(rows: List<Row>, id: String, by: Int): List<Row> {
        val i = rows.indexOf(Row.Item(id)).takeIf { it >= 0 } ?: return rows
        val list = rows.toMutableList()
        val j = maxOf(1, minOf(list.size - 1, i + by))
        val t = list[i]; list[i] = list[j]; list[j] = t
        return list
    }

    fun nudged(rows: List<Row>, id: String, by: Int, hidden: PlanHidden = emptyMap()) = place(expand(nudging(rows, id, by), hidden))

    /** The shown rows with each collapsed section's items put back at its end. */
    fun expand(rows: List<Row>, hidden: PlanHidden): List<Row> {
        if (hidden.isEmpty()) return rows
        val out = mutableListOf<Row>()
        var open: Bucket? = null
        fun flush() { open?.let { b -> out += (hidden[b] ?: emptyList()).map(Row::Item) } }
        for (r in rows) {
            if (r is Row.Header) { flush(); open = r.bucket }
            out += r
        }
        flush()
        return out
    }

    fun place(list: List<Row>): Map<String, Place> {
        val out = LinkedHashMap<String, Place>()
        var bucket = Bucket.TODO
        var n = 0.0
        for (r in list) when (r) {
            is Row.Header -> { bucket = r.bucket; n = 0.0 }
            is Row.Item -> { n += 1; out[r.id] = Place(bucket, n) }
        }
        return out
    }
}
