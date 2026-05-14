package com.hermes.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.SessionStore
import com.hermes.mobile.ui.theme.HermesTheme

@Composable
fun SettingsScreen(
    sessionStore: SessionStore,
    currentTheme: HermesTheme,
    onThemeChange: (HermesTheme) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionLabel("Theme")
            }
            item {
                ThemeGrid(currentTheme, onThemeChange)
            }

            item { Spacer(Modifier.height(20.dp)) }

            item { SectionLabel("Connection") }
            item {
                InfoCard(
                    label = "Space URL",
                    value = sessionStore.spaceUrl ?: "Not set",
                    monospace = true,
                )
            }
            item {
                InfoCard(
                    label = "Default model",
                    value = sessionStore.defaultModel ?: "Use server default",
                    monospace = true,
                )
            }

            item { Spacer(Modifier.height(20.dp)) }

            item { SectionLabel("Account") }
            item {
                ActionRow(
                    label = "Sign out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = onLogout,
                    destructive = true,
                )
            }

            item { Spacer(Modifier.height(40.dp)) }
            item {
                Text(
                    "Hermes Mobile · v0.3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.16f, androidx.compose.ui.unit.TextUnitType.Em),
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeGrid(current: HermesTheme, onChange: (HermesTheme) -> Unit) {
    val themes = HermesTheme.entries.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        themes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    ThemeCard(
                        theme = t,
                        selected = t == current,
                        onClick = { onChange(t) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: HermesTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatches = themeSwatches(theme)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Mock chat preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(swatches.background, RoundedCornerShape(8.dp))
                    .padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(swatches.surface, RoundedCornerShape(6.dp))
                        .height(10.dp)
                        .fillMaxWidth(0.6f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(swatches.accent, RoundedCornerShape(6.dp))
                        .height(14.dp)
                        .fillMaxWidth(0.5f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        Icons.Filled.Check, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, monospace: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = if (monospace)
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = color,
            )
        }
    }
}

private data class Swatches(
    val background: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
)

private fun themeSwatches(theme: HermesTheme): Swatches = when (theme) {
    HermesTheme.DARK -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF0B0B12),
        androidx.compose.ui.graphics.Color(0xFF1B1A28),
        androidx.compose.ui.graphics.Color(0xFF7C6CF2),
    )
    HermesTheme.LIGHT -> Swatches(
        androidx.compose.ui.graphics.Color(0xFFFAFAFA),
        androidx.compose.ui.graphics.Color(0xFFF1F5F9),
        androidx.compose.ui.graphics.Color(0xFF6557DF),
    )
    HermesTheme.OLED -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF000000),
        androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        androidx.compose.ui.graphics.Color(0xFF8B7DFF),
    )
    HermesTheme.SLATE -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF0F172A),
        androidx.compose.ui.graphics.Color(0xFF334155),
        androidx.compose.ui.graphics.Color(0xFF60A5FA),
    )
    HermesTheme.SOLARIZED_DARK -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF002B36),
        androidx.compose.ui.graphics.Color(0xFF073642),
        androidx.compose.ui.graphics.Color(0xFF268BD2),
    )
    HermesTheme.MONOKAI -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF272822),
        androidx.compose.ui.graphics.Color(0xFF3E3D32),
        androidx.compose.ui.graphics.Color(0xFFF92672),
    )
    HermesTheme.NORD -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF2E3440),
        androidx.compose.ui.graphics.Color(0xFF434C5E),
        androidx.compose.ui.graphics.Color(0xFF5E81AC),
    )
    HermesTheme.INDIGO_DARK -> Swatches(
        androidx.compose.ui.graphics.Color(0xFF0E0E1A),
        androidx.compose.ui.graphics.Color(0xFF2A2A47),
        androidx.compose.ui.graphics.Color(0xFF818CF8),
    )
}
