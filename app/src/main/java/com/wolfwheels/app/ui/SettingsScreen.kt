package com.wolfwheels.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

            SectionTitle("Cuenta")
            Card {
                if (com.wolfwheels.app.data.Account.loggedIn.value) {
                    Text(com.wolfwheels.app.data.Account.name.value, color = WW.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(com.wolfwheels.app.data.Account.email.value, color = WW.text2, fontSize = 14.sp)
                } else {
                    Text("Crea una cuenta para guardar tu colección y verla en todos tus aparatos.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (com.wolfwheels.app.data.Account.loggedIn.value) "Mi cuenta" else "Entrar o crear cuenta", Modifier.fillMaxWidth()) { AppState.push(Route.Account) }
            }

            SectionTitle("Actualizaciones")
            Card {
                if (update != null) {
                    Text("Hay una versión nueva: ${update.name}", color = WW.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    when {
                        ready != null -> PrimaryButton("Instalar", Modifier.fillMaxWidth()) {
                            if (!Updater.install(ctx, ready!!)) status = "Permite instalar apps desde Wolf Wheels y vuelve a pulsar Instalar."
                        }
                        progress >= 0f -> {
                            LinearProgressIndicator(progress = { progress }, color = WW.orange, trackColor = WW.surface2, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text("Descargando… ${(progress * 100).toInt()} %", color = WW.text2, fontSize = 13.sp)
                        }
                        else -> PrimaryButton("Descargar e instalar", Modifier.fillMaxWidth()) {
                            status = null; progress = 0f
                            scope.launch {
                                try {
                                    val f = Updater.download(ctx, update) { progress = it }
                                    ready = f
                                    if (!Updater.install(ctx, f)) status = "Permite instalar apps desde Wolf Wheels y vuelve a pulsar Instalar."
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                                } catch (e: Exception) { status = e.message ?: "No se pudo descargar."
                                } finally { progress = -1f }
                            }
                        }
                    }
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
                                r.versionCode > Updater.currentCode -> AppState.update.value = r
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
                    Text("VERSIÓN ${e.version}", color = WW.amber, fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
