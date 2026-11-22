package com.wolfwheels.app.data

import android.content.Context
import android.os.Build
import com.wolfwheels.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** If the app closes by itself, saves what happened (no personal data) to show it next time it opens. */
object ErrorLog {
    private lateinit var file: File

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val head = "Wolf Wheels 1.0.${BuildConfig.VERSION_CODE} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date()) + " · hilo ${t.name}\n\n"
                file.writeText(head + sw.toString().take(12000))
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** The report of the last close, if there is one. */
    fun pending(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear() { runCatching { file.delete() } }
}
