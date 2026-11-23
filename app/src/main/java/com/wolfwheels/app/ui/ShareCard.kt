package com.wolfwheels.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wolfwheels.app.R
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A nice image of a car (Wolf Wheels style) to share on WhatsApp, Instagram… */
object ShareCard {
    suspend fun share(ctx: Context, title: String, v: Wiki.Version?, photo: String?) {
        val file = withContext(Dispatchers.IO) { render(ctx, title, v, photo) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "$title en Wolf Wheels").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Compartir coche"))
    }

    private suspend fun render(ctx: Context, title: String, v: Wiki.Version?, photo: String?): File {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0B0C0F.toInt())
        val barlow = ResourcesCompat.getFont(ctx, R.font.barlow_extrabold_italic) ?: Typeface.DEFAULT_BOLD
        val inter = ResourcesCompat.getFont(ctx, R.font.inter_medium) ?: Typeface.DEFAULT
        val accent = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0xFFFF3D17.toInt(), 0xFFFF5A1A.toInt(), 0xFFFFB81C.toInt()), null, Shader.TileMode.CLAMP)

        // Logo
        ResourcesCompat.getDrawable(ctx.resources, R.drawable.wolf_mark, null)?.toBitmap(132, 110)?.let { c.drawBitmap(it, 70f, 60f, null) }
        val logo = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = barlow; textSize = 64f; color = 0xFFF4F4F5.toInt() }
        c.drawText("WOLF", 222f, 140f, logo)
        val wolfW = logo.measureText("WOLF ")
        c.drawText("WHEELS", 222f + wolfW, 140f, Paint(logo).apply { shader = accent })

        // Photo on the light "showroom" panel
        val panel = RectF(60f, 210f, w - 60f, 900f)
        c.drawRoundRect(panel, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECECEF.toInt() })
        val url = Wiki.big(photo, 1000) ?: photo
        if (url != null) {
            val r = ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build())
            (r as? SuccessResult)?.drawable?.toBitmap()?.let { img ->
                val pad = 40f
                val box = RectF(panel.left + pad, panel.top + pad, panel.right - pad, panel.bottom - pad)
                val scale = minOf(box.width() / img.width, box.height() / img.height)
                val dw = img.width * scale; val dh = img.height * scale
                val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
                c.drawBitmap(img, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
        }
        c.drawRect(60f, 930f, w - 60f, 942f, Paint().apply { shader = accent })

        // Name and version
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = barlow; color = 0xFFF4F4F5.toInt(); textSize = 96f }
        var t = title.uppercase()
        while (name.measureText(t) > w - 140 && name.textSize > 54f) name.textSize -= 4f
        while (name.measureText(t) > w - 140 && t.length > 4) t = t.dropLast(2) + "…"
        c.drawText(t, 70f, 1050f, name)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = inter; color = 0xFFA3A7B0.toInt(); textSize = 40f }
        if (v != null) {
            c.drawText(listOf(v.year, v.colorEs).filter { it.isNotBlank() }.joinToString(" · ").take(48), 72f, 1115f, sub)
            if (v.series.isNotBlank()) c.drawText(v.series.take(48), 72f, 1170f, sub.apply { color = 0xFF6B707A.toInt(); textSize = 34f })
            var x = 72f
            val badge = { text: String, gradient: Boolean ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = barlow; textSize = 40f }
                val bw = p.measureText(text) + 52f
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { if (gradient) shader = accent else color = 0xFFF4F4F5.toInt() }
                val path = android.graphics.Path().apply { moveTo(x + 18f, 1205f); lineTo(x + bw, 1205f); lineTo(x + bw - 18f, 1265f); lineTo(x, 1265f); close() }
                c.drawPath(path, bg)
                c.drawText(text, x + 26f, 1250f, p.apply { color = 0xFF14100A.toInt() })
                x += bw + 16f
            }
            if (v.th == 2) badge("SUPER TREASURE HUNT", true) else if (v.th == 1) badge("TREASURE HUNT", false)
            if (MyCollection.statusOf(v.key(title)) == "have") badge("EN MI GARAJE", true)
        }
        c.drawText("Wolf Wheels · tu garaje de coches a escala", 72f, 1315f, Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = inter; color = 0xFF6B707A.toInt(); textSize = 28f })

        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "wolf-wheels-coche.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}
