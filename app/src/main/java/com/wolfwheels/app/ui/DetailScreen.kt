package com.wolfwheels.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(title: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Wiki.Detail?>(null) }
    var error by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<Wiki.Photo>() }
    var cont by remember { mutableStateOf<Map<String, String>?>(null) }
    var photosLoading by remember { mutableStateOf(true) }
    var photosError by remember { mutableStateOf(false) }

    // Versions: group filter, order and the selected one
    var group by remember { mutableStateOf<String?>(null) }
    var newestFirst by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Wiki.Version?>(null) }
    var descOpen by remember { mutableStateOf(false) }
    val versionsRow = rememberLazyListState()

    fun loadPhotos() {
        photosLoading = true; photosError = false
        scope.launch {
            try {
                val p = Wiki.photos(title, cont)
                photos.addAll(p.items); cont = p.cont
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { photosError = true
            } finally { photosLoading = false }
        }
    }
    fun loadAll() {
        error = false
        scope.launch {
            try {
                val d = Wiki.detail(title)
                detail = d
                selected = d.versions.lastOrNull { it.photo != null } ?: d.versions.lastOrNull()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = true }
        }
        if (photos.isEmpty()) loadPhotos()
    }
    LaunchedEffect(title) { loadAll() }

    val d = detail
    val groups = d?.versions?.map { it.group }?.distinct().orEmpty()
    val shown = d?.versions.orEmpty().filter { group == null || it.group == group }.let { if (newestFirst) it.asReversed() else it }
    val versionPhotos = shown.filter { it.photo != null }.map {
        Wiki.Photo(it.photo!!, it.photo!!, listOf(it.year, it.series, it.color).filter { s -> s.isNotEmpty() }.joinToString(" · "))
    }
    val years = d?.versions.orEmpty().mapNotNull { it.year.take(4).toIntOrNull() }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { AppState.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = WW.text) }
            Text(title.uppercase(), color = WW.text, fontSize = 19.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ---------- Main photo (the selected version's) ----------
            item(span = { GridItemSpan(maxLineSpan) }) {
                val sel = selected
                val heroUrl = Wiki.big(sel?.photo, 1000) ?: d?.hero
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.showroom)
                        .clickable(enabled = versionPhotos.isNotEmpty() || photos.isNotEmpty()) {
                            val i = versionPhotos.indexOfFirst { it.thumb == sel?.photo }
                            AppState.viewer.value = if (i >= 0) AppState.Viewer(versionPhotos, i) else AppState.Viewer(photos.toList(), 0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        heroUrl != null -> AsyncImage(heroUrl, title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
                        d == null && !error -> CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        else -> Text("SIN FOTO", color = WW.text3, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                    }
                    if (sel != null && sel.year.isNotEmpty()) {
                        Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(Slant).background(WW.accent).padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text(sel.year, color = WW.onAccent, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                        }
                    }
                }
            }

            // ---------- Title, key numbers, model data ----------
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(title.uppercase(), color = WW.text, fontSize = 34.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic, lineHeight = 36.sp)
                    if (d != null && d.versions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("${d.versions.size}", "versiones", Modifier.weight(1f))
                            if (years.isNotEmpty()) Stat("${years.min()}", "debut", Modifier.weight(1f))
                            if (years.isNotEmpty()) Stat("${years.max()}", "última", Modifier.weight(1f))
                        }
                    }
                    if (error) {
                        Spacer(Modifier.height(12.dp))
                        Text("No se pudo cargar la ficha.", color = WW.text2, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Reintentar") { loadAll() }
                    }
                    if (d != null && d.facts.isNotEmpty()) {
                        SectionTitle("Ficha del modelo")
                        TileGrid(d.facts)
                    }
                }
            }

            // ---------- Versions: pick one ----------
            if (d != null && d.versions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Versiones", trailing = "${shown.size}")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (groups.size > 1) {
                                item { Pill("Todas", group == null) { group = null } }
                                items(groups.size) { i -> Pill(groups[i], group == groups[i]) { group = groups[i] } }
                            }
                            item { Pill(if (newestFirst) "Recientes ↓" else "Antiguas ↓", false) { newestFirst = !newestFirst } }
                        }
                        Spacer(Modifier.height(12.dp))
                        LaunchedEffect(group, newestFirst) { versionsRow.scrollToItem(0) }
                        LazyRow(state = versionsRow, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(shown) { _, v -> VersionCard(v, v == selected) { selected = v } }
                        }
                    }
                }
                selected?.let { v ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 6.dp)) {
                            SectionTitle(listOf(v.year, v.series).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Versión" })
                            val main = v.fields.filter { it.first != "Notas" && it.first != "Año" && it.first != "Serie" }
                            TileGrid(main)
                            v.fields.firstOrNull { it.first == "Notas" }?.let { (_, n) ->
                                Spacer(Modifier.height(8.dp))
                                SpecTile("Notas", n, Modifier.fillMaxWidth())
                            }
                            if (groups.size > 1) {
                                Spacer(Modifier.height(8.dp))
                                Text("Tipo: ${v.group}", color = WW.text3, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ---------- Story, card facts, notes ----------
            if (d != null && d.description.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Historia")
                        val long = d.description.length > 450
                        Text(if (!long || descOpen) d.description else d.description.take(450).substringBeforeLast(' ') + "…",
                            color = WW.text2, fontSize = 15.sp, lineHeight = 22.sp)
                        if (long) {
                            Text(if (descOpen) "LEER MENOS" else "LEER MÁS", color = WW.orange, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp).clickable { descOpen = !descOpen })
                        }
                    }
                }
            }
            if (d != null && d.cardFacts.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("En la tarjeta")
                        TileGrid(d.cardFacts)
                    }
                }
            }
            if (d != null && d.notes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Curiosidades")
                        d.notes.forEach { n ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Box(Modifier.padding(top = 7.dp).size(width = 10.dp, height = 6.dp).clip(Slant).background(WW.accent))
                                Spacer(Modifier.width(10.dp))
                                Text(n, color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }

            // ---------- All photos ----------
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Todas las fotos", trailing = if (photos.isEmpty()) null else "${photos.size}${if (cont != null) "+" else ""}")
            }
            itemsIndexed(photos) { i, p ->
                AsyncImage(
                    p.thumb, p.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(WW.showroom)
                        .clickable { AppState.viewer.value = AppState.Viewer(photos.toList(), i) }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    when {
                        photosLoading -> CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        photosError -> SecondaryButton("Reintentar fotos") { loadPhotos() }
                        cont != null -> SecondaryButton("Más fotos") { loadPhotos() }
                        photos.isEmpty() -> Text("Este modelo no tiene más fotos.", color = WW.text3, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    SecondaryButton("Ver en la Hot Wheels Wiki", Modifier.fillMaxWidth()) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Wiki.pageUrl(title)))) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Datos e imágenes de la Hot Wheels Wiki (Fandom), creada por la comunidad. Texto con licencia CC BY-SA; las fotos pertenecen a sus autores.",
                        color = WW.text3, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, style = androidx.compose.ui.text.TextStyle(brush = WW.accent), fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontSize = 26.sp)
        Text(label.uppercase(), color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Two tiles per row; long values take the whole row. */
@Composable
private fun TileGrid(items: List<Pair<String, String>>) {
    val rows = mutableListOf<List<Pair<String, String>>>()
    var pending: Pair<String, String>? = null
    for (it in items) {
        if (it.second.length > 28) { pending?.let { p -> rows.add(listOf(p)); pending = null }; rows.add(listOf(it)) }
        else if (pending == null) pending = it
        else { rows.add(listOf(pending!!, it)); pending = null }
    }
    pending?.let { rows.add(listOf(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { (k, v) -> SpecTile(k, v, Modifier.weight(1f)) }
                if (r.size == 1 && r[0].second.length <= 28) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VersionCard(v: Wiki.Version, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(128.dp).clip(shape).background(WW.surface)
            .then(if (selected) Modifier.border(2.dp, WW.accent, shape) else Modifier.border(1.dp, WW.line, shape))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(4.dp), contentAlignment = Alignment.Center) {
            if (v.photo != null) AsyncImage(Wiki.big(v.photo, 400), v.color, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text("SIN FOTO", color = WW.text3, fontSize = 11.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(v.year.ifEmpty { "—" }, color = if (selected) WW.amber else WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Text(v.color.ifEmpty { v.series }, color = WW.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (v.series.isNotEmpty()) Text(v.series, color = WW.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
