package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Recents
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.launch
import java.io.File

/** New castings of the year, loaded once per session. */
private val newModels = mutableStateOf<List<Wiki.ListCar>?>(null)

@Composable
fun HomeExtras() {
    val scope = rememberCoroutineScope()
    var randomBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (newModels.value == null) newModels.value = runCatching { Wiki.listFrom("2026 New Models").filter { it.thumb != null }.take(20) }.getOrDefault(emptyList())
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        // My garage at a glance
        val have = MyCollection.items.values.filter { !it.deleted && it.status == "have" }
        val want = MyCollection.items.values.count { !it.deleted && it.status == "want" }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WW.surface)
                .border(1.5.dp, WW.accent, RoundedCornerShape(18.dp)).clickable { AppState.push(Route.Collection) }.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MI GARAJE", color = WW.text3, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (have.isEmpty()) "Empieza tu colección" else "${have.sumOf { it.qty }} coches · ${have.map { it.title }.distinct().size} modelos",
                        color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 22.sp)
                }
                Text("›", color = WW.orange, fontSize = 28.sp)
            }
            val last = have.sortedByDescending { it.added }.take(5)
            if (last.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    last.forEach { item ->
                        Box(Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)).background(WW.showroom)) {
                            when {
                                item.myPhoto != null -> AsyncImage(File(item.myPhoto), item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                item.photo != null -> AsyncImage(Wiki.big(item.photo, 200), item.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                            }
                        }
                    }
                    repeat(5 - last.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("Modo tienda", if (want > 0) "$want en tu lista" else "Tus «La quiero»", Modifier.weight(1f)) {
                AppState.push(Route.Shop)
            }
            QuickButton("Coche al azar", if (randomBusy) "Buscando…" else "Descubre uno", Modifier.weight(1f)) {
                if (!randomBusy) {
                    randomBusy = true
                    scope.launch { runCatching { Wiki.randomCar() }.getOrNull()?.let { AppState.open(it) }; randomBusy = false }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface).border(1.dp, WW.amber.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable { AppState.push(Route.Matches) }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("¿QUIÉN TIENE LO QUE BUSCO?", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 1)
                Text("Cruza tu lista con otros garajes", color = WW.amber, fontSize = 12.sp, maxLines = 1)
            }
            Text("›", color = WW.orange, fontSize = 24.sp)
        }

        // Recently viewed
        if (Recents.items.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Heading("Vistos recientemente", size = 17)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Recents.items.size) { i -> MiniCar(Recents.items[i].title, Recents.items[i].thumb) }
            }
        }

        // New castings of the year
        Spacer(Modifier.height(16.dp))
        Heading("Nuevos modelos 2026", size = 17)
        Spacer(Modifier.height(8.dp))
        val nm = newModels.value
        when {
            nm == null -> Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            nm.isEmpty() -> Text("No se pudieron cargar ahora.", color = WW.text3, fontSize = 13.sp)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nm.size) { i -> MiniCar(nm[i].title, nm[i].thumb) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuickButton(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 1)
        Text(sub, color = WW.amber, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MiniCar(title: String, thumb: String?) {
    Column(
        Modifier.width(128.dp).clip(RoundedCornerShape(12.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(12.dp))
            .clickable { AppState.open(title) }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(4.dp), contentAlignment = Alignment.Center) {
            if (thumb != null) AsyncImage(Wiki.big(thumb, 300), title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}
