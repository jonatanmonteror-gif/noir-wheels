package com.wolfwheels.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.data.Changelog
import com.wolfwheels.app.data.Updater
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var ready by remember { mutableStateOf<File?>(null) }
    val update = AppState.update.value
    var viewOpen by remember { mutableStateOf(false) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val restorePicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupMsg = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val n = com.wolfwheels.app.data.Backup.import(text)
            if (n == 0) "La copia no tenía nada nuevo: tu garaje ya estaba al día." else "Copia recuperada: $n coches añadidos o actualizados."
        }.getOrElse { "No se pudo leer: ${it.message ?: "archivo no válido"}" }
    }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Ajustes")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Wordmark(markSize = 46.dp, textSize = 28)
                    Spacer(Modifier.height(10.dp))
                    Text("Versión 1.0.${Updater.currentCode}", color = WW.text3, fontSize = 13.sp)
                }
            }

            SectionTitle("Vista")
            Card {
                Row(
                    Modifier.fillMaxWidth().clickable { viewOpen = !viewOpen }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Coches por fila", color = WW.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        val n = com.wolfwheels.app.data.Prefs.columns.intValue
                        Text(if (n == 1) "1 columna" else "$n columnas", color = WW.text3, fontSize = 13.sp)
                    }
                    Text(if (viewOpen) "▴" else "▾", color = WW.orange, fontSize = 20.sp)
                }
                if (viewOpen) {
                    Spacer(Modifier.height(6.dp))
                    Text("En el inicio, las listas y Mi garaje.", color = WW.text3, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { c ->
                            Pill(if (c == 1) "1 columna" else "$c columnas", com.wolfwheels.app.data.Prefs.columns.intValue == c) {
                                com.wolfwheels.app.data.Prefs.setColumns(c)
                            }
                        }
                    }
                }
            }

            SectionTitle("Cuenta")
            Card {
                if (com.wolfwheels.app.data.Account.loggedIn.value) {
                    Text(com.wolfwheels.app.data.Account.name.value, color = WW.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(com.wolfwheels.app.data.Account.email.value, color = WW.text2, fontSize = 14.sp)
                } else {
                    Text("Crea una cuenta para guardar tu garaje y verlo en todos tus aparatos.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (com.wolfwheels.app.data.Account.loggedIn.value) "Mi cuenta" else "Entrar o crear cuenta", Modifier.fillMaxWidth()) { AppState.push(Route.Account) }
            }

            SectionTitle("Copia de seguridad")
            Card {
                Text("Guarda tu garaje en un archivo (en Drive, el correo o el móvil) y recupéralo cuando quieras, también en otro móvil.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Guardar copia", Modifier.weight(1f)) {
                        runCatching {
                            val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
                            val f = java.io.File(dir, "wolf-wheels-copia-$stamp.json").apply { writeText(com.wolfwheels.app.data.Backup.export()) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                            ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("application/json").putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Guardar copia de Mi garaje"))
                        }
                    }
                    SecondaryButton("Restaurar", Modifier.weight(1f)) { restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                }
                backupMsg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.amber, fontSize = 13.sp) }
            }

            SectionTitle("Actualizaciones")
            Card {
                if (update != null) {
                    Text("Hay una versión nueva: ${update.name}", color = WW.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    val p = UpdateFlow.progress.floatValue
                    if (p >= 0f) {
                        LinearProgressIndicator(progress = { p }, color = WW.orange, trackColor = WW.surface2, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Descargando… ${(p * 100).toInt()} %", color = WW.text2, fontSize = 13.sp)
                    } else {
                        PrimaryButton(if (UpdateFlow.ready.value != null) "Instalar" else "Descargar e instalar", Modifier.fillMaxWidth()) { UpdateFlow.start(ctx, scope) }
                    }
                    UpdateFlow.status.value?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.orange, fontSize = 13.sp) }
                } else {
                    Text("Tienes la última versión.", color = WW.text, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(if (checking) "Buscando…" else "Buscar actualizaciones", Modifier.fillMaxWidth()) {
                        if (checking) return@SecondaryButton
                        checking = true; status = null
                        scope.launch {
                            val r = Updater.latest()
                            checking = false
                            when {
                                r == null -> status = "No se pudo comprobar. Revisa la conexión."
                                r.versionCode > Updater.currentCode -> { AppState.update.value = r; UpdateFlow.dismissed.value = true }
                                else -> status = "Ya tienes la versión más reciente."
                            }
                        }
                    }
                }
                status?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.text2, fontSize = 13.sp) }
            }

            SectionTitle("Novedades")
            Card {
                Changelog.entries.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Text(e.version.uppercase(), color = WW.amber, fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Spacer(Modifier.height(6.dp))
                    e.items.forEach { Text("· $it", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }

            SectionTitle("Acerca de")
            Card {
                Text("Wolf Wheels es una galería personal y no oficial. No tiene relación con Mattel; Hot Wheels es una marca registrada de Mattel.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                Text("Los datos y las fotos vienen en directo de la Hot Wheels Wiki (Fandom), hecha por la comunidad. El texto tiene licencia CC BY-SA y las fotos pertenecen a sus autores.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Abrir la Hot Wheels Wiki", Modifier.fillMaxWidth()) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Wiki.SITE + "Hot_Wheels_Wiki"))) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface)
            .border(1.dp, WW.line, RoundedCornerShape(14.dp)).padding(14.dp)
    ) { content() }
}
