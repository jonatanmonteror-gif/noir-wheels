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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Toy numbers printed on Hot Wheels cards: HKG43, JBC18, J3247, W4240… */
private val toyRegex = Regex("\\b([A-Z]{3}[0-9]{2}|[A-Z]{2}[0-9]{3}|[A-Z][0-9]{4})\\b")

fun toyCodesIn(text: String): List<String> =
    toyRegex.findAll(text.uppercase()).map { it.value }.distinct().take(8).toList()

/** Scan the card: photo (or gallery, or typing) → toy number → the exact car and version. */
@Composable
fun ScanScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf("") }
    var codes by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Pair<String, List<Wiki.ToyMatch>>>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<File?>(null) }
    var baseCode by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf(false) }                 // scan card after card without leaving
    val added = remember { androidx.compose.runtime.mutableStateListOf<String>() }   // added in this session

    fun search(list: List<String>) {
        if (list.isEmpty()) { msg = "No he encontrado ningún número de juguete. Prueba con más luz o escríbelo."; return }
        codes = list; msg = null; results = null
        scope.launch {
            val out = mutableListOf<Pair<String, List<Wiki.ToyMatch>>>()
            for (c in list) {
                busy = "Buscando $c…"
                val m = runCatching { Wiki.findByToy(c) }.getOrDefault(emptyList())
                if (m.isNotEmpty()) out += c to m
                if (out.sumOf { it.second.size } >= 6) break
            }
            busy = null
            results = out
            if (out.isEmpty()) msg = "No encuentro ese número en la wiki (${list.joinToString(", ")}). Puede ser muy nuevo o estar mal leído."
            else if (!serial && out.size == 1 && out[0].second.size == 1) {
                val m = out[0].second[0]
                AppState.push(Route.Detail(m.title, selectKey = m.version.key(m.title)))
            }
        }
    }

    fun readImage(uri: Uri) {
        busy = "Leyendo la tarjeta…"; msg = null; results = null
        scope.launch {
            val text = runCatching {
                val img = InputImage.fromFilePath(ctx, uri)
                val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                suspendCancellableCoroutine<String> { cont ->
                    rec.process(img).addOnSuccessListener { cont.resume(it.text) }.addOnFailureListener { cont.resume("") }
                }
            }.getOrDefault("")
            busy = null
            search(toyCodesIn(text))
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = photo
        if (ok && f != null) readImage(Uri.fromFile(f))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readImage(it) } }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Escanear tarjeta")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Text("Haz una foto a la parte de atrás del blíster, donde viene el número de juguete (por ejemplo HKG43). La app encuentra el coche y la versión exacta.",
                color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface)
                .border(1.dp, if (serial) WW.orange else WW.line, RoundedCornerShape(12.dp)).clickable { serial = !serial }.padding(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("ESCANEO EN SERIE", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    Text("Varias tarjetas seguidas: añade cada una con un toque y pasa a la siguiente.", color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp)
                }
                androidx.compose.material3.Switch(checked = serial, onCheckedChange = { serial = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = WW.orange, checkedThumbColor = WW.text))
            }
            if (serial && added.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Añadidos ahora (${added.size}): ${added.takeLast(4).joinToString(", ")}${if (added.size > 4) "…" else ""}", color = WW.amber, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (serial && results != null) "Siguiente tarjeta" else "Hacer foto a la tarjeta", Modifier.fillMaxWidth(), enabled = busy == null) {
                val dir = File(ctx.cacheDir, "scan").apply { mkdirs() }
                val f = File(dir, "card.jpg").also { it.delete() }
                photo = f
                runCatching { camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Elegir una foto", Modifier.fillMaxWidth()) { if (busy == null) gallery.launch("image/*") }
            Spacer(Modifier.height(16.dp))
            Text("O ESCRIBE EL NÚMERO", color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(manual, "HKG43", Modifier.weight(1f), KeyboardType.Ascii) { manual = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Buscar") { if (busy == null && manual.length >= 4) search(listOf(manual)) }
            }

            // Loose car without card: AI recognition from a photo
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface).border(1.5.dp, WW.amber.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .clickable { AppState.push(Route.Identify) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("¿COCHE SUELTO, SIN TARJETA?", color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    Text("Hazle una foto y te digo qué modelo es", color = WW.amber, fontSize = 13.sp)
                }
                Text("›", color = WW.orange, fontSize = 26.sp)
            }

            // Base code of a loose car (no card): letter = year, two digits = week
            Spacer(Modifier.height(22.dp))
            Heading("Código de la base", size = 17)
            Spacer(Modifier.height(6.dp))
            Text("¿Coche sin blíster? Debajo lleva un código como «N32»: te digo cuándo se fabricó.", color = WW.text2, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Input(baseCode, "N32", Modifier.fillMaxWidth(), KeyboardType.Ascii) { baseCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(3) }
            if (baseCode.length == 3) {
                val d = com.wolfwheels.app.data.BaseCode.decode(baseCode)
                Spacer(Modifier.height(8.dp))
                if (d != null) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface).border(1.dp, WW.orange.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("FABRICADO EN ${d.year}", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                        Text("Semana ${d.week} (hacia ${com.wolfwheels.app.data.BaseCode.monthOf(d)}). Suele salir a la venta ese año o el siguiente; el año grabado con © es el del diseño, no el de fabricación.",
                            color = WW.text2, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                } else Text("Ese código no sigue el formato letra + 2 cifras (sin I, O ni Q).", color = WW.orange, fontSize = 13.sp)
            }

            busy?.let {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp)); Text(it, color = WW.text2, fontSize = 14.sp)
                }
            }
            if (codes.isNotEmpty() && busy == null) {
                Spacer(Modifier.height(14.dp))
                Text("Números leídos: ${codes.joinToString(" · ")}", color = WW.text3, fontSize = 12.sp)
            }
            msg?.let { Spacer(Modifier.height(14.dp)); Text(it, color = WW.orange, fontSize = 14.sp, lineHeight = 20.sp) }

            results?.forEach { (code, matches) ->
                SectionTitle("Nº $code", trailing = "${matches.size}")
                val have = MyCollection.haveToy(code)
                if (have.isNotEmpty()) {
                    Box(Modifier.clip(Slant).background(WW.accent).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Text("✓ YA LO TIENES${if (have.sumOf { it.qty } > 1) " (×${have.sumOf { it.qty }})" else ""}", color = WW.onAccent,
                            fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (matches.any { MyCollection.statusOf(it.version.key(it.title)) == "want" }) {
                    Box(Modifier.clip(Slant).background(WW.text).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Text("★ ESTÁ EN TU LISTA DE DESEOS", color = WW.onAccent, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                matches.forEach { m ->
                    MatchRow(m)
                    if (serial) {
                        val k = m.version.key(m.title)
                        SecondaryButton(if (MyCollection.statusOf(k) == "have") "+1 (ya tienes ${MyCollection.items[k]?.qty ?: 1})" else "+ La tengo",
                            Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            MyCollection.addOne(m.title, m.version, m.version.photo)
                            added += m.title
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MatchRow(m: Wiki.ToyMatch) {
    val v = m.version
    val st = MyCollection.statusOf(v.key(m.title))
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(WW.surface)
            .border(1.dp, WW.line, RoundedCornerShape(14.dp)).clickable { AppState.push(Route.Detail(m.title, selectKey = v.key(m.title))) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(86.dp, 64.dp).clip(RoundedCornerShape(10.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
            if (v.photo != null) AsyncImage(Wiki.big(v.photo, 300), m.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(v.year, v.colorEs).filter { it.isNotBlank() }.joinToString(" · "), color = WW.text2, fontSize = 13.sp, maxLines = 1)
            if (v.series.isNotBlank()) Text(v.series, color = WW.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (st != null) Text(if (st == "have") "✓" else "★", color = WW.amber, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
        if (v.th > 0) Text(if (v.th == 2) "STH" else "TH", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}
