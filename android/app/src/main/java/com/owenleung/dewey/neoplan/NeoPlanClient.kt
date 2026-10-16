package com.owenleung.dewey.neoplan

import com.owenleung.dewey.engine.Engine
import com.owenleung.dewey.engine.toJson
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.store.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/** One neo-plan item, as much of it as the app uses. ids arrive as numbers or strings. */
data class NeoPlanItem(
    val id: String,
    val sourceId: String?,
    val type: String?,
    val work: String?,      // todo | ready
    val cleared: Boolean?,
    val removed: Boolean?,
) {
    companion object {
        fun from(e: JsonElement?): NeoPlanItem? {
            val o = e as? JsonObject ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
            val id = s("id") ?: return null
            return NeoPlanItem(id, s("source_id"), s("type"), s("work"),
                (o["cleared"] as? JsonPrimitive)?.booleanOrNull, (o["removed"] as? JsonPrimitive)?.booleanOrNull)
        }
    }
}

sealed class NeoPlanError : Exception() {
    data object NoToken : NeoPlanError()
    data class Refused(val status: Int) : NeoPlanError()
    data object Network : NeoPlanError()
}

/**
 * neo-plan, optional sync (docs/ios/ENGINE.md, "neo-plan"). Same server, token
 * and routes as the extension's outputs/neoplan/api.js buildRequest; if they
 * disagree, that file wins. The token lives in the Android Keystore.
 */
object NeoPlanClient {
    const val SERVER = "https://neo-plan.vercel.app"

    val token: String? get() = Secrets.get("neoplanToken")
    val connected: Boolean get() = token != null

    fun validToken(t: String) = Regex("""^np_\S{8,}$""").matches(t)

    /** "np_••••••••a1b2" (api.js maskToken). */
    fun mask(t: String?): String = if (t == null || t.length <= 7) "" else "${t.take(3)}••••••••${t.takeLast(4)}"

    fun setToken(t: String?) = Secrets.set("neoplanToken", t)

    private suspend fun request(method: String, path: String, body: Map<String, Any?>? = null): String = withContext(Dispatchers.IO) {
        val tok = token ?: throw NeoPlanError.NoToken
        val c = try {
            (URL("$SERVER/api/extension/$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $tok")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(toJson(body).toString().toByteArray()) }
                }
            }
        } catch (e: Exception) {
            throw NeoPlanError.Network
        }
        try {
            val status = c.responseCode
            if (status !in 200..299) throw NeoPlanError.Refused(status)
            c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: NeoPlanError) {
            throw e
        } catch (e: Exception) {
            throw NeoPlanError.Network
        } finally {
            c.disconnect()
        }
    }

    suspend fun today(): String = request("GET", "today")

    /** ≤ 100 ids per call. */
    suspend fun items(ids: List<String>): List<NeoPlanItem> {
        val clean = ids.filter { Regex("""^\d{1,20}$""").matches(it) }.distinct()
        val out = mutableListOf<NeoPlanItem>()
        for (chunk in clean.chunked(100)) {
            val text = request("GET", "items?schoology_ids=${chunk.joinToString(",")}")
            val arr = runCatching { Json.parseToJsonElement(text).jsonObject["items"] as? JsonArray }.getOrNull() ?: continue
            out += arr.mapNotNull(NeoPlanItem::from)
        }
        return out
    }

    suspend fun setWork(id: String, ready: Boolean) { request("POST", "items/$id/work", mapOf("work" to if (ready) "ready" else "todo")) }
    suspend fun setType(id: String, type: ItemType) { request("POST", "items/$id/type", mapOf("type" to type.raw)) }
    suspend fun turnIn(id: String) { request("POST", "items/$id/turn-in") }
    suspend fun putBack(id: String) { request("POST", "items/$id/put-back") }

    suspend fun addItem(schoologyId: String, sectionId: String?, title: String, due: Date?, type: ItemType?): NeoPlanItem? {
        val body = mutableMapOf<String, Any?>(
            "schoology_id" to schoologyId,
            "section_id" to sectionId,
            "title" to title.take(500),
            "due_at" to due?.let(Fmt::iso),
            "source_url" to "${Engine.ORIGIN}/assignment/$schoologyId",
        )
        if (type == ItemType.EXAM || type == ItemType.ASSIGNMENT) body["type"] = type.raw
        val text = request("POST", "items", body)
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return NeoPlanItem.from(o["item"]) ?: NeoPlanItem.from(o)
    }
}
