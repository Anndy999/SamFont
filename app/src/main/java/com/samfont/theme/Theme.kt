package com.samfont.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SamFontGreen,
    onPrimary = Color.White,
    secondary = Color(0xFF4E5A53),
    onSecondary = Color.White,
    background = SamFontBackgroundLight,
    onBackground = Color(0xFF111614),
    surface = SamFontSurfaceLight,
    onSurface = Color(0xFF111614),
    surfaceVariant = Color(0xFFF0F2F0),
    onSurfaceVariant = Color(0xFF5E6762),
    error = SamFontRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF85C9A5),
    onPrimary = Color(0xFF0B1510),
    secondary = Color(0xFFAAB8B1),
    onSecondary = Color(0xFF0B1510),
    background = SamFontBackgroundDark,
    onBackground = Color(0xFFE7ECE8),
    surface = SamFontSurfaceDark,
    onSurface = Color(0xFFE7ECE8),
    surfaceVariant = Color(0xFF252A28),
    onSurfaceVariant = Color(0xFFB0B7B3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun SamFontTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = SamFontTypography,
        content = content
    )
}
