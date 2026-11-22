package com.noirwheels.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.noirwheels.app.data.Wiki
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(title: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Wiki.Detail?>(null) }
    var error by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<Wiki.Photo>() }
    var cont by remember { mutableStateOf<Map<String, String>?>(null) }
    var photosLoading by remember { mutableStateOf(true) }
    var photosError by remember { mutableStateOf(false) }

    fun loadPhotos() {
        photosLoading = true; photosError = false
        scope.launch {
            try {
                val p = Wiki.photos(title, cont)
                photos.addAll(p.items); cont = p.cont
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { photosError = true
            } finally { photosLoading = false }
        }
    }
    fun loadAll() {
        error = false
        scope.launch {
            try { detail = Wiki.detail(title) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = true }
        }
        if (photos.isEmpty()) loadPhotos()
    }
    LaunchedEffect(title) { loadAll() }

    Column(Modifier.fillMaxSize().background(NW.bg).blockTouches()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { AppState.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = NW.text) }
            Text(title, color = NW.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Main photo
            item(span = { GridItemSpan(maxLineSpan) }) {
                val d = detail
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)).background(NW.surface)
                        .clickable(enabled = photos.isNotEmpty()) { AppState.viewer.value = AppState.Viewer(photos.toList(), 0) },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        d?.hero != null -> AsyncImage(d.hero, title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        d == null && !error -> CircularProgressIndicator(color = NW.text2, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        else -> Text("Sin foto principal", color = NW.text3, fontSize = 13.sp)
                    }
                }
            }
            // Title + facts + intro
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 10.dp)) {
                    Text(title, color = NW.text, fontSize = 26.sp, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
                    val d = detail
                    if (error) {
                        Spacer(Modifier.height(12.dp))
                        Text("No se pudo cargar la ficha.", color = NW.text2, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Reintentar") { loadAll() }
                    }
                    if (d != null && d.facts.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NW.surface)
                                .border(1.dp, NW.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            d.facts.forEachIndexed { i, (k, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(factLabel(k), color = NW.text2, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                                    Text(v, color = NW.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                }
                                if (i < d.facts.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(NW.line))
                            }
                        }
                    }
                    if (d != null && d.intro.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(if (d.intro.length > 700) d.intro.take(700) + "…" else d.intro, color = NW.text2, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    SectionTitle(if (photos.isEmpty()) "Fotos y versiones" else "Fotos y versiones · ${photos.size}${if (cont != null) "+" else ""}")
                }
            }
            itemsIndexed(photos) { i, p ->
                AsyncImage(
                    p.thumb, p.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(NW.surface2)
                        .clickable { AppState.viewer.value = AppState.Viewer(photos.toList(), i) }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    when {
                        photosLoading -> CircularProgressIndicator(color = NW.text2, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        photosError -> SecondaryButton("Reintentar fotos") { loadPhotos() }
                        cont != null -> SecondaryButton("Más fotos") { loadPhotos() }
                        photos.isEmpty() -> Text("Este modelo no tiene más fotos.", color = NW.text3, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    SecondaryButton("Ver página completa en la wiki", Modifier.fillMaxWidth()) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Wiki.pageUrl(title)))) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Datos e imágenes de la Hot Wheels Wiki (Fandom), creada por la comunidad. Texto con licencia CC BY-SA; las fotos pertenecen a sus autores.",
                        color = NW.text3, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

/** The wiki is in English: translate the usual labels. */
private fun factLabel(k: String): String = when (k.trim().lowercase()) {
    "debut series" -> "Serie de debut"
    "produced" -> "Producido"
    "designer", "designers" -> "Diseñador"
    "number" -> "Número"
    "casting name" -> "Molde"
    "first release" -> "Primera edición"
    "year" -> "Año"
    "series" -> "Serie"
    "scale" -> "Escala"
    "color", "colour" -> "Color"
    else -> k
}
