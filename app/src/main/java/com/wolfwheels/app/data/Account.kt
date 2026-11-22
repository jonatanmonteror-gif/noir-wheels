package com.wolfwheels.app.data

import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** The user's account on Jonatan's server. Without an account the collection stays on this phone. */
object Account {
    val name = mutableStateOf("")
    val email = mutableStateOf("")
    val loggedIn = mutableStateOf(false)
    var token: String? = null
        private set

    fun init() {
        token = Prefs.token.ifEmpty { null }
        name.value = Prefs.userName
        email.value = Prefs.userEmail
        loggedIn.value = token != null
    }

    private fun save(j: JSONObject) {
        token = j.getString("token")
        name.value = j.optString("name"); email.value = j.optString("email")
        Prefs.token = token!!; Prefs.userName = name.value; Prefs.userEmail = email.value
        loggedIn.value = true
    }

    /** Creates the account; the server sends a 6-digit code to the email (the account works once verified). */
    suspend fun register(name: String, email: String, password: String) {
        Server.call("register", JSONObject().put("name", name).put("email", email).put("password", password))
    }

    suspend fun verify(email: String, code: String) {
        save(Server.call("verify", JSONObject().put("email", email).put("code", code)))
        MyCollection.sync(force = true)
    }

    /** Sends a code to change the password. */
    suspend fun forgot(email: String) { Server.call("forgot", JSONObject().put("email", email)) }

    /** New password with the emailed code; logs in. */
    suspend fun reset(email: String, code: String, password: String) {
        save(Server.call("reset", JSONObject().put("email", email).put("code", code).put("password", password)))
        MyCollection.sync(force = true)
    }

    data class Public(val on: Boolean, val slug: String, val want: Boolean, val url: String?)

    /** Public garage settings (web page jonatanmontero.es/garaje/<slug>). */
    suspend fun getPublic(): Public {
        val j = Server.call("public", token = token)
        val slug = j.optString("slug")
        return Public(j.optBoolean("on"), slug, j.optBoolean("want"), if (j.optBoolean("on")) "https://jonatanmontero.es/garaje/$slug" else null)
    }

    suspend fun setPublic(on: Boolean, slug: String, want: Boolean): Public {
        val j = Server.call("public", JSONObject().put("on", on).put("slug", slug).put("want", want), token = token)
        return Public(j.optBoolean("on"), j.optString("slug"), j.optBoolean("want"), j.optString("url").ifEmpty { null }.takeIf { j.optBoolean("on") })
    }

    suspend fun resend(email: String) { Server.call("resend", JSONObject().put("email", email)) }

    suspend fun login(email: String, password: String) {
        save(Server.call("login", JSONObject().put("email", email).put("password", password)))
        MyCollection.sync(force = true)
    }

    suspend fun logout() {
        val t = token
        token = null; Prefs.token = ""; loggedIn.value = false
        if (t != null) runCatching { Server.call("logout", JSONObject(), token = t) }
    }

    /** Session no longer valid on the server. */
    fun expired() { token = null; Prefs.token = ""; loggedIn.value = false }
}
