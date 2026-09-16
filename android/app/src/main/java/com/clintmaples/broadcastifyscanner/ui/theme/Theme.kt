package com.clintmaples.broadcastifyscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D1117)
val BgElev = Color(0xFF161B22)
val BgCard = Color(0xFF1C2128)
val Border = Color(0xFF30363D)
val TextPrimary = Color(0xFFE6EDF3)
val Muted = Color(0xFF8B949E)
val Accent = Color(0xFF3FB950)
val AccentDim = Color(0xFF238636)
val Warn = Color(0xFFD29922)
val Danger = Color(0xFFF85149)
val Info = Color(0xFF58A6FF)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Info,
    background = Bg,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgElev,
    onSurfaceVariant = Muted,
    outline = Border,
    error = Danger,
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content,
    )
}
