package com.noirwheels.app.data

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
    data class Detail(val title: String, val hero: String?, val facts: List<Pair<String, String>>, val intro: String)
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
        val parsed = call(mapOf("action" to "parse", "page" to title, "prop" to "text", "section" to "0", "redirects" to "1"))
        val html = parsed.optJSONObject("parse")?.optString("text").orEmpty()
        val doc = Jsoup.parse(html)
        val facts = doc.select(".pi-data").mapNotNull { e ->
            val k = e.selectFirst(".pi-data-label")?.text()?.trim().orEmpty()
            val v = e.selectFirst(".pi-data-value")?.text()?.trim().orEmpty()
            if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
        }
        val intro = doc.select("p").map { it.text().replace(Regex("\\[\\d+]"), "").trim() }
            .firstOrNull { it.length > 40 }.orEmpty()
        return Detail(title, hero, facts, intro)
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
