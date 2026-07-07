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
    val allGateways: List<GatewayEntity> = emptyList(),
    val defaultGateway: GatewayEntity? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val showGatewayPicker: Boolean = false,
    val showModelPicker: Boolean = false,
    val selectedGateway: GatewayEntity? = null,
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

    private var pendingNewChatCallback: ((Long) -> Unit)? = null

    init {
        viewModelScope.launch {
            chatRepo.observeChats().combine(_search) { chats, query ->
                if (query.isBlank()) chats
                else chats.filter { it.title.contains(query, ignoreCase = true) }
            }.collect { filtered ->
                _state.update { it.copy(chats = filtered) }
            }
        }
        viewModelScope.launch {
            _search.collect { q -> _state.update { it.copy(searchQuery = q) } }
        }
        // Observe gateways live
        viewModelScope.launch {
            gatewayRepo.observeGateways().collect { gateways ->
                Log.d("Cortex", "ChatListVM: gateways=${gateways.size}")
                val default = gateways.find { it.isDefault } ?: gateways.firstOrNull()
                _state.update { it.copy(allGateways = gateways, defaultGateway = default) }
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
     * Start new chat flow:
     * - If 0 gateways → error
     * - If 1 gateway → skip to model picker
     * - If 2+ gateways → show gateway picker first
     */
    fun createNewChat(onCreated: (Long) -> Unit) {
        pendingNewChatCallback = onCreated
        val gateways = _state.value.allGateways
        if (gateways.isEmpty()) {
            _state.update { it.copy(error = "No gateway configured. Add one in Settings first.") }
            return
        }
        if (gateways.size == 1) {
            // Single gateway — skip picker
            selectGateway(gateways.first())
        } else {
            // Multiple gateways — show picker
            _state.update { it.copy(showGatewayPicker = true) }
        }
    }

    fun selectGateway(gateway: GatewayEntity) {
        _state.update { it.copy(selectedGateway = gateway, showGatewayPicker = false, isCreating = true, error = null) }
        viewModelScope.launch {
            try {
                val models = try {
                    gatewayRepo.fetchAndCacheModels(gateway)
                } catch (e: Exception) {
                    Log.w("Cortex", "Model fetch failed, using cache: ${e.message}")
                    gatewayRepo.getCachedModels(gateway.id)
                }
                Log.d("Cortex", "Fetched ${models.size} models for ${gateway.name}")
                _state.update { it.copy(isCreating = false, availableModels = models, showModelPicker = models.isNotEmpty()) }
                if (models.isEmpty()) {
                    createChatWithModel(gateway, "gpt-4o-mini")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Failed to load models: ${e.message}") }
            }
        }
    }

    fun createChatWithModel(gateway: GatewayEntity, modelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null, showModelPicker = false) }
            try {
                com.cortex.app.CortexApp.instance.settingsStore.setDefaultModel(gateway.id, modelId)
                val wsConfig = com.cortex.app.CortexApp.instance.settingsStore.webSearchConfig
                val id = chatRepo.createChat(
                    title = "New Chat",
                    gatewayId = gateway.id,
                    model = modelId,
                    webSearchEnabled = wsConfig.provider != com.cortex.app.data.model.SearchProvider.DISABLED
                )
                Log.d("Cortex", "Created chat id=$id model=$modelId")
                _state.update { it.copy(isCreating = false, showModelPicker = false, availableModels = emptyList(), selectedGateway = null) }
                pendingNewChatCallback?.invoke(id)
                pendingNewChatCallback = null
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Failed to create chat: ${e.message}") }
            }
        }
    }

    fun dismissGatewayPicker() {
        _state.update { it.copy(showGatewayPicker = false) }
        pendingNewChatCallback = null
    }

    fun dismissModelPicker() {
        _state.update { it.copy(showModelPicker = false, availableModels = emptyList(), selectedGateway = null) }
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
