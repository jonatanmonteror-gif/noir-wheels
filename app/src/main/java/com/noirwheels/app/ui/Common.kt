package com.noirwheels.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen layers on top of the list: stop taps from reaching what is underneath. */
fun Modifier.blockTouches(): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
}

/** "Noir" in Playfair with "WHEELS" underneath, like the icon (same family as Noir Books). */
@Composable
fun Wordmark(big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Noir", fontFamily = Playfair, fontSize = if (big) 44.sp else 26.sp, color = NW.text, lineHeight = if (big) 48.sp else 28.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (big) 18.dp else 10.dp).height(1.dp).background(NW.text3))
            Spacer(Modifier.width(6.dp))
            Text("WHEELS", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = if (big) 12.sp else 9.sp,
                letterSpacing = if (big) 4.sp else 3.sp, color = NW.text2)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(if (big) 18.dp else 10.dp).height(1.dp).background(NW.text3))
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) NW.text else NW.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) NW.onText else NW.text, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontFamily = Inter)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (enabled) NW.text else NW.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) NW.onText else NW.text3, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(NW.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = NW.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = NW.text2, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp))
}

val Gap = Arrangement.spacedBy(10.dp)
