package com.cortex.app.ui.chatlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.ModelInfo
import com.cortex.app.data.repo.ChatRepository
import com.cortex.app.data.repo.GatewayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListState(
    val chats: List<ChatEntity> = emptyList(),
    val defaultGateway: GatewayEntity? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val showModelPicker: Boolean = false,
    val searchQuery: String = "",
    val isCreating: Boolean = false,
    val error: String? = null
)

class ChatListViewModel(
    private val chatRepo: ChatRepository,
    private val gatewayRepo: GatewayRepository
) : ViewModel() {

    private val _search = MutableStateFlow("")
    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    init {
        // Combine chats + search query → filtered list
        viewModelScope.launch {
            chatRepo.observeChats().combine(_search) { chats, query ->
                if (query.isBlank()) chats
                else chats.filter { it.title.contains(query, ignoreCase = true) }
            }.collect { filtered ->
                _state.update { it.copy(chats = filtered) }
            }
        }
        // Track search query in state
        viewModelScope.launch {
            _search.collect { q -> _state.update { it.copy(searchQuery = q) } }
        }
        // CRITICAL: Observe gateways flow so defaultGateway updates live when user saves a gateway
        viewModelScope.launch {
            gatewayRepo.observeGateways().collect { gateways ->
                Log.d("Cortex", "ChatListVM: gateways updated, count=${gateways.size}")
                val default = gateways.find { it.isDefault } ?: gateways.firstOrNull()
                _state.update { it.copy(defaultGateway = default) }
                Log.d("Cortex", "ChatListVM: defaultGateway = ${default?.name}")
            }
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun setPinned(chat: ChatEntity, pinned: Boolean) {
        viewModelScope.launch { chatRepo.setPinned(chat.id, pinned) }
    }

    fun deleteChat(chat: ChatEntity) {
        viewModelScope.launch { chatRepo.deleteChat(chat) }
    }

    fun renameChat(chat: ChatEntity, newTitle: String) {
        viewModelScope.launch {
            chatRepo.updateChat(chat.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Start the new-chat flow. Fetches models for the default gateway,
     * then either shows the model picker (if multiple models) or creates
     * the chat immediately (if only one model).
     */
    fun startNewChat() {
        val gw = _state.value.defaultGateway
        if (gw == null) {
            _state.update { it.copy(error = "No gateway configured. Add one in Settings first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                // Fetch models from API (or fall back to cache)
                val models = try {
                    gatewayRepo.fetchAndCacheModels(gw)
                } catch (e: Exception) {
                    Log.w("Cortex", "Model fetch failed, using cache: ${e.message}")
                    gatewayRepo.getCachedModels(gw.id)
                }
                Log.d("Cortex", "New chat: fetched ${models.size} models")
                _state.update { it.copy(isCreating = false, availableModels = models, showModelPicker = models.isNotEmpty()) }

                if (models.isEmpty()) {
                    // No models available — create with fallback model name
                    createChatWithModel(gw, "gpt-4o-mini")
                }
                // If models exist, the UI will show the picker dialog.
                // User selects a model → createChatWithModel is called.
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Failed to load models: ${e.message}") }
            }
        }
    }

    /**
     * Create a new chat with the selected model.
     */
    fun createChatWithModel(gateway: GatewayEntity, modelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null, showModelPicker = false) }
            try {
                // Save as default model for this gateway
                com.cortex.app.CortexApp.instance.settingsStore.setDefaultModel(gateway.id, modelId)

                val wsConfig = com.cortex.app.CortexApp.instance.settingsStore.webSearchConfig
                val id = chatRepo.createChat(
                    title = "New Chat",
                    gatewayId = gateway.id,
                    model = modelId,
                    webSearchEnabled = wsConfig.provider != com.cortex.app.data.model.SearchProvider.DISABLED
                )
                Log.d("Cortex", "Created chat id=$id with model=$modelId")
                _state.update { it.copy(isCreating = false, showModelPicker = false, availableModels = emptyList()) }
                pendingNewChatCallback?.invoke(id)
                pendingNewChatCallback = null
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Failed to create chat: ${e.message}") }
            }
        }
    }

    // Callback holder — the screen sets this before calling startNewChat
    private var pendingNewChatCallback: ((Long) -> Unit)? = null

    fun createNewChat(onCreated: (Long) -> Unit) {
        pendingNewChatCallback = onCreated
        startNewChat()
    }

    fun dismissModelPicker() {
        _state.update { it.copy(showModelPicker = false, availableModels = emptyList()) }
        pendingNewChatCallback = null
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

class ChatListViewModelFactory(
    private val chatRepo: ChatRepository,
    private val gatewayRepo: GatewayRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatListViewModel(chatRepo, gatewayRepo) as T
}
