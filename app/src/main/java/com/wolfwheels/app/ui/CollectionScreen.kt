package com.wolfwheels.app.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Translator
import com.wolfwheels.app.data.Wiki
import java.io.File

/** Series sizes already counted (series page → number of cars), for the progress bars. */
private val seriesTotals = mutableStateMapOf<String, Int>()

@Composable
fun CollectionScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("have") }            // have · want · dup · series
    var garage by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(0) }                 // 0 recent · 1 year · 2 name
    var query by remember { mutableStateOf("") }
    var customOpen by remember { mutableStateOf(true) }

    val all = MyCollection.items.values.filter { !it.deleted }
    val haveItems = all.filter { it.status == "have" }
    val cars = haveItems.sumOf { it.qty }
    val models = haveItems.map { it.title }.distinct().size
    val want = all.count { it.status == "want" }
    val dups = haveItems.filter { it.qty > 1 }
    val spent = haveItems.mapNotNull { it.price }.sum()
    val garages = MyCollection.garages()

    val list = when (tab) { "want" -> all.filter { it.status == "want" }; "dup" -> dups; else -> haveItems }
        .filter { garage == null || it.garage == garage }
        .filter { q -> query.isBlank() || listOf(q.title, q.series, q.color, q.toy, q.year).any { it.contains(query.trim(), true) } }
        .let { l ->
            when (sort) {
                1 -> l.sortedWith(compareByDescending<MyCollection.Item> { it.year }.thenBy { it.title })
                2 -> l.sortedBy { it.title.lowercase() }
                else -> l.sortedByDescending { it.added }
            }
        }

    fun export() {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "wolf-wheels-mi-garaje.csv")
        f.writeText("﻿" + MyCollection.csv())   // BOM so Excel shows accents
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Mi garaje Wolf Wheels").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(i, "Exportar mi garaje")) }
    }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Mi garaje") {
            IconButton(onClick = { export() }) { Icon(Icons.Filled.Share, "Exportar a Excel (CSV)", tint = WW.text2) }
        }
        LazyVerticalGrid(
            columns = carColumns(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Gap, verticalArrangement = Gap, modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    // Garage summary
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WW.surface)
                            .border(1.5.dp, WW.accent, RoundedCornerShape(18.dp)).padding(16.dp)
                    ) {
                        Text("MI GARAJE", color = WW.text3, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$cars", style = androidx.compose.ui.text.TextStyle(brush = WW.accent), fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic, fontSize = 56.sp, lineHeight = 58.sp)
                            Text(if (cars == 1) " COCHE" else " COCHES", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("$models", "modelos", Modifier.weight(1f))
                            MiniStat("$want", "deseos", Modifier.weight(1f))
                            MiniStat("${dups.sumOf { it.qty - 1 }}", "repes", Modifier.weight(1f))
                            MiniStat("${haveItems.count { it.th > 0 }}", "TH/STH", Modifier.weight(1f))
                        }
                        if (spent > 0) Text("Gastado: ${"%.2f".format(spent).replace('.', ',')} €", color = WW.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        Spacer(Modifier.height(8.dp))
                        if (Account.loggedIn.value) {
                            val err = MyCollection.lastError.value
                            Text(
                                when { MyCollection.syncing.value -> "Sincronizando…"; err != null -> err; else -> "✓ Guardada en tu cuenta" },
                                color = if (err != null) WW.orange else WW.text3, fontSize = 12.sp
                            )
                        } else {
                            Text("Guardada solo en este móvil · Crea una cuenta ›", color = WW.amber, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { AppState.push(Route.Account) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Escanear", Modifier.weight(1f)) { AppState.push(Route.Scan) }
                        SecondaryButton("Exportar", Modifier.weight(1f)) { export() }
                    }
                    Spacer(Modifier.height(14.dp))
                    // View options, folded by default so the garage stays clean
                    val tabName = mapOf("have" to "Tengo", "want" to "Quiero", "dup" to "Repes", "series" to "Series")[tab] ?: "Tengo"
                    val summary = listOfNotNull(tabName, listOf("Recientes", "Por año", "A-Z")[sort].takeIf { tab != "series" },
                        garage, query.takeIf { it.isNotBlank() }?.let { "“$it”" }).joinToString(" · ")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(14.dp))) {
                        Row(
                            Modifier.fillMaxWidth().clickable { customOpen = !customOpen }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("PERSONALIZAR VISTA", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                                Text(summary, color = WW.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (customOpen) "▴" else "▾", color = WW.orange, fontSize = 20.sp)
                        }
                        if (customOpen) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface2).padding(4.dp)) {
                                listOf("have" to "Tengo", "want" to "Quiero", "dup" to "Repes", "series" to "Series").forEach { (k, l) ->
                                    val on = tab == k
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).then(if (on) Modifier.background(WW.accent) else Modifier)
                                            .clickable { tab = k }.padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(l.uppercase(), color = if (on) WW.onAccent else WW.text2, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                                            fontStyle = FontStyle.Italic, fontSize = 15.sp)
                                    }
                                }
                            }
                            if (tab != "series") {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Input(query, "Buscar en mi garaje", Modifier.weight(1f)) { query = it }
                                    Spacer(Modifier.padding(4.dp))
                                    SmallChip(listOf("Recientes", "Por año", "A-Z")[sort], true) { sort = (sort + 1) % 3 }
                                }
                                if (garages.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item { SmallChip("Todos los garajes", garage == null) { garage = null } }
                                        items(garages.size) { i -> SmallChip(garages[i], garage == garages[i]) { garage = if (garage == garages[i]) null else garages[i] } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tab == "series") {
                item(span = { GridItemSpan(maxLineSpan) }) { SeriesProgress(haveItems) }
            } else {
                if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when {
                            query.isNotBlank() || garage != null -> "Nada con estos filtros."
                            tab == "want" -> "Tu lista de deseos está vacía. Pulsa «La quiero» en la versión que buscas."
                            tab == "dup" -> "No tienes coches repetidos. Sube la cantidad en la ficha de la versión si tienes más de uno."
                            else -> "Aún no has marcado ningún coche. Abre un modelo, elige una versión y pulsa «La tengo», o escanea la tarjeta."
                        }, color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 20.dp)
                    )
                }
                if (sort == 1) {
                    list.groupBy { it.year.take(4).ifBlank { "Sin año" } }.forEach { (y, group) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "y$y") { Heading(y, Modifier.padding(top = 10.dp), size = 20, trailing = "${group.sumOf { it.qty }}") }
                        items(group, key = { it.key }) { item -> CollectionCard(item) }
                    }
                } else {
                    items(list, key = { it.key }) { item -> CollectionCard(item) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun CollectionCard(item: MyCollection.Item) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(16.dp))
            .clickable { AppState.open(item.title, item.key) }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom), contentAlignment = Alignment.Center) {
            when {
                item.myPhoto != null -> AsyncImage(File(item.myPhoto), item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                item.photo != null -> AsyncImage(Wiki.big(item.photo, 400), item.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                else -> Text("SIN FOTO", color = WW.text3, fontSize = 13.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
            }
            if (item.year.isNotEmpty()) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Slant).background(WW.accent).padding(horizontal = 9.dp, vertical = 1.dp)) {
                Text(item.year.take(4), color = WW.onAccent, fontSize = 12.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
            if (item.qty > 1) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(WW.bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("×${item.qty}", color = WW.amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (item.th > 0) Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(Slant).background(if (item.th == 2) WW.amber else WW.text).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(if (item.th == 2) "STH" else "TH", color = WW.onAccent, fontSize = 11.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(WW.accent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(item.title.uppercase(), color = WW.text, fontSize = 16.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
            Text((Translator.cached(item.color) ?: item.color).ifEmpty { item.series }, color = WW.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(
                item.garage.takeIf { it.isNotBlank() },
                when (item.cond) { "blister" -> "En blíster"; "suelto" -> "Suelto"; "dañado" -> "Dañado"; else -> null },
            ).joinToString(" · ")
            Text(extra.ifEmpty { item.series }, color = WW.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How much of each series I have: my cars of that series / cars in the series page of the wiki. */
@Composable
private fun SeriesProgress(have: List<MyCollection.Item>) {
    val groups = have.filter { it.seriesLink != null }.groupBy { it.seriesLink!! }.entries.sortedByDescending { it.value.size }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (groups.isEmpty()) Text("Marca coches con «La tengo» y aquí verás cuántos te faltan de cada serie.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
        groups.forEach { (link, items) ->
            LaunchedEffect(link) {
                if (!seriesTotals.containsKey(link)) runCatching { Wiki.listFrom(link).size }.getOrNull()?.let { seriesTotals[link] = it }
            }
            val mine = items.map { it.title }.distinct().size
            val total = seriesTotals[link]
            val name = items.first().series.ifBlank { link }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(12.dp))
                    .clickable { AppState.push(Route.Listing(name, link)) }.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = WW.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (total != null && total > 0) "$mine / $total" else "$mine", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic, fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                val frac = if (total != null && total > 0) (mine.toFloat() / total).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(WW.surface2)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(WW.accent))
                }
                Text(if (total == null) "Contando la serie…" else if (total > 0 && mine >= total) "¡Serie completa!" else if (total > 0) "Te faltan ${total - mine}" else "La wiki no tiene la lista de esta serie",
                    color = WW.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(WW.surface2).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(value, color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
        Text(label.uppercase(), color = WW.text3, fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) WW.text else WW.surface)
            .border(1.dp, if (selected) WW.text else WW.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    ) { Text(text, color = if (selected) WW.onAccent else WW.text2, fontSize = 13.sp, maxLines = 1) }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, style = androidx.compose.ui.text.TextStyle(brush = WW.accent), fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontSize = 28.sp)
        Text(label.uppercase(), color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
}
