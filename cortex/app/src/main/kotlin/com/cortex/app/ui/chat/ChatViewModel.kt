package com.cortex.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.MessageEntity
import com.cortex.app.data.model.ModelInfo
import com.cortex.app.data.model.SearchResult
import com.cortex.app.data.repo.ChatRepository
import com.cortex.app.data.repo.GatewayRepository
import com.cortex.app.data.repo.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val chat: ChatEntity? = null,
    val gateway: GatewayEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val streamingSearchResults: List<SearchResult> = emptyList(),
    val showThinking: Boolean = true,
    val error: String? = null,
    val showModelPicker: Boolean = false,
    val showChatSettings: Boolean = false
)

class ChatViewModel(
    private val chatId: Long,
    private val chatRepo: ChatRepository,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(showThinking = settingsRepo.showThinkingDefault))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var sendJob: Job? = null
    // Per-message "show thinking" toggle (keyed by message id)
    val thinkingExpanded = mutableStateMapOf<Long, Boolean>()

    init {
        loadChat()
    }

    private fun loadChat() {
        viewModelScope.launch {
            chatRepo.observeChat(chatId).collect { chat ->
                if (chat == null) {
                    _state.update { it.copy(error = "Chat not found") }
                    return@collect
                }
                val gw = gatewayRepo.getGateway(chat.gatewayId)
                val models = gatewayRepo.getCachedModels(chat.gatewayId)
                _state.update { it.copy(chat = chat, gateway = gw, models = models) }
            }
        }
        viewModelScope.launch {
            chatRepo.observeMessages(chatId).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    fun updateInput(text: String) { _state.update { it.copy(inputText = text) } }

    fun toggleThinking() {
        _state.update { it.copy(showThinking = !it.showThinking) }
        settingsRepo.showThinkingDefault = _state.value.showThinking
    }

    fun toggleThinkingForMessage(id: Long) {
        thinkingExpanded[id] = !(thinkingExpanded[id] ?: false)
    }

    fun toggleWebSearch() {
        val chat = _state.value.chat ?: return
        viewModelScope.launch {
            chatRepo.updateChat(chat.copy(webSearchEnabled = !chat.webSearchEnabled))
        }
    }

    fun setShowModelPicker(show: Boolean) {
        // Refresh models when opening
        if (show) {
            val chat = _state.value.chat ?: return
            viewModelScope.launch {
                val models = gatewayRepo.fetchAndCacheModels(_state.value.gateway ?: return@launch)
                _state.update { it.copy(models = models, showModelPicker = show) }
            }
        } else {
            _state.update { it.copy(showModelPicker = show) }
        }
    }

    fun setShowChatSettings(show: Boolean) {
        _state.update { it.copy(showChatSettings = show) }
    }

    fun selectModel(modelId: String) {
        val chat = _state.value.chat ?: return
        viewModelScope.launch {
            chatRepo.updateChat(chat.copy(model = modelId))
            _state.value.gateway?.let { com.cortex.app.CortexApp.instance.settingsStore.setDefaultModel(it.id, modelId) }
            _state.update { it.copy(showModelPicker = false) }
        }
    }

    fun updateChatSettings(
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null,
        topP: Float? = null
    ) {
        val chat = _state.value.chat ?: return
        viewModelScope.launch {
            val updated = chat.copy(
                systemPrompt = systemPrompt ?: chat.systemPrompt,
                temperature = temperature ?: chat.temperature,
                maxTokens = maxTokens ?: chat.maxTokens,
                topP = topP ?: chat.topP
            )
            chatRepo.updateChat(updated)
        }
    }

    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isStreaming) return
        _state.update {
            it.copy(
                inputText = "",
                isStreaming = true,
                streamingContent = "",
                streamingReasoning = "",
                streamingSearchResults = emptyList(),
                error = null
            )
        }

        sendJob = viewModelScope.launch {
            val result = chatRepo.sendMessage(
                chatId = chatId,
                userText = text,
                onToken = { piece ->
                    _state.update { it.copy(streamingContent = it.streamingContent + piece) }
                },
                onReasoning = { piece ->
                    _state.update { it.copy(streamingReasoning = it.streamingReasoning + piece) }
                },
                onSearchResults = { results ->
                    _state.update { it.copy(streamingSearchResults = results) }
                }
            )
            result.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Failed to send message") }
            }
            _state.update { it.copy(isStreaming = false, streamingContent = "", streamingReasoning = "", streamingSearchResults = emptyList()) }
        }
    }

    fun stop() {
        chatRepo.cancelStream()
        sendJob?.cancel()
        _state.update { it.copy(isStreaming = false) }
    }

    fun regenerate(message: MessageEntity) {
        if (_state.value.isStreaming) return
        // Delete the last assistant message and re-send the previous user message
        viewModelScope.launch {
            chatRepo.deleteMessage(message)
            // Find the last user message
            val history = chatRepo.getMessages(chatId)
            val lastUser = history.lastOrNull { it.role == "user" } ?: return@launch
            // Re-inject its text as input and call send
            _state.update { it.copy(inputText = lastUser.content) }
            // Delete the user message (we'll re-add via send)
            chatRepo.deleteMessage(lastUser)
            send()
        }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { chatRepo.deleteMessage(message) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

class ChatViewModelFactory(
    private val chatId: Long,
    private val chatRepo: ChatRepository,
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(chatId, chatRepo, gatewayRepo, settingsRepo) as T
}
