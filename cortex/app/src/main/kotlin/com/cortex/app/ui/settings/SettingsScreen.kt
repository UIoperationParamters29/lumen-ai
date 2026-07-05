package com.cortex.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.SearchProvider
import com.cortex.app.ui.components.CortexOrb
import com.cortex.app.ui.components.GlassCard
import com.cortex.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // === GATEWAYS ===
            SectionHeader("Gateways", "Your AI providers")
            state.gateways.forEach { gw ->
                GatewayRow(
                    gateway = gw,
                    onSetDefault = { vm.setDefaultGateway(gw) },
                    onEdit = { vm.openGatewayEditor(gw) },
                    onDelete = { vm.deleteGateway(gw) }
                )
            }
            AddGatewayButton(onClick = { vm.openGatewayEditor(null) })
            Spacer(Modifier.height(24.dp))

            // === WEB SEARCH ===
            SectionHeader("Web Search", "Ground responses with live results")
            WebSearchSection(
                config = state.webSearchConfig,
                onProviderChange = vm::updateSearchProvider,
                onMaxChange = vm::updateSearchMaxResults,
                onKeyChange = vm::updateSearchApiKey,
                onUrlChange = vm::updateSearchInstanceUrl
            )
            Spacer(Modifier.height(24.dp))

            // === CHAT ===
            SectionHeader("Chat", "Streaming and behavior")
            ToggleRow("Streaming responses", "Show tokens as they arrive", state.streamingEnabled, vm::setStreamingEnabled)
            ToggleRow("Show thinking by default", "Expand reasoning automatically", state.showThinkingDefault, vm::setShowThinkingDefault)
            ToggleRow("Send on Enter", "Enter to send, Shift+Enter for newline", state.sendOnEnter, vm::setSendOnEnter)
            ToggleRow("Auto-title chats", "Rename from first message", state.autoTitle, vm::setAutoTitle)
            Spacer(Modifier.height(24.dp))

            // === ABOUT ===
            SectionHeader("About", "")
            AboutSection()
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showGatewayEditor) {
        GatewayEditorDialog(
            existing = state.editingGateway,
            testing = state.testing,
            testResult = state.testResult,
            testModels = state.testModels,
            saving = state.saving,
            saveError = state.saveError,
            onSave = { name, url, key -> vm.saveGateway(name, url, key, state.editingGateway) },
            onTest = vm::testGateway,
            onClearTest = vm::clearTestResult,
            onDismiss = vm::closeGatewayEditor
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun GatewayRow(
    gateway: GatewayEntity,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onEdit() },
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gateway icon orb
            CortexOrb(size = 32.dp, pulse = false)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gateway.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    if (gateway.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = AccentGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Default", style = MaterialTheme.typography.labelSmall, color = AccentGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(gateway.baseUrl, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                Text("••••••••${gateway.apiKey.takeLast(4)}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.MoreVert, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (!gateway.isDefault) {
                    DropdownMenuItem(text = { Text("Set as default") }, onClick = { showMenu = false; onSetDefault() }, leadingIcon = { Icon(Icons.Rounded.Star, null) })
                }
                DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                DropdownMenuItem(text = { Text("Delete", color = StatusError) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = StatusError) })
            }
        }
    }
}

@Composable
private fun AddGatewayButton(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        cornerRadius = 12.dp,
        gradient = Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.08f), BgSurface.copy(alpha = 0.5f))),
        borderGradient = Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.3f), Color.Transparent))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Add, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Gateway", style = MaterialTheme.typography.titleMedium, color = AccentBlue, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WebSearchSection(
    config: com.cortex.app.data.model.WebSearchConfig,
    onProviderChange: (SearchProvider) -> Unit,
    onMaxChange: (Int) -> Unit,
    onKeyChange: (String) -> Unit,
    onUrlChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SearchProvider.entries.forEach { provider ->
            val selected = config.provider == provider
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onProviderChange(provider) },
                cornerRadius = 10.dp,
                gradient = if (selected) Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.12f), BgSurface.copy(alpha = 0.6f))) else CardGradient,
                borderGradient = if (selected) Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.4f), Color.Transparent)) else GlassGradient
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onProviderChange(provider) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentBlue, unselectedColor = TextTertiary)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(provider.displayName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        val desc = when (provider) {
                            SearchProvider.DISABLED -> "No web search"
                            SearchProvider.DUCK_DUCK_GO -> "Free, no API key required"
                            SearchProvider.EXA -> "Neural search, requires API key"
                            SearchProvider.FIRECRAWL -> "Deep crawl, requires API key"
                            SearchProvider.SEARXNG -> "Self-hosted, requires instance URL"
                        }
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }

        if (config.provider != SearchProvider.DISABLED) {
            Spacer(Modifier.height(12.dp))
            Text("Max Results: ${config.maxResults}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(
                value = config.maxResults.toFloat(),
                onValueChange = { onMaxChange(it.toInt()) },
                valueRange = 1f..10f, steps = 8,
                colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderSubtle)
            )
        }

        if (config.provider.requiresKey) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.apiKey, onValueChange = onKeyChange,
                label = { Text("Provider API Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = outTextFieldColors(), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (config.provider.requiresInstance) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.instanceUrl, onValueChange = onUrlChange,
                label = { Text("Instance URL") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = outTextFieldColors(), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Switch(
                checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BgSurfaceHigh
                )
            )
        }
    }
}

@Composable
private fun AboutSection() {
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        cornerRadius = 14.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CortexOrb(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cortex", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Version 1.2.0", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Bring your own key. Stream thoughts. Stay in flow. Built with native Kotlin + Jetpack Compose for maximum performance.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }
    }
}

@Composable
private fun GatewayEditorDialog(
    existing: GatewayEntity?,
    testing: Boolean,
    testResult: String?,
    testModels: List<com.cortex.app.data.model.ModelInfo>,
    saving: Boolean,
    saveError: String?,
    onSave: (String, String, String) -> Unit,
    onTest: (String, String) -> Unit,
    onClearTest: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.baseUrl ?: "https://api.openai.com/v1") }
    var key by remember { mutableStateOf(existing?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CortexOrb(size = 24.dp, pulse = false)
                Spacer(Modifier.width(8.dp))
                Text(if (existing == null) "Add Gateway" else "Edit Gateway", color = TextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Name", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("My Gateway") }, singleLine = true,
                    colors = outTextFieldColors(), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Base URL", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    placeholder = { Text("https://api.openai.com/v1") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = outTextFieldColors(), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("API Key", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    placeholder = { Text("sk-...") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TextTertiary)
                        }
                    },
                    colors = outTextFieldColors(), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Test button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onTest(url, key) },
                        enabled = !testing && !saving && url.isNotBlank() && key.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = AccentCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Testing…")
                        } else {
                            Text("Test Connection")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (testResult != null) {
                        Text(
                            testResult,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (testResult.startsWith("✓")) AccentGreen else StatusError,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearTest, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // Show fetched models
                if (testModels.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("${testModels.size} models available", style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                    Spacer(Modifier.height(4.dp))
                    testModels.take(5).forEach { m ->
                        Text("• ${m.displayName ?: m.id}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    if (testModels.size > 5) {
                        Text("…and ${testModels.size - 5} more", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }

                // Save error
                AnimatedVisibility(visible = saveError != null) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = StatusError.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                saveError ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusError,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), url.trim().trimEnd('/'), key.trim()) },
                enabled = !saving && name.isNotBlank() && url.isNotBlank() && key.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Saving…")
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel", color = TextSecondary) }
        },
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
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextSecondary
)
