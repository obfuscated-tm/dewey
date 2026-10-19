package com.owenleung.dewey.engine

import com.owenleung.dewey.store.Fmt
import kotlinx.serialization.Serializable
import java.util.Date

// Mirrors of the engine's results (docs/ios/ENGINE.md, "Engine API"), the same
// shapes as ios/App/Engine/Models.swift. Decoded with Json.engine, which turns
// snake_case keys into camelCase first (like Swift's convertFromSnakeCase).
// Unknown fields are ignored; anything Schoology might leave out is nullable.

@Serializable
data class Course(val sectionId: String, val title: String, val sectionTitle: String = "") {
    /** "Chemistry H - 12345: P3" → "Chemistry H" (the course code goes). */
    val shortTitle: String get() = short(title)

    /** "P3", "Period 3" … from the section title, or the section title itself. */
    val periodLabel: String
        get() = Regex("""(?i)\b(?:p(?:er(?:iod)?)?\.?\s*)(\d)\b""").find(sectionTitle)?.let { "P${it.groupValues[1]}" } ?: sectionTitle

    /** A class, not a hub or advisory: the student's choice in Settings, else the guess from the name. */
    val isClass: Boolean
        get() = com.owenleung.dewey.store.CourseKinds.choice(sectionId) ?: looksLikeClass

    /** The guess: "Name - 12345: …" (overlays/todo.js isClassCard). Other schools may name classes differently. */
    val looksLikeClass: Boolean
        get() = Regex("""^.+ - \d{3,5}:""").containsMatchIn("$title: $sectionTitle") || Regex("""- \d{3,5}$""").containsMatchIn(title)

    companion object {
        /** The same for any Schoology course name, e.g. "Spanish 2 - 1404: GarciaJ p4 T1" → "Spanish 2". */
        fun short(title: String): String {
            val t = title.replace(Regex("""\s*-\s*\d{3,6}\b.*$"""), "")
            return t.ifEmpty { title }
        }
    }
}

@Serializable
data class TodoLists(val overdue: List<EventItem> = emptyList(), val upcoming: List<EventItem> = emptyList())

/** A row of /v2/events/{list} (reader/parse/sync.js parseEventsJson). */
@Serializable
data class EventItem(
    val schoologyId: String,
    val title: String,
    val course: String? = null,
    val sectionId: String? = null,
    val due: String? = null,
    val graded: Boolean? = null,
    val url: String? = null,
)

@Serializable
data class SubmissionStatus(val state: String, val late: Boolean? = null, val quiz: Boolean? = null)

@Serializable
data class Attachment(val url: String, val title: String)

@Serializable
data class Assignment(
    val id: String = "",
    val title: String? = null,
    val type: String? = null,           // assignment | quiz | external_tool
    val due: String? = null,
    val instructionsMd: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val submission: SubmissionStatus? = null,
    val dropboxUrl: String? = null,
    val quiz: QuizInfo? = null,         // a newer quiz: its landing page's facts
)

/** reader/parse/quiz.js quizInfo(): what a newer quiz's landing page says. */
@Serializable
data class QuizInfo(
    val title: String? = null,
    val instructions: String? = null,
    val attemptsAllowed: Int? = null,   // null: no limit
    val attemptsTaken: Int = 0,
    val timeLimitMin: Double? = null,
    val opensAt: String? = null,
    val closesAt: String? = null,
    val overdue: Boolean? = null,
    val score: Double? = null,
    val maxPoints: Double? = null,
    val letter: String? = null,
    val state: String = "not_started", // in_progress | submitted | not_started
    val attempts: List<Attempt> = emptyList(),
) {
    @Serializable
    data class Attempt(val n: Int, val finished: Boolean, val at: String? = null, val minutes: Double? = null, val canView: Boolean = false)
}

@Serializable
data class RubricRef(val rubricId: String? = null, val enrollmentId: String? = null)

