package com.hermes.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C6CF2),
    onPrimary = Color.White,
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.Black,
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFF6F4FF),
    surface = Color(0xFF12111B),
    onSurface = Color(0xFFF6F4FF),
    surfaceVariant = Color(0xFF1B1A28),
    onSurfaceVariant = Color(0xFFB8B3D7),
    outline = Color(0xFF26243A),
    error = Color(0xFFFB7185),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6557DF),
    onPrimary = Color.White,
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
