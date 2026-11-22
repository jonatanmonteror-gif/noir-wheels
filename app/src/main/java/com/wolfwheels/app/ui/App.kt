package com.wolfwheels.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.wolfwheels.app.data.Updater
import com.wolfwheels.app.data.Wiki

/** Screens shown on top of the home list. */
sealed class Route {
    data class Detail(val title: String, val selectKey: String? = null, val selectToy: String? = null) : Route()
    data object Scan : Route()
    data class Listing(val label: String, val target: String) : Route()
    data object Collection : Route()
    data object Account : Route()
    data object Settings : Route()
}

object AppState {
    val stack = mutableStateListOf<Route>()
    val viewer = mutableStateOf<Viewer?>(null)
    val update = mutableStateOf<Updater.Release?>(null)
    val resumes = androidx.compose.runtime.mutableIntStateOf(0)
    /** Tab Mi garaje opens with ("have", "want"…). */
    val garageTab = mutableStateOf("have")

    data class Viewer(val photos: List<Wiki.Photo>, val start: Int)

    fun open(title: String, selectKey: String? = null) { stack.add(Route.Detail(title, selectKey)) }
    fun push(r: Route) { stack.add(r) }
    fun back(): Boolean = when {
        viewer.value != null -> { viewer.value = null; true }
        stack.isNotEmpty() -> { stack.removeAt(stack.lastIndex); true }
        else -> false
    }
}

@Composable
fun App() {
    WolfTheme {
        // Check for a new version when the app opens and every time it comes back (at most every 30 min)
        LaunchedEffect(AppState.resumes.intValue) { UpdateFlow.check() }
        BackHandler(enabled = AppState.viewer.value != null || AppState.stack.isNotEmpty()) { AppState.back() }

        Box(Modifier.fillMaxSize().background(WW.bg)) {
            HomeScreen()
            AppState.stack.lastOrNull()?.let { r ->
                // key: each screen in the stack keeps its own state while it is on top
                key(AppState.stack.size, r) {
                    when (r) {
                        is Route.Detail -> DetailScreen(r.title, r.selectKey, r.selectToy)
                        Route.Scan -> ScanScreen()
                        is Route.Listing -> ListingScreen(r.label, r.target)
                        Route.Collection -> CollectionScreen()
                        Route.Account -> AccountScreen()
                        Route.Settings -> SettingsScreen()
                    }
                }
            }
            AppState.viewer.value?.let { PhotoViewer(it) }
            UpdateDialog()
            CrashDialog()
        }
    }
}
