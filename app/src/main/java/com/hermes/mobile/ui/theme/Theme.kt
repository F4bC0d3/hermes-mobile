package com.hermes.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The full theme catalog mirrors what hermes-webui ships:
 *   Dark, Light, Slate, Solarized Dark, Monokai, Nord, OLED, Indigo Dark
 *
 * Each theme is a (name, ColorScheme, accentForBubbles) triple. ColorScheme
 * drives all Material 3 surfaces; the explicit accent is what user message
 * bubbles use so themes stay distinctive even when most surfaces look similar.
 */
enum class HermesTheme(
    val displayName: String,
    val isDark: Boolean,
) {
    DARK("Dark", true),
    LIGHT("Light", false),
    OLED("AMOLED Black", true),
    SLATE("Slate", true),
    SOLARIZED_DARK("Solarized Dark", true),
    MONOKAI("Monokai", true),
    NORD("Nord", true),
    INDIGO_DARK("Indigo Dark", true),
}

val LocalHermesAccents = staticCompositionLocalOf {
    HermesAccents(
        userBubble = Color(0xFF7C6CF2),
        userBubbleOn = Color.White,
        thinking = Color(0xFFCAA94B),
        toolBadge = Color(0xFF7C6CF2),
    )
}

data class HermesAccents(
    val userBubble: Color,
    val userBubbleOn: Color,
    val thinking: Color,
    val toolBadge: Color,
)

@Composable
fun HermesTheme(
    theme: HermesTheme = HermesTheme.DARK,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme)
    CompositionLocalProvider(LocalHermesAccents provides palette.accents) {
        MaterialTheme(
            colorScheme = palette.colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

private data class HermesPalette(
    val colorScheme: androidx.compose.material3.ColorScheme,
    val accents: HermesAccents,
)

private fun paletteFor(t: HermesTheme): HermesPalette = when (t) {
    HermesTheme.DARK -> dark()
    HermesTheme.LIGHT -> light()
    HermesTheme.OLED -> oled()
    HermesTheme.SLATE -> slate()
    HermesTheme.SOLARIZED_DARK -> solarizedDark()
    HermesTheme.MONOKAI -> monokai()
    HermesTheme.NORD -> nord()
    HermesTheme.INDIGO_DARK -> indigoDark()
}

private fun dark() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF7C6CF2),
        onPrimary = Color.White,
        secondary = Color(0xFF38BDF8),
        background = Color(0xFF0B0B12),
        onBackground = Color(0xFFF6F4FF),
        surface = Color(0xFF12111B),
        onSurface = Color(0xFFF6F4FF),
        surfaceVariant = Color(0xFF1B1A28),
        onSurfaceVariant = Color(0xFFB8B3D7),
        outline = Color(0xFF26243A),
        error = Color(0xFFFB7185),
    ),
    HermesAccents(
        userBubble = Color(0xFF7C6CF2),
        userBubbleOn = Color.White,
        thinking = Color(0xFFCAA94B),
        toolBadge = Color(0xFF7C6CF2),
    ),
)

private fun light() = HermesPalette(
    lightColorScheme(
        primary = Color(0xFF6557DF),
        onPrimary = Color.White,
        secondary = Color(0xFF0EA5E9),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF111827),
        surface = Color.White,
        onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFE2E8F0),
        error = Color(0xFFDC2626),
    ),
    HermesAccents(
        userBubble = Color(0xFF6557DF),
        userBubbleOn = Color.White,
        thinking = Color(0xFFB45309),
        toolBadge = Color(0xFF6557DF),
    ),
)

private fun oled() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF8B7DFF),
        onPrimary = Color.Black,
        secondary = Color(0xFFFFB870),
        background = Color.Black,
        onBackground = Color(0xFFEAEAF0),
        surface = Color.Black,
        onSurface = Color(0xFFEAEAF0),
        surfaceVariant = Color(0xFF0A0A0A),
        onSurfaceVariant = Color(0xFFA8A8B0),
        outline = Color(0xFF1F1F22),
        error = Color(0xFFFF6B7B),
    ),
    HermesAccents(
        userBubble = Color(0xFF8B7DFF),
        userBubbleOn = Color.Black,
        thinking = Color(0xFFFFB870),
        toolBadge = Color(0xFF8B7DFF),
    ),
)

private fun slate() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color.White,
        secondary = Color(0xFF34D399),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF1E293B),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF334155),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF475569),
        error = Color(0xFFF87171),
    ),
    HermesAccents(
        userBubble = Color(0xFF60A5FA),
        userBubbleOn = Color.White,
        thinking = Color(0xFFFBBF24),
        toolBadge = Color(0xFF34D399),
    ),
)

private fun solarizedDark() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF268BD2),
        onPrimary = Color(0xFFFDF6E3),
        secondary = Color(0xFF2AA198),
        background = Color(0xFF002B36),
        onBackground = Color(0xFF93A1A1),
        surface = Color(0xFF073642),
        onSurface = Color(0xFFEEE8D5),
        surfaceVariant = Color(0xFF0E4853),
        onSurfaceVariant = Color(0xFF93A1A1),
        outline = Color(0xFF586E75),
        error = Color(0xFFDC322F),
    ),
    HermesAccents(
        userBubble = Color(0xFF268BD2),
        userBubbleOn = Color(0xFFFDF6E3),
        thinking = Color(0xFFB58900),
        toolBadge = Color(0xFF6C71C4),
    ),
)

private fun monokai() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFFA6E22E),
        onPrimary = Color.Black,
        secondary = Color(0xFFFD971F),
        background = Color(0xFF272822),
        onBackground = Color(0xFFF8F8F2),
        surface = Color(0xFF2D2A2E),
        onSurface = Color(0xFFF8F8F2),
        surfaceVariant = Color(0xFF3E3D32),
        onSurfaceVariant = Color(0xFFCFCFC2),
        outline = Color(0xFF49483E),
        error = Color(0xFFF92672),
    ),
    HermesAccents(
        userBubble = Color(0xFFF92672),
        userBubbleOn = Color.White,
        thinking = Color(0xFFE6DB74),
        toolBadge = Color(0xFF66D9EF),
    ),
)

private fun nord() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF2E3440),
        secondary = Color(0xFF81A1C1),
        background = Color(0xFF2E3440),
        onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF3B4252),
        onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF434C5E),
        onSurfaceVariant = Color(0xFFD8DEE9),
        outline = Color(0xFF4C566A),
        error = Color(0xFFBF616A),
    ),
    HermesAccents(
        userBubble = Color(0xFF5E81AC),
        userBubbleOn = Color(0xFFECEFF4),
        thinking = Color(0xFFEBCB8B),
        toolBadge = Color(0xFFA3BE8C),
    ),
)

private fun indigoDark() = HermesPalette(
    darkColorScheme(
        primary = Color(0xFF818CF8),
        onPrimary = Color.White,
        secondary = Color(0xFFFCA5A5),
        background = Color(0xFF0E0E1A),
        onBackground = Color(0xFFE5E7FF),
        surface = Color(0xFF1A1A2E),
        onSurface = Color(0xFFE5E7FF),
        surfaceVariant = Color(0xFF2A2A47),
        onSurfaceVariant = Color(0xFFC7CCEA),
        outline = Color(0xFF383859),
        error = Color(0xFFFDA4AF),
    ),
    HermesAccents(
        userBubble = Color(0xFF818CF8),
        userBubbleOn = Color.White,
        thinking = Color(0xFFFCD34D),
        toolBadge = Color(0xFFFCA5A5),
    ),
)
