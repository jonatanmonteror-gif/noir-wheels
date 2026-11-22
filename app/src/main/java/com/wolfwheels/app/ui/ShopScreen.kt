package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Translator
import com.wolfwheels.app.data.Wiki

/** Shop mode: big text, my wish list and the scanner at hand. The screen stays on. */
@Composable
fun ShopScreen() {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val wish = MyCollection.items.values.filter { !it.deleted && it.status == "want" }.sortedBy { it.title }
    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Modo tienda")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WW.accent)
                        .clickable { AppState.push(Route.Scan) }.padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ESCANEAR", color = WW.onAccent, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 34.sp)
                }
                Spacer(Modifier.height(18.dp))
                Heading("Busco", size = 24, trailing = "${wish.size}")
                Spacer(Modifier.height(10.dp))
                if (wish.isEmpty()) Text("Tu lista de deseos está vacía. Pulsa «La quiero» en las versiones que buscas.", color = WW.text2, fontSize = 17.sp, lineHeight = 24.sp)
            }
            items(wish, key = { it.key }) { w ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(WW.surface)
                        .border(1.dp, WW.line, RoundedCornerShape(16.dp)).clickable { if (w.custom) AppState.push(Route.CustomCar(w.key)) else AppState.open(w.title, w.key) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(120.dp, 90.dp).clip(RoundedCornerShape(12.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                        if (w.photo != null) AsyncImage(Wiki.big(w.photo, 300), w.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 22.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                        Text(listOf(w.year, Translator.cached(w.color) ?: w.color).filter { it.isNotBlank() }.joinToString(" · "), color = WW.text2, fontSize = 16.sp)
                        if (w.toy.isNotBlank()) Text("Nº ${w.toy}", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        if (w.th > 0) Text(if (w.th == 2) "SUPER TREASURE HUNT" else "TREASURE HUNT", color = WW.orange, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
                PrimaryButton("¡Lo tengo!", Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    AppState.undo.value = "Pasado a Mi garaje" to w
                    MyCollection.update(w.key) { it.copy(status = "have", added = System.currentTimeMillis()) }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}
