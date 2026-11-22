package com.wolfwheels.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.wolfwheels.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
/** Racing display type: Barlow Condensed (italic for titles). */
val Barlow = FontFamily(
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
    Font(R.font.barlow_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.barlow_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
)

/** Wolf Wheels colours: night asphalt with a flame orange → amber accent (the logo's). */
object WW {
    val bg = Color(0xFF0B0C0F)
    val surface = Color(0xFF15171C)
    val surface2 = Color(0xFF1E2128)
    val line = Color(0xFF2A2E37)
    val text = Color(0xFFF4F4F5)
    val text2 = Color(0xFFA3A7B0)
    val text3 = Color(0xFF6B707A)
    val orange = Color(0xFFFF5A1A)
    val amber = Color(0xFFFFB81C)
    val showroom = Color(0xFFECECEF)   // light panel behind car photos (most have white backgrounds)
    val onAccent = Color(0xFF14100A)
    val accent: Brush get() = Brush.linearGradient(listOf(Color(0xFFFF3D17), orange, amber))
}

@Composable
fun WolfTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = WW.orange, onPrimary = WW.onAccent, secondary = WW.amber,
        background = WW.bg, onBackground = WW.text,
        surface = WW.surface, onSurface = WW.text,
        surfaceVariant = WW.surface2, onSurfaceVariant = WW.text2,
        surfaceContainer = WW.surface, surfaceContainerHigh = WW.surface2, surfaceContainerLow = WW.surface,
        outline = WW.line, outlineVariant = WW.line,
    )
    val base = TextStyle(fontFamily = Inter)
    val t = MaterialTheme.typography
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            bodyLarge = t.bodyLarge.merge(base), bodyMedium = t.bodyMedium.merge(base), bodySmall = t.bodySmall.merge(base),
            labelLarge = t.labelLarge.merge(base), labelMedium = t.labelMedium.merge(base), labelSmall = t.labelSmall.merge(base),
            titleMedium = t.titleMedium.merge(base), titleSmall = t.titleSmall.merge(base),
        ),
        content = content,
    )
}
