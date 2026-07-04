package com.cortex.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.GatewayEntity
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
        // Load default gateway
        viewModelScope.launch {
            try {
                val gw = gatewayRepo.getDefaultGateway()
                _state.update { it.copy(defaultGateway = gw) }
            } catch (_: Exception) { }
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

    fun createNewChat(onCreated: (Long) -> Unit) {
        val gw = _state.value.defaultGateway
        if (gw == null) {
            _state.update { it.copy(error = "No gateway configured. Add one in Settings first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                // Try to fetch models, but don't block on failure
                val models = try { gatewayRepo.fetchAndCacheModels(gw) } catch (_: Exception) { gatewayRepo.getCachedModels(gw.id) }
                val preferred = com.cortex.app.CortexApp.instance.settingsStore.getDefaultModel(gw.id)
                    ?: models.firstOrNull()?.id
                    ?: "gpt-4o-mini"
                val wsConfig = com.cortex.app.CortexApp.instance.settingsStore.webSearchConfig
                val id = chatRepo.createChat(
                    title = "New Chat",
                    gatewayId = gw.id,
                    model = preferred,
                    webSearchEnabled = wsConfig.provider != com.cortex.app.data.model.SearchProvider.DISABLED
                )
                _state.update { it.copy(isCreating = false) }
                onCreated(id)
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Failed to create chat: ${e.message}") }
            }
        }
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
