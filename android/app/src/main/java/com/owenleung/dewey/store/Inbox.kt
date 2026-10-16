package com.owenleung.dewey.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owenleung.dewey.AppModel
import com.owenleung.dewey.engine.Course
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.engine.MessageList
import com.owenleung.dewey.engine.MessageThread
import com.owenleung.dewey.engine.Notice
import com.owenleung.dewey.engine.NoticeList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import java.util.Date

/**
 * R2 · The Inbox: Schoology's messages list and /home/notifications, sorted
 * into From teachers, New in your classes and School. Only list pages are
 * fetched here; a thread is opened (and so marked read on Schoology) only by
 * the message screen, and only with "Mark messages read when opened" on.
 */
class Inbox {
    companion object {
        const val MARK_READ_KEY = "markMessagesRead"
        private const val SEEN_KEY = "inboxSeen"
        private const val KNOWN_MESSAGES_KEY = "inboxKnownMessages"
        private const val KNOWN_NOTICES_KEY = "inboxKnownNotices"
        /** When the Inbox first loaded: older messages never get a "new" dot of the app's own. */
        private const val SINCE_KEY = "inboxSince"

        val markRead: Boolean get() = Prefs.bool(MARK_READ_KEY, true)

        enum class Kind { TEACHER, SCHOOL }

        /**
         * A direct message, or one from a class's group ("[AP Biology - 1101: RiveraM p2 T1] …"), is from a
         * teacher; anything from a hub or other group ("[School Hub: Gr10] …") is School.
         */
        fun kind(group: String?, classes: List<Course>): Kind {
            val g = group?.trim()
            if (g.isNullOrEmpty()) return Kind.TEACHER
            val isClass = classes.any { c -> g == "${c.title}: ${c.sectionTitle}" || g == c.title || g.startsWith("${c.title}:") }
            return if (isClass) Kind.TEACHER else Kind.SCHOOL
        }

        /** The ids in `ids` no earlier refresh saw; remembers them all. None on the very first run. */
        private fun unknown(ids: List<String>, key: String): Set<String> {
            val first = !Prefs.has(key)
            val known = Prefs.strings(key).toMutableList()
            val fresh = ids.filter { it !in known }
            known += fresh
            Prefs.set(key, known.takeLast(300))
            return if (first) emptySet() else fresh.toSet()
        }
    }

    var threads by mutableStateOf<List<MessageThread>>(emptyList())
    var notices by mutableStateOf<List<Notice>>(emptyList())
    var asOf by mutableStateOf<Date?>(null)
    var error by mutableStateOf<EngineError?>(null)
    var loading by mutableStateOf(false)
    /** The last page loaded of each list: 0 until a box's "Show older" asks for more. */
    private var messagePage = 0
    private var noticePage = 0
    var hasOlderMessages by mutableStateOf(true)
        private set
    var hasOlderNotices by mutableStateOf(true)
        private set
    private var loadingOlderMessages = false
    private var loadingOlderNotices = false
    private var seen by mutableStateOf(Prefs.strings(SEEN_KEY).toSet())

    init {
        Cache.load("inbox-messages", ListSerializer(MessageThread.serializer()))?.let { threads = it.first; asOf = it.second }
        Cache.load("inbox-notices", ListSerializer(Notice.serializer()))?.let { notices = it.first }
    }

    fun teacherThreads(classes: List<Course>) = threads.filter { kind(it.group, classes) == Kind.TEACHER }
    fun schoolThreads(classes: List<Course>) = threads.filter { kind(it.group, classes) == Kind.SCHOOL }

    /** Posted materials and new grades in the student's classes (grade rows carry no course). */
    fun classNotices(classes: List<Course>): List<Notice> {
        val ids = classes.map { it.sectionId }.toSet()
        return notices.filter { it.type == "grade" || (it.sectionId?.let(ids::contains) ?: false) }
    }

    fun otherNotices(classes: List<Course>): List<Notice> {
        val shown = classNotices(classes).map { it.key }.toSet()
        return notices.filter { it.key !in shown }
    }

