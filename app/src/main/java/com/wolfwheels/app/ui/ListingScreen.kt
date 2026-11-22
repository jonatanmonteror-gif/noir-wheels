package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Wiki

/** Cars of a series, designer, wheel type, year… (the wiki page a data value links to). */
@Composable
fun ListingScreen(label: String, target: String) {
    var cars by remember { mutableStateOf<List<Wiki.ListCar>?>(null) }
    var error by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var year by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableIntStateOf(0) }   // 0 as in the wiki · 1 A-Z · 2 newest · 3 oldest
    LaunchedEffect(target, attempt) {
        error = false
        try { cars = Wiki.listFrom(target) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = true }
    }
    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(label)
        LazyVerticalGrid(
            columns = carColumns(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Gap, verticalArrangement = Gap, modifier = Modifier.fillMaxSize()
        ) {
            val yearOf = { c: Wiki.ListCar -> Regex("\\b(19[6-9]\\d|20[0-4]\\d)\\b").find(c.sub)?.value }
            val years = cars.orEmpty().mapNotNull(yearOf).distinct().sortedDescending()
            val list = cars?.let { l ->
                l.filter { query.isBlank() || it.title.contains(query.trim(), true) || it.sub.contains(query.trim(), true) }
                    .filter { year == null || yearOf(it) == year }
                    .let {
                        when (sort) {
                            1 -> it.sortedBy { c -> c.title.lowercase() }
                            2 -> it.sortedByDescending { c -> yearOf(c) ?: "" }
                            3 -> it.sortedBy { c -> yearOf(c) ?: "9999" }
                            else -> it
                        }
                    }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Heading(label, Modifier.weight(1f), size = 22, trailing = list?.size?.let { n -> if (n == cars?.size) "$n" else "$n de ${cars?.size}" })
                        ColumnsToggle()
                    }
                    if (!cars.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Input(query, "Filtrar por nombre", Modifier.weight(1f)) { query = it }
                            Spacer(Modifier.padding(4.dp))
                            SmallChip(listOf("Orden wiki", "A-Z", "Recientes", "Antiguos")[sort], true) { sort = (sort + 1) % 4 }
                        }
                        if (years.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                item { SmallChip("Todos los años", year == null) { year = null } }
                                items(years.size) { i -> SmallChip(years[i], year == years[i]) { year = if (year == years[i]) null else years[i] } }
                            }
                        }
                    }
                }
            }
            when {
                error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 30.dp)) {
                        Text("No se pudo cargar la lista.", color = WW.text2, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Reintentar") { attempt++ }
                    }
                }
                list == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (cars.isNullOrEmpty()) "Esta página de la wiki no tiene una lista de coches." else "Ningún coche con estos filtros.", color = WW.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp))
                }
                else -> items(list, key = { it.title }) { c -> ListCard(c) { AppState.open(c.title) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun ListCard(c: Wiki.ListCar, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(WW.surface)
            .border(1.dp, WW.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(6.dp), contentAlignment = Alignment.Center) {
            if (c.thumb != null) AsyncImage(Wiki.big(c.thumb, 400), c.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text("SIN FOTO", color = WW.text3, fontSize = 13.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(WW.accent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(c.title.uppercase(), color = WW.text, fontSize = 16.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
            if (c.sub.isNotEmpty()) Text(c.sub, color = WW.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
