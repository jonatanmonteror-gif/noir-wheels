package com.wolfwheels.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * My collection: versions I have ("have") and versions I want ("want").
 * Saved on the phone and, with an account, on the server (each change wins by time, so several phones can be used).
 */
object MyCollection {
    data class Item(
        val key: String, val title: String, val year: String, val series: String, val color: String,
        val toy: String, val photo: String?, val status: String, val t: Long, val deleted: Boolean = false,
    ) {
        fun json(): JSONObject = JSONObject().put("key", key).put("title", title).put("year", year).put("series", series)
            .put("color", color).put("toy", toy).put("photo", photo ?: "").put("status", status).put("t", t).put("deleted", deleted)
        companion object {
            fun from(j: JSONObject) = Item(
                j.optString("key"), j.optString("title"), j.optString("year"), j.optString("series"), j.optString("color"),
                j.optString("toy"), j.optString("photo").ifEmpty { null }, j.optString("status", "have"), j.optLong("t"), j.optBoolean("deleted"),
            )
        }
    }

    /** All items, including deleted ones (kept so the deletion reaches the other phones). */
    val items = mutableStateMapOf<String, Item>()
    val syncing = mutableStateOf(false)
    val lastError = mutableStateOf<String?>(null)
    val lastSync = mutableStateOf(0L)

    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private var pending: Job? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "collection.json")
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) Item.from(arr.getJSONObject(i)).let { items[it.key] = it }
        }
        lastSync.value = Prefs.lastSync
    }

    private fun persist() {
        val arr = JSONArray(); items.values.forEach { arr.put(it.json()) }
        runCatching { file.writeText(arr.toString()) }
    }

    fun statusOf(key: String): String? = items[key]?.takeIf { !it.deleted }?.status
    fun visible(status: String) = items.values.filter { !it.deleted && it.status == status }.sortedByDescending { it.t }
    fun countFor(title: String, status: String = "have") = items.values.count { !it.deleted && it.status == status && it.title == title }

    /** Sets the status of a version (null = remove from the collection). */
    fun set(title: String, v: Wiki.Version, photo: String?, status: String?) {
        val key = v.key(title)
        val now = System.currentTimeMillis()
        items[key] = Item(key, title, v.year, v.series, v.color, v.toy, photo ?: v.photo, status ?: (items[key]?.status ?: "have"), now, deleted = status == null)
        persist()
        scheduleSync()
    }

    fun scheduleSync() {
        if (!Account.loggedIn.value) return
        pending?.cancel()
        pending = scope.launch { delay(1500); sync() }
    }

    /** Sends everything and keeps what the server returns (it merges with the other phones). */
    suspend fun sync(force: Boolean = false) {
        val token = Account.token ?: return
        mutex.withLock {
            syncing.value = true
            try {
                val arr = JSONArray(); items.values.forEach { arr.put(it.json()) }
                val r = Server.call("col", JSONObject().put("items", arr), token = token)
                val back = r.optJSONArray("items") ?: JSONArray()
                val merged = HashMap<String, Item>()
                for (i in 0 until back.length()) Item.from(back.getJSONObject(i)).let { merged[it.key] = it }
                // Keep local changes made while the request was travelling
                items.values.forEach { local -> val srv = merged[local.key]; if (srv == null || local.t > srv.t) merged[local.key] = local }
                items.clear(); items.putAll(merged)
                persist()
                lastSync.value = System.currentTimeMillis(); Prefs.lastSync = lastSync.value
                lastError.value = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                if (e is Server.ApiError && e.reason == "login") Account.expired()
                lastError.value = Server.message(e)
            } finally { syncing.value = false }
        }
    }
}