/** engine.js courseGrade(). */
@Serializable
data class CourseGrade(
    val period: String? = null,
    val pct: Double? = null,
    val letter: String? = null,
    val mode: String = "",
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val history: List<Point> = emptyList(),
    val periods: List<String> = emptyList(),
) {
    @Serializable
    data class Category(
        val title: String,
        val weight: Double? = null,
        val earned: Double = 0.0,
        val possible: Double = 0.0,
        val pct: Double? = null,
        val floor: Double? = null,
        val floorStatus: String = "none", // none | any | out | need
    )

    @Serializable
    data class Item(
        val id: String,
        val category: String = "",
        val title: String = "",
        val due: String? = null,          // 'YYYY-MM-DDTHH:MM', wall clock
        val grade: String = "",
        val comment: String = "",
        val rubric: RubricRef? = null,
        val earned: Double? = null,
        val possible: Double? = null,
        val pct: Double? = null,
        val impact: Double? = null,
    )

    @Serializable
    data class Point(val day: String? = null, val pct: Double? = null, val items: List<String> = emptyList())
}

@Serializable
data class PlanResult(val status: String, val need: Double? = null) // any | out | need | none

@Serializable
data class Rubric(
    val title: String = "",
    val total: Double? = null,
    val earned: Double? = null,
    val criteria: List<Criterion> = emptyList(),
) {
    @Serializable
    data class Criterion(val title: String = "", val max: Double? = null, val earned: Double? = null, val level: String? = null, val comment: String = "")
}

@Serializable
data class DropboxComment(val author: String? = null, val text: String = "")

@Serializable
data class DropboxInfo(val grade: String? = null, val comments: List<DropboxComment> = emptyList()) {
    /** Without Schoology's own lines ("Revision 1 submitted"). */
    val teacherComments: List<DropboxComment>
        get() = comments.filter { !Regex("""(?i)^revision \d+ (submitted|resubmitted)""").containsMatchIn(it.text) }
}

/** A Materials folder row (reader/parse/mcp.js materialsRows). */
@Serializable
data class MaterialRow(
    val kind: String,                   // folder | document | assignment | page | discussion | quiz | other
    val title: String,
    val id: String? = null,
    val url: String? = null,
) {
    val key: String get() = "$kind:${id ?: url ?: title}"
}

@Serializable
data class FileLink(val title: String, val url: String, val ext: String? = null)

@Serializable
data class Material(
    val url: String? = null,
    val kind: String = "",
    val title: String? = null,
    val bodyMd: String? = null,
    val target: String? = null,
    val files: List<FileLink> = emptyList(),
    val links: List<FileLink> = emptyList(),
)

/** feed({ sectionId, page }): one post in a course's or group's updates. */
@Serializable
data class FeedPost(
    val id: String? = null,
    val author: String? = null,
    val date: String? = null,
    val time: String? = null,
    val bodyMd: String? = null,
)

/** One update from a group's feed (a hub, counseling, advisory), with the group it came from. */
@Serializable
data class GroupPost(val group: String, val sectionId: String, val post: FeedPost) {
    val id: String get() = "$sectionId-${post.id ?: post.date ?: post.bodyMd ?: ""}"
    val `when`: Date? get() = Fmt.date(post.time)
}

// ── Inbox (reader/parse/inbox.js) ────────────────────────────────────────

@Serializable
data class MessageList(val threads: List<MessageThread> = emptyList(), val unreadCount: Int? = null, val hasMore: Boolean? = null)

/** A row of the messages list. `group` is the "[Course: section]" prefix, null for a direct message. */
@Serializable
data class MessageThread(
    val id: String,
    val subject: String = "",
    val group: String? = null,
    val preview: String? = null,
    val from: String? = null,
    val fromId: String? = null,
    val date: String? = null,
    val unread: Boolean? = null,
) {
    val time: Date? get() = Fmt.date(date)
}

/** message({ id }): the whole thread. Fetching it marks it read on Schoology. */
@Serializable
data class MessageDetail(
    val id: String = "",
    val subject: String = "",
    val group: String? = null,
    val participants: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
) {
    @Serializable
    data class Message(
        val author: String? = null,
        val authorId: String? = null,
        val date: String? = null,
        val bodyMd: String? = null,
        val attachments: List<Attachment> = emptyList(),
    )
}

@Serializable
data class NoticeList(val items: List<Notice> = emptyList(), val hasMore: Boolean? = null)

/** A /home/notifications row: posted materials (`material`) or new grades (`grade`). */
@Serializable
data class Notice(
    val key: String,
    val type: String = "",
    val course: String? = null,
    val sectionId: String? = null,
    val text: String = "",
    val date: String? = null,
    val items: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(val title: String, val url: String, val kind: String = "other")

    val time: Date? get() = Fmt.date(date)
}
