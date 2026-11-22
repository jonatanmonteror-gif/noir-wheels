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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Wiki
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Server
import com.wolfwheels.app.data.Translator
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(title: String, selectKey: String? = null, selectToy: String? = null) {
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
    var colorFilter by remember { mutableStateOf<String?>(null) }
    var onlyTh by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Wiki.Version?>(null) }
    var descOpen by remember { mutableStateOf(false) }
    var allPhotos by remember { mutableStateOf(false) }
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
                val raw = Wiki.detail(title)
                // Spanish first: the texts are few and quick; the versions' data follow in the background
                Translator.awaitReady(4000)
                val d = Translator.textsOf(raw)
                detail = d
                com.wolfwheels.app.data.Recents.add(title, d.hero ?: d.versions.lastOrNull { it.photo != null }?.photo)
                selected = d.versions.firstOrNull { selectKey != null && it.key(title) == selectKey }
                    ?: d.versions.firstOrNull { selectToy != null && it.toy.uppercase().contains(selectToy.uppercase()) }
                    ?: d.versions.lastOrNull { it.photo != null } ?: d.versions.lastOrNull()
                val withVersions = Translator.versionsOf(d)
                detail = withVersions
                selected = selected?.let { s -> withVersions.versions.firstOrNull { it.key(title) == s.key(title) } } ?: selected
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (detail == null) error = true }
        }
        if (photos.isEmpty()) loadPhotos()
    }
    LaunchedEffect(title) { loadAll() }

    val d = detail
    // Photos for versions the wiki marks as "Image not available": first another wiki photo, then eBay (via the server)
    val backup = remember { mutableStateMapOf<String, String>() }
    fun photoOf(v: Wiki.Version): String? = v.photo ?: backup[v.key(title)] ?: wikiMatch(v, photos)
    val groups = d?.versions?.map { it.group }?.distinct().orEmpty()
    val colors = d?.versions.orEmpty().map { it.colorEs }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }.take(14)
    val thCount = d?.versions.orEmpty().count { it.th > 0 }
    val shown = d?.versions.orEmpty()
        .filter { group == null || it.group == group }
        .filter { colorFilter == null || it.colorEs == colorFilter }
        .filter { !onlyTh || it.th > 0 }
        .let { if (newestFirst) it.asReversed() else it }
    val versionPhotos = shown.mapNotNull { v ->
        photoOf(v)?.let { p -> Wiki.Photo(p, p, listOf(v.year, v.series, v.colorEs).filter { s -> s.isNotEmpty() }.joinToString(" · ")) }
    }
    val years = d?.versions.orEmpty().mapNotNull { it.year.take(4).toIntOrNull() }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(title) {
            IconButton(onClick = {
                scope.launch { runCatching { ShareCard.share(ctx, title, selected, selected?.let { photoOf(it) } ?: detail?.hero) } }
            }) { Icon(androidx.compose.material.icons.Icons.Filled.Share, "Compartir como imagen", tint = WW.text2) }
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
                if (sel != null && photoOf(sel) == null) LaunchedEffect(sel) {
                    Server.backupImage(ebayQuery(title, sel))?.let { backup[sel.key(title)] = it }
                }
                val heroUrl = sel?.let { photoOf(it) }?.let { Wiki.big(it, 1000) } ?: d?.hero
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.showroom)
                        .clickable(enabled = versionPhotos.isNotEmpty() || photos.isNotEmpty()) {
                            val i = versionPhotos.indexOfFirst { it.thumb == sel?.let { v -> photoOf(v) } }
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
                        val have = MyCollection.countFor(title)
                        if (have > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text("Tienes $have ${if (have == 1) "versión" else "versiones"} de este modelo", color = WW.amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
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
                            if (thCount > 0) item { Pill("Solo TH / STH · $thCount", onlyTh) { onlyTh = !onlyTh } }
                        }
                        if (colors.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { ColorChip("Todos los colores", colorFilter == null) { colorFilter = null } }
                                items(colors.size) { i -> ColorChip(colors[i], colorFilter == colors[i]) { colorFilter = if (colorFilter == colors[i]) null else colors[i] } }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LaunchedEffect(group, newestFirst, colorFilter, onlyTh) { versionsRow.scrollToItem(0) }
                        if (shown.isEmpty()) Text("Ninguna versión con estos filtros.", color = WW.text2, fontSize = 14.sp)
                        LazyRow(state = versionsRow, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(shown) { _, v ->
                                val k = v.key(title)
                                if (photoOf(v) == null) LaunchedEffect(k) { Server.backupImage(ebayQuery(title, v))?.let { backup[k] = it } }
                                VersionCard(v, photoOf(v), MyCollection.statusOf(k), v == selected) { selected = v }
                            }
                        }
                    }
                }
                selected?.let { v ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 6.dp)) {
                            SectionTitle(listOf(v.year, v.series).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Versión" })
                            if (v.th > 0) {
                                ThBadge(v.th)
                                Spacer(Modifier.height(10.dp))
                            }
                            val st = MyCollection.statusOf(v.key(title))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToggleButton(if (st == "have") "✓ La tengo" else "La tengo", st == "have", Modifier.weight(1f)) {
                                    if (st != "have") MyCollection.set(title, v, photoOf(v), "have")
                                }
                                ToggleButton(if (st == "want") "★ La quiero" else "La quiero", st == "want", Modifier.weight(1f)) {
                                    MyCollection.set(title, v, photoOf(v), if (st == "want") null else "want")
                                }
                            }
                            MyCollection.items[v.key(title)]?.takeIf { !it.deleted && it.status == "have" }?.let { item ->
                                Spacer(Modifier.height(10.dp))
                                CollectionEditor(item)
                            }
                            Spacer(Modifier.height(10.dp))
                            val main = v.fields.filter { it.label != "Notas" && it.label != "Año" }
                            TileGrid(main)
                            v.fields.firstOrNull { it.label == "Notas" }?.let { n ->
                                Spacer(Modifier.height(8.dp))
                                SpecTile("Notas", n.value, Modifier.fillMaxWidth())
                            }
                            if (v.photo == null && photoOf(v) != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Foto aproximada: la wiki no tiene foto de esta versión.", color = WW.text3, fontSize = 12.sp)
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
            itemsIndexed(if (allPhotos) photos else photos.take(15)) { i, p ->
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
                        !allPhotos && (photos.size > 15 || cont != null) ->
                            PrimaryButton("Ver todas las fotos (${photos.size}${if (cont != null) "+" else ""})", Modifier.fillMaxWidth()) { allPhotos = true }
                        cont != null -> SecondaryButton("Cargar más fotos") { loadPhotos() }
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

/** Two tiles per row; long values take the whole row. Values with a wiki link open the list of cars. */
@Composable
private fun TileGrid(items: List<Wiki.Field>) {
    val rows = mutableListOf<List<Wiki.Field>>()
    var pending: Wiki.Field? = null
    for (f in items) {
        if (f.value.length > 28) { pending?.let { p -> rows.add(listOf(p)); pending = null }; rows.add(listOf(f)) }
        else if (pending == null) pending = f
        else { rows.add(listOf(pending!!, f)); pending = null }
    }
    pending?.let { rows.add(listOf(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { f ->
                    val click: (() -> Unit)? = f.link?.let { link -> { AppState.push(Route.Listing(f.value, link)) } }
                    SpecTile(f.label, f.value, Modifier.weight(1f), click)
                }
                if (r.size == 1 && r[0].value.length <= 28) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColorChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) WW.text else WW.surface)
            .border(1.dp, if (selected) WW.text else WW.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = if (selected) WW.onAccent else WW.text2, fontSize = 13.sp) }
}

@Composable
fun ThBadge(th: Int) {
    Box(Modifier.clip(Slant).background(if (th == 2) WW.accent else androidx.compose.ui.graphics.SolidColor(WW.text)).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(if (th == 2) "SUPER TREASURE HUNT" else "TREASURE HUNT", color = WW.onAccent, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun ToggleButton(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // Small "pop" when it turns on
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(on) {
        if (first) { first = false; return@LaunchedEffect }
        if (on) { scale.animateTo(1.07f, androidx.compose.animation.core.tween(110)); scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f)) }
    }
    val border by androidx.compose.animation.animateColorAsState(if (on) WW.orange else WW.line, label = "border")
    Box(
        modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(shape)
            .then(if (on) Modifier.background(WW.accent) else Modifier.background(WW.surface2).border(1.dp, border, shape))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = if (on) WW.onAccent else WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontSize = 16.sp)
    }
}

/** Another wiki photo that clearly belongs to this version (toy number, or year + colour in the file name). */
private fun wikiMatch(v: Wiki.Version, photos: List<Wiki.Photo>): String? {
    if (photos.isEmpty()) return null
    val toy = v.toy.trim()
    if (toy.length >= 4) photos.firstOrNull { it.name.contains(toy, ignoreCase = true) }?.let { return it.thumb }
    val year = v.year.take(4)
    val color = v.color.lowercase().split(' ', '/', '-').lastOrNull { it.length >= 3 } ?: return null
    if (year.length != 4) return null
    return photos.firstOrNull { val n = it.name.lowercase(); n.contains(year) && n.contains(color) }?.thumb
}

private fun ebayQuery(title: String, v: Wiki.Version) =
    listOf("Hot Wheels", title, v.year.take(4), v.color, v.series.take(30)).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun VersionCard(v: Wiki.Version, photo: String?, status: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(128.dp).clip(shape).background(WW.surface)
            .then(if (selected) Modifier.border(2.dp, WW.accent, shape) else Modifier.border(1.dp, WW.line, shape))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(4.dp), contentAlignment = Alignment.Center) {
            if (photo != null) AsyncImage(Wiki.big(photo, 400), v.color, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text("SIN FOTO", color = WW.text3, fontSize = 11.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
            if (v.th > 0) Box(Modifier.align(Alignment.TopStart).clip(Slant).background(if (v.th == 2) WW.amber else WW.text).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(if (v.th == 2) "STH" else "TH", color = WW.onAccent, fontSize = 11.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
            if (status != null) Box(Modifier.align(Alignment.TopEnd).clip(Slant).background(WW.accent).padding(horizontal = 8.dp, vertical = 1.dp)) {
                Text(if (status == "have") "✓ TENGO" else "★ QUIERO", color = WW.onAccent, fontSize = 11.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
        }
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(v.year.ifEmpty { "—" }, color = if (selected) WW.amber else WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic, fontSize = 17.sp)
            Text(v.colorEs.ifEmpty { v.series }, color = WW.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (v.series.isNotEmpty()) Text(v.series, color = WW.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
