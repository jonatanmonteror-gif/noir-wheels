package com.wolfwheels.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wolfwheels.app.MainActivity
import com.wolfwheels.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * News notifications, once a day with internet: new castings of the year in the wiki,
 * and new versions of the models in my wish list. Each one can be switched off in Settings.
 */
object News {
    private const val CHANNEL = "novedades"
    private const val WORK = "wolf-news"

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!Prefs.notifyNew && !Prefs.notifyWish) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<NewsWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Novedades", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notify).setContentTitle(title)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFFF5A1A.toInt()).setAutoCancel(true).setContentIntent(pi).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    /** The daily check. The first time it only remembers what there is, so it doesn't notify the whole list. */
    suspend fun check(ctx: Context) {
        val seen = runCatching { JSONObject(Prefs.newsSeen) }.getOrDefault(JSONObject())
        val year = Calendar.getInstance().get(Calendar.YEAR)
        if (Prefs.notifyNew) {
            val key = "new$year"
            val now = Wiki.listFrom("$year New Models").map { it.title }
            if (now.isNotEmpty()) {
                val before = seen.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                if (before != null) {
                    val fresh = now.filter { it !in before }
                    if (fresh.isNotEmpty()) notify(ctx, 1, if (fresh.size == 1) "Nuevo modelo en la wiki" else "${fresh.size} modelos nuevos en la wiki",
                        fresh.take(6).joinToString(", ") + if (fresh.size > 6) "…" else "")
                }
                seen.put(key, JSONArray(now))
            }
        }
        if (Prefs.notifyWish) {
            val counts = seen.optJSONObject("wish") ?: JSONObject()
            val titles = MyCollection.items.values.filter { !it.deleted && it.status == "want" && !it.custom }.map { it.title }.distinct().take(20)
            val news = mutableListOf<String>()
            for (t in titles) {
                val d = runCatching { Wiki.detail(t) }.getOrNull() ?: continue
                val n = d.versions.size
                if (counts.has(t) && n > counts.getInt(t)) {
                    val v = d.versions.last()
                    news += "$t: nueva versión" + listOf(v.year, v.color).filter { it.isNotBlank() }.joinToString(" · ", " (", ")") + if (v.th == 2) " ¡Super TH!" else if (v.th == 1) " ¡TH!" else ""
                }
                counts.put(t, n)
            }
            seen.put("wish", counts)
            if (news.isNotEmpty()) notify(ctx, 2, "Novedades de tu lista de deseos", news.joinToString("\n"))
        }
        Prefs.newsSeen = seen.toString()
    }
}

class NewsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        Wiki.init(applicationContext)
        MyCollection.init(applicationContext)
        return runCatching { News.check(applicationContext); Result.success() }.getOrDefault(Result.retry())
    }
}
