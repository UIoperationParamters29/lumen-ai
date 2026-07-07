package com.cortex.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cortex.app.data.model.MessageEntity
import com.cortex.app.data.model.SearchResult
import com.cortex.app.ui.components.CortexOrb
import com.cortex.app.ui.components.MarkdownText
import com.cortex.app.ui.components.copyToClipboard
import com.cortex.app.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun MessageBubble(
    message: MessageEntity,
    isLast: Boolean,
    showThinking: Boolean,
    isThinkingExpanded: Boolean,
    onToggleThinking: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val searchResults = remember(message.searchResults) { parseSearchResults(message.searchResults) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        if (isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(UserBubbleBg, UserBubbleBg.copy(alpha = 0.8f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                CortexOrb(size = 28.dp, pulse = message.isStreaming)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Thinking panel
                    if (!message.reasoningContent.isNullOrBlank()) {
                        ThinkingPanel(
                            reasoning = message.reasoningContent!!,
                            isExpanded = isThinkingExpanded && showThinking,
                            onToggle = onToggleThinking,
                            isStreaming = message.isStreaming
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    // Web search results
                    if (searchResults.isNotEmpty()) {
                        SearchResultsPanel(results = searchResults)
                        Spacer(Modifier.height(6.dp))
                    }

                    // Content
                    if (message.content.isNotBlank()) {
                        SelectionContainer {
                            MarkdownText(text = message.content, color = TextPrimary, fontSize = 15)
                        }
                    } else if (message.isStreaming) {
                        StreamingDots()
                    }

                    if (message.errorMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = StatusError.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "⚠ ${message.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusError,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Actions
                    if (!message.isStreaming && message.content.isNotBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.model != null) {
                                Text(message.model, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconActionButton(Icons.Rounded.ContentCopy, "Copy") { copyToClipboard(context, message.content, "message") }
                            if (isLast) {
                                IconActionButton(Icons.Rounded.Refresh, "Regenerate", onRegenerate)
                            }
                            Box {
                                IconActionButton(Icons.Rounded.MoreVert, "More") { showMenu = true }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = StatusError) },
                                        onClick = { showMenu = false; onDelete() },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = StatusError) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingPanel(
    reasoning: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isStreaming: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(ThinkingBg, ThinkingBg.copy(alpha = 0.7f)))
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isStreaming) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AccentCyan)
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(Icons.Rounded.Lightbulb, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text("Thinking", style = MaterialTheme.typography.labelMedium, color = AccentCyan, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(
                if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null, tint = TextTertiary, modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(reasoning, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SearchResultsPanel(results: List<SearchResult>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(BgSurfaceHigh, BgSurfaceHigh.copy(alpha = 0.7f))))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TravelExplore, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Web search • ${results.size} results", style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        results.take(5).forEachIndexed { i, r ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("[${i + 1}] ", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(r.url, style = MaterialTheme.typography.labelSmall, color = AccentBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    // Three dots with staggered wave animation
    val dot1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 0), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 150), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 300), RepeatMode.Reverse),
        label = "dot3"
    )
    val alphas = listOf(dot1, dot2, dot3)
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            listOf(AccentCyan.copy(alpha = alphas[i]), AccentBlue.copy(alpha = alphas[i] * 0.5f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(icon, contentDescription, tint = TextTertiary, modifier = Modifier.size(16.dp))
    }
}

private fun parseSearchResults(jsonStr: String?): List<SearchResult> {
    if (jsonStr.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = Json.parseToJsonElement(jsonStr) as JsonArray
        arr.map { el ->
            val o = el as JsonObject
            SearchResult(
                o["title"]?.jsonPrimitive?.contentOrNull ?: "",
                o["url"]?.jsonPrimitive?.contentOrNull ?: "",
                o["snippet"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
    }.getOrDefault(emptyList())
}
