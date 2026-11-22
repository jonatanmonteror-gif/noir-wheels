package com.noirwheels.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.noirwheels.app.data.Updater
import com.noirwheels.app.data.Wiki

/** Global screen state. Simple stack: list → model page → (photo viewer). Settings on top of the list. */
object AppState {
    val stack = mutableStateListOf<String>()          // model titles opened, last = visible
    val settings = mutableStateOf(false)
    val viewer = mutableStateOf<Viewer?>(null)
    val update = mutableStateOf<Updater.Release?>(null)

    data class Viewer(val photos: List<Wiki.Photo>, val start: Int)

    fun open(title: String) { stack.add(title) }
    fun back(): Boolean = when {
        viewer.value != null -> { viewer.value = null; true }
        stack.isNotEmpty() -> { stack.removeAt(stack.lastIndex); true }
        settings.value -> { settings.value = false; true }
        else -> false
    }
}

@Composable
fun App() {
    NoirTheme {
        LaunchedEffect(Unit) {
            val r = Updater.latest()
            if (r != null && r.versionCode > Updater.currentCode) AppState.update.value = r
        }
        val canGoBack = AppState.viewer.value != null || AppState.stack.isNotEmpty() || AppState.settings.value
        BackHandler(enabled = canGoBack) { AppState.back() }

        Box(Modifier.fillMaxSize().background(NW.bg)) {
            HomeScreen()
            if (AppState.settings.value) SettingsScreen()
            AppState.stack.lastOrNull()?.let { title ->
                // key: each model page keeps its own state
                androidx.compose.runtime.key(AppState.stack.size, title) { DetailScreen(title) }
            }
            AppState.viewer.value?.let { PhotoViewer(it) }
        }
    }
}
