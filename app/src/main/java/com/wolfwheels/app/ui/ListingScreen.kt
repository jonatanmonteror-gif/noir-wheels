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
    LaunchedEffect(target, attempt) {
        error = false
        try { cars = Wiki.listFrom(target) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = true }
    }
    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(label)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Gap, verticalArrangement = Gap, modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Heading(label, size = 22, trailing = cars?.size?.toString())
            }
            val list = cars
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
                    Text("Esta página de la wiki no tiene una lista de coches.", color = WW.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp))
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
