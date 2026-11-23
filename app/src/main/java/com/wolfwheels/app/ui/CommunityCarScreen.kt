package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Community
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Server
import kotlinx.coroutines.launch
import java.io.File

/** A car from the community catalog (added by a user, not in the wiki). */
@Composable
fun CommunityCarScreen(id: String) {
    val scope = rememberCoroutineScope()
    val key = "cat|$id"
    val local = MyCollection.items[key]
    var entry by remember { mutableStateOf<Community.Entry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        try {
            entry = Community.get(id)
            if (entry == null) error = "Esta ficha ya no está en el catálogo."
        } catch (e: Exception) { error = Server.message(e) }
    }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(entry?.title ?: local?.title ?: "Catálogo")
        val e = entry
        // Without connection, what I have saved is still shown
        val title = e?.title ?: local?.title
        if (title == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Text(error!!, color = WW.text2, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                else CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                val photo = e?.photo ?: local?.photo
                when {
                    local?.myPhoto != null -> AsyncImage(File(local.myPhoto), title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    photo != null -> AsyncImage(photo, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Text("SIN FOTO", color = WW.text3, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.clip(Slant).background(WW.surface2).padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text("CATÁLOGO DE LA COMUNIDAD", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 30.sp, lineHeight = 32.sp)
            if (e != null && e.by.isNotBlank()) Text("Ficha creada por ${e.by}", color = WW.text3, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            val facts = listOf("Marca" to (e?.brand ?: local?.brand), "Año" to (e?.year ?: local?.year), "Serie" to (e?.series ?: local?.series),
                "Color" to (e?.color ?: local?.color), "Nº de juguete" to (e?.toy ?: local?.toy)).filter { !it.second.isNullOrBlank() }
            facts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { (l, v) -> SpecTile(l, v!!, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (!e?.notes.isNullOrBlank()) Text(e!!.notes, color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))

            val st = local?.takeIf { !it.deleted }?.status
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val toggle = { s: String ->
                    val ent = e ?: Community.Entry(id, local!!.title, local.brand, local.year, local.series, local.color, local.toy, "", "", local.photo, false)
                    if (st == s) {
                        AppState.undo.value = "Quitado de Mi garaje" to local!!
                        MyCollection.setCatalog(ent, null)
                    } else MyCollection.setCatalog(ent, s)
                }
                if (st == "have") PrimaryButton("✓ La tengo", Modifier.weight(1f)) { toggle("have") }
                else SecondaryButton("La tengo", Modifier.weight(1f)) { toggle("have") }
                if (st == "want") PrimaryButton("★ La quiero", Modifier.weight(1f)) { toggle("want") }
                else SecondaryButton("La quiero", Modifier.weight(1f)) { toggle("want") }
            }
            MyCollection.items[key]?.takeIf { !it.deleted && it.status == "have" }?.let {
                Spacer(Modifier.height(16.dp))
                CollectionEditor(it)
            }

            if (e?.canEdit == true) {
                Spacer(Modifier.height(22.dp))
                Heading("Tu ficha", size = 17)
                Spacer(Modifier.height(8.dp))
                if (local != null && !local.deleted) {
                    SecondaryButton("Editar datos y foto", Modifier.fillMaxWidth()) { AppState.push(Route.CustomCar(key)) }
                    Spacer(Modifier.height(8.dp))
                }
                SecondaryButton(if (busy) "Borrando…" else if (confirmDelete) "Pulsa otra vez para borrarla para todos" else "Borrar del catálogo", Modifier.fillMaxWidth()) {
                    if (busy) return@SecondaryButton
                    if (!confirmDelete) { confirmDelete = true; return@SecondaryButton }
                    busy = true
                    scope.launch {
                        try {
                            Community.delete(id)
                            MyCollection.items[key]?.let { MyCollection.update(key) { it.copy(cat = "") } }
                            AppState.back()
                        } catch (ex: Exception) { error = Server.message(ex); busy = false; confirmDelete = false }
                    }
                }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = WW.orange, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
