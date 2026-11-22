package com.wolfwheels.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.data.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** Download/installation of an update, shared by the notice and Settings. */
object UpdateFlow {
    val progress = mutableFloatStateOf(-1f)
    val ready = mutableStateOf<File?>(null)
    val status = mutableStateOf<String?>(null)
    /** The notice was closed with "Luego" (shows again next time the app opens). */
    val dismissed = mutableStateOf(false)
    private var lastCheck = 0L

    fun install(ctx: Context) {
        val f = ready.value ?: return
        if (!Updater.install(ctx, f)) status.value = "Permite instalar apps desde Wolf Wheels y vuelve a pulsar Instalar."
    }

    fun start(ctx: Context, scope: CoroutineScope) {
        val rel = AppState.update.value ?: return
        if (progress.floatValue >= 0f) return
        if (ready.value != null) { install(ctx); return }
        status.value = null; progress.floatValue = 0f
        scope.launch {
            try {
                val f = Updater.download(ctx, rel) { progress.floatValue = it }
                ready.value = f
                install(ctx)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { status.value = e.message ?: "No se pudo descargar."
            } finally { progress.floatValue = -1f }
        }
    }

    /** Looks for a new version (at most every 30 minutes). */
    suspend fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 30 * 60_000) return
        lastCheck = now
        val r = Updater.latest() ?: return
        if (r.versionCode > Updater.currentCode && r.versionCode != AppState.update.value?.versionCode) {
            AppState.update.value = r
            dismissed.value = false
        }
    }
}

/** Notice shown when the app opens and there is a new version. */
@Composable
fun UpdateDialog() {
    val rel = AppState.update.value ?: return
    if (UpdateFlow.dismissed.value) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = UpdateFlow.progress.floatValue
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (progress < 0f) UpdateFlow.dismissed.value = true },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WW.surface)
                .border(1.dp, WW.orange.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            Wordmark(markSize = 34.dp, textSize = 22)
            Spacer(Modifier.height(14.dp))
            Heading("Nueva versión", size = 20)
            Spacer(Modifier.height(6.dp))
            Text("Ya puedes instalar ${rel.name}. Tienes la 1.0.${Updater.currentCode}.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            when {
                progress >= 0f -> {
                    LinearProgressIndicator(progress = { progress }, color = WW.orange, trackColor = WW.surface2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Descargando… ${(progress * 100).toInt()} %", color = WW.text2, fontSize = 13.sp)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Luego", Modifier.weight(1f)) { UpdateFlow.dismissed.value = true }
                    PrimaryButton(if (UpdateFlow.ready.value != null) "Instalar" else "Actualizar", Modifier.weight(1f)) { UpdateFlow.start(ctx, scope) }
                }
            }
            UpdateFlow.status.value?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.orange, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}
