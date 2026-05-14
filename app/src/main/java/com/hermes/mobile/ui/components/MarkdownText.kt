package com.hermes.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight markdown renderer for assistant text.
 *
 * Handles the markdown features that actually appear in agent responses:
 *   - fenced ```code blocks``` rendered as monospace cards
 *   - inline `code` rendered with monospace + tinted background
 *   - **bold** and *italic*
 *   - # / ## / ### headers
 *   - bullet and numbered lists with their original indent
 *   - links [text](url) become underlined
 *
 * Tables, mermaid, latex, footnotes — out of scope for v0.2; falls back to
 * raw text. The agent's tool cards and reasoning blocks are rendered
 * separately by ChatBubbles, not through this function.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { splitBlocks(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            when (block) {
                is Block.CodeFence -> CodeBlock(block.lang, block.body)
                is Block.Paragraph -> Text(
                    text = renderInline(block.text, color),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is Block.Header -> Text(
                    text = block.text,
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    },
                )
                is Block.ListItem -> Text(
                    text = renderInline(block.text, color),
                    color = color,
                    modifier = Modifier.padding(start = (block.indent * 16).dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is Block.Quote -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(0.dp),
                        )
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = renderInline(block.text, color),
                        color = color.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String?, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
    ) {
        if (!lang.isNullOrBlank()) {
            Text(
                lang,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Header(val level: Int, val text: String) : Block
    data class ListItem(val text: String, val indent: Int) : Block
    data class Quote(val text: String) : Block
    data class CodeFence(val lang: String?, val body: String) : Block
}

private fun splitBlocks(input: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = input.split('\n')
    var i = 0
    val paraBuf = StringBuilder()

    fun flushPara() {
        if (paraBuf.isNotEmpty()) {
            out.add(Block.Paragraph(paraBuf.toString().trimEnd()))
            paraBuf.clear()
        }
    }

    while (i < lines.size) {
        val ln = lines[i]
        val trimmed = ln.trimStart()

        // Code fence
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            i++
            val body = StringBuilder()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            out.add(Block.CodeFence(lang, body.toString()))
            i++
            continue
        }

        // Header
        val headerMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headerMatch != null) {
            flushPara()
            val level = headerMatch.groupValues[1].length
            out.add(Block.Header(level, headerMatch.groupValues[2].trim()))
            i++
            continue
        }

        // Bullet list
        val bulletMatch = Regex("^([\\s]*)[\\-\\*\\+]\\s+(.+)$").find(ln)
        if (bulletMatch != null) {
            flushPara()
            val indent = bulletMatch.groupValues[1].length / 2
            out.add(Block.ListItem("• " + bulletMatch.groupValues[2], indent))
            i++
            continue
        }

        // Numbered list
        val numberedMatch = Regex("^([\\s]*)(\\d+\\.)\\s+(.+)$").find(ln)
        if (numberedMatch != null) {
            flushPara()
            val indent = numberedMatch.groupValues[1].length / 2
            out.add(Block.ListItem(
                "${numberedMatch.groupValues[2]} ${numberedMatch.groupValues[3]}",
                indent,
            ))
            i++
            continue
        }

        // Quote
        if (trimmed.startsWith("> ")) {
            flushPara()
            out.add(Block.Quote(trimmed.removePrefix("> ").trim()))
            i++
            continue
        }

        if (ln.isBlank()) {
            flushPara()
            i++
            continue
        }

        if (paraBuf.isNotEmpty()) paraBuf.append('\n')
        paraBuf.append(ln)
        i++
    }
    flushPara()
    return out
}

private fun renderInline(text: String, baseColor: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // inline code: `xyz`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33808080),
                    )) { append(text.substring(i + 1, end)) }
                    i = end + 1
                    continue
                }
            }
            // bold: **xyz**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            // italic: *xyz* or _xyz_
            if ((text[i] == '*' || text[i] == '_') && i + 1 < text.length && text[i + 1] != ' ') {
                val ch = text[i]
                val end = text.indexOf(ch, i + 1)
                if (end > i + 1 && (end + 1 >= text.length || text[end + 1] != ch)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            // link: [text](url)
            if (text[i] == '[') {
                val close = text.indexOf(']', i + 1)
                if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                    val urlEnd = text.indexOf(')', close + 2)
                    if (urlEnd > close + 2) {
                        withStyle(SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = Color(0xFF7C6CF2),
                        )) { append(text.substring(i + 1, close)) }
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            append(text[i])
            i++
        }
    }
