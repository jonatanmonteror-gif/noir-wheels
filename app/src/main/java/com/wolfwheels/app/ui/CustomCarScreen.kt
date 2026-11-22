package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wolfwheels.app.data.MyCollection
import java.io.File

/** A car that is not in the wiki (customs, other brands…): created by hand, with my own photo and details. */
@Composable
fun CustomCarScreen(key: String?) {
    var currentKey by remember { mutableStateOf(key) }
    val item = currentKey?.let { MyCollection.items[it] }
    var name by remember { mutableStateOf(item?.title ?: "") }
    var brand by remember { mutableStateOf(item?.brand?.ifBlank { null } ?: "Hot Wheels") }
    var year by remember { mutableStateOf(item?.year ?: "") }
    var series by remember { mutableStateOf(item?.series ?: "") }
    var color by remember { mutableStateOf(item?.color ?: "") }
    var toy by remember { mutableStateOf(item?.toy ?: "") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar(if (item == null) "Añadir a mano" else item.title)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            if (item == null) {
                Text("Para coches que no están en la wiki: customs, otras marcas, ediciones raras… Luego le pones tu foto.", color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(14.dp))
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                    if (item.myPhoto != null) AsyncImage(File(item.myPhoto), item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("HAZ UNA FOTO ABAJO", color = WW.text3, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("NOMBRE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(name, "Ej.: Mi custom Skyline", Modifier.fillMaxWidth()) { name = it.take(60) }
            Spacer(Modifier.height(10.dp))
            Text("MARCA", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(brand, "Hot Wheels, Matchbox, Mini GT…", Modifier.fillMaxWidth()) { brand = it.take(40) }
            Spacer(Modifier.height(10.dp))
            Text("AÑO", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(year, "2026", Modifier.fillMaxWidth(), KeyboardType.Number) { year = it.filter { c -> c.isDigit() }.take(4) }
            Spacer(Modifier.height(10.dp))
            Text("SERIE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(series, "Opcional", Modifier.fillMaxWidth()) { series = it.take(60) }
            Spacer(Modifier.height(10.dp))
            Text("COLOR", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(color, "Opcional", Modifier.fillMaxWidth()) { color = it.take(40) }
            Spacer(Modifier.height(10.dp))
            Text("Nº DE JUGUETE", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Input(toy, "Opcional", Modifier.fillMaxWidth(), KeyboardType.Ascii) { toy = it.uppercase().take(12) }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (item == null) "Añadir a mi garaje" else if (saved) "Guardado ✓" else "Guardar cambios", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                if (item == null) currentKey = MyCollection.addCustom(name.trim(), brand.trim(), year, series.trim(), color.trim(), toy.trim())
                else MyCollection.update(item.key) { it.copy(title = name.trim(), brand = brand.trim(), year = year, series = series.trim(), color = color.trim(), toy = toy.trim()) }
                saved = true
            }
            item?.takeIf { !it.deleted }?.let {
                Spacer(Modifier.height(16.dp))
                CollectionEditor(it)
            }
            if (item?.deleted == true) {
                Spacer(Modifier.height(12.dp))
                Text("Este coche ya no está en tu garaje.", color = WW.text3, fontSize = 13.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
