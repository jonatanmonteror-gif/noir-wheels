package com.wolfwheels.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.Identify
import com.wolfwheels.app.data.Server
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Loose car without card: a photo and the AI suggests which casting it is. */
@Composable
fun IdentifyScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { File(ctx.cacheDir, "scan").apply { mkdirs() } }
    var shot by remember { mutableStateOf<File?>(null) }
    var stamp by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Identify.Candidate>?>(null) }

    fun analyse(f: File) {
        shot = f; stamp++; busy = true; msg = null; results = null
        scope.launch {
            try {
                val r = Identify.run(f)
                results = r
                if (r.isEmpty()) msg = "No he sabido reconocerlo. Prueba con el coche de lado, buena luz y fondo liso."
            } catch (e: Exception) {
                msg = if (e is IllegalArgumentException) "No he podido abrir esa foto." else Server.message(e)
            }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = File(dir, "loose.jpg")
        if (ok && f.exists()) analyse(f)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val f = File(dir, "loose_pick.jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }.isSuccess
            }
            if (ok) analyse(f) else msg = "No he podido abrir esa foto."
        }
    }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Reconocer por foto")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("¿Un coche suelto, sin tarjeta? Hazle una foto de lado, con buena luz, y te digo qué modelo es.",
                color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            if (!Account.loggedIn.value) {
                Text("Necesitas una cuenta para usar el reconocimiento por foto.", color = WW.amber, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Entrar", Modifier.fillMaxWidth()) { AppState.push(Route.Account) }
                return@Column
            }
            PrimaryButton(if (busy) "Analizando…" else "Hacer foto al coche", Modifier.fillMaxWidth(), enabled = !busy) {
                val f = File(dir, "loose.jpg").also { it.delete() }
                runCatching { camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Elegir una foto", Modifier.fillMaxWidth()) { if (!busy) gallery.launch("image/*") }

            shot?.let { f ->
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(WW.surface), contentAlignment = Alignment.Center) {
                    AsyncImage(coil.request.ImageRequest.Builder(ctx).data(f).memoryCacheKey("loose$stamp").diskCacheKey("loose$stamp").build(),
                        "Tu foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (busy) Box(Modifier.fillMaxSize().background(WW.bg.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
                    }
                }
            }
            msg?.let { Spacer(Modifier.height(12.dp)); Text(it, color = WW.orange, fontSize = 14.sp, lineHeight = 20.sp) }

            results?.takeIf { it.isNotEmpty() }?.let { list ->
                Spacer(Modifier.height(18.dp))
                Heading("Puede ser", size = 18)
                Spacer(Modifier.height(10.dp))
                list.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(WW.surface)
                            .border(1.dp, WW.line, RoundedCornerShape(16.dp))
                            .clickable { AppState.open(c.title ?: c.name) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(104.dp, 78.dp).clip(RoundedCornerShape(10.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                            if (c.thumb != null) AsyncImage(Wiki.big(c.thumb, 300), c.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text((c.title ?: c.name).uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                                fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                            Text(listOf(c.color, "${c.confidence}% seguro").filter { it.isNotBlank() }.joinToString(" · "), color = WW.amber, fontSize = 13.sp)
                            if (c.notes.isNotBlank()) Text(c.notes, color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Text("›", color = WW.orange, fontSize = 24.sp)
                    }
                }
                Text("Abre el que sea y elige la versión exacta por color y año. La IA puede fallar con modelos parecidos.",
                    color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
