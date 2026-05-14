package com.hermes.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.screens.ChatBubble
import com.hermes.mobile.ui.screens.ToolCallView

/**
 * A single chat row. ChatGPT/Claude-style:
 *   - User bubble: filled accent, right-aligned, rounded
 *   - Assistant: full-width text on background, no bubble, with tiny avatar
 *   - Tool calls: collapsible cards inline above the assistant text
 *   - Reasoning (Claude / o3 thinking): gold-tinted collapsible card
 *   - Streaming: blinking caret at the end of partial text
 */
@Composable
fun ChatRow(bubble: ChatBubble) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (bubble.isUser) UserBubble(bubble) else AssistantBubble(bubble)
    }
}

@Composable
private fun UserBubble(bubble: ChatBubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (bubble.attachments.isNotEmpty()) {
                    bubble.attachments.forEach { a ->
                        Text(
                            text = "📎 ${a.name}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (bubble.content.isNotBlank()) {
                    Text(
                        text = bubble.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(bubble: ChatBubble) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Avatar
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (bubble.reasoning.isNotBlank()) {
                ReasoningCard(bubble.reasoning)
                Spacer(Modifier.height(8.dp))
            }
            bubble.toolCalls.forEach { tc ->
                ToolCallCard(tc)
                Spacer(Modifier.height(8.dp))
            }
            MarkdownText(
                text = bubble.content + if (bubble.streaming) STREAMING_CARET else "",
                color = if (bubble.error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ReasoningCard(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val gold = androidx.compose.ui.graphics.Color(0xFFCAA94B)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = gold.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .border(1.dp, gold.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Thinking",
                    color = gold,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, tint = gold, modifier = Modifier.size(16.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    color = gold.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(tc: ToolCallView) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Build, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tc.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (tc.result == null) {
                    Text(
                        "running…",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (expanded && tc.args.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "args: ${tc.args.take(400)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            if (expanded && !tc.result.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tc.result.take(2000),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

private const val STREAMING_CARET = " ▌"
