package com.wolfwheels.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Loose car (no card) → photo → AI on the server → candidate castings → matching wiki pages. */
object Identify {
    data class Candidate(val name: String, val confidence: Int, val color: String, val notes: String, val title: String?, val thumb: String?)

    /** Small upright JPEG (max 1024 px) in base64, ready to send. */
    suspend fun prepare(file: File): String = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("image")
        val rot = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
        if (rot != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun run(file: File): List<Candidate> {
        val img = prepare(file)
        val j = Server.call("identify", JSONObject().put("image", img), token = Account.token)
        val a = j.optJSONArray("candidates") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
            val name = c.optString("name")
            val hit = runCatching { Wiki.search(name, null).items.firstOrNull() }.getOrNull()
            Candidate(name, c.optInt("confidence"), c.optString("color"), c.optString("notes"), hit?.title, hit?.thumb)
        }.distinctBy { it.title ?: it.name }
    }
}
