package com.wolfwheels.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfwheels.app.R

/** Full-screen layers on top of the list: stop taps from reaching what is underneath. */
fun Modifier.blockTouches(): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
}

/** Slanted parallelogram, like the speed lines of the logo. */
val Slant = GenericShape { size, _ ->
    val k = size.height * 0.28f
    moveTo(k, 0f); lineTo(size.width, 0f); lineTo(size.width - k, size.height); lineTo(0f, size.height); close()
}

val Gap = Arrangement.spacedBy(10.dp)

/** Wolf mark + "WOLF WHEELS" in racing italic. */
@Composable
fun Wordmark(markSize: Dp = 38.dp, textSize: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.wolf_mark), contentDescription = null, modifier = Modifier.size(markSize * 1.2f, markSize))
        Spacer(Modifier.width(6.dp))
        Text("WOLF", fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
            fontSize = textSize.sp, color = WW.text, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(5.dp))
        Text("WHEELS", style = TextStyle(brush = WW.accent), fontFamily = Barlow, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontSize = textSize.sp, letterSpacing = 0.5.sp)
    }
}

/** Racing heading: italic uppercase with a slanted accent bar. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: Int = 20, trailing: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 16.dp).clip(Slant).background(WW.accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
            fontSize = size.sp, color = WW.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = (size - 4).sp, color = WW.text3)
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Heading(text, Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp), size = 18, trailing = trailing)
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(Slant)
            .then(if (selected) Modifier.background(WW.accent) else Modifier.background(WW.surface2))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text.uppercase(), color = if (selected) WW.onAccent else WW.text, fontSize = 15.sp, fontFamily = Barlow,
            fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = 0.4.sp)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.background(WW.accent) else Modifier.background(WW.surface2))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = if (enabled) WW.onAccent else WW.text3, fontFamily = Barlow,
            fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 17.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(WW.surface2)
            .border(1.dp, WW.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = WW.text, fontFamily = Barlow, fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic, fontSize = 16.sp, letterSpacing = 0.5.sp)
    }
}

/** Spec tile: small label above a bold value (like a car spec sheet). With [onClick] the value is a link (shows "›"). */
@Composable
fun SpecTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(WW.surface)
            .border(1.dp, if (onClick != null) WW.orange.copy(alpha = 0.45f) else WW.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), color = WW.text3, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = if (onClick != null) WW.amber else WW.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp, modifier = Modifier.weight(1f, fill = false))
            if (onClick != null) Text("  ›", color = WW.orange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Screen top bar: back arrow + racing title. */
@Composable
fun TopBar(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = { AppState.back() }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = WW.text)
        }
        Text(title.uppercase(), color = WW.text, fontSize = 19.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp))
        trailing?.invoke()
    }
}


/** 1, 2 or 3 cars per row. Tap to change; it is remembered for every list. */
@Composable
fun ColumnsToggle() {
    val n = com.wolfwheels.app.data.Prefs.columns.intValue
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(WW.surface).border(1.dp, WW.line, RoundedCornerShape(50)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..3).forEach { c ->
            Box(
                Modifier.clip(RoundedCornerShape(50)).then(if (c == n) Modifier.background(WW.accent) else Modifier)
                    .clickable { com.wolfwheels.app.data.Prefs.setColumns(c) }.padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(c) { Box(Modifier.size(width = 4.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(if (c == n) WW.onAccent else WW.text2)) }
                }
            }
        }
    }
}

/** Grid columns from the user's choice. */
@Composable
fun carColumns(): androidx.compose.foundation.lazy.grid.GridCells =
    androidx.compose.foundation.lazy.grid.GridCells.Fixed(com.wolfwheels.app.data.Prefs.columns.intValue)
