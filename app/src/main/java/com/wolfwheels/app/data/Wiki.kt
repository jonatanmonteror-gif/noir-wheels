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
    /** A piece of data. [link] = wiki page it points to (tap to see the cars of that series, designer…). */
    data class Field(val label: String, val value: String, val link: String? = null)

    /** One released version of a casting (one row of the wiki's versions tables). */
    data class Version(
        val group: String,
        val year: String,
        val series: String,
        val color: String,
        val toy: String,
        val fields: List<Field>,   // all columns, labels already in Spanish
        val photo: String?,
        val colorEs: String = color,   // colour shown (translated); [color] stays original for the collection key
        val th: Int = 0,               // 0 normal · 1 Treasure Hunt · 2 Super Treasure Hunt
        val seriesLink: String? = null,
    ) {
        /** Stable id for the collection. */
        fun key(title: String) = listOf(title, year, color, toy, series).joinToString("|")
    }
    data class Detail(
        val title: String,
        val hero: String?,
        val facts: List<Field>,
        val intro: String,
        val description: String,
        val versions: List<Version>,
        val cardFacts: List<Field>,
        val notes: List<String>,
    )

    /** A car in a list page (series, designer, wheel type…). */
    data class ListCar(val title: String, val thumb: String?, val sub: String)
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
            val valueEl = e.selectFirst(".pi-data-value")
            val v = clean(valueEl?.text())
            if (k.isNotEmpty() && v.isNotEmpty()) Field(factLabel(k), v, valueEl?.let { linkIn(it) }) else null
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
        val cardFacts = mutableListOf<Field>()
        for (t in doc.select("table.wikitable")) {
            val grid = tableGrid(t)
            if (grid.size < 2) continue
            val head = grid[0].map { it.text.trim() }
            val lower = head.map { it.lowercase() }
            if ("year" in lower && lower.any { it.startsWith("color") }) {
                val group = sectionOf(t)
                for (row in grid.drop(1)) {
                    if (row.size < head.size / 2) continue
                    val fields = mutableListOf<Field>()
                    var photo: String? = null
                    var year = ""; var series = ""; var color = ""; var toy = ""
                    head.forEachIndexed { i, h ->
                        val cell = row.getOrNull(i) ?: return@forEachIndexed
                        val key = h.lowercase()
                        if (key.startsWith("photo") || key.startsWith("image")) { photo = cell.img ?: photo; return@forEachIndexed }
                        val v = clean(cell.text)
                        if (v.isEmpty() || v == "-" || v == "?") return@forEachIndexed
                        when {
                            key == "year" -> year = v
                            key.startsWith("series") -> series = v
                            key == "color" -> color = v
                            key.startsWith("toy") -> toy = v
                        }
                        fields += Field(versionLabel(h), v, cell.link)
                    }
                    if (photo == null) photo = row.firstNotNullOfOrNull { it.img }
                    val all = row.joinToString(" ") { it.text } + " " + group
                    val th = when {
                        superTh.containsMatchIn(all) -> 2
                        normalTh.containsMatchIn(all) -> 1
                        else -> 0
                    }
                    val seriesLink = head.indexOfFirst { it.lowercase().startsWith("series") }.let { i -> if (i >= 0) row.getOrNull(i)?.link else null }
                    if (fields.isNotEmpty()) versions += Version(group, year, series, color, toy, fields, photo, th = th, seriesLink = seriesLink)
                }
            } else if (lower.firstOrNull()?.startsWith("year of card") == true) {
                for (row in grid.drop(1)) head.forEachIndexed { i, h ->
                    if (i == 0) return@forEachIndexed
                    val c = row.getOrNull(i)
                    val v = clean(c?.text)
                    if (v.isNotEmpty()) cardFacts += Field(cardLabel(h), v, c?.link)
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

    data class Cell(val text: String, val img: String?, val link: String?)

    private val placeholder = Regex("image[_ ]not[_ ]available|no[_ ]image|placeholder", RegexOption.IGNORE_CASE)

    private fun imgOf(td: org.jsoup.nodes.Element): String? {
        for (im in td.select("img")) {
            val src = im.attr("data-src").ifEmpty { im.attr("src") }
            if (!src.startsWith("http")) continue
            val keyName = im.attr("data-image-key") + " " + im.attr("alt") + " " + src
            if (placeholder.containsMatchIn(keyName)) continue
            return src
        }
        return null
    }

    /** First link in the element to another wiki article (not files, years-only navboxes are kept). */
    private fun linkIn(e: org.jsoup.nodes.Element): String? {
        for (a in e.select("a[href]")) {
            val t = hrefToTitle(a.attr("href")) ?: continue
            return t
        }
        return null
    }

    private fun hrefToTitle(href: String): String? {
        if (!href.startsWith("/wiki/")) return null
        val raw = href.removePrefix("/wiki/").substringBefore('#').substringBefore('?')
        val t = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).replace('_', ' ').trim()
        if (t.isEmpty() || t.contains(':') && !t.startsWith("Category:")) return null
        return t
    }

    /** Table to a grid of cells honouring rowspan/colspan, so columns line up. */
    private fun tableGrid(t: org.jsoup.nodes.Element): List<List<Cell>> {
        val rows = t.select("tr").filter { it.closest("table") == t }
        val out = mutableListOf<MutableList<Cell>>()
        val pending = mutableMapOf<Int, Pair<Cell, Int>>() // col -> (cell, rows left)
        for (tr in rows) {
            val line = mutableListOf<Cell>()
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
                val cell = Cell(td.text(), imgOf(td), linkIn(td))
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
        return out
    }

    private val superTh = Regex("super\\s*treasure\\s*hunt|\\\$\\s*TH\\b|\\bSTH\\b", RegexOption.IGNORE_CASE)
    private val normalTh = Regex("treasure\\s*hunt|\\bT\\.?H\\.?\\b")

    /** A version found by its toy number (printed on the card). */
    data class ToyMatch(val title: String, val version: Version)

    /** Finds which car and version has this toy number: wiki search, then each car's versions table. */
    suspend fun findByToy(code: String): List<ToyMatch> {
        val c = code.trim().uppercase()
        val j = call(mapOf("action" to "query", "list" to "search", "srsearch" to c, "srnamespace" to "0", "srlimit" to "8"))
        val arr = j.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val titles = (0 until arr.length()).map { arr.getJSONObject(it).optString("title") }.filter { okCar(it) }.take(4)
        val out = mutableListOf<ToyMatch>()
        for (t in titles) {
            val d = runCatching { detail(t) }.getOrNull() ?: continue
            d.versions.filter { v -> v.toy.uppercase().split(' ', '/', ',', '·').any { it.trim() == c } || v.toy.uppercase().contains(c) }
                .forEach { out += ToyMatch(d.title, it) }
        }
        return out
    }

    private val yearOnly = Regex("^\\d{4}$")

    /**
     * Cars listed on another wiki page (a series, a designer, a wheel type, a year…).
     * Reads every table row that links to a car page; a bare year goes to its "Category:<year> Hot Wheels".
     */
    suspend fun listFrom(target: String): List<ListCar> {
        val page = if (yearOnly.matches(target)) "Category:$target Hot Wheels" else target
        if (page.startsWith("Category:")) {
            val out = mutableListOf<ListCar>()
            var cont: Map<String, String>? = null
            do {
                val p = category(page, cont)
                out += p.items.map { ListCar(it.title, it.thumb, "") }
                cont = p.cont
            } while (cont != null && out.size < 600)
            return out
        }
        val parsed = call(mapOf("action" to "parse", "page" to page, "prop" to "text", "redirects" to "1"))
        val doc = Jsoup.parse(parsed.optJSONObject("parse")?.optString("text").orEmpty())
        doc.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode(" · ")) }
        val seen = HashSet<String>()
        val out = mutableListOf<ListCar>()
        for (t in doc.select("table.wikitable, table.sortable")) {
            val grid = tableGrid(t)
            if (grid.size < 2) continue
            // Some list tables have no header row: then the first row is already a car
            val hasHeader = grid[0].none { it.img != null }
            val head = if (hasHeader) grid[0].map { it.text.trim().lowercase() } else emptyList()
            val nameCol = head.indexOfFirst { it.contains("name") || it.contains("casting") || it.contains("model") }
            for (row in if (hasHeader) grid.drop(1) else grid) {
                val title = (if (nameCol >= 0) row.getOrNull(nameCol)?.link else null)
                    ?: row.firstNotNullOfOrNull { c -> c.link?.takeIf { okCar(it) } }
                    ?: continue
                if (!okCar(title) || title == page || !seen.add(title)) continue
                val sub = row.mapNotNull { c ->
                    val v = clean(c.text)
                    v.takeIf { it.isNotEmpty() && it.length <= 22 && it != title && c.link != title && it.any(Char::isDigit) }
                }.distinct().take(2).joinToString(" · ")
                out += ListCar(title, row.firstNotNullOfOrNull { it.img }, sub)
                if (out.size >= 600) break
            }
        }
        // Pages without tables: plain lists of links
        if (out.isEmpty()) {
            for (a in doc.select("li a[href], .wikia-gallery-item a[href]")) {
                val t = hrefToTitle(a.attr("href")) ?: continue
                if (okCar(t) && t != page && seen.add(t)) out += ListCar(t, null, "")
                if (out.size >= 300) break
            }
        }
        // Photos for the ones the table didn't show
        val missing = out.filter { it.thumb == null }.map { it.title }
        if (missing.isNotEmpty()) {
            val thumbs = HashMap<String, String>()
            missing.chunked(50).take(6).forEach { chunk ->
                runCatching {
                    val j = call(mapOf("action" to "query", "titles" to chunk.joinToString("|"), "prop" to "pageimages",
                        "piprop" to "thumbnail", "pithumbsize" to "400", "pilimit" to "50"))
                    val arr = j.optJSONObject("query")?.optJSONArray("pages")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        o.optJSONObject("thumbnail")?.optString("source")?.let { thumbs[o.optString("title")] = it }
                    }
                }
            }
            return out.map { if (it.thumb == null) it.copy(thumb = thumbs[it.title]) else it }
        }
        return out
    }

    private fun okCar(t: String) = !yearOnly.matches(t) && !t.startsWith("List of", true) && !t.startsWith("Category:") &&
        !t.endsWith("Hot Wheels") && !t.contains("Series", true) && t.length > 1

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
            if (!Regex("\\.(jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE).containsMatchIn(name) || skip.containsMatchIn(name) || placeholder.containsMatchIn(name)) continue
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
