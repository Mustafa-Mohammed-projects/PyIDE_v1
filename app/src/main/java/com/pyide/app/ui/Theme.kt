package com.pyide.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ConsoleBg = Color(0xFF0A0C13)

private val Scheme = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0B0B1A),
    primaryContainer = Color(0xFF2A2455),
    onPrimaryContainer = Color(0xFFE3DEFF),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF0E1018),
    onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF151826),
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1F2336),
    onSurfaceVariant = Color(0xFF9AA0B8),
    outline = Color(0xFF2B3047),
    error = Color(0xFFFF6B7A),
)

@Composable
fun PyIdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
