package com.cortex.app.data.repo

import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.ModelData
import com.cortex.app.data.model.ModelInfo
import com.cortex.app.data.model.WebSearchConfig
import com.cortex.app.data.prefs.SettingsStore
import com.cortex.app.data.remote.GatewayClient
import com.cortex.app.data.store.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

class GatewayRepository(
    private val settingsStore: SettingsStore,
    private val gatewayClient: GatewayClient,
    private val store: FileStore<GatewayEntity>
) {
    fun observeGateways(): Flow<List<GatewayEntity>> = store.flow

    suspend fun getAllGateways(): List<GatewayEntity> {
        store.load()
        return store.flow.value
    }

    suspend fun getGateway(id: Long): GatewayEntity? {
        store.load()
        return store.flow.value.find { it.id == id }
    }

    suspend fun getDefaultGateway(): GatewayEntity? {
        store.load()
        return store.flow.value.find { it.isDefault } ?: store.flow.value.firstOrNull()?.also {
            setDefault(it.id)
        }
    }

    private var nextId: Long = 1L

    suspend fun addGateway(name: String, baseUrl: String, apiKey: String, makeDefault: Boolean = false): Long {
        android.util.Log.d("Cortex", "GatewayRepo.addGateway: name=$name, url=$baseUrl, makeDefault=$makeDefault")
        store.load()
        val id = (store.flow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val entity = GatewayEntity(id = id, name = name, baseUrl = baseUrl, apiKey = apiKey, isDefault = false)
        android.util.Log.d("Cortex", "GatewayRepo.addGateway: creating entity id=$id")
        store.add(entity)
        android.util.Log.d("Cortex", "GatewayRepo.addGateway: store.add done, flow size=${store.flow.value.size}")
        if (makeDefault || store.flow.value.size == 1) {
            setDefault(id)
            android.util.Log.d("Cortex", "GatewayRepo.addGateway: set default done")
        }
        return id
    }

    suspend fun updateGateway(gateway: GatewayEntity) {
        store.update({ it.id == gateway.id }) { gateway }
    }

    suspend fun deleteGateway(gateway: GatewayEntity) {
        store.remove { it.id == gateway.id }
        if (gateway.isDefault) {
            store.flow.value.firstOrNull()?.let { setDefault(it.id) }
        }
    }

    suspend fun setDefault(id: Long) {
        store.mutate { list ->
            list.map { it.copy(isDefault = it.id == id) }
        }
        settingsStore.defaultGatewayId = id
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): Result<List<ModelData>> =
        gatewayClient.testConnection(baseUrl, apiKey)

    suspend fun fetchAndCacheModels(gateway: GatewayEntity): List<ModelInfo> {
        val raw = runCatching { gatewayClient.fetchModels(gateway.baseUrl, gateway.apiKey) }.getOrDefault(emptyList())
        val mapped = raw.map { d ->
            ModelInfo(
                id = d.id,
                displayName = d.display_name ?: d.id,
                ownedBy = d.owned_by,
                maxInputTokens = d.max_input_tokens,
                maxOutputTokens = d.max_output_tokens,
                supportsVision = (d.input_modalities ?: emptyList()).any { it.equals("image", true) }
            )
        }
        val jsonStr = kotlinx.serialization.json.Json.encodeToString(
            ListSerializer(ModelData.serializer()),
            raw
        )
        settingsStore.setCachedModels(gateway.id, jsonStr)
        return mapped
    }

    fun getCachedModels(gatewayId: Long): List<ModelInfo> {
        val jsonStr = settingsStore.getCachedModels(gatewayId) ?: return emptyList()
        return runCatching {
            val raw = kotlinx.serialization.json.Json.decodeFromString(
                ListSerializer(ModelData.serializer()),
                jsonStr
            )
            raw.map { d ->
                ModelInfo(
                    id = d.id,
                    displayName = d.display_name ?: d.id,
                    ownedBy = d.owned_by,
                    maxInputTokens = d.max_input_tokens,
                    maxOutputTokens = d.max_output_tokens,
                    supportsVision = (d.input_modalities ?: emptyList()).any { it.equals("image", true) }
                )
            }
        }.getOrDefault(emptyList())
    }
}

class SettingsRepository(private val settingsStore: SettingsStore) {
    val webSearchConfig get() = settingsStore.webSearchConfig
    fun updateWebSearchConfig(config: WebSearchConfig) { settingsStore.webSearchConfig = config }

    var sendOnEnter: Boolean
        get() = settingsStore.sendOnEnter
        set(value) { settingsStore.sendOnEnter = value }
    var streamingEnabled: Boolean
        get() = settingsStore.streamingEnabled
        set(value) { settingsStore.streamingEnabled = value }
    var showThinkingDefault: Boolean
        get() = settingsStore.showThinkingDefault
        set(value) { settingsStore.showThinkingDefault = value }
    var autoTitle: Boolean
        get() = settingsStore.autoTitle
        set(value) { settingsStore.autoTitle = value }
}
