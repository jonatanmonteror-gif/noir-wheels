package com.wolfwheels.app.ui

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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.offset
import com.wolfwheels.app.data.MyCollection
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Prefs
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the grid is showing: a search or one of the collections. */
private sealed class Source {
    data class Search(val q: String) : Source()
    data class Cat(val index: Int) : Source()
}

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val initial = remember {
        val last = Prefs.lastList
        if (last.startsWith("search:")) Source.Search(last.removePrefix("search:"))
        else Source.Cat(last.removePrefix("cat:").toIntOrNull()?.coerceIn(0, Wiki.collections.lastIndex) ?: 0)
    }
    var source by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf((initial as? Source.Search)?.q ?: "") }
    val cars = remember { mutableStateListOf<Wiki.Car>() }
    var cont by remember { mutableStateOf<Map<String, String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val grid = rememberLazyGridState()

    fun load(reset: Boolean) {
        if (!reset && (loading || finished)) return
        job?.cancel()
        if (reset) { cars.clear(); cont = null; finished = false; error = null; scope.launch { grid.scrollToItem(0) } }
        val src = source
        loading = true
        job = scope.launch {
            try {
                val page = when (src) {
                    is Source.Search -> Wiki.search(src.q, cont)
                    is Source.Cat -> Wiki.category(Wiki.collections[src.index].category, cont)
                }
                val seen = cars.map { it.title }.toHashSet()
                cars.addAll(page.items.filter { it.title !in seen })
                cont = page.cont
                finished = page.cont == null
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo conectar con la wiki. Revisa la conexión."
            } finally {
                loading = false
            }
        }
    }

    fun show(s: Source) {
        source = s
        Prefs.lastList = when (s) { is Source.Search -> "search:${s.q}"; is Source.Cat -> "cat:${s.index}" }
        load(reset = true)
    }

    LaunchedEffect(Unit) { load(reset = true) }

    // Search while typing (short pause so it doesn't fire on every letter)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2 || (source as? Source.Search)?.q == q) return@LaunchedEffect
        delay(500)
        show(Source.Search(q))
    }

    // Infinite scroll
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, cars.size) { if (nearEnd && cars.isNotEmpty() && error == null) load(reset = false) }

    Column(Modifier.fillMaxSize().background(WW.bg)) {
        // Header
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Wordmark() }
                IconButton(onClick = { AppState.push(Route.Collection) }) {
                    Box {
                        Icon(Icons.Filled.Star, contentDescription = "Mi colección", tint = WW.amber)
                        val n = MyCollection.items.values.count { !it.deleted && it.status == "have" }
                        if (n > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp).clip(RoundedCornerShape(50)).background(WW.orange).padding(horizontal = 4.dp)) {
                            Text("$n", color = WW.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = { AppState.push(Route.Settings) }) {
                    Box {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = WW.text2)
                        if (AppState.update.value != null) Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(WW.orange).align(Alignment.TopEnd))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Search box
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface)
                    .border(1.dp, WW.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = WW.orange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Busca un modelo: Bone Shaker, Skyline…", color = WW.text3, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = WW.text, fontSize = 15.sp, fontFamily = Inter),
                        cursorBrush = SolidColor(WW.orange),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            if (query.isNotBlank()) show(Source.Search(query.trim()))
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Filled.Close, "Borrar", tint = WW.text2,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).clickable { query = ""; focus.clearFocus() })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.accent)
                    .clickable { AppState.push(Route.Scan) }.padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(androidx.compose.ui.res.painterResource(com.wolfwheels.app.R.drawable.ic_scan), null, tint = WW.onAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("ESCANEAR TARJETA", color = WW.onAccent, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(Wiki.collections) { i, c ->
                Pill(c.label, selected = (source as? Source.Cat)?.index == i) {
                    query = ""; focus.clearFocus(); show(Source.Cat(i))
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        val title = when (val s = source) {
            is Source.Search -> "Resultados de “${s.q}”"
            is Source.Cat -> Wiki.collections[s.index].label
        }
        LazyVerticalGrid(
            columns = carColumns(),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Gap,
            verticalArrangement = Gap,
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Heading(title, Modifier.weight(1f).padding(top = 4.dp, bottom = 2.dp), size = 22,
                        trailing = if (cars.isNotEmpty()) "${cars.size}${if (finished) "" else "+"}" else null)
                    ColumnsToggle()
                }
            }
            itemsIndexed(cars, key = { _, c -> c.title }) { _, car -> CarCard(car) { AppState.open(car.title) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error!!, color = WW.text2, fontSize = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("Reintentar") { error = null; load(reset = cars.isEmpty()) }
                        }
                        cars.isEmpty() -> Text("Sin resultados. Prueba con el nombre en inglés.", color = WW.text2, fontSize = 14.sp)
                        finished -> Text("No hay más modelos", color = WW.text3, fontSize = 13.sp)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun CarCard(car: Wiki.Car, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(WW.surface)
            .border(1.dp, WW.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(6.dp), contentAlignment = Alignment.Center) {
            if (car.thumb != null) {
                AsyncImage(model = car.thumb, contentDescription = car.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("SIN FOTO", color = WW.text3, fontSize = 13.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(WW.accent))
        Text(car.title.uppercase(), color = WW.text, fontSize = 16.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic, maxLines = 2, lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
    }
}
