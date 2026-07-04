package com.cortex.app.data.remote

import com.cortex.app.data.model.ChatRequest
import com.cortex.app.data.model.ModelData
import com.cortex.app.data.model.ModelsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Streaming event emitted by the gateway during a chat completion. */
sealed class StreamEvent {
    data class DeltaContent(val text: String) : StreamEvent()
    data class DeltaReasoning(val text: String) : StreamEvent()
    data class ToolCall(val toolCallId: String, val name: String, val arguments: String) : StreamEvent()
    data class Done(val finishReason: String?) : StreamEvent()
    data class Error(val message: String, val code: Int? = null) : StreamEvent()
}

class GatewayClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Fetch list of models from a gateway. */
    suspend fun fetchModels(baseUrl: String, apiKey: String): List<ModelData> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/models"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string()?.take(300) ?: "no body"
                    throw IOException("HTTP ${resp.code}: $errBody")
                }
                val body = resp.body?.string() ?: throw IOException("Empty response body")
                val parsed = json.decodeFromString(ModelsResponse.serializer(), body)
                parsed.data
            }
        } catch (e: Exception) {
            throw IOException("Failed to fetch models: ${e.message}", e)
        }
    }

    /** Quick auth/connection test against /models. */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<List<ModelData>> =
        withContext(Dispatchers.IO) {
            runCatching { fetchModels(baseUrl, apiKey) }
        }

    /**
     * Streaming chat completion. Emits StreamEvent items.
     * Uses raw line-by-line parsing (more reliable than okhttp-sse for various gateway quirks).
     */
    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Flow<StreamEvent> = callbackFlow {
        val streamReq = request.copy(stream = true)
        val payload = json.encodeToString(ChatRequest.serializer(), streamReq)
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(req)
        val response = try {
            call.execute()
        } catch (e: Exception) {
            trySend(StreamEvent.Error(e.message ?: "Network error"))
            channel.close()
            return@callbackFlow
        }

        val body = response.body ?: run {
            trySend(StreamEvent.Error("No response body", response.code))
            response.close()
            channel.close()
            return@callbackFlow
        }

        // Launch a reader coroutine inside this callbackFlow scope
        launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            try {
                if (!response.isSuccessful) {
                    val errText = reader.readText()
                    trySend(StreamEvent.Error("HTTP ${response.code}: ${errText.take(500)}", response.code))
                    return@launch
                }
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (channel.isClosedForSend) break
                    val l = line ?: continue
                    if (l.isEmpty() || l.startsWith(":")) continue
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") {
                        trySend(StreamEvent.Done(null))
                        break
                    }
                    try {
                        val obj = json.parseToJsonElement(data).jsonObject
                        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                        val delta = choice["delta"]?.jsonObject
                        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull

                        delta?.get("content")?.jsonPrimitive?.contentOrNull?.let { c ->
                            if (c.isNotEmpty()) trySend(StreamEvent.DeltaContent(c))
                        }
                        delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull?.let { r ->
                            if (r.isNotEmpty()) trySend(StreamEvent.DeltaReasoning(r))
                        }
                        delta?.get("tool_calls")?.jsonArray?.forEach { tc ->
                            val tcObj = tc.jsonObject
                            val id = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            val func = tcObj["function"]?.jsonObject ?: return@forEach
                            val name = func["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                            val args = func["arguments"]?.jsonPrimitive?.contentOrNull ?: ""
                            trySend(StreamEvent.ToolCall(id, name, args))
                        }

                        if (finishReason != null) {
                            trySend(StreamEvent.Done(finishReason))
                        }
                    } catch (_: Exception) {
                        // Swallow malformed chunks
                    }
                }
            } catch (e: Exception) {
                if (!channel.isClosedForSend) {
                    trySend(StreamEvent.Error(e.message ?: "Stream error"))
                }
            } finally {
                runCatching { reader.close() }
                runCatching { response.close() }
                if (!channel.isClosedForSend) trySend(StreamEvent.Done(null))
                channel.close()
            }
        }

        awaitClose {
            runCatching { call.cancel() }
        }
    }.flowOn(Dispatchers.IO)
}
