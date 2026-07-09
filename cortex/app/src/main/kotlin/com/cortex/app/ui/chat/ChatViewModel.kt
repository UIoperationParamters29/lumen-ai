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
import java.util.concurrent.atomic.AtomicBoolean

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

    // Atomic guard against double-send (double-tap send button / IME + button).
    // compareAndSet guarantees only ONE caller wins per send cycle.
    private val sendInFlight = AtomicBoolean(false)

    // Throttling for streaming UI updates — prevents jank on long responses
    // while keeping the UI feeling live. 120ms ≈ 8 fps UI updates, plenty for
    // text streaming and light on the main thread.
    private val streamingBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var lastFlushTime = 0L
    private val FLUSH_INTERVAL_MS = 120L

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
                // When streaming has finished, keep the streaming bubble visible
                // UNTIL the final assistant message lands in the list — this
                // eliminates the flicker / content-gap that used to happen when
                // the streaming bubble was cleared before the saved message
                // arrived.
                _state.update { st ->
                    if (!st.isStreaming && st.streamingContent.isNotEmpty()) {
                        val lastAssistant = messages.lastOrNull { it.role == "assistant" }
                        if (lastAssistant != null && !lastAssistant.isStreaming && lastAssistant.content.isNotBlank()) {
                            st.copy(
                                messages = messages,
                                streamingContent = "",
                                streamingReasoning = ""
                            )
                        } else {
                            st.copy(messages = messages)
                        }
                    } else {
                        st.copy(messages = messages)
                    }
                }
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

    /**
     * Send the current input. Atomic guard prevents duplicate sends from
     * double-taps or concurrent IME + button triggers.
     */
    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return
        // Atomic compareAndSet: only the first caller proceeds. This closes
        // the TOCTOU window that previously allowed two user messages.
        if (!sendInFlight.compareAndSet(false, true)) return

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
                // Final flush to ensure all content is shown.
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
                // Keep the streaming bubble visible until the messages flow
                // emits the final assistant message (handled in loadChat).
                // We only flip isStreaming off here; streamingContent stays
                // so the bubble doesn't vanish mid-flicker.
                _state.update {
                    it.copy(
                        isStreaming = false,
                        streamingSearchResults = emptyList()
                    )
                }
                sendInFlight.set(false)
            }
        }
    }

    /** Throttle UI updates to prevent jank on long responses. */
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
        sendInFlight.set(false)
        _state.update {
            it.copy(
                isStreaming = false,
                streamingContent = "",
                streamingReasoning = "",
                streamingSearchResults = emptyList()
            )
        }
    }

    /**
     * Regenerate an assistant response: delete the assistant message + its
     * preceding user message, then re-send the user's text. Uses the same
     * atomic send guard, and waits for the deletes to settle before sending.
     */
    fun regenerate(message: MessageEntity) {
        if (_state.value.isStreaming) return
        if (!sendInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                chatRepo.deleteMessage(message)
                val history = chatRepo.getMessages(chatId)
                val lastUser = history.lastOrNull { it.role == "user" }
                if (lastUser != null) {
                    val userText = lastUser.content
                    chatRepo.deleteMessage(lastUser)

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

                    val result = chatRepo.sendMessage(
                        chatId = chatId,
                        userText = userText,
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
                    _state.update {
                        it.copy(
                            streamingContent = streamingBuffer.toString(),
                            streamingReasoning = reasoningBuffer.toString()
                        )
                    }
                    result.onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "Regenerate failed") }
                    }
                }
            } catch (e: Exception) {
                Log.e("Cortex", "Regenerate exception", e)
                _state.update { it.copy(error = e.message ?: "Regenerate failed") }
            } finally {
                _state.update { it.copy(isStreaming = false, streamingSearchResults = emptyList()) }
                sendInFlight.set(false)
            }
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
