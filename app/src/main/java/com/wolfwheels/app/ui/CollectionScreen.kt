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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Wiki

@Composable
fun CollectionScreen() {
    var tab by remember { mutableStateOf("have") }
    val have = MyCollection.items.values.count { !it.deleted && it.status == "have" }
    val want = MyCollection.items.values.count { !it.deleted && it.status == "want" }
    val list = MyCollection.visible(tab)
    val models = MyCollection.items.values.filter { !it.deleted && it.status == "have" }.map { it.title }.distinct().size

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Mi colección")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Gap, verticalArrangement = Gap, modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigStat("$have", "coches", Modifier.weight(1f))
                        BigStat("$models", "modelos", Modifier.weight(1f))
                        BigStat("$want", "deseos", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!Account.loggedIn.value) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface)
                                .border(1.dp, WW.orange.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(14.dp)
                        ) {
                            Text("Tu colección se guarda en este móvil. Crea una cuenta para no perderla y verla en todos tus aparatos.",
                                color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Entrar o crear cuenta", Modifier.fillMaxWidth()) { AppState.push(Route.Account) }
                        }
                    } else {
                        val err = MyCollection.lastError.value
                        Text(
                            when {
                                MyCollection.syncing.value -> "Sincronizando…"
                                err != null -> err
                                else -> "Guardada en tu cuenta (${Account.email.value})"
                            }, color = if (err != null) WW.orange else WW.text3, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Tengo · $have", tab == "have") { tab = "have" }
                        Pill("Quiero · $want", tab == "want") { tab = "want" }
                    }
                }
            }
            if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (tab == "have") "Aún no has marcado ningún coche. Abre un modelo, elige una versión y pulsa «La tengo»."
                    else "Tu lista de deseos está vacía. Pulsa «La quiero» en la versión que buscas.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 20.dp)
                )
            }
            items(list, key = { it.key }) { item ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(16.dp))
                        .clickable { AppState.open(item.title, item.key) }
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(WW.showroom).padding(6.dp), contentAlignment = Alignment.Center) {
                        if (item.photo != null) AsyncImage(Wiki.big(item.photo, 400), item.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        else Text("SIN FOTO", color = WW.text3, fontSize = 13.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                        if (item.year.isNotEmpty()) Box(Modifier.align(Alignment.BottomStart).clip(Slant).background(WW.accent).padding(horizontal = 9.dp, vertical = 1.dp)) {
                            Text(item.year.take(4), color = WW.onAccent, fontSize = 12.sp, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(WW.accent))
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Text(item.title.uppercase(), color = WW.text, fontSize = 16.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
                        Text((com.wolfwheels.app.data.Translator.cached(item.color) ?: item.color).ifEmpty { item.series }, color = WW.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.series.isNotEmpty()) Text(item.series, color = WW.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
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
