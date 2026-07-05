package com.cortex.app.ui.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.ui.components.CortexOrb
import com.cortex.app.ui.components.GlassCard
import com.cortex.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    vm: ChatListViewModel,
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: (Long) -> Unit
) {
    val state by vm.state.collectAsState()
    var menuChat by remember { mutableStateOf<ChatEntity?>(null) }
    var renameDialog by remember { mutableStateOf<ChatEntity?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CortexOrb(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Cortex",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.createNewChat(onNewChat) },
                containerColor = AccentBlue,
                contentColor = Color.White,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New Chat", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(16.dp)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AccentBlue.copy(alpha = 0.03f),
                            BgPrimary,
                            BgPrimary
                        )
                    )
                )
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    cornerRadius = 14.dp
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = vm::setSearch,
                        placeholder = { Text("Search chats…", color = TextTertiary) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AccentCyan) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { vm.setSearch("") }) {
                                    Icon(Icons.Rounded.Close, null, tint = TextTertiary)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = outTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.error != null) {
                ErrorBanner(state.error!!) { vm.clearError() }
            }

            if (state.isCreating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = AccentCyan,
                    trackColor = BgSurfaceHigh
                )
            }

            if (state.chats.isEmpty() && state.searchQuery.isBlank()) {
                EmptyChatList(defaultGateway = state.defaultGateway)
            } else if (state.chats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No chats match \"${state.searchQuery}\"", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.chats, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onMenu = { menuChat = chat }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        DropdownMenu(
            expanded = menuChat != null,
            onDismissRequest = { menuChat = null }
        ) {
            val c = menuChat
            if (c != null) {
                DropdownMenuItem(
                    text = { Text(if (c.pinned) "Unpin" else "Pin") },
                    onClick = { vm.setPinned(c, !c.pinned); menuChat = null },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { renameDialog = c; menuChat = null },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = StatusError) },
                    onClick = { vm.deleteChat(c); menuChat = null },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = StatusError) }
                )
            }
        }

        renameDialog?.let { chat ->
            RenameDialog(
                initial = chat.title,
                onConfirm = { newTitle ->
                    vm.renameChat(chat, newTitle)
                    renameDialog = null
                },
                onDismiss = { renameDialog = null }
            )
        }

        // Model picker dialog for new chat
        if (state.showModelPicker) {
            NewChatModelPickerDialog(
                models = state.availableModels,
                gatewayName = state.defaultGateway?.name ?: "Gateway",
                onSelect = { modelId ->
                    state.defaultGateway?.let { gw -> vm.createChatWithModel(gw, modelId) }
                },
                onDismiss = vm::dismissModelPicker
            )
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatEntity,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pinned indicator
            if (chat.pinned) {
                Icon(Icons.Rounded.PushPin, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Model badge
                    Surface(
                        color = AccentBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            chat.model.take(20),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (chat.webSearchEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.TravelExplore, null, tint = AccentGreen, modifier = Modifier.size(12.dp))
                    }
                }
            }

            IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.MoreVert, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyChatList(defaultGateway: com.cortex.app.data.model.GatewayEntity? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CortexOrb(size = 100.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            "Welcome to Cortex",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bring your own key. Stream thoughts. Stay in flow.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        if (defaultGateway == null) {
            GlassCard(
                cornerRadius = 12.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Info, null, tint = StatusWarning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Add a gateway in Settings to begin",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 12.dp,
        gradient = Brush.linearGradient(listOf(StatusError.copy(alpha = 0.15f), StatusError.copy(alpha = 0.05f))),
        borderGradient = Brush.linearGradient(listOf(StatusError.copy(alpha = 0.3f), Color.Transparent))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Error, null, tint = StatusError, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = outTextFieldColors()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = BgElevated
    )
}

@Composable
private fun NewChatModelPickerDialog(
    models: List<com.cortex.app.data.model.ModelInfo>,
    gatewayName: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.cortex.app.ui.components.CortexOrb(size = 24.dp, pulse = false)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("New Chat", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(gatewayName, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        },
        text = {
            Column {
                Text("Pick a model to start chatting", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(models) { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(m.id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.displayName ?: m.id, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                if (m.ownedBy != null) {
                                    Text(m.ownedBy, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                }
                                if (m.supportsVision) {
                                    Text("vision", style = MaterialTheme.typography.labelSmall, color = AccentGreen, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = BgElevated
    )
}

@Composable
fun outTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = BorderSubtle,
    cursorColor = AccentBlue,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
