package com.wolfwheels.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Prefs
import kotlinx.coroutines.delay
import java.io.File

/** The details of a car I have: quantity, condition, price, garage, notes and my own photo. */
@Composable
fun CollectionEditor(item: MyCollection.Item) {
    val ctx = LocalContext.current
    val key = item.key
    var notes by remember(key) { mutableStateOf(item.notes) }
    var price by remember(key) { mutableStateOf(item.price?.let { "%.2f".format(it).replace('.', ',') } ?: "") }
    var newGarage by remember(key) { mutableStateOf("") }
    var addingGarage by remember(key) { mutableStateOf(false) }
    var confirmRemove by remember(key) { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }

    // Save notes and price a moment after typing stops
    LaunchedEffect(notes, price) {
        delay(700)
        val p = price.replace(',', '.').trim().toDoubleOrNull()
        val cur = MyCollection.items[key] ?: return@LaunchedEffect
        if (cur.notes != notes || cur.price != p) MyCollection.update(key) { it.copy(notes = notes, price = p) }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null && f.length() > 0) {
            item.myPhoto?.let { runCatching { File(it).delete() } }
            MyCollection.update(key) { it.copy(myPhoto = f.absolutePath) }
        } else f?.delete()
    }

    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(WW.surface).border(1.dp, WW.orange.copy(alpha = 0.45f), shape).padding(14.dp)
    ) {
        Heading("En mi garaje", size = 17)
        Spacer(Modifier.height(12.dp))

        // Quantity (repeated cars, for swapping)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CANTIDAD", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StepButton("−") { if (item.qty > 1) MyCollection.update(key) { it.copy(qty = it.qty - 1) } }
            Text("${item.qty}", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp))
            StepButton("+") { MyCollection.update(key) { it.copy(qty = it.qty + 1) } }
        }
        if (item.qty > 1) Text("Tienes ${item.qty - 1} repetido${if (item.qty > 2) "s" else ""} para cambiar.", color = WW.amber, fontSize = 13.sp)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PARA CAMBIAR", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                Text("Sale en tu lista de cambios y en tu garaje público.", color = WW.text3, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(checked = item.trade, onCheckedChange = { on -> MyCollection.update(key) { it.copy(trade = on) } },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = WW.orange, checkedThumbColor = WW.text))
        }

        Label("Conservación")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("blister" to "En blíster", "suelto" to "Suelto", "dañado" to "Dañado").forEach { (v, l) ->
                Pill(l, item.cond == v) { MyCollection.update(key) { it.copy(cond = if (it.cond == v) "" else v) } }
            }
        }

        Label("Garaje")
        val garages = MyCollection.garages()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Pill("Sin garaje", item.garage.isBlank()) { MyCollection.update(key) { it.copy(garage = "") } } }
            items(garages.size) { i -> Pill(garages[i], item.garage == garages[i]) { MyCollection.update(key) { it.copy(garage = garages[i]) } } }
            item { Pill("+ Nuevo", false) { addingGarage = true } }
        }
        if (addingGarage) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(newGarage, "Nombre del garaje", Modifier.weight(1f)) { newGarage = it }
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Crear") {
                    val n = newGarage.trim()
                    if (n.isNotEmpty()) {
                        Prefs.garages = (Prefs.garages + n).distinct()
                        MyCollection.update(key) { it.copy(garage = n) }
                    }
                    newGarage = ""; addingGarage = false
                }
            }
        }

        Label("Precio pagado (€)")
        Input(price, "0,00", Modifier.fillMaxWidth(), KeyboardType.Decimal) { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }

        Label("Notas")
        Input(notes, "Dónde lo encontré, a quién se lo cambié…", Modifier.fillMaxWidth(), singleLine = false) { notes = it }

        Label("Mi foto")
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.myPhoto?.let { path ->
                AsyncImage(File(path), "Mi foto", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(WW.showroom))
                Spacer(Modifier.width(10.dp))
            }
            SecondaryButton(if (item.myPhoto == null) "Hacer foto" else "Cambiar foto") {
                val dir = File(ctx.filesDir, "myphotos").apply { mkdirs() }
                val f = File(dir, "${System.currentTimeMillis()}.jpg")
                pendingPhoto = f
                val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                runCatching { camera.launch(uri) }
            }
        }
        Text("Tu foto se guarda en este móvil.", color = WW.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(14.dp))
        if (!confirmRemove) {
            Text("QUITAR DE MI GARAJE", color = WW.text3, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                fontSize = 14.sp, modifier = Modifier.clickable { confirmRemove = true }.padding(vertical = 4.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("¿Seguro?", color = WW.text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                SecondaryButton("No") { confirmRemove = false }
                PrimaryButton("Quitar") {
                    AppState.undo.value = "Quitado de Mi garaje" to item
                    MyCollection.update(key) { it.copy(deleted = true) }
                }
            }
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t.uppercase(), color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun StepButton(t: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(WW.surface2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, color = WW.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Input(value: String, hint: String, modifier: Modifier, type: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(WW.surface2).border(1.dp, WW.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = WW.text3, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = WW.text, fontSize = 15.sp, fontFamily = Inter),
            cursorBrush = SolidColor(WW.orange), keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
