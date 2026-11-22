package com.wolfwheels.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wolfwheels.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Actualizaciones desde GitHub Releases, con comprobación de huella SHA-256 antes de instalar. */
object Updater {
    private const val REPO = "jonatanmonteror-gif/noir-wheels"

    data class Release(val versionCode: Int, val name: String, val apkUrl: String, val shaUrl: String?)

    val currentCode: Int get() = BuildConfig.VERSION_CODE

    /**
     * Lee la versión más reciente siguiendo la redirección de github.com/…/releases/latest
     * (lección de Noir+: la API de GitHub limita a 60 consultas por hora).
     */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val client = Http.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            // Follows up to 4 redirects (also works if the repository is renamed) until it reaches …/releases/tag/<tag>.
            var url = "https://github.com/$REPO/releases/latest"
            var tag = ""
            repeat(4) {
                if (tag.isNotEmpty()) return@repeat
                val req = Request.Builder().url(url).head().header("User-Agent", Http.UA).build()
                val location = client.newCall(req).execute().use { it.header("Location") } ?: return@runCatching null
                url = if (location.startsWith("http")) location else "https://github.com$location"
                tag = url.substringAfterLast("/tag/", "")
            }
            if (tag.isEmpty()) return@runCatching null
            val code = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
            val base = url.substringBefore("/releases/tag/") + "/releases/download/$tag"
            Release(code, "Wolf Wheels 1.0.$code", "$base/WolfWheels.apk", "$base/WolfWheels.apk.sha256")
        }.getOrNull()
    }

    /** Descarga el APK a la caché y verifica la huella. Devuelve el archivo listo para instalar. */
    /**
     * Descarga la versión nueva con el gestor de descargas de Android: aguanta cortes de red, cambios de wifi a datos
     * y que la app se vaya a segundo plano (antes la descarga se cortaba con «connection abort»).
     * Al terminar comprueba la huella SHA-256.
     */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        // La huella se pide ANTES de descargar, con la app delante. Si se pide al final y la app está en
        // segundo plano, Android (ahorro de datos, Samsung) le corta internet y falla con «Unable to resolve host».
        val expected = rel.shaUrl?.let { url -> fetchSha(url) }
        File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        val ext = ctx.getExternalFilesDir(null)
        val file = if (ext != null) {
            val dir = File(ext, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "WolfWheels-${rel.versionCode}.apk")
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val req = android.app.DownloadManager.Request(Uri.parse(rel.apkUrl))
                .setTitle(rel.name)
                .setDescription("Descargando la actualización")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(f))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val id = dm.enqueue(req)
            var ok = false
            try {
                while (true) {
                    kotlinx.coroutines.delay(400)
                    dm.query(android.app.DownloadManager.Query().setFilterById(id)).use { c ->
                        if (c == null || !c.moveToFirst()) throw IOException("Se canceló la descarga")
                        val status = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        val done = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) onProgress(done.toFloat() / total)
                        when (status) {
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> { ok = true }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                val reason = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))
                                throw IOException("La descarga falló (código $reason). Revisa la conexión y vuelve a intentarlo.")
                            }
                        }
                    }
                    if (ok) break
                }
            } finally {
                if (!ok) runCatching { dm.remove(id) }
            }
            f
        } else {
            downloadDirect(ctx, rel, onProgress)
        }
        expected?.let { expected ->
            val actual = sha256(file)
            if (expected.length == 64 && expected != actual) {
                file.delete()
                throw IOException("La huella del archivo no coincide. Descarga cancelada por seguridad.")
            }
        }
        file
    }

    /** Huella SHA-256 publicada junto al APK, con reintentos por si la conexión parpadea. */
    private suspend fun fetchSha(url: String): String {
        var last: Exception? = null
        repeat(3) { i ->
            try { return Http.get(url).trim().take(64).lowercase() } catch (e: Exception) { last = e; kotlinx.coroutines.delay(1200L * (i + 1)) }
        }
        throw IOException("No hay conexión con GitHub. Revisa internet y vuelve a pulsar Descargar.", last)
    }

    /** Descarga directa con reintentos y continuación (si el móvil no tiene almacenamiento para el gestor de descargas). */
    private suspend fun downloadDirect(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "WolfWheels-${rel.versionCode}.apk")
        var attempt = 0
        while (true) {
            try {
                val have = if (file.exists()) file.length() else 0L
                val b = Request.Builder().url(rel.apkUrl).header("User-Agent", Http.UA)
                if (have > 0) b.header("Range", "bytes=$have-")
                Http.client.newCall(b.build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Error ${r.code} al descargar")
                    val body = r.body ?: throw IOException("Descarga vacía")
                    val append = r.code == 206
                    val total = body.contentLength().let { if (it > 0 && append) it + have else it }
                    var done = if (append) have else 0L
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(file, append).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
                return file
            } catch (e: IOException) {
                if (++attempt >= 5) throw IOException("Se cortó la conexión varias veces. Vuelve a intentarlo con buena cobertura.")
                kotlinx.coroutines.delay(1500L * attempt)
            }
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Abre el instalador de Android con el APK ya descargado y verificado.
     * Si falta el permiso de "instalar apps desconocidas", abre esa pantalla y devuelve false.
     */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i); true }.getOrDefault(false)
    }
}