    /** The dot: unread on Schoology, or arrived since the Inbox first loaded, and not opened in the app. */
    fun isNew(t: MessageThread): Boolean {
        if (t.id in seen) return false
        if (t.unread == true) return true
        val since = Prefs.date(SINCE_KEY) ?: return false
        val d = t.time ?: return false
        return d.after(since)
    }

    /** The tab badge: new messages, from teachers and from school. */
    fun newCount(): Int = threads.count(::isNew)

    fun markSeen(id: String) {
        if (id in seen) return
        seen = seen + id
        Prefs.set(SEEN_KEY, seen.toList().takeLast(500))
    }

    /**
     * First page of each list. `notify`: alert for teacher messages and class
     * materials not seen by an earlier refresh (background runs only; the first
     * refresh ever just remembers what's there).
     */
    suspend fun refresh(m: AppModel, notify: Boolean = false) {
        if (loading) return
        loading = true
        try {
            val (list, nl) = coroutineScope {
                val messages = async { m.engine.call<MessageList>("messages", mapOf("page" to 0)) }
                val notifications = async { m.engine.call<NoticeList>("notifications", mapOf("page" to 0)) }
                messages.await() to notifications.await()
            }
            if (!Prefs.has(SINCE_KEY)) Prefs.setDate(SINCE_KEY, Date())
            threads = list.threads
            notices = nl.items
            messagePage = 0
            noticePage = 0
            hasOlderMessages = list.hasMore ?: list.threads.isNotEmpty()
            hasOlderNotices = nl.hasMore ?: nl.items.isNotEmpty()
            asOf = Date()
            error = null
            Cache.save("inbox-messages", ListSerializer(MessageThread.serializer()), threads)
            Cache.save("inbox-notices", ListSerializer(Notice.serializer()), notices)
            val classes = m.classes
            val newMessages = unknown(teacherThreads(classes).map { it.id }, KNOWN_MESSAGES_KEY)
            val newNotices = unknown(classNotices(classes).filter { it.type == "material" }.map { it.key }, KNOWN_NOTICES_KEY)
            if (notify) {
                Reminders.newMessages(teacherThreads(classes).filter { it.id in newMessages && isNew(it) })
                Reminders.newMaterials(notices.filter { it.key in newNotices })
            }
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        } finally {
            loading = false
        }
    }

    /**
     * The next page of the messages list, added under what's there. No alerts,
     * and not cached: the next refresh goes back to the first page.
     */
    suspend fun loadOlderMessages(m: AppModel) {
        if (loading || loadingOlderMessages || !hasOlderMessages) return
        loadingOlderMessages = true
        try {
            val list: MessageList = m.engine.call("messages", mapOf("page" to messagePage + 1))
            val ids = threads.map { it.id }.toSet()
            val fresh = list.threads.filter { it.id !in ids }
            threads = threads + fresh
            messagePage += 1
            hasOlderMessages = fresh.isNotEmpty() && (list.hasMore ?: true)
            error = null
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        } finally {
            loadingOlderMessages = false
        }
    }

    /** The next page of /home/notifications, the same way. */
    suspend fun loadOlderNotices(m: AppModel) {
        if (loading || loadingOlderNotices || !hasOlderNotices) return
        loadingOlderNotices = true
        try {
            val nl: NoticeList = m.engine.call("notifications", mapOf("page" to noticePage + 1))
            val keys = notices.map { it.key }.toSet()
            val fresh = nl.items.filter { it.key !in keys }
            notices = notices + fresh
            noticePage += 1
            hasOlderNotices = fresh.isNotEmpty() && (nl.hasMore ?: true)
            error = null
        } catch (e: EngineError) {
            error = e
        } catch (e: Exception) {
            error = EngineError.Js(e.message ?: "$e")
        } finally {
            loadingOlderNotices = false
        }
    }

    fun clear() {
        threads = emptyList()
        notices = emptyList()
        asOf = null
        messagePage = 0
        noticePage = 0
        hasOlderMessages = true
        hasOlderNotices = true
    }
}
