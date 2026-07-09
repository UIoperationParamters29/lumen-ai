package com.cortex.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cortex.app.data.model.SearchResult
import com.cortex.app.ui.components.CortexOrb
import com.cortex.app.ui.components.GlassCard
import com.cortex.app.ui.components.MarkdownText
import com.cortex.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current

    // Track whether the user is pinned near the bottom; only auto-scroll then
    // so reading older messages isn't yanked away mid-stream on long replies.
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || last >= total - 2
        }
    }

    // Auto-scroll on new content, but only when the user is at the bottom.
    // This is the key fix for "long texts break" — previously every token
    // triggered an animated scroll that fought the renderer.
    LaunchedEffect(state.messages.size, state.streamingContent.length, state.streamingReasoning.length) {
        if (isAtBottom) {
            val totalItems = state.messages.size +
                if (state.isStreaming || state.streamingContent.isNotEmpty()) 1 else 0
            if (totalItems > 0) {
                try { listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0)) } catch (_: Exception) { }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier.clickable { vm.setShowModelPicker(true) }
                        ) {
                            Text(
                                state.chat?.title ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.chat?.model ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentCyan,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextPrimary)
                        }
                    },
                    actions = {
                        val wsOn = state.chat?.webSearchEnabled == true
                        IconButton(onClick = vm::toggleWebSearch) {
                            Icon(
                                Icons.Rounded.TravelExplore,
                                "Web search",
                                tint = if (wsOn) AccentGreen else TextTertiary
                            )
                        }
                        IconButton(onClick = vm::toggleThinking) {
                            Icon(
                                Icons.Rounded.Psychology,
                                "Thinking",
                                tint = if (state.showThinking) AccentCyan else TextTertiary
                            )
                        }
                        IconButton(onClick = { vm.setShowModelPicker(true) }) {
                            Icon(Icons.Rounded.Tune, "Model", tint = TextPrimary)
                        }
                        IconButton(onClick = { vm.setShowChatSettings(true) }) {
                            Icon(Icons.Rounded.MoreHoriz, "Settings", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary,
                        navigationIconContentColor = TextPrimary,
                        actionIconContentColor = TextPrimary
                    )
                )
                AnimatedVisibility(visible = state.isStreaming) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(1.5.dp),
                        color = AccentCyan,
                        trackColor = BgSurfaceHigh
                    )
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                text = state.inputText,
                onTextChange = vm::updateInput,
                onSend = {
                    keyboard?.hide()
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    vm.send()
                },
                onStop = vm::stop,
                isStreaming = state.isStreaming
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(AccentBlue.copy(alpha = 0.02f), BgPrimary, BgPrimary)
                    )
                )
                .padding(padding)
        ) {
            AnimatedVisibility(visible = state.error != null) {
                Surface(modifier = Modifier.fillMaxWidth(), color = StatusError.copy(alpha = 0.1f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Error, null, tint = StatusError, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = vm::clearError) { Text("Dismiss", color = AccentCyan) }
                    }
                }
            }

            if (state.messages.isEmpty() && !state.isStreaming && state.streamingContent.isEmpty()) {
                EmptyChatView(model = state.chat?.model ?: "")
            } else {
                // Filter out streaming placeholder messages — StreamingBubble handles those.
                val displayMessages = state.messages.filter { !it.isStreaming }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(displayMessages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            isLast = msg.id == displayMessages.lastOrNull { it.role == "assistant" }?.id,
                            showThinking = state.showThinking,
                            isThinkingExpanded = vm.thinkingExpanded[msg.id] ?: true,
                            onToggleThinking = { vm.toggleThinkingForMessage(msg.id) },
                            onRegenerate = { vm.regenerate(msg) },
                            onDelete = { vm.deleteMessage(msg) }
                        )
                    }
                    // Show the streaming bubble while actively streaming OR while
                    // the final message is still being delivered to the list
                    // (prevents flicker / content gap at the end of a reply).
                    val showStreaming = state.isStreaming ||
                        state.streamingContent.isNotEmpty() ||
                        state.streamingReasoning.isNotEmpty()
                    if (showStreaming) {
                        item(key = "streaming") {
                            StreamingBubble(
                                content = state.streamingContent,
                                reasoning = state.streamingReasoning,
                                searchResults = state.streamingSearchResults,
                                showThinking = state.showThinking
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (state.showModelPicker) {
        ModelPickerDialog(
            models = state.models,
            current = state.chat?.model ?: "",
            onSelect = vm::selectModel,
            onDismiss = { vm.setShowModelPicker(false) }
        )
    }

    if (state.showChatSettings) {
        ChatSettingsSheet(
            chat = state.chat,
            onSave = { sys, t, mt, p -> vm.updateChatSettings(sys, t, mt, p); vm.setShowChatSettings(false) },
            onDismiss = { vm.setShowChatSettings(false) }
        )
    }
}

/**
 * Redesigned input bar: the text field and the send button are now separate
 * elements in a row, so the send target is a big, easy-to-tap circle sitting
 * OUTSIDE the field. Much more comfortable on phones than the old in-field
 * trailing icon, which was cramped and easy to miss.
 */
@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 24.dp
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 160.dp),
                    placeholder = { Text("Message Cortex…", color = TextTertiary) },
                    colors = outTextFieldColors(),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.width(8.dp))

            // Send / Stop button — large, circular, sits outside the field.
            val sendBg = when {
                isStreaming -> SolidColor(StatusError.copy(alpha = 0.18f))
                text.isNotBlank() -> Brush.linearGradient(listOf(AccentBlue, AccentCyan))
                else -> SolidColor(BgSurfaceHigh)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(sendBg)
                    .clickable(
                        enabled = isStreaming || text.isNotBlank(),
                        onClick = {
                            if (isStreaming) onStop() else onSend()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isStreaming) {
                    Icon(
                        Icons.Rounded.Stop,
                        "Stop",
                        tint = StatusError,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        "Send",
                        tint = if (text.isNotBlank()) Color.White else TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
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
private fun StreamingBubble(
    content: String,
    reasoning: String,
    searchResults: List<SearchResult>,
    showThinking: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            CortexOrb(size = 28.dp, pulse = true, spin = true)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // If no content yet and no reasoning, show "thinking" indicator.
                if (content.isBlank() && reasoning.isBlank() && searchResults.isEmpty()) {
                    StreamingDots()
                }
                if (reasoning.isNotBlank() && showThinking) {
                    StreamingThinking(reasoning = reasoning)
                    Spacer(Modifier.height(6.dp))
                }
                if (searchResults.isNotEmpty()) {
                    StreamingSearchResults(results = searchResults)
                    Spacer(Modifier.height(6.dp))
                }
                if (content.isNotBlank()) {
                    // PLAIN TEXT during streaming — no markdown parsing (prevents
                    // jank / crashes on very long partial responses).
                    SelectionContainer {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingThinking(reasoning: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThinkingBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AccentCyan)
            Spacer(Modifier.width(6.dp))
            Text("Thinking", style = MaterialTheme.typography.labelMedium, color = AccentCyan, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Text(reasoning, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
private fun StreamingSearchResults(results: List<SearchResult>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TravelExplore, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Web search • ${results.size} results", style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        results.take(3).forEachIndexed { i, r ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("[${i + 1}] ", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                Text(r.title, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyChatView(model: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CortexOrb(size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Start a conversation", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Send a message to begin chatting with $model", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<com.cortex.app.data.model.ModelInfo>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Model", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            if (models.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading models…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(models) { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(m.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.displayName ?: m.id, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                if (m.ownedBy != null) {
                                    Text(m.ownedBy, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                }
                            }
                            if (m.id == current) {
                                Icon(Icons.Rounded.Check, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = AccentCyan) } },
        containerColor = BgElevated
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsSheet(
    chat: com.cortex.app.data.model.ChatEntity?,
    onSave: (String, Float, Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var systemPrompt by remember { mutableStateOf(chat?.systemPrompt ?: "") }
    var temperature by remember { mutableStateOf(chat?.temperature ?: 0.7f) }
    var maxTokens by remember { mutableStateOf(chat?.maxTokens ?: 2048) }
    var topP by remember { mutableStateOf(chat?.topP ?: 1.0f) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
            Text("Chat Settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            Text("System Prompt", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp),
                colors = outTextFieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))

            Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f, steps = 39,
                colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = BorderSubtle))

            Text("Max Tokens: $maxTokens", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = it.toInt() }, valueRange = 256f..8192f, steps = 30,
                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderSubtle))

            Text("Top P: ${"%.2f".format(topP)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(value = topP, onValueChange = { topP = it }, valueRange = 0f..1f, steps = 19,
                colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen, inactiveTrackColor = BorderSubtle))

            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(systemPrompt, temperature, maxTokens, topP) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun outTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = AccentBlue,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
