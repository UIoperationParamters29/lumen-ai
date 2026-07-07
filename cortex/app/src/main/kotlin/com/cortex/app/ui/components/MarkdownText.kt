package com.cortex.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.app.ui.theme.*

// Markdown block types
private sealed class MDBlock {
    data class Header(val level: Int, val text: String) : MDBlock()
    data class Code(val language: String?, val code: String) : MDBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MDBlock()
    data class Blockquote(val text: String) : MDBlock()
    data class Paragraph(val text: String) : MDBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MDBlock()
    object HRule : MDBlock()
}

/** Parse markdown text into structured blocks. */
private fun parseMarkdown(text: String): List<MDBlock> {
    val blocks = mutableListOf<MDBlock>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Skip empty lines
        if (trimmed.isEmpty()) { i++; continue }

        // Code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MDBlock.Code(lang, codeLines.joinToString("\n")))
            continue
        }

        // Header
        val headerMatch = Regex("^(#{1,6})\\s+(.*)").matchEntire(trimmed)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val headerText = headerMatch.groupValues[2]
            blocks.add(MDBlock.Header(level, headerText))
            i++
            continue
        }

        // Horizontal rule
        if (Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(trimmed)) {
            blocks.add(MDBlock.HRule)
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MDBlock.Blockquote(quoteLines.joinToString(" ")))
            continue
        }

        // Ordered list item
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)").matchEntire(trimmed)
        if (orderedMatch != null) {
            val idx = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val itemText = orderedMatch.groupValues[2]
            blocks.add(MDBlock.ListItem(itemText, true, idx))
            i++
            continue
        }

        // Unordered list item
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val itemText = trimmed.substring(2)
            blocks.add(MDBlock.ListItem(itemText, false, 0))
            i++
            continue
        }

        // Table (basic: header | header \n ---|--- \n row | row)
        if (trimmed.contains("|") && i + 1 < lines.size && Regex("^\\|?\\s*[-:]+\\s*\\|").containsMatchIn(lines[i + 1])) {
            val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            i += 2 // skip header separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|") && lines[i].trim().isNotEmpty()) {
                val cells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                rows.add(cells)
                i++
            }
            blocks.add(MDBlock.Table(headers, rows))
            continue
        }

        // Paragraph (collect consecutive non-empty, non-special lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i].trim()
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("```") || l.startsWith(">") ||
                l.startsWith("- ") || l.startsWith("* ") || l.startsWith("+ ") ||
                Regex("^\\d+\\.\\s+").matches(l) || Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(l)
            ) break
            paraLines.add(l)
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MDBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return blocks
}

/** Build an AnnotatedString with inline formatting (bold, italic, code, links). */
private fun buildInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        when {
            // Inline code
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = AccentCyan, background = CodeBlockBg)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            // Bold **text** or __text__
            (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            // Italic *text* or _text_
            (text[i] == '*' || text[i] == '_') -> {
                val c = text[i]
                val end = text.indexOf(c, i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            // Strikethrough ~~text~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = TextTertiary)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            // Link [text](url)
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val linkText = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }
            else -> {
                // Regular character
                append(text[i])
                i++
            }
        }
        // Fallback: append single char if no match (prevents infinite loop)
        if (i < n && (text[i] == '*' || text[i] == '_' || text[i] == '`' || text[i] == '[' || text.startsWith("~~", i))) {
            append(text[i])
            i++
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: Int = 15,
    isStreaming: Boolean = false
) {
    val context = LocalContext.current
    // During streaming, limit parse frequency by using a substring if extremely long
    val displayText = if (isStreaming && text.length > 50000) text.take(50000) + "\n\n…(streaming)" else text
    val blocks = remember(displayText) {
        runCatching { parseMarkdown(displayText) }.getOrDefault(emptyList())
    }
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(color = color, fontSize = fontSize.sp)

    Column(modifier = modifier.fillMaxWidth()) {
        if (blocks.isEmpty() && displayText.isNotBlank()) {
            // Fallback: just show raw text if parsing failed
            Text(text = displayText, style = baseStyle, modifier = Modifier.padding(vertical = 2.dp))
        }
        blocks.forEach { block ->
            when (block) {
                is MDBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.headlineMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        3 -> MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        4 -> MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium)
                        5 -> MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Medium)
                        else -> MaterialTheme.typography.labelLarge.copy(color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = buildInline(block.text),
                        style = headerStyle,
                        modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp, bottom = 4.dp)
                    )
                }
                is MDBlock.Code -> {
                    Spacer(Modifier.height(6.dp))
                    CodeBlock(code = block.code, language = block.language)
                    Spacer(Modifier.height(6.dp))
                }
                is MDBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                        val bullet = if (block.ordered) "${block.index}." else "•"
                        Text(bullet, style = baseStyle, color = AccentCyan, modifier = Modifier.width(24.dp))
                        Text(text = buildInline(block.text), style = baseStyle, modifier = Modifier.fillMaxWidth())
                    }
                }
                is MDBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgSurfaceHigh)
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Box(modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(AccentCyan.copy(alpha = 0.6f)))
                        Spacer(Modifier.width(8.dp))
                        Text(text = buildInline(block.text), style = baseStyle.copy(color = TextSecondary))
                    }
                }
                is MDBlock.Paragraph -> {
                    val annotated = buildInline(block.text)
                    val urlAnnotations = annotated.getStringAnnotations("URL", 0, annotated.length)
                    if (urlAnnotations.isNotEmpty()) {
                        ClickableText(
                            text = annotated,
                            style = baseStyle,
                            modifier = Modifier.padding(vertical = 2.dp),
                            onClick = { offset ->
                                urlAnnotations.find { offset >= it.start && offset <= it.end }?.let { ann ->
                                    openUrl(context, ann.item)
                                }
                            }
                        )
                    } else {
                        Text(text = annotated, style = baseStyle, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                is MDBlock.Table -> {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgSurfaceHigh)
                            .padding(8.dp)
                    ) {
                        Row {
                            block.headers.forEach { h ->
                                Text(
                                    h,
                                    style = baseStyle.copy(fontWeight = FontWeight.Bold, color = AccentCyan),
                                    modifier = Modifier.weight(1f).padding(4.dp)
                                )
                            }
                        }
                        block.rows.forEach { row ->
                            Row {
                                row.forEach { cell ->
                                    Text(
                                        cell,
                                        style = baseStyle.copy(color = TextSecondary),
                                        modifier = Modifier.weight(1f).padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                MDBlock.HRule -> {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderSubtle)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val copyInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBlockBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                language ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = copyInteraction,
                        indication = null,
                        onClick = { copyToClipboard(context, code, "code") }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "text") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) { }
}
