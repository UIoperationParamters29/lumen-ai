package com.cortex.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GatewayEntity(
    var id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0
)

@Serializable
data class ChatEntity(
    var id: Long = 0,
    val title: String,
    val gatewayId: Long,
    val model: String,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 1.0f,
    val webSearchEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

@Serializable
data class MessageEntity(
    var id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val searchResults: String? = null
)

// UI models
data class Gateway(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val isDefault: Boolean,
    val models: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val id: String,
    val displayName: String? = null,
    val ownedBy: String? = null,
    val maxInputTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsVision: Boolean = false
)

data class Chat(
    val id: Long,
    val title: String,
    val gatewayId: Long,
    val model: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val webSearchEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean
)

data class Message(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val reasoningContent: String?,
    val model: String?,
    val createdAt: Long,
    val isStreaming: Boolean,
    val errorMessage: String?,
    val searchResults: String?
)

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

@Serializable
data class WebSearchConfig(
    val provider: SearchProvider = SearchProvider.DUCK_DUCK_GO,
    val maxResults: Int = 5,
    val apiKey: String = "",
    val instanceUrl: String = ""
)

@Serializable
enum class SearchProvider(val displayName: String, val requiresKey: Boolean, val requiresInstance: Boolean) {
    DISABLED("Disabled", false, false),
    DUCK_DUCK_GO("DuckDuckGo (Free)", false, false),
    EXA("Exa", true, false),
    FIRECRAWL("Firecrawl", true, false),
    SEARXNG("SearXNG", false, true)
}
