package com.wolfwheels.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Cars I opened lately (home "Vistos recientemente"). */
object Recents {
    data class Seen(val title: String, val thumb: String?)
    val items = androidx.compose.runtime.mutableStateListOf<Seen>()
    private lateinit var ctx: Context

    fun init(c: Context) {
        ctx = c
        runCatching {
            val arr = JSONArray(c.getSharedPreferences("wolfwheels", Context.MODE_PRIVATE).getString("recents", "[]"))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { items += Seen(it.optString("t"), it.optString("i").ifEmpty { null }) }
        }
    }

    fun add(title: String, thumb: String?) {
        items.removeAll { it.title == title }
        items.add(0, Seen(title, thumb))
        while (items.size > 15) items.removeAt(items.lastIndex)
        val arr = JSONArray(); items.forEach { arr.put(JSONObject().put("t", it.title).put("i", it.thumb ?: "")) }
        ctx.getSharedPreferences("wolfwheels", Context.MODE_PRIVATE).edit().putString("recents", arr.toString()).apply()
    }
}

/** Base code (on the car's base since 2008): letter = year, two digits = week. I, O and Q are not used. */
object BaseCode {
    private val letters = "ABCDEFGHJKLMNPRSTUVWXYZ"
    data class Decoded(val code: String, val year: Int, val week: Int)

    fun decode(raw: String): Decoded? {
        val c = raw.trim().uppercase()
        val m = Regex("^([A-Z])\\s*([0-9]{2})$").find(c) ?: return null
        val idx = letters.indexOf(m.groupValues[1][0])
        val week = m.groupValues[2].toInt()
        if (idx < 0 || week !in 1..53) return null
        return Decoded("${m.groupValues[1]}${m.groupValues[2]}", 2008 + idx, week)
    }

    fun monthOf(d: Decoded): String {
        val cal = java.util.Calendar.getInstance().apply { clear(); set(java.util.Calendar.YEAR, d.year); set(java.util.Calendar.WEEK_OF_YEAR, d.week) }
        return java.text.SimpleDateFormat("MMMM", java.util.Locale("es", "ES")).format(cal.time)
    }
}

/** Collection milestones. */
object Achievements {
    data class Badge(val icon: String, val title: String, val desc: String, val done: Boolean)

    fun all(items: Collection<MyCollection.Item>, completeSeries: Int): List<Badge> {
        val have = items.filter { !it.deleted && it.status == "have" }
        val cars = have.sumOf { it.qty }
        val models = have.map { it.title }.distinct().size
        return listOf(
            Badge("🏁", "Arranque", "Tu primer coche en el garaje", cars >= 1),
            Badge("🔟", "Diez en la pista", "10 coches", cars >= 10),
            Badge("🚗", "Garaje lleno", "50 coches", cars >= 50),
            Badge("🏆", "Centenario", "100 coches", cars >= 100),
            Badge("🐺", "Manada", "25 modelos distintos", models >= 25),
            Badge("🔥", "Cazador", "Tu primera Treasure Hunt", have.any { it.th >= 1 }),
            Badge("💎", "Super cazador", "Tu primera Super Treasure Hunt", have.any { it.th == 2 }),
            Badge("🔁", "Para cambiar", "Tu primer repetido", have.any { it.qty > 1 }),
            Badge("⭐", "Lista de deseos", "Marca un coche que quieres", items.any { !it.deleted && it.status == "want" }),
            Badge("📦", "Coleccionista", "10 coches en blíster", have.filter { it.cond == "blister" }.sumOf { it.qty } >= 10),
            Badge("🧩", "Serie completa", "Completa una serie", completeSeries > 0),
            Badge("🗂", "Organizado", "Crea 3 garajes", have.map { it.garage }.filter { it.isNotBlank() }.distinct().size >= 3),
        )
    }
}

/** Backup of my garage in a file (to keep it or move it to another phone). */
object Backup {
    fun export(): String {
        val arr = JSONArray(); MyCollection.items.values.forEach { arr.put(it.json()) }
        return JSONObject().put("app", "wolfwheels").put("version", 1).put("date", System.currentTimeMillis())
            .put("garages", JSONArray(Prefs.garages)).put("items", arr).toString(1)
    }

    /** Merges a backup with what there is now (the most recent change of each car wins). Returns how many were added or updated. */
    fun import(text: String): Int {
        val j = JSONObject(text)
        require(j.optString("app") == "wolfwheels") { "No es una copia de Wolf Wheels" }
        val arr = j.getJSONArray("items")
        var n = 0
        for (i in 0 until arr.length()) {
            val it = MyCollection.Item.from(arr.getJSONObject(i))
            val cur = MyCollection.items[it.key]
            if (cur == null || it.t > cur.t) { MyCollection.items[it.key] = it; n++ }
        }
        j.optJSONArray("garages")?.let { g -> Prefs.garages = (Prefs.garages + (0 until g.length()).map { g.getString(it) }).distinct() }
        MyCollection.saveAndSync()
        return n
    }
}
