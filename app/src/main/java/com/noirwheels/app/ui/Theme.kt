package com.noirwheels.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.noirwheels.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val InterDisplay = FontFamily(Font(R.font.inter_display_semi_bold, FontWeight.SemiBold))
val Playfair = FontFamily(Font(R.font.playfair_regular, FontWeight.Normal))

/** Noir family colours (same palette as Noir Books in dark mode). */
object NW {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF111113)
    val surface2 = Color(0xFF1C1C1F)
    val line = Color(0xFF2A2A2E)
    val text = Color(0xFFF2F2F2)
    val text2 = Color(0xFFA1A1A6)
    val text3 = Color(0xFF6C6C70)
    val onText = Color(0xFF000000)
}

@Composable
fun NoirTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = NW.text, onPrimary = NW.onText, secondary = NW.text2,
        background = NW.bg, onBackground = NW.text,
        surface = NW.surface, onSurface = NW.text,
        surfaceVariant = NW.surface2, onSurfaceVariant = NW.text2,
        surfaceContainer = NW.surface, surfaceContainerHigh = NW.surface2, surfaceContainerLow = NW.surface,
        outline = NW.line, outlineVariant = NW.line,
    )
    val base = TextStyle(fontFamily = Inter)
    val t = MaterialTheme.typography
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            headlineSmall = t.headlineSmall.merge(base).copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
            titleLarge = t.titleLarge.merge(base).copy(fontWeight = FontWeight.SemiBold),
            titleMedium = t.titleMedium.merge(base).copy(fontWeight = FontWeight.SemiBold),
            titleSmall = t.titleSmall.merge(base).copy(fontWeight = FontWeight.Medium),
            bodyLarge = t.bodyLarge.merge(base),
            bodyMedium = t.bodyMedium.merge(base),
            bodySmall = t.bodySmall.merge(base),
            labelLarge = t.labelLarge.merge(base).copy(fontWeight = FontWeight.Medium),
            labelMedium = t.labelMedium.merge(base),
            labelSmall = t.labelSmall.merge(base),
        ),
        content = content,
    )
}
