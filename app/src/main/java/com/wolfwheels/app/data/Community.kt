package com.wolfwheels.app.data

import org.json.JSONObject
import java.io.File

/** Community catalog: cars the users add by hand (not in the wiki), shared with everybody on Jonatan's server. */
object Community {
    const val ADMIN = "jonatanmontero.r@gmail.com"

    data class Entry(
        val id: String, val title: String, val brand: String, val year: String, val series: String, val color: String,
        val toy: String, val notes: String, val by: String, val photo: String?, val mine: Boolean,
    ) {
        val canEdit get() = mine || Account.email.value.equals(ADMIN, true)
        companion object {
            fun from(j: JSONObject) = Entry(j.optString("id"), j.optString("title"), j.optString("brand"), j.optString("year"),
                j.optString("series"), j.optString("color"), j.optString("toy"), j.optString("notes"), j.optString("by"),
                j.optString("photo").ifEmpty { null }, j.optBoolean("mine"))
        }
    }

    private suspend fun find(query: Map<String, String>): List<Entry> {
        val j = Server.call("catsearch", query = query, token = Account.token)
        val a = j.optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).map { Entry.from(a.getJSONObject(it)) }
    }

    suspend fun search(q: String) = find(mapOf("q" to q))
    suspend fun byToy(code: String) = find(mapOf("toy" to code))
    suspend fun get(id: String) = find(mapOf("id" to id)).firstOrNull()

    /** Creates (id = null) or updates my entry. The photo is sent small (max 1024 px). */
    suspend fun save(id: String?, title: String, brand: String, year: String, series: String, color: String, toy: String,
                     photo: File?): Entry {
        val b = JSONObject().put("title", title).put("brand", brand).put("year", year).put("series", series)
            .put("color", color).put("toy", toy)
        if (id != null) b.put("id", id)
        if (photo != null && photo.exists()) b.put("image", Identify.prepare(photo))
        return Entry.from(Server.call("catadd", b, token = Account.token).getJSONObject("entry"))
    }

    suspend fun delete(id: String) { Server.call("catdel", JSONObject().put("id", id), token = Account.token) }

    /** Shares a car I added by hand. Its key becomes "cat|id" (the same for everybody). Returns the new key. */
    suspend fun publish(key: String, sendPhoto: Boolean = true): String {
        val it = MyCollection.items[key] ?: throw IllegalStateException("item")
        val e = save(it.cat.ifBlank { null }, it.title, it.brand, it.year, it.series, it.color, it.toy,
            if (sendPhoto) it.myPhoto?.let { p -> File(p) } else null)
        val newKey = "cat|" + e.id
        if (key == newKey) MyCollection.update(key) { i -> i.copy(cat = e.id, photo = e.photo ?: i.photo) }
        else MyCollection.rekey(key, newKey) { i -> i.copy(cat = e.id, photo = e.photo ?: i.photo) }
        return newKey
    }
}
