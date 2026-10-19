package com.owenleung.dewey.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App settings (UserDefaults on iOS). Set up once in DeweyApp. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    /** Bumped on every write so Compose screens reading settings recompose. */
    val revision = mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.getSharedPreferences("dewey", Context.MODE_PRIVATE)
    }

    fun bool(k: String, def: Boolean = false) = sp.getBoolean(k, def)
    fun int(k: String, def: Int = 0) = sp.getInt(k, def)
    fun long(k: String, def: Long = 0) = sp.getLong(k, def)
    fun string(k: String): String? = sp.getString(k, null)
    fun strings(k: String): List<String> = sp.getString(k, null)?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    fun has(k: String) = sp.contains(k)

    fun set(k: String, v: Any?) {
        sp.edit().apply {
            when (v) {
                null -> remove(k)
                is Boolean -> putBoolean(k, v)
                is Int -> putInt(k, v)
                is Long -> putLong(k, v)
                is String -> putString(k, v)
                is List<*> -> putString(k, Json.encodeToString(v.map { it.toString() }))
                else -> putString(k, v.toString())
            }
        }.apply()
        revision.intValue++
    }

    fun date(k: String): Date? = if (sp.contains(k)) Date(sp.getLong(k, 0)) else null
    fun setDate(k: String, d: Date?) = set(k, d?.time)
}

/**
 * Last good engine results on disk (files/cache), so every screen can show
 * the saved copy offline with "as of N min ago".
 */
object Cache {
    @Serializable
    data class Entry<T>(val savedAt: Long, val value: T)

    private lateinit var realDir: File
    /** The tour's pretend week keeps its own copies, emptied when it starts and ends. */
    private lateinit var practiceDir: File
    val dir: File get() = if (com.owenleung.dewey.engine.Practice.active) practiceDir else realDir
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun init(ctx: Context) {
        realDir = File(ctx.filesDir, "cache").apply { mkdirs() }
        practiceDir = File(ctx.filesDir, "cache-practice").apply { mkdirs() }
    }

    fun clearPractice() {
        practiceDir.deleteRecursively()
        practiceDir.mkdirs()
    }

    private fun file(key: String) = File(dir, key.replace("/", "_") + ".json")

    fun <T> save(key: String, s: KSerializer<T>, value: T, at: Date = Date()) {
        runCatching {
            val text = json.encodeToString(Entry.serializer(s), Entry(at.time, value))
            atomicWrite(file(key), text)
        }
    }

    fun <T> load(key: String, s: KSerializer<T>): Pair<T, Date>? = runCatching {
        val e = json.decodeFromString(Entry.serializer(s), file(key).readText())
        e.value to Date(e.savedAt)
    }.getOrNull()

    fun saveDate(key: String, d: Date) = save(key, Long.serializer(), d.time)
    fun loadDate(key: String): Date? = load(key, Long.serializer())?.first?.let(::Date)

    fun clear() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    /** Raw JSON (gradebook rows go back to the engine untouched). */
    fun saveRaw(key: String, text: String) = runCatching { atomicWrite(file("$key.raw"), text) }

    fun loadRaw(key: String): Pair<String, Date>? {
        val f = file("$key.raw")
        if (!f.exists()) return null
        return runCatching { f.readText() to Date(f.lastModified()) }.getOrNull()
    }

    fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}

/** A secret in the Android Keystore (the neo-plan token; the Keychain on iOS). */
object Secrets {
    private const val ALIAS = "dewey-secrets"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    fun set(name: String, value: String?) {
        if (value == null) return Prefs.set("secret.$name", null)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.toByteArray())
        Prefs.set("secret.$name", Base64.encodeToString(out, Base64.NO_WRAP))
    }

    fun get(name: String): String? = runCatching {
        val raw = Base64.decode(Prefs.string("secret.$name") ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrNull()
}
