package com.cortex.app.ui.chat

import android.util.Log
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
    val thinkingExpanded = mutableStateMapOf<Long, Boolean>()

    // Throttling for streaming UI updates — prevents crash on long responses
    private val streamingBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var lastFlushTime = 0L
    private val FLUSH_INTERVAL_MS = 80L  // Update UI at most every 80ms

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
        streamingBuffer.setLength(0)
        reasoningBuffer.setLength(0)
        lastFlushTime = System.currentTimeMillis()

        sendJob = viewModelScope.launch {
            try {
                val result = chatRepo.sendMessage(
                    chatId = chatId,
                    userText = text,
                    onToken = { piece ->
                        streamingBuffer.append(piece)
                        flushStreamingIfNeeded()
                    },
                    onReasoning = { piece ->
                        reasoningBuffer.append(piece)
                        flushStreamingIfNeeded()
                    },
                    onSearchResults = { results ->
                        _state.update { it.copy(streamingSearchResults = results) }
                    }
                )
                // Final flush to ensure all content is shown
                _state.update {
                    it.copy(
                        streamingContent = streamingBuffer.toString(),
                        streamingReasoning = reasoningBuffer.toString()
                    )
                }
                result.onFailure { e ->
                    Log.e("Cortex", "Send failed", e)
                    _state.update { it.copy(error = e.message ?: "Failed to send message") }
                }
            } catch (e: Exception) {
                Log.e("Cortex", "Send exception", e)
                _state.update { it.copy(error = e.message ?: "Unknown error") }
            } finally {
                _state.update {
                    it.copy(
                        isStreaming = false,
                        streamingContent = "",
                        streamingReasoning = "",
                        streamingSearchResults = emptyList()
                    )
                }
            }
        }
    }

    /** Throttle UI updates to prevent crash on long responses — max 1 update per 80ms */
    private fun flushStreamingIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastFlushTime >= FLUSH_INTERVAL_MS) {
            lastFlushTime = now
            _state.update {
                it.copy(
                    streamingContent = streamingBuffer.toString(),
                    streamingReasoning = reasoningBuffer.toString()
                )
            }
        }
    }

    fun stop() {
        chatRepo.cancelStream()
        sendJob?.cancel()
        _state.update { it.copy(isStreaming = false) }
    }

    fun regenerate(message: MessageEntity) {
        if (_state.value.isStreaming) return
        viewModelScope.launch {
            chatRepo.deleteMessage(message)
            val history = chatRepo.getMessages(chatId)
            val lastUser = history.lastOrNull { it.role == "user" } ?: return@launch
            _state.update { it.copy(inputText = lastUser.content) }
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
