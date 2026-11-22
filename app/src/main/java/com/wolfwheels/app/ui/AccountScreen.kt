package com.wolfwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.data.Account
import com.wolfwheels.app.data.MyCollection
import com.wolfwheels.app.data.Server
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountScreen() {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var codeFor by remember { mutableStateOf<String?>(null) }   // email waiting for its verification code
    var code by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var resetFor by remember { mutableStateOf<String?>(null) }   // email changing its password
    var newPass by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(WW.bg).blockTouches()) {
        TopBar("Mi cuenta")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) { Wordmark(markSize = 46.dp, textSize = 28) }

            if (Account.loggedIn.value) {
                Heading("Hola, ${Account.name.value}", size = 22)
                Spacer(Modifier.height(6.dp))
                Text(Account.email.value, color = WW.text2, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                val have = MyCollection.items.values.count { !it.deleted && it.status == "have" }
                val want = MyCollection.items.values.count { !it.deleted && it.status == "want" }
                SpecTile("Mi garaje", "$have coches · $want en deseos", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val last = MyCollection.lastSync.value
                SpecTile("Última sincronización",
                    if (MyCollection.syncing.value) "Sincronizando…" else if (last == 0L) "Todavía no" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)),
                    Modifier.fillMaxWidth())
                MyCollection.lastError.value?.let { Spacer(Modifier.height(8.dp)); Text(it, color = WW.orange, fontSize = 13.sp) }
                Spacer(Modifier.height(16.dp))
                PublicGarage()
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Sincronizar ahora", Modifier.fillMaxWidth(), enabled = !MyCollection.syncing.value) {
                    scope.launch { MyCollection.sync(force = true) }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Cerrar sesión", Modifier.fillMaxWidth()) { scope.launch { Account.logout() } }
                Spacer(Modifier.height(10.dp))
                Text("Al cerrar sesión tu garaje sigue en este móvil y en tu cuenta.", color = WW.text3, fontSize = 12.sp)
            } else if (resetFor != null) {
                Heading("Nueva contraseña", size = 22)
                Spacer(Modifier.height(8.dp))
                Text("Si hay una cuenta con ${resetFor}, te hemos mandado un código de 6 cifras. Escríbelo con tu contraseña nueva.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Field("Código", code, KeyboardType.Number) { code = it.filter { c -> c.isDigit() }.take(6) }
                Spacer(Modifier.height(10.dp))
                Field("Contraseña nueva", newPass, KeyboardType.Password, secret = true) { newPass = it }
                Text("Mínimo 6 caracteres.", color = WW.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (busy) "Un momento…" else "Cambiar contraseña", Modifier.fillMaxWidth(), enabled = !busy && code.length == 6 && newPass.length >= 6) {
                    busy = true; msg = null
                    scope.launch {
                        try { Account.reset(resetFor!!, code, newPass); resetFor = null; code = ""; newPass = ""; pass = "" }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { msg = Server.message(e) }
                        finally { busy = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Reenviar código", Modifier.weight(1f)) {
                        msg = null; info = null
                        scope.launch {
                            try { Account.forgot(resetFor!!); info = "Código enviado de nuevo." }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { msg = Server.message(e) }
                        }
                    }
                    SecondaryButton("Volver", Modifier.weight(1f)) { resetFor = null; code = ""; newPass = ""; msg = null; info = null }
                }
                info?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.amber, fontSize = 14.sp) }
                msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.orange, fontSize = 14.sp) }
            } else if (codeFor != null) {
                Heading("Verifica tu email", size = 22)
                Spacer(Modifier.height(8.dp))
                Text("Te hemos mandado un código de 6 cifras a ${codeFor}. Escríbelo aquí para activar tu cuenta. Si no lo ves, mira en la carpeta de spam.",
                    color = WW.text2, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Field("Código", code, KeyboardType.Number) { code = it.filter { c -> c.isDigit() }.take(6) }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (busy) "Un momento…" else "Verificar", Modifier.fillMaxWidth(), enabled = !busy && code.length == 6) {
                    busy = true; msg = null
                    scope.launch {
                        try { Account.verify(codeFor!!, code); pass = ""; codeFor = null; code = "" }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { msg = Server.message(e) }
                        finally { busy = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Reenviar código", Modifier.weight(1f)) {
                        msg = null; info = null
                        scope.launch {
                            try { Account.resend(codeFor!!); info = "Código enviado de nuevo." }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { msg = Server.message(e) }
                        }
                    }
                    SecondaryButton("Cambiar email", Modifier.weight(1f)) { codeFor = null; code = ""; msg = null; info = null }
                }
                info?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.amber, fontSize = 14.sp) }
                msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.orange, fontSize = 14.sp) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Entrar", !register) { register = false; msg = null }
                    Pill("Crear cuenta", register) { register = true; msg = null }
                }
                Spacer(Modifier.height(16.dp))
                if (register) { Field("Nombre", name) { name = it }; Spacer(Modifier.height(10.dp)) }
                Field("Email", email, KeyboardType.Email) { email = it.trim() }
                Spacer(Modifier.height(10.dp))
                Field("Contraseña", pass, KeyboardType.Password, secret = true) { pass = it }
                if (register) Text("Mínimo 6 caracteres.", color = WW.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (busy) "Un momento…" else if (register) "Crear cuenta" else "Entrar", Modifier.fillMaxWidth(),
                    enabled = !busy && email.isNotBlank() && pass.isNotBlank()) {
                    busy = true; msg = null
                    scope.launch {
                        try {
                            if (register) { Account.register(name.trim(), email, pass); codeFor = email.lowercase(); code = "" }
                            else { Account.login(email, pass); pass = "" }
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e
                        } catch (e: Exception) {
                            if (e is Server.ApiError && e.reason == "unverified") { codeFor = email.lowercase(); code = ""; info = Server.message(e) }
                            else msg = Server.message(e)
                        } finally { busy = false }
                    }
                }
                msg?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WW.orange, fontSize = 14.sp) }
                if (!register) {
                    Spacer(Modifier.height(14.dp))
                    Text("¿HAS OLVIDADO LA CONTRASEÑA?", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            val e = email.trim().lowercase()
                            if (!e.contains("@")) { msg = "Escribe primero tu email."; return@clickable }
                            msg = null; busy = true
                            scope.launch {
                                try { Account.forgot(e); resetFor = e; code = ""; newPass = "" }
                                catch (ex: kotlinx.coroutines.CancellationException) { throw ex }
                                catch (ex: Exception) { msg = Server.message(ex) }
                                finally { busy = false }
                            }
                        }.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (register) "Te mandaremos un código a tu email para verificarlo. Los coches que ya marcaste en este móvil se suben a tu cuenta." else "Los coches que ya marcaste en este móvil se suben a tu cuenta al entrar.", color = WW.text3, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, onChange: (String) -> Unit) {
    Column {
        Text(label.uppercase(), color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = WW.text, fontSize = 16.sp, fontFamily = Inter),
            cursorBrush = SolidColor(WW.orange),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WW.surface)
                .border(1.dp, WW.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 14.dp)
        )
    }
}

