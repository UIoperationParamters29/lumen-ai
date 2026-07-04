package com.cortex.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.repo.ChatRepository
import com.cortex.app.data.repo.GatewayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListState(
    val chats: List<ChatEntity> = emptyList(),
    val defaultGateway: GatewayEntity? = null,
    val availableModels: List<String> = emptyList(),
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
        viewModelScope.launch {
            // Observe chats
            chatRepo.observeChats().collect { chats ->
                _state.update { it.copy(chats = chats.filter { c ->
                    if (_search.value.isBlank()) true
                    else c.title.contains(_search.value, ignoreCase = true)
                }) }
            }
        }
        viewModelScope.launch {
            // Watch search
            _search.collect { q ->
                _state.update { it.copy(searchQuery = q) }
            }
        }
        viewModelScope.launch {
            // Ensure default gateway is loaded
            val gw = gatewayRepo.getDefaultGateway()
            _state.update { it.copy(defaultGateway = gw) }
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
     * Create a new chat using the default gateway + cached default model.
     * Returns the new chat id (passed to onNewChat callback).
     */
    fun createNewChat(onCreated: (Long) -> Unit) {
        val gw = _state.value.defaultGateway
        if (gw == null) {
            _state.update { it.copy(error = "No gateway configured. Add one in Settings.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            val models = gatewayRepo.fetchAndCacheModels(gw)
            val preferred = gatewayRepo.let { _ ->
                // Use cached default model or first model
                com.cortex.app.CortexApp.instance.settingsStore.getDefaultModel(gw.id)
                    ?: models.firstOrNull()?.id
                    ?: "gpt-4o-mini"
            }
            val id = chatRepo.createChat(
                title = "New Chat",
                gatewayId = gw.id,
                model = preferred,
                webSearchEnabled = com.cortex.app.CortexApp.instance.settingsStore.webSearchConfig.let { it.provider != com.cortex.app.data.model.SearchProvider.DISABLED }
            )
            _state.update { it.copy(isCreating = false) }
            onCreated(id)
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
