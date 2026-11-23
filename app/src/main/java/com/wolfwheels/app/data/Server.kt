package com.wolfwheels.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Jonatan's server (Hostinger): accounts, collection and backup photos from eBay. */
object Server {
    private const val BASE = "https://jonatanmontero.es/wolf-wheels/api.php"
    private const val APP_KEY = "wolfwheels-v1"

    class ApiError(val code: Int, val reason: String) : IOException(reason)

    suspend fun call(action: String, body: JSONObject? = null, query: Map<String, String> = emptyMap(), token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val url = BASE.toHttpUrl().newBuilder().addQueryParameter("a", action).apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val b = Request.Builder().url(url).header("User-Agent", Http.UA).header("X-Wolf-App", APP_KEY)
            if (token != null) b.header("Authorization", "Bearer $token").header("X-Wolf-Token", token)
            if (body != null) b.post(body.toString().toRequestBody("application/json".toMediaType()))
            Http.client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val j = runCatching { JSONObject(text) }.getOrNull()
                if (!r.isSuccessful || j == null) throw ApiError(r.code, j?.optString("error").orEmpty().ifEmpty { "http${r.code}" })
                j
            }
        }

    /** Human message for an error, in Spanish. */
    fun message(e: Throwable): String = when {
        e is ApiError && e.reason == "exists" -> "Ya hay una cuenta con ese email. Entra con tu contraseña."
        e is ApiError && e.reason == "wrong" -> "Email o contraseña incorrectos."
        e is ApiError && e.reason == "email" -> "Ese email no es válido."
        e is ApiError && e.reason == "password" -> "La contraseña debe tener al menos 6 caracteres."
        e is ApiError && e.reason == "wait" -> "Demasiados intentos. Espera 15 minutos."
        e is ApiError && e.reason == "login" -> "Tu sesión ha caducado. Vuelve a entrar."
        e is ApiError && e.reason == "code" -> "El código no es correcto."
        e is ApiError && e.reason == "expired" -> "El código ha caducado. Pide uno nuevo."
        e is ApiError && e.reason == "mail" -> "No se pudo enviar el correo. Inténtalo en un momento."
        e is ApiError && e.reason == "mail_wait" -> "Espera un minuto antes de pedir otro código."
        e is ApiError && e.reason == "unverified" -> "Falta verificar tu email. Te hemos mandado un código nuevo."
        e is ApiError && e.reason == "slug" -> "El nombre del enlace debe tener de 3 a 30 letras, números o guiones."
        e is ApiError && e.reason == "slug_taken" -> "Ese nombre ya lo usa otra persona. Prueba con otro."
        e is ApiError && e.reason == "too_many" -> "Has llegado al límite de hoy. Vuelve a probar mañana."
        e is ApiError && e.reason == "garage" -> "Ese garaje ya no es público."
        e is ApiError && e.reason == "no_ai" -> "El reconocimiento por foto aún no está activado en el servidor."
        e is ApiError && e.reason == "ai" -> "El reconocimiento no ha respondido. Prueba otra vez en un momento."
        e is ApiError && e.reason == "image" -> "La foto no es válida. Prueba con otra."
        e is ApiError && e.code == 404 -> "El servidor de Wolf Wheels aún no está instalado."
        e is ApiError -> "El servidor respondió con un error (${e.code})."
        else -> "No hay conexión con el servidor. Revisa internet."
    }

    private val imgCache = HashMap<String, String?>()

    /** Photo from eBay listings for a version without a photo (the server keeps the key and caches). */
    suspend fun backupImage(q: String): String? {
        synchronized(imgCache) { if (imgCache.containsKey(q)) return imgCache[q] }
        val url = runCatching { call("img", query = mapOf("q" to q)).optString("url").takeIf { it.startsWith("http") } }.getOrNull()
        synchronized(imgCache) { imgCache[q] = url }
        return url
    }
}
