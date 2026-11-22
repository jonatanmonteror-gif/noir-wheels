package com.wolfwheels.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.data.MyCollection
import kotlinx.coroutines.delay

/** "Quitado de Mi garaje · DESHACER" bar at the bottom for a few seconds after a change. */
@Composable
fun UndoBar() {
    val current = AppState.undo.value
    var shown by remember { mutableStateOf(current) }
    LaunchedEffect(current) {
        if (current != null) { shown = current; delay(5000); if (AppState.undo.value === current) AppState.undo.value = null }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(current != null, enter = slideInVertically(tween(220)) { it } + fadeIn(), exit = slideOutVertically(tween(200)) { it } + fadeOut()) {
            val (msg, before) = shown ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WW.surface2).border(1.dp, WW.line, RoundedCornerShape(14.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(msg, color = WW.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("DESHACER", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        val key = AppState.undo.value?.second?.key
                        if (before != null) MyCollection.restore(before)
                        else {
                            // It was not in the garage before: remove what was just added
                            val added = MyCollection.items.values.maxByOrNull { it.t }
                            if (added != null && (key == null || added.key == key)) MyCollection.update(added.key) { it.copy(deleted = true) }
                        }
                        AppState.undo.value = null
                    }.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}
