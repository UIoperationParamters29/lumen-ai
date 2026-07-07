package com.cortex.app.data.repo

import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.ChatMessage
import com.cortex.app.data.model.ChatRequest
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.MessageEntity
import com.cortex.app.data.model.SearchResult
import com.cortex.app.data.prefs.SettingsStore
import com.cortex.app.data.remote.GatewayClient
import com.cortex.app.data.remote.StreamEvent
import com.cortex.app.data.remote.WebSearchProvider
import com.cortex.app.data.store.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class StreamState(
    val isActive: Boolean = false,
    val content: String = "",
    val reasoning: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val error: String? = null
)

class ChatRepository(
    private val chatStore: FileStore<ChatEntity>,
    private val messageStore: FileStore<MessageEntity>,
    private val gatewayStore: FileStore<GatewayEntity>,
    private val gatewayClient: GatewayClient,
    private val webSearchProvider: WebSearchProvider,
    private val settingsStore: SettingsStore
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _streamState = MutableStateFlow(StreamState())
    val streamState: Flow<StreamState> = _streamState.asStateFlow()
    private val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    fun observeChats(): Flow<List<ChatEntity>> = chatStore.flow
    fun observeChat(id: Long): Flow<ChatEntity?> = chatStore.flow.map { list -> list.find { it.id == id } }
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>> =
        messageStore.flow.map { list -> list.filter { it.chatId == chatId }.sortedBy { it.createdAt } }

    suspend fun getChat(id: Long): ChatEntity? {
        chatStore.load()
        return chatStore.flow.value.find { it.id == id }
    }

    suspend fun getMessages(chatId: Long): List<MessageEntity> {
        messageStore.load()
        return messageStore.flow.value.filter { it.chatId == chatId }.sortedBy { it.createdAt }
    }

    suspend fun createChat(
        title: String = "New Chat",
        gatewayId: Long,
        model: String,
        systemPrompt: String = "",
        temperature: Float = 0.7f,
        maxTokens: Int = 2048,
        topP: Float = 1.0f,
        webSearchEnabled: Boolean = false
    ): Long {
        chatStore.load()
        val id = (chatStore.flow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val now = System.currentTimeMillis()
        chatStore.add(
            ChatEntity(
                id = id, title = title, gatewayId = gatewayId, model = model,
                systemPrompt = systemPrompt, temperature = temperature, maxTokens = maxTokens,
                topP = topP, webSearchEnabled = webSearchEnabled, createdAt = now, updatedAt = now
            )
        )
        return id
    }

    suspend fun updateChat(chat: ChatEntity) {
        chatStore.update({ it.id == chat.id }) { chat.copy(updatedAt = System.currentTimeMillis()) }
    }

    suspend fun deleteChat(chat: ChatEntity) {
        chatStore.remove { it.id == chat.id }
        messageStore.remove { it.chatId == chat.id }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        chatStore.update({ it.id == id }) { it.copy(pinned = pinned) }
    }

    private suspend fun touchChat(id: Long) {
        chatStore.update({ it.id == id }) { it.copy(updatedAt = System.currentTimeMillis()) }
    }

    suspend fun deleteMessage(message: MessageEntity) {
        messageStore.remove { it.id == message.id }
    }

    private suspend fun insertMessage(msg: MessageEntity): Long {
        messageStore.load()
        val id = (messageStore.flow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        messageStore.add(msg.copy(id = id))
        return id
    }

    private suspend fun updateMessage(msg: MessageEntity) {
        messageStore.update({ it.id == msg.id }) { msg }
    }

    suspend fun insertUserMessage(chatId: Long, content: String): Long {
        val now = System.currentTimeMillis()
        val id = insertMessage(MessageEntity(chatId = chatId, role = "user", content = content, createdAt = now))
        touchChat(chatId)
        return id
    }

    private suspend fun createAssistantPlaceholder(chatId: Long, model: String): Long {
        return insertMessage(
            MessageEntity(
                chatId = chatId, role = "assistant", content = "", reasoningContent = null,
                model = model, isStreaming = true, createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun sendMessage(
        chatId: Long,
        userText: String,
        onToken: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onSearchResults: (List<SearchResult>) -> Unit
    ): Result<Long> {
        cancelFlag.set(false)
        _streamState.value = StreamState(isActive = true)

        try {
            val chat = getChat(chatId) ?: return Result.failure(IllegalStateException("Chat not found"))
            gatewayStore.load()
            val gateway = gatewayStore.flow.value.find { it.id == chat.gatewayId }
                ?: return Result.failure(IllegalStateException("Gateway not found"))

            insertUserMessage(chatId, userText)

            var searchResultsJson: String? = null
            if (chat.webSearchEnabled) {
                val config = settingsStore.webSearchConfig
                android.util.Log.d("Cortex", "Web search enabled, provider=${config.provider}")
                if (config.provider != com.cortex.app.data.model.SearchProvider.DISABLED) {
                    try {
                        val results = webSearchProvider.search(userText, config)
                        android.util.Log.d("Cortex", "Web search returned ${results.size} results")
                        if (results.isNotEmpty()) {
                            onSearchResults(results)
                            _streamState.value = _streamState.value.copy(searchResults = results)
                            searchResultsJson = serializeSearchResults(results)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("Cortex", "Web search failed (non-fatal): ${e.message}")
                    }
                }
            }

            val history = getMessages(chatId)
            val messages = buildMessagesForApi(history, chat, searchResultsJson)
            val assistantMsgId = createAssistantPlaceholder(chatId, chat.model)
            val request = ChatRequest(
                model = chat.model, messages = messages, temperature = chat.temperature,
                max_tokens = chat.maxTokens, top_p = chat.topP, stream = true
            )

            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var streamError: String? = null
            var lastFlushLen = 0
            var gotError405 = false

            gatewayClient.streamChatCompletion(gateway.baseUrl, gateway.apiKey, request).collect { event ->
                if (cancelFlag.get()) { streamError = "Stopped by user"; return@collect }
                when (event) {
                    is StreamEvent.DeltaContent -> {
                        contentBuilder.append(event.text)
                        onToken(event.text)
                        _streamState.value = _streamState.value.copy(content = contentBuilder.toString())
                        if (contentBuilder.length - lastFlushLen > 40) {
                            updateMessage(
                                MessageEntity(
                                    id = assistantMsgId, chatId = chatId, role = "assistant",
                                    content = contentBuilder.toString(),
                                    reasoningContent = reasoningBuilder.toString().ifEmpty { null },
                                    model = chat.model, isStreaming = true,
                                    createdAt = (history.find { it.id == assistantMsgId }?.createdAt ?: System.currentTimeMillis()),
                                    searchResults = searchResultsJson
                                )
                            )
                            lastFlushLen = contentBuilder.length
                        }
                    }
                    is StreamEvent.DeltaReasoning -> {
                        reasoningBuilder.append(event.text)
                        onReasoning(event.text)
                        _streamState.value = _streamState.value.copy(reasoning = reasoningBuilder.toString())
                    }
                    is StreamEvent.Done -> { /* stream finished */ }
                    is StreamEvent.ToolCall -> { /* tool calls not executed in V1 */ }
                    is StreamEvent.Error -> {
                        streamError = event.message
                        // If 405, mark for fallback to non-streaming
                        if (event.code == 405 || event.message.contains("405")) {
                            gotError405 = true
                        }
                    }
                }
            }

            // FALLBACK: If streaming returned 405, try non-streaming
            if (gotError405 && contentBuilder.isEmpty() && !cancelFlag.get()) {
                android.util.Log.d("Cortex", "Streaming gave 405, falling back to non-streaming")
                streamError = null
                try {
                    val (content, reasoning) = gatewayClient.chatCompletion(gateway.baseUrl, gateway.apiKey, request)
                    contentBuilder.append(content)
                    onToken(content)
                    if (reasoning != null) {
                        reasoningBuilder.append(reasoning)
                        onReasoning(reasoning)
                    }
                    _streamState.value = _streamState.value.copy(content = content, reasoning = reasoning ?: "")
                } catch (e: Exception) {
                    streamError = e.message ?: "Non-streaming fallback failed"
                }
            }

            val finalContent = contentBuilder.toString()
            val existing = messageStore.flow.value.find { it.id == assistantMsgId }
            updateMessage(
                (existing ?: MessageEntity(id = assistantMsgId, chatId = chatId, role = "assistant", content = finalContent)).copy(
                    content = finalContent,
                    reasoningContent = reasoningBuilder.toString().ifEmpty { null },
                    isStreaming = false,
                    errorMessage = streamError,
                    searchResults = searchResultsJson
                )
            )
            touchChat(chatId)

            if (settingsStore.autoTitle && chat.title == "New Chat" && finalContent.isNotBlank()) {
                val newTitle = deriveTitle(userText)
                updateChat(chat.copy(title = newTitle))
            }

            _streamState.value = StreamState(isActive = false)
            return Result.success(assistantMsgId)
        } catch (e: Exception) {
            _streamState.value = _streamState.value.copy(isActive = false, error = e.message)
            return Result.failure(e)
        }
    }

    fun cancelStream() { cancelFlag.set(true) }

    private fun buildMessagesForApi(
        history: List<MessageEntity>,
        chat: ChatEntity,
        searchResultsJson: String?
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (chat.systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = chat.systemPrompt))
        }
        if (searchResultsJson != null) {
            val results = deserializeSearchResults(searchResultsJson)
            if (results.isNotEmpty()) {
                val formatted = webSearchProvider.formatResultsForPrompt(
                    history.lastOrNull { it.role == "user" }?.content ?: "", results
                )
                messages.add(ChatMessage(role = "system", content = formatted))
            }
        }
        history.filter { it.role != "system" && !(it.role == "assistant" && it.content.isEmpty() && it.isStreaming) }
            .forEach { msg ->
                messages.add(ChatMessage(role = msg.role, content = msg.content))
            }
        return messages
    }

    private fun serializeSearchResults(results: List<SearchResult>): String {
        return buildJsonArray {
            results.forEach { r ->
                add(buildJsonObject {
                    put("title", r.title); put("url", r.url); put("snippet", r.snippet)
                })
            }
        }.toString()
    }

    private fun deserializeSearchResults(jsonStr: String): List<SearchResult> = runCatching {
        val arr = json.parseToJsonElement(jsonStr) as JsonArray
        arr.map { el ->
            val o = el as JsonObject
            SearchResult(
                (o["title"] as? JsonPrimitive)?.content ?: "",
                (o["url"] as? JsonPrimitive)?.content ?: "",
                (o["snippet"] as? JsonPrimitive)?.content ?: ""
            )
        }
    }.getOrDefault(emptyList())

    private fun deriveTitle(userText: String): String {
        val clean = userText.trim().replace("\n", " ")
        return if (clean.length <= 40) clean else clean.take(37) + "…"
    }
}
