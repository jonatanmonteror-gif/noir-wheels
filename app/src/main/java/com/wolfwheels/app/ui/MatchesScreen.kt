package com.wolfwheels.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.Matches
import com.wolfwheels.app.data.Server
import com.wolfwheels.app.data.Translator
import com.wolfwheels.app.data.Wiki
import kotlinx.coroutines.launch

/** ¿Quién tiene lo que busco? · and who is looking for what I trade. */
@Composable
fun MatchesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Matches.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var writeTo by remember { mutableStateOf<Pair<Matches.Person, String>?>(null) }

    LaunchedEffect(reload, Account.loggedIn.value) {
        if (!Account.loggedIn.value) return@LaunchedEffect
        error = null
        try { result = Matches.load() } catch (e: Exception) { error = Server.message(e) }
    }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Quién lo tiene")
        if (!Account.loggedIn.value) {
            Notice("Entra en tu cuenta", "Para cruzar tu lista con los garajes de otros coleccionistas necesitas una cuenta.", "Entrar") { AppState.push(Route.Account) }
            return@Column
        }
        val r = result
        when {
            error != null && r == null -> Notice("Sin conexión", error!!, "Reintentar") { reload++ }
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WW.orange, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text("Cruzamos tus «La quiero» con los garajes públicos de ${r.garages} coleccionista${if (r.garages == 1) "" else "s"}.",
                        color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                    if (!r.isPublic) {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface)
                                .border(1.5.dp, WW.amber, RoundedCornerShape(14.dp)).clickable { AppState.push(Route.Account) }.padding(14.dp)
                        ) {
                            Text("HAZ PÚBLICO TU GARAJE", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                            Text("Así los demás también te encuentran cuando buscan lo que tú tienes para cambiar. Se activa en tu cuenta  ›",
                                color = WW.text2, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface2).padding(4.dp)) {
                        listOf("Tienen lo que buscas" to r.want.size, "Buscan lo que cambias" to r.trade.size).forEachIndexed { i, (l, n) ->
                            val on = tab == i
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).then(if (on) Modifier.background(WW.accent) else Modifier)
                                    .clickable { tab = i }.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$l · $n", color = if (on) WW.onAccent else WW.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val rows = if (tab == 0) r.want else r.trade
                    if (rows.isEmpty()) {
                        val msg = when {
                            tab == 0 && r.wants == 0 -> "Aún no tienes nada en tu lista. Pulsa «La quiero» en las versiones que buscas y aquí verás quién las tiene."
                            tab == 0 -> "De momento nadie tiene lo que buscas. Cuantos más coleccionistas hagan público su garaje, más cruces saldrán."
                            r.trades == 0 -> "Marca coches «Para cambiar» en Mi garaje (o ten repes) y aquí verás quién los busca."
                            else -> "De momento nadie busca lo que tienes para cambiar."
                        }
                        Text(msg, color = WW.text3, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 20.dp))
                    }
                }
                items(if (tab == 0) r.want else r.trade, key = { "$tab|${it.car.key}" }) { row ->
                    MatchCard(row, tab == 0,
                        onGarage = { p -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Matches.garageUrl(p.slug)))) } },
                        onWrite = { p -> writeTo = p to row.car.title })
                }
                item { Spacer(Modifier.height(16.dp)); Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }

    writeTo?.let { (p, title) ->
        WriteDialog(p, title, onClose = { writeTo = null }) { msg, done ->
            scope.launch {
                try { Matches.contact(p.slug, title, msg); done(null) } catch (e: Exception) { done(Server.message(e)) }
            }
        }
    }
}

@Composable
private fun MatchCard(row: Matches.Row, wanted: Boolean, onGarage: (Matches.Person) -> Unit, onWrite: (Matches.Person) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).animateContentSize().clip(RoundedCornerShape(16.dp)).background(WW.surface)
            .border(1.dp, WW.line, RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable { AppState.open(row.car.title, row.car.key) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(10.dp)).background(WW.showroom), contentAlignment = Alignment.Center) {
                if (row.car.photo != null) AsyncImage(Wiki.big(row.car.photo, 300), row.car.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (wanted) "BUSCAS" else "TIENES PARA CAMBIAR", color = WW.text3, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Text(row.car.title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                    fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Text(listOf(row.car.year, Translator.cached(row.car.color) ?: row.car.color).filter { it.isNotBlank() }.joinToString(" · "),
                    color = WW.text2, fontSize = 13.sp, maxLines = 1)
            }
        }
        row.people.forEach { p ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(WW.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = WW.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (label, color) = when (p.kind) {
                        "trade" -> "La tiene para cambiar" to WW.amber
                        "have" -> "La tiene en su garaje" to WW.text2
                        "exact" -> "Busca justo esta versión" to WW.amber
                        "model" -> if (wanted) (("Tiene otra versión" + listOf(p.year, Translator.cached(p.color) ?: p.color).filter { it.isNotBlank() }
                            .joinToString(" · ").let { if (it.isBlank()) "" else " ($it)" }) + if (p.trade) " · para cambiar" else "") to WW.text2
                            else "Busca este modelo" to WW.text2
                        else -> "" to WW.text2
                    }
                    Text(label, color = color, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(8.dp))
                SmallAction("Garaje") { onGarage(p) }
                Spacer(Modifier.width(6.dp))
                SmallAction("Escribir", accent = true) { onWrite(p) }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (accent) WW.accent else WW.surface2)
            .then(if (accent) Modifier else Modifier.border(1.dp, WW.line, RoundedCornerShape(10.dp)))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (accent) WW.onAccent else WW.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(title: String, text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Heading(title)
        Spacer(Modifier.height(8.dp))
        Text(text, color = WW.text2, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(button, Modifier.fillMaxWidth(), onClick = onClick)
    }
}

/** Short message to the owner of a garage. The server emails it and the answer comes back to my email. */
@Composable
private fun WriteDialog(p: Matches.Person, title: String, onClose: () -> Unit, send: (String, (String?) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("¡Hola ${p.name}! He visto en Wolf Wheels que tienes el $title. ¿Te interesaría cambiarlo? Mira mi lista por si hay algo que te guste.") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Heading(if (sent) "Enviado" else "Escribir a ${p.name}", size = 18)
            Spacer(Modifier.height(10.dp))
            if (sent) {
                Text("Le ha llegado un correo con tu mensaje. Cuando conteste, la respuesta te llegará a ${Account.email.value}.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Vale", Modifier.fillMaxWidth(), onClick = onClose)
                return@Column
            }
            BasicTextField(
                value = text, onValueChange = { if (it.length <= 1500) text = it },
                textStyle = TextStyle(color = WW.text, fontSize = 15.sp, fontFamily = Inter, lineHeight = 21.sp),
                cursorBrush = SolidColor(WW.orange),
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp).clip(RoundedCornerShape(12.dp)).background(WW.bg)
                    .border(1.dp, WW.line, RoundedCornerShape(12.dp)).padding(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Le llegará por correo. Si contesta, te escribirá a tu email (${Account.email.value}).", color = WW.text3, fontSize = 12.sp, lineHeight = 16.sp)
            msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = WW.orange, fontSize = 13.sp) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancelar", Modifier.weight(1f)) { if (!busy) onClose() }
                PrimaryButton(if (busy) "Enviando…" else "Enviar", Modifier.weight(1f), enabled = !busy && text.isNotBlank()) {
                    busy = true; msg = null
                    send(text.trim()) { err -> busy = false; if (err == null) sent = true else msg = err }
                }
            }
        }
    }
}
