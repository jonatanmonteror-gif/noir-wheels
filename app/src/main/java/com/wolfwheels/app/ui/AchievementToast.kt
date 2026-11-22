package com.wolfwheels.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.wolfwheels.app.data.Achievements
import com.wolfwheels.app.data.MyCollection
import kotlinx.coroutines.delay

/** "Logro conseguido" banner that drops from the top when a new achievement is unlocked. */
@Composable
fun AchievementToast() {
    val done by remember { derivedStateOf { Achievements.all(MyCollection.items.values, 0).filter { it.done }.map { it.title }.toSet() } }
    var known by remember { mutableStateOf<Set<String>?>(null) }
    var show by remember { mutableStateOf<Achievements.Badge?>(null) }
    LaunchedEffect(done) {
        val before = known
        known = done
        if (before == null) return@LaunchedEffect          // first time: just remember
        val fresh = done - before
        if (fresh.isNotEmpty()) {
            show = Achievements.all(MyCollection.items.values, 0).firstOrNull { it.title in fresh }
            delay(3200); show = null
        }
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = show != null,
            enter = slideInVertically(spring(dampingRatio = 0.6f)) { -it * 2 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(250)) { -it * 2 } + fadeOut(tween(200))
        ) {
            val b = show
            if (b != null) Row(
                Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(WW.surface)
                    .border(1.5.dp, WW.accent, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(b.icon, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("¡LOGRO CONSEGUIDO!", color = WW.amber, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 14.sp)
                    Text(b.title.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                    Text(b.desc, color = WW.text3, fontSize = 12.sp)
                }
            }
        }
    }
}
