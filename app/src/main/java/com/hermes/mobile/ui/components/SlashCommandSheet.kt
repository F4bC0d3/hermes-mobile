package com.hermes.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Inline autocomplete that appears above the composer when the user types
 * a "/" at the start of the message. Supports the slash commands hermes-webui
 * actually exposes; selecting one inserts it into the textfield without
 * sending — the user still hits Send to submit.
 */
@Composable
fun SlashCommandSheet(
    query: String,
    onSelect: (String) -> Unit,
) {
    val all = remember { SLASH_COMMANDS }
    val filtered = remember(query) {
        if (query.isBlank() || query == "/") all
        else all.filter { it.name.startsWith(query, ignoreCase = true) }
    }
    if (filtered.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
            items(filtered, key = { it.name }) { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cmd.name) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = cmd.name,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cmd.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

data class SlashCommand(val name: String, val description: String)

private val SLASH_COMMANDS = listOf(
    SlashCommand("/help", "Show available commands"),
    SlashCommand("/clear", "Clear the current session"),
    SlashCommand("/compress", "Compress conversation history"),
    SlashCommand("/compact", "Alias for /compress"),
    SlashCommand("/model", "Switch model: /model openai/gpt-4o"),
    SlashCommand("/workspace", "Switch workspace"),
    SlashCommand("/new", "Start a new chat"),
    SlashCommand("/usage", "Toggle token + cost display"),
    SlashCommand("/theme", "Switch theme on the server"),
)
