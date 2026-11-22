package com.noirwheels.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirwheels.app.data.Prefs
import com.noirwheels.app.data.Wiki
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

    Column(Modifier.fillMaxSize().background(NW.bg)) {
        // Header
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Box(Modifier.align(Alignment.Center)) { Wordmark() }
                IconButton(onClick = { AppState.settings.value = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Box {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = NW.text2)
                        if (AppState.update.value != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(NW.text).align(Alignment.TopEnd))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Search box
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NW.surface)
                    .border(1.dp, NW.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = NW.text3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Busca un modelo: Bone Shaker, Skyline…", color = NW.text3, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = NW.text, fontSize = 15.sp, fontFamily = Inter),
                        cursorBrush = SolidColor(NW.text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            if (query.isNotBlank()) show(Source.Search(query.trim()))
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Filled.Close, "Borrar", tint = NW.text2,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).clickable { query = ""; focus.clearFocus() })
                }
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
            columns = GridCells.Adaptive(150.dp),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Gap,
            verticalArrangement = Gap,
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, color = NW.text, fontSize = 20.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cars.isNotEmpty()) Text("${cars.size}${if (finished) "" else "+"}", color = NW.text3, fontSize = 13.sp)
                }
            }
            itemsIndexed(cars, key = { _, c -> c.title }) { _, car -> CarCard(car) { AppState.open(car.title) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = NW.text2, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error!!, color = NW.text2, fontSize = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("Reintentar") { error = null; load(reset = cars.isEmpty()) }
                        }
                        cars.isEmpty() -> Text("Sin resultados. Prueba con el nombre en inglés.", color = NW.text2, fontSize = 14.sp)
                        finished -> Text("No hay más modelos", color = NW.text3, fontSize = 13.sp)
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
        Modifier.clip(RoundedCornerShape(14.dp)).background(NW.surface)
            .border(1.dp, NW.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(NW.surface2), contentAlignment = Alignment.Center) {
            if (car.thumb != null) {
                AsyncImage(model = car.thumb, contentDescription = car.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("Sin foto", color = NW.text3, fontSize = 12.sp)
            }
        }
        Text(car.title, color = NW.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
    }
}
