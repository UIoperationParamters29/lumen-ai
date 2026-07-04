package com.cortex.app.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.sp
import com.cortex.app.data.model.ChatEntity
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
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(OrbStart, OrbMid, OrbEnd)
                                    )
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Cortex",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.createNewChat(onNewChat) },
                containerColor = Brush.linearGradient(listOf(AccentBlue, AccentCyan)).let { _ -> AccentBlue },
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
                .padding(padding)
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::setSearch,
                    placeholder = { Text("Search chats…", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextTertiary) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (state.error != null) {
                ErrorBanner(state.error!!) { vm.clearError() }
            }

            if (state.chats.isEmpty() && state.searchQuery.isBlank()) {
                EmptyChatList(defaultGateway = state.defaultGateway)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.chats, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onMenu = { menuChat = chat }
                        )
                        HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Context menu
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
    }
}

@Composable
private fun ChatRow(
    chat: ChatEntity,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.webSearchEnabled) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.TravelExplore, null, tint = AccentGreen, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
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
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(
                        colors = listOf(OrbStart, OrbMid, OrbEnd),
                        radius = 200f
                    )
                )
        )
        Spacer(Modifier.height(24.dp))
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
            Text(
                "Add a gateway in Settings to begin.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusWarning
            )
        }
    }
}

// Helper retained for backward compatibility — no longer used.
@Composable
private fun unusedStateHelper(): com.cortex.app.ui.chatlist.ChatListState = ChatListState()

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
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
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
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
fun outTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = BgSurfaceHigh,
    unfocusedContainerColor = BgSurface,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = BorderSubtle,
    cursorColor = AccentBlue,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
