package com.wolfwheels.app.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Hot Wheels Wiki (Fandom), public MediaWiki API. No key needed.
 * Text is CC BY-SA; photos belong to their authors.
 */
object Wiki {
    private const val API = "https://hotwheels.fandom.com/api.php"
    const val SITE = "https://hotwheels.fandom.com/wiki/"

    data class Car(val title: String, val thumb: String?)
    data class Page(val items: List<Car>, val cont: Map<String, String>?)
    /** One released version of a casting (one row of the wiki's versions tables). */
    data class Version(
        val group: String,
        val year: String,
        val series: String,
        val color: String,
        val fields: List<Pair<String, String>>,   // all columns, labels already in Spanish
        val photo: String?,
    )
    data class Detail(
        val title: String,
        val hero: String?,
        val facts: List<Pair<String, String>>,
        val intro: String,
        val description: String,
        val versions: List<Version>,
        val cardFacts: List<Pair<String, String>>,
        val notes: List<String>,
    )
    data class Photo(val thumb: String, val full: String, val name: String)
    data class Photos(val items: List<Photo>, val cont: Map<String, String>?)

    data class Collection(val label: String, val category: String)

    val collections = listOf(
        Collection("Novedades 2026", "Category:2026 Hot Wheels"),
        Collection("2025", "Category:2025 Hot Wheels"),
        Collection("2024", "Category:2024 Hot Wheels"),
        Collection("Treasure Hunts", "Category:Treasure Hunts"),
        Collection("Super Treasure Hunts", "Category:Super Treasure Hunts"),
        Collection("Monster Trucks", "Category:Monster Trucks"),
    )

    private suspend fun call(params: Map<String, String>): JSONObject {
        val b = API.toHttpUrl().newBuilder()
        (mapOf("format" to "json", "formatversion" to "2") + params).forEach { (k, v) -> b.addQueryParameter(k, v) }
        return JSONObject(Http.get(b.build().toString()))
    }

    private fun contOf(j: JSONObject): Map<String, String>? {
        val c = j.optJSONObject("continue") ?: return null
        return c.keys().asSequence().associateWith { c.optString(it) }
    }