/** Public web page of my garage: on/off, link name, show the wish list. */
@Composable
private fun PublicGarage() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(false) }
    var slug by remember { mutableStateOf("") }
    var want by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { Account.getPublic() }.onSuccess { p ->
            on = p.on; want = p.want; url = p.url
            slug = p.slug.ifBlank { Account.name.value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30) }
        }
        loaded = true
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Heading("Garaje público", size = 17)
        Spacer(Modifier.height(6.dp))
        Text("Una página web con tu garaje y tus coches para cambiar, que puedes enviar a otros coleccionistas. Nadie puede tocar nada.",
            color = WW.text2, fontSize = 13.sp, lineHeight = 18.sp)
        if (!loaded) return@Column
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activar", color = WW.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = on, onCheckedChange = { on = it },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = WW.orange, checkedThumbColor = WW.text))
        }
        if (on) {
            Spacer(Modifier.height(8.dp))
            Field("Nombre del enlace", slug) { slug = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(30) }
            Text("jonatanmontero.es/garaje/$slug", color = WW.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar lo que busco (deseos)", color = WW.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = want, onCheckedChange = { want = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = WW.orange, checkedThumbColor = WW.text))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Guardar", Modifier.weight(1f)) {
                msg = null
                scope.launch {
                    try { val p = Account.setPublic(on, slug, want); url = p.url; msg = if (p.on) "Listo: tu garaje ya es público." else "Tu garaje ya no es público." }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { msg = Server.message(e) }
                }
            }
            url?.let { u ->
                SecondaryButton("Compartir", Modifier.weight(1f)) {
                    runCatching {
                        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, "Mi garaje de Hot Wheels: $u"), "Compartir mi garaje"))
                    }
                }
            }
        }
        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = WW.amber, fontSize = 13.sp) }
    }
}
