package com.wolfwheels.app.data

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * English → Spanish on the phone (Google ML Kit, the language is downloaded once, ~30 MB).
 * Every translation is kept on disk, so each text is translated only once and next time it is already in Spanish.
 */
object Translator {
    private val client = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
    )
    private val cache = HashMap<String, String>()
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var saveJob: Job? = null
    @Volatile var ready = false
        private set

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "translations.json")
        runCatching {
            val j = JSONObject(file.readText())
            j.keys().forEach { k -> cache[k] = j.getString(k) }
        }
        // Download the language in the background as soon as the app opens
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).addOnSuccessListener { ready = true }
    }

    /** Already translated (or null). Instant, for lists. */
    fun cached(text: String): String? = synchronized(cache) { cache[text.trim()] }

    /** Translation, or the original text if the language isn't ready yet or it fails. */
    suspend fun es(text: String): String {
        val t = text.trim()
        if (t.isEmpty() || !t.any { it.isLetter() }) return text
        synchronized(cache) { cache[t] }?.let { return it }
        if (!ready) return text
        val out = mutex.withLock {
            suspendCancellableCoroutine<String?> { cont ->
                client.translate(t).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
            }
        } ?: return text
        synchronized(cache) { cache[t] = out }
        scheduleSave()
        return out
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            val j = synchronized(cache) { JSONObject(cache as Map<*, *>) }
            runCatching { file.writeText(j.toString()) }
        }
    }

    /** Waits (up to [maxMs]) for the language to be downloaded the first time. */
    suspend fun awaitReady(maxMs: Long) {
        var waited = 0L
        while (!ready && waited < maxMs) { delay(200); waited += 200 }
    }

    private val translatable = setOf("Color", "Decoración", "Base", "Ventanas", "Interior", "Notas", "País", "Motor", "Producido")

    /** The model's texts in Spanish: story, card, notes and data (not names of cars or series). */
    suspend fun textsOf(d: Wiki.Detail): Wiki.Detail = d.copy(
        intro = es(d.intro),
        description = d.description.split("\n\n").map { es(it) }.joinToString("\n\n"),
        notes = d.notes.map { es(it) },
        cardFacts = d.cardFacts.map { it.copy(value = es(it.value)) },
        facts = d.facts.map { if (it.label in translatable) it.copy(value = es(it.value)) else it },
    )

    /** The versions' data in Spanish (colour, decoration, base, interior, notes…). */
    suspend fun versionsOf(d: Wiki.Detail): Wiki.Detail = d.copy(
        versions = d.versions.map { v ->
            v.copy(
                colorEs = es(v.color),
                fields = v.fields.map { f -> if (f.label in translatable) f.copy(value = es(f.value)) else f },
            )
        }
    )
}
