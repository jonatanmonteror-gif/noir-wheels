package com.wolfwheels.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
    data object Shop : Route()
    data object Matches : Route()
    data object Identify : Route()
    data class CustomCar(val key: String?, val toy: String? = null, val photo: String? = null) : Route()
    data class Community(val id: String) : Route()
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
    /** Last change that can be undone: (message, item as it was before). */
    val undo = mutableStateOf<Pair<String, com.wolfwheels.app.data.MyCollection.Item?>?>(null)
    /** Tab Mi garaje opens with ("have", "want"…). */
    val garageTab = mutableStateOf("have")

    data class Viewer(val photos: List<Wiki.Photo>, val start: Int)

    fun open(title: String, selectKey: String? = null) { stack.add(Route.Detail(title, selectKey)) }
    fun push(r: Route) { stack.add(r) }
    /** Opens a car of my garage: wiki page, community catalog entry or my own car added by hand. */
    fun openItem(i: com.wolfwheels.app.data.MyCollection.Item) = when {
        i.cat.isNotBlank() -> push(Route.Community(i.cat))
        i.custom -> push(Route.CustomCar(i.key))
        else -> open(i.title, i.key)
    }
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
            // Screens slide in softly when opened and fade out when going back
            AnimatedContent(
                targetState = AppState.stack.size to AppState.stack.lastOrNull(),
                transitionSpec = {
                    if (targetState.first > initialState.first)
                        (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                    else
                        fadeIn(tween(160)) togetherWith (slideOutHorizontally(tween(240)) { it / 5 } + fadeOut(tween(200)))
                },
                label = "screens"
            ) { (_, r) ->
                if (r != null) when (r) {
                    is Route.Detail -> DetailScreen(r.title, r.selectKey, r.selectToy)
                    Route.Scan -> ScanScreen()
                    Route.Shop -> ShopScreen()
                    Route.Matches -> MatchesScreen()
                    Route.Identify -> IdentifyScreen()
                    is Route.CustomCar -> CustomCarScreen(r.key, r.toy, r.photo)
                    is Route.Community -> CommunityCarScreen(r.id)
                    is Route.Listing -> ListingScreen(r.label, r.target)
                    Route.Collection -> CollectionScreen()
                    Route.Account -> AccountScreen()
                    Route.Settings -> SettingsScreen()
                }
            }
            AnimatedVisibility(AppState.viewer.value != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
                AppState.viewer.value?.let { PhotoViewer(it) }
            }
            AchievementToast()
            UndoBar()
            UpdateDialog()
            CrashDialog()
        }
    }
}