    private fun cars(j: JSONObject, sortBySearch: Boolean): List<Car> {
        val arr = j.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optString("title").startsWith("List of", true) }
        val sorted = if (sortBySearch) list.sortedBy { it.optInt("index", 9999) } else list.sortedBy { it.optString("title").lowercase() }
        return sorted.map { Car(it.optString("title"), it.optJSONObject("thumbnail")?.optString("source")) }
    }

    private val thumbs = mapOf("prop" to "pageimages", "piprop" to "thumbnail", "pithumbsize" to "400")

    suspend fun search(q: String, cont: Map<String, String>?): Page {
        val j = call(thumbs + mapOf("action" to "query", "generator" to "search", "gsrsearch" to q,
            "gsrnamespace" to "0", "gsrlimit" to "24") + (cont ?: emptyMap()))
        return Page(cars(j, true), contOf(j))
    }

    suspend fun category(cat: String, cont: Map<String, String>?): Page {
        val j = call(thumbs + mapOf("action" to "query", "generator" to "categorymembers", "gcmtitle" to cat,
            "gcmnamespace" to "0", "gcmtype" to "page", "gcmlimit" to "30") + (cont ?: emptyMap()))
        return Page(cars(j, false), contOf(j))
    }

    suspend fun detail(title: String): Detail {
        val info = call(mapOf("action" to "query", "titles" to title, "redirects" to "1",
            "prop" to "pageimages", "piprop" to "thumbnail", "pithumbsize" to "1000"))
        val hero = info.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            ?.optJSONObject("thumbnail")?.optString("source")
        val parsed = call(mapOf("action" to "parse", "page" to title, "prop" to "text", "redirects" to "1"))
        val html = parsed.optJSONObject("parse")?.optString("text").orEmpty()
        val doc = Jsoup.parse(html)
        doc.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode(" · ")) }
        doc.select("sup.reference, .mw-editsection, style, script").remove()

        val facts = doc.select(".pi-data").mapNotNull { e ->
            val k = e.selectFirst(".pi-data-label")?.text()?.trim().orEmpty()
            val v = clean(e.selectFirst(".pi-data-value")?.text())
            if (k.isNotEmpty() && v.isNotEmpty()) factLabel(k) to v else null
        }

        // Description: paragraphs before the first table / until the next big section
        val paras = mutableListOf<String>()
        for (el in doc.select("p, h2, table")) {
            if (el.tagName() == "table" && el.hasClass("wikitable")) break
            if (el.tagName() == "h2" && paras.isNotEmpty()) break
            if (el.tagName() == "p" && el.parents().none { it.hasClass("portable-infobox") }) {
                val t = clean(el.text())
                if (t.length > 25) paras += t
            }
        }
        val description = paras.joinToString("\n\n")
        val intro = paras.firstOrNull().orEmpty()

        val versions = mutableListOf<Version>()
        val cardFacts = mutableListOf<Pair<String, String>>()
        for (t in doc.select("table.wikitable")) {
            val grid = tableGrid(t)
            if (grid.size < 2) continue
            val head = grid[0].map { it.first.trim() }
            val lower = head.map { it.lowercase() }
            if ("year" in lower && lower.any { it.startsWith("color") }) {
                val group = sectionOf(t)
                for (row in grid.drop(1)) {
                    if (row.size < head.size / 2) continue
                    val fields = mutableListOf<Pair<String, String>>()
                    var photo: String? = null
                    var year = ""; var series = ""; var color = ""
                    head.forEachIndexed { i, h ->
                        val (txt, img) = row.getOrNull(i) ?: return@forEachIndexed
                        val key = h.lowercase()
                        if (key.startsWith("photo") || key.startsWith("image")) { photo = img ?: photo; return@forEachIndexed }
                        val v = clean(txt)
                        if (v.isEmpty() || v == "-" || v == "?") return@forEachIndexed
                        when {
                            key == "year" -> year = v
                            key.startsWith("series") -> series = v
                            key == "color" -> color = v
                        }
                        fields += versionLabel(h) to v
                    }
                    if (photo == null) photo = row.firstNotNullOfOrNull { it.second }
                    if (fields.isNotEmpty()) versions += Version(group, year, series, color, fields, photo)
                }
            } else if (lower.firstOrNull()?.startsWith("year of card") == true) {
                for (row in grid.drop(1)) head.forEachIndexed { i, h ->
                    if (i == 0) return@forEachIndexed
                    val v = clean(row.getOrNull(i)?.first)
                    if (v.isNotEmpty()) cardFacts += cardLabel(h) to v
                }
            }
        }

        // "Notes" section bullet list
        val notes = mutableListOf<String>()
        doc.select("h2").firstOrNull { it.text().trim().equals("Notes", true) }?.let { h ->
            var e = h.nextElementSibling()
            while (e != null && e.tagName() != "h2") {
                if (e.tagName() == "ul") e.select("li").forEach { li -> clean(li.text()).takeIf { it.isNotEmpty() }?.let { notes += it } }
                e = e.nextElementSibling()
            }
        }
        return Detail(title, hero, facts, intro, description, versions, cardFacts.distinct(), notes.take(20))
    }

    private fun clean(s: String?): String = (s ?: "").replace(Regex("\\[\\d+]"), "")
        .replace(Regex("\\s*·\\s*(·\\s*)+"), " · ").replace(Regex("\\s+"), " ").trim().trim('·').trim()

    /** Table to a grid of (text, image) honouring rowspan/colspan, so columns line up. */
    private fun tableGrid(t: org.jsoup.nodes.Element): List<List<Pair<String, String?>>> {
        val rows = t.select("tr").filter { it.closest("table") == t }
        val out = mutableListOf<MutableList<Pair<String, String?>?>>()
        val pending = mutableMapOf<Int, Pair<Pair<String, String?>, Int>>() // col -> (cell, rows left)
        for (tr in rows) {
            val line = mutableListOf<Pair<String, String?>?>()
            var col = 0
            fun fillPending() {
                while (pending.containsKey(col)) {
                    val (cell, left) = pending.getValue(col)
                    line += cell
                    if (left <= 1) pending.remove(col) else pending[col] = cell to left - 1
                    col++
                }
            }
            for (td in tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }) {
                fillPending()
                val img = td.selectFirst("img")?.let { im ->
                    im.attr("data-src").ifEmpty { im.attr("src") }.takeIf { it.startsWith("http") }
                }
                val cell = td.text() to img
                val cs = td.attr("colspan").toIntOrNull()?.coerceIn(1, 20) ?: 1
                val rs = td.attr("rowspan").toIntOrNull()?.coerceIn(1, 200) ?: 1
                repeat(cs) {
                    line += cell
                    if (rs > 1) pending[col] = cell to rs - 1
                    col++
                }
            }
            fillPending()
            out += line
        }
        return out.map { r -> r.map { it ?: ("" to null) } }
    }

    private fun sectionOf(t: org.jsoup.nodes.Element): String {
        var e: org.jsoup.nodes.Element? = t
        var steps = 0
        while (e != null && steps++ < 60) {
            val prev = e.previousElementSibling()
            if (prev == null) { e = e.parent(); continue }
            if (prev.tagName() in setOf("h2", "h3")) return groupName(prev.text())
            e = prev
        }
        return "Versiones"
    }

    private fun groupName(raw: String): String {
        val s = raw.trim()
        if (s.equals("Versions", true)) return "Versiones"
        return s.replace(Regex("\\s*Versions?$", RegexOption.IGNORE_CASE), "").ifEmpty { "Versiones" }
    }

    private fun versionLabel(h: String): String = when (h.trim().lowercase()) {
        "col #", "col. #", "collector #" -> "Nº de colección"
        "year" -> "Año"
        "series" -> "Serie"
        "color" -> "Color"
        "tampo" -> "Decoración"
        "base color/type", "base color / type", "base" -> "Base"
        "window color" -> "Ventanas"
        "interior color" -> "Interior"
        "wheel type", "wheels" -> "Ruedas"
        "toy #", "toy number" -> "Nº de juguete"
        "country" -> "País"
        "notes", "notes/variations" -> "Notas"
        else -> h.trim()
    }

    private fun cardLabel(h: String): String = when (h.trim().trimEnd(':').lowercase()) {
        "fun facts" -> "Curiosidad"
        "born" -> "Nacido"
        "birthplace" -> "Lugar de nacimiento"
        "designer" -> "Diseñador"
        "specialty" -> "Especialidad"
        "top speed" -> "Velocidad máxima"
        "engine" -> "Motor"
        "0-60" -> "0-100"
        else -> h.trim().trimEnd(':')
    }

    private fun factLabel(k: String): String = when (k.trim().lowercase()) {
        "debut series" -> "Serie de debut"
        "produced" -> "Producido"
        "designer", "designers" -> "Diseñador"
        "number" -> "Número"
        "casting name" -> "Molde"
        "first release" -> "Primera edición"
        "year" -> "Año"
        "series" -> "Serie"
        "scale" -> "Escala"
        else -> k
    }

    private val skip = Regex("logo|icon|flag|placeholder|wiki", RegexOption.IGNORE_CASE)

    suspend fun photos(title: String, cont: Map<String, String>?): Photos {
        val j = call(mapOf("action" to "query", "titles" to title, "redirects" to "1", "generator" to "images",
            "gimlimit" to "50", "prop" to "imageinfo", "iiprop" to "url|size", "iiurlwidth" to "300") + (cont ?: emptyMap()))
        val arr = j.optJSONObject("query")?.optJSONArray("pages")
        val list = mutableListOf<Photo>()
        if (arr != null) for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val name = p.optString("title").removePrefix("File:")
            if (!Regex("\\.(jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE).containsMatchIn(name) || skip.containsMatchIn(name)) continue
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            if (ii.optInt("width", 999) < 120) continue
            val full = ii.optString("url")
            list += Photo(ii.optString("thumburl").ifEmpty { full }, full, name.replace('_', ' '))
        }
        return Photos(list.sortedBy { it.name.lowercase() }, contOf(j))
    }

    /** Same image at a bigger width (Fandom scales on the fly). */
    fun big(url: String?, width: Int): String? = url?.replace(Regex("scale-to-width-down/\\d+"), "scale-to-width-down/$width")

    fun pageUrl(title: String) = SITE + java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("%2F", "/")
}
