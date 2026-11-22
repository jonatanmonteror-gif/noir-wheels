package com.wolfwheels.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.data.ErrorLog

/** After the app closed by itself: shows the report with Copy / Share. */
@Composable
fun CrashDialog() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(ErrorLog.pending()) }
    val r = report ?: return
    fun close() { ErrorLog.clear(); report = null }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WW.surface)
                .border(1.dp, WW.orange.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(18.dp)
        ) {
            Heading("La app se cerró", size = 20)
            Spacer(Modifier.height(6.dp))
            Text("Perdona. Copia o comparte este informe con Claude para arreglarlo. No lleva datos personales.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(WW.bg).padding(10.dp)) {
                Text(r, color = WW.text3, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Copiar", Modifier.weight(1f)) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Informe Wolf Wheels", r))
                }
                PrimaryButton("Compartir", Modifier.weight(1f)) {
                    runCatching {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Informe de error de Wolf Wheels").putExtra(Intent.EXTRA_TEXT, r), "Compartir informe"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cerrar", Modifier.fillMaxWidth()) { close() }
        }
    }
}
