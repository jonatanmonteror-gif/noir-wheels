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
        val qty: Int = 1,
        val cond: String = "",          // "blister" · "suelto" · "dañado"
        val price: Double? = null,      // what I paid (total)
        val notes: String = "",
        val garage: String = "",
        val myPhoto: String? = null,    // my own photo (only on this phone)
        val seriesLink: String? = null,
        val th: Int = 0,
        val added: Long = t,
        val trade: Boolean = false,     // offered for swapping (public garage "Para cambiar")
        val custom: Boolean = false,    // added by hand (not in the wiki)
        val brand: String = "",         // for cars added by hand
    ) {
        fun json(): JSONObject = JSONObject().put("key", key).put("title", title).put("year", year).put("series", series)
            .put("color", color).put("toy", toy).put("photo", photo ?: "").put("status", status).put("t", t).put("deleted", deleted)
            .put("qty", qty).put("cond", cond).put("price", price ?: JSONObject.NULL).put("notes", notes).put("garage", garage)
            .put("myPhoto", myPhoto ?: "").put("seriesLink", seriesLink ?: "").put("th", th).put("added", added)
            .put("trade", trade).put("custom", custom).put("brand", brand)
        companion object {
            fun from(j: JSONObject) = Item(
                j.optString("key"), j.optString("title"), j.optString("year"), j.optString("series"), j.optString("color"),
                j.optString("toy"), j.optString("photo").ifEmpty { null }, j.optString("status", "have"), j.optLong("t"), j.optBoolean("deleted"),
                qty = j.optInt("qty", 1).coerceAtLeast(1), cond = j.optString("cond"),
                price = if (j.isNull("price") || !j.has("price")) null else j.optDouble("price").takeIf { !it.isNaN() },
                notes = j.optString("notes"), garage = j.optString("garage"), myPhoto = j.optString("myPhoto").ifEmpty { null },
                seriesLink = j.optString("seriesLink").ifEmpty { null }, th = j.optInt("th"), added = j.optLong("added", j.optLong("t")),
                trade = j.optBoolean("trade"), custom = j.optBoolean("custom"), brand = j.optString("brand"),
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
        if (this::file.isInitialized) return
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

    /** Sets the status of a version (null = remove from the collection). Keeps the details already written. */
    fun set(title: String, v: Wiki.Version, photo: String?, status: String?) {
        val key = v.key(title)
        val now = System.currentTimeMillis()
        val old = items[key]?.takeIf { !it.deleted }
        items[key] = (old ?: Item(key, title, v.year, v.series, v.color, v.toy, photo ?: v.photo, status ?: "have", now,
            seriesLink = v.seriesLink, th = v.th, added = now))
            .copy(status = status ?: old?.status ?: "have", t = now, deleted = status == null,
                photo = old?.photo ?: photo ?: v.photo, seriesLink = v.seriesLink ?: old?.seriesLink, th = v.th)
        persist()
        scheduleSync()
    }

    /** A car that is not in the wiki (custom, other brands…). Returns its key. */
    fun addCustom(title: String, brand: String, year: String, series: String, color: String, toy: String): String {
        val now = System.currentTimeMillis()
        val key = "custom|" + java.util.UUID.randomUUID().toString()
        items[key] = Item(key, title, year, series, color, toy, null, "have", now, custom = true, brand = brand, added = now)
        persist(); scheduleSync()
        return key
    }

    /** One more of a version I already have (serial scanning), or adds it. */
    fun addOne(title: String, v: Wiki.Version, photo: String?) {
        val key = v.key(title)
        val cur = items[key]?.takeIf { !it.deleted && it.status == "have" }
        if (cur != null) update(key) { it.copy(qty = it.qty + 1) } else set(title, v, photo, "have")
    }

    /** Changes the details of an item (quantity, condition, price, notes, garage, photo). */
    fun update(key: String, change: (Item) -> Item) {
        val old = items[key] ?: return
        items[key] = change(old).copy(t = System.currentTimeMillis())
        persist()
        scheduleSync()
    }

    /** Items of this toy number I already have (for the scanner). */
    fun haveToy(code: String) = items.values.filter { !it.deleted && it.status == "have" && it.toy.uppercase().contains(code.uppercase()) }

    /** Garages used in the collection plus the empty ones I created. */
    fun garages(): List<String> = (items.values.filter { !it.deleted }.map { it.garage } + Prefs.garages).filter { it.isNotBlank() }.distinct().sorted()

    /** The collection as a CSV table (opens in Excel / Google Sheets). */
    fun csv(): String {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("Estado;Modelo;Año;Serie;Color;Nº de juguete;Cantidad;Conservación;Precio pagado;Garaje;TH;Notas\n")
        items.values.filter { !it.deleted }.sortedWith(compareBy({ it.status }, { it.title }, { it.year })).forEach {
            sb.append(listOf(if (it.status == "have") "Tengo" else "Quiero", it.title, it.year, it.series, it.color, it.toy, it.qty.toString(),
                it.cond, it.price?.let { p -> "%.2f".format(p) } ?: "", it.garage, when (it.th) { 2 -> "STH"; 1 -> "TH"; else -> "" }, it.notes)
                .joinToString(";") { v -> q(v) }).append('\n')
        }
        return sb.toString()
    }

    /** Puts an item back as it was (undo). */
    fun restore(old: Item) {
        items[old.key] = old.copy(t = System.currentTimeMillis())
        persist(); scheduleSync()
    }

    fun saveAndSync() { persist(); scheduleSync() }

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
