package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.Community
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Server
import kotlinx.coroutines.launch
import java.io.File

/** A car that is not in the wiki (customs, other brands…): created by hand, with my own photo and details. */
@Composable
fun CustomCarScreen(key: String?, presetToy: String? = null, presetPhoto: String? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var currentKey by remember { mutableStateOf(key) }
    var share by remember { mutableStateOf(Account.loggedIn.value) }
    var shareBusy by remember { mutableStateOf(false) }
    var shareMsg by remember { mutableStateOf<String?>(null) }
    // The card photo from the scanner, copied so it stays as my photo of the car
    val startPhoto = remember {
        presetPhoto?.let { File(it) }?.takeIf { it.exists() }?.let { src ->
            val dir = File(ctx.filesDir, "myphotos").apply { mkdirs() }
            File(dir, "scan_${System.currentTimeMillis()}.jpg").also { src.copyTo(it, overwrite = true) }
        }
    }

    fun publish(k: String) {
        shareBusy = true; shareMsg = null
        scope.launch {
            try { currentKey = Community.publish(k); shareMsg = "Publicado: ya lo puede encontrar todo el mundo." }
            catch (e: Exception) { shareMsg = Server.message(e) }
            shareBusy = false
        }
    }
    val item = currentKey?.let { MyCollection.items[it] }
    var name by remember { mutableStateOf(item?.title ?: "") }
    var brand by remember { mutableStateOf(item?.brand ?: "") }
    var year by remember { mutableStateOf(item?.year ?: "") }
    var series by remember { mutableStateOf(item?.series ?: "") }
    var color by remember { mutableStateOf(item?.color ?: "") }
    var toy by remember { mutableStateOf(item?.toy ?: presetToy ?: "") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(if (item == null) "Añadir a mano" else item.title)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            if (item == null) {
                Text(if (presetToy != null) "Este número no está ni en la wiki ni en el catálogo de la comunidad. Crea la ficha y quedará para todos."
                    else "Para coches que no están en la wiki: customs, otras marcas, ediciones raras… Luego le pones tu foto.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(14.dp))
                if (startPhoto != null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.surface)) {
                        AsyncImage(startPhoto, "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(14.dp))
                }
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                    if (item.myPhoto != null) AsyncImage(File(item.myPhoto), item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("HAZ UNA FOTO ABAJO", color = WW.text3, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("NOMBRE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(name, "Ej.: Mi custom Skyline", Modifier.fillMaxWidth()) { name = it.take(60) }
            Spacer(Modifier.height(10.dp))
            Text("MARCA", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(brand, "Opcional", Modifier.fillMaxWidth()) { brand = it.take(40) }
            Spacer(Modifier.height(10.dp))
            Text("AÑO", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(year, "2026", Modifier.fillMaxWidth(), KeyboardType.Number) { year = it.filter { c -> c.isDigit() }.take(4) }
            Spacer(Modifier.height(10.dp))
            Text("SERIE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(series, "Opcional", Modifier.fillMaxWidth()) { series = it.take(60) }
            Spacer(Modifier.height(10.dp))
            Text("COLOR", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(color, "Opcional", Modifier.fillMaxWidth()) { color = it.take(40) }
            Spacer(Modifier.height(10.dp))
            Text("Nº DE JUGUETE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(toy, "Opcional", Modifier.fillMaxWidth(), KeyboardType.Ascii) { toy = it.uppercase().take(12) }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (item == null) "Añadir a mi garaje" else if (saved) "Guardado ✓" else "Guardar cambios", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                if (item == null) {
                    val k = MyCollection.addCustom(name.trim(), brand.trim(), year, series.trim(), color.trim(), toy.trim())
                    if (startPhoto != null) MyCollection.update(k) { it.copy(myPhoto = startPhoto.absolutePath) }
                    currentKey = k
                    if (share && Account.loggedIn.value) publish(k)
                } else {
                    MyCollection.update(item.key) { it.copy(title = name.trim(), brand = brand.trim(), year = year, series = series.trim(), color = color.trim(), toy = toy.trim()) }
                    if (item.cat.isNotBlank() && Account.loggedIn.value) publish(item.key)
                }
                saved = true
            }
            // Community catalog
            Spacer(Modifier.height(12.dp))
            val published = item?.cat?.isNotBlank() == true
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface)
                .border(1.dp, if (published || (item == null && share)) WW.amber.copy(alpha = 0.7f) else WW.line, RoundedCornerShape(14.dp)).padding(14.dp)) {
                if (item == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { share = !share }) {
                        Column(Modifier.weight(1f)) {
                            Text("COMPARTIR EN EL CATÁLOGO", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                            Text(if (Account.loggedIn.value) "Los demás podrán encontrarlo en el buscador y al escanear su número."
                                else "Entra en tu cuenta para compartirlo con los demás.", color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        androidx.compose.material3.Switch(checked = share && Account.loggedIn.value, enabled = Account.loggedIn.value, onCheckedChange = { share = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = WW.orange, checkedThumbColor = WW.text))
                    }
                } else {
                    Text(if (published) "EN EL CATÁLOGO DE LA COMUNIDAD" else "SOLO EN TU GARAJE", color = if (published) WW.amber else WW.text,
                        fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    Text(if (published) "Los cambios que guardes y tu foto se actualizan para todos." else "Compártelo para que los demás lo encuentren en el buscador y al escanear.",
                        color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    if (!Account.loggedIn.value) SecondaryButton("Entrar para compartir", Modifier.fillMaxWidth()) { AppState.push(Route.Account) }
                    else PrimaryButton(if (shareBusy) "Enviando…" else if (published) "Actualizar foto y datos" else "Publicar en el catálogo",
                        Modifier.fillMaxWidth(), enabled = !shareBusy && name.isNotBlank()) { publish(item.key) }
                }
                shareMsg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = WW.amber, fontSize = 13.sp, lineHeight = 18.sp) }
            }
            item?.takeIf { !it.deleted }?.let {
                Spacer(Modifier.height(16.dp))
                CollectionEditor(it)
            }
            if (item?.deleted == true) {
                Spacer(Modifier.height(12.dp))
                Text("Este coche ya no está en tu garaje.", color = WW.text3, fontSize = 13.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
