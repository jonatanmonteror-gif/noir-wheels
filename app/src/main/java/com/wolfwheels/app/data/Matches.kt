package com.wolfwheels.app.data

import org.json.JSONObject

/** "¿Quién tiene lo que busco?": crosses my wish list and my trade list with the other public garages. */
object Matches {
    data class Car(val key: String, val title: String, val year: String, val color: String, val series: String, val photo: String?, val toy: String)
    /** kind: trade (lo cambia), have (lo tiene), model (mismo modelo, otra versión) · exact / model for seekers. */
    data class Person(val slug: String, val name: String, val kind: String, val year: String = "", val color: String = "",
                      val photo: String? = null, val trade: Boolean = false, val versions: Int = 0)
    data class Row(val car: Car, val people: List<Person>)
    data class Result(val want: List<Row>, val trade: List<Row>, val isPublic: Boolean, val wants: Int, val trades: Int, val garages: Int)

    suspend fun load(): Result {
        MyCollection.sync(force = true)
        val j = Server.call("matches", token = Account.token)
        fun car(o: JSONObject) = Car(o.optString("key"), o.optString("title"), o.optString("year"), o.optString("color"),
            o.optString("series"), o.optString("photo").ifEmpty { null }, o.optString("toy"))
        fun rows(name: String, field: String): List<Row> {
            val a = j.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { i ->
                val r = a.getJSONObject(i)
                val p = r.optJSONArray(field)
                Row(car(r.getJSONObject("item")), (0 until (p?.length() ?: 0)).map { k ->
                    val o = p!!.getJSONObject(k)
                    Person(o.optString("slug"), o.optString("name"), o.optString("kind"), o.optString("year"), o.optString("color"),
                        o.optString("photo").ifEmpty { null }, o.optBoolean("trade"), o.optInt("versions"))
                })
            }
        }
        return Result(rows("want", "offers"), rows("trade", "seekers"), j.optBoolean("public"), j.optInt("wants"), j.optInt("trades"), j.optInt("garages"))
    }

    suspend fun contact(slug: String, title: String, message: String) {
        Server.call("contact", JSONObject().put("slug", slug).put("title", title).put("message", message), token = Account.token)
    }

    fun garageUrl(slug: String) = "https://jonatanmontero.es/garaje/$slug"
}
