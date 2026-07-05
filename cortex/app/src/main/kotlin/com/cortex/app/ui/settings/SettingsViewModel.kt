package com.cortex.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.ModelInfo
import com.cortex.app.data.model.SearchProvider
import com.cortex.app.data.model.WebSearchConfig
import com.cortex.app.data.repo.GatewayRepository
import com.cortex.app.data.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val gateways: List<GatewayEntity> = emptyList(),
    val webSearchConfig: WebSearchConfig = WebSearchConfig(),
    val sendOnEnter: Boolean = false,
    val streamingEnabled: Boolean = true,
    val showThinkingDefault: Boolean = true,
    val autoTitle: Boolean = true,
    val testing: Boolean = false,
    val testResult: String? = null,
    val testModels: List<ModelInfo> = emptyList(),
    val editingGateway: GatewayEntity? = null,
    val showGatewayEditor: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null
)

class SettingsViewModel(
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(
        webSearchConfig = settingsRepo.webSearchConfig,
        sendOnEnter = settingsRepo.sendOnEnter,
        streamingEnabled = settingsRepo.streamingEnabled,
        showThinkingDefault = settingsRepo.showThinkingDefault,
        autoTitle = settingsRepo.autoTitle
    ))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            gatewayRepo.observeGateways().collect { gws ->
                _state.update { it.copy(gateways = gws) }
            }
        }
    }

    fun openGatewayEditor(gateway: GatewayEntity? = null) {
        _state.update { it.copy(editingGateway = gateway, showGatewayEditor = true, testResult = null, testModels = emptyList(), saveError = null) }
    }

    fun closeGatewayEditor() {
        if (_state.value.saving) return // prevent dismiss during save
        _state.update { it.copy(editingGateway = null, showGatewayEditor = false, testResult = null, testModels = emptyList(), saveError = null) }
    }

    fun testGateway(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null, testModels = emptyList()) }
            val result = gatewayRepo.testConnection(baseUrl, apiKey)
            result.onSuccess { models ->
                val mapped = models.map { d ->
                    ModelInfo(
                        id = d.id,
                        displayName = d.display_name ?: d.id,
                        ownedBy = d.owned_by,
                        maxInputTokens = d.max_input_tokens,
                        maxOutputTokens = d.max_output_tokens,
                        supportsVision = (d.input_modalities ?: emptyList()).any { it.equals("image", true) }
                    )
                }
                _state.update {
                    it.copy(
                        testing = false,
                        testResult = "✓ ${models.size} models found",
                        testModels = mapped
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(testing = false, testResult = "✗ ${e.message ?: "Connection failed"}") }
            }
        }
    }

    fun saveGateway(name: String, baseUrl: String, apiKey: String, existing: GatewayEntity?) {
        if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank()) {
            _state.update { it.copy(saveError = "All fields are required") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            try {
                Log.d("Cortex", "Saving gateway: name=$name, url=$baseUrl, existing=${existing?.id}")
                val gatewayId: Long
                if (existing == null) {
                    // Check existing gateways from the store (not just state which may be stale)
                    val allGateways = gatewayRepo.getAllGateways()
                    val isFirst = allGateways.isEmpty()
                    gatewayId = gatewayRepo.addGateway(name, baseUrl, apiKey, makeDefault = isFirst)
                    Log.d("Cortex", "Created new gateway with id=$gatewayId")
                } else {
                    gatewayRepo.updateGateway(existing.copy(name = name, baseUrl = baseUrl, apiKey = apiKey))
                    gatewayId = existing.id
                    Log.d("Cortex", "Updated gateway id=$gatewayId")
                }

                // Auto-fetch and cache models so model picker works immediately
                try {
                    val savedGw = gatewayRepo.getGateway(gatewayId)
                    if (savedGw != null) {
                        val models = gatewayRepo.fetchAndCacheModels(savedGw)
                        Log.d("Cortex", "Cached ${models.size} models for gateway $gatewayId")
                        // Set a default model if none set
                        val currentDefault = com.cortex.app.CortexApp.instance.settingsStore.getDefaultModel(gatewayId)
                        if (currentDefault == null && models.isNotEmpty()) {
                            com.cortex.app.CortexApp.instance.settingsStore.setDefaultModel(gatewayId, models.first().id)
                            Log.d("Cortex", "Set default model: ${models.first().id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Cortex", "Model fetch failed (non-fatal): ${e.message}")
                    // Don't fail the save if model fetch fails
                }

                // Verify the save worked
                val verify = gatewayRepo.getGateway(gatewayId)
                if (verify == null) {
                    _state.update { it.copy(saving = false, saveError = "Failed to save gateway") }
                    return@launch
                }

                Log.d("Cortex", "Gateway saved and verified: ${verify.name}")
                _state.update { it.copy(saving = false, showGatewayEditor = false, editingGateway = null, testResult = null, testModels = emptyList(), saveError = null) }
            } catch (e: Exception) {
                Log.e("Cortex", "Save gateway failed", e)
                _state.update { it.copy(saving = false, saveError = "Save failed: ${e.message ?: "Unknown error"}") }
            }
        }
    }

    fun deleteGateway(gateway: GatewayEntity) {
        viewModelScope.launch { gatewayRepo.deleteGateway(gateway) }
    }

    fun setDefaultGateway(gateway: GatewayEntity) {
        viewModelScope.launch { gatewayRepo.setDefault(gateway.id) }
    }

    fun clearTestResult() { _state.update { it.copy(testResult = null, testModels = emptyList()) } }
    fun clearSaveError() { _state.update { it.copy(saveError = null) } }

    fun updateSearchProvider(provider: SearchProvider) {
        val cfg = _state.value.webSearchConfig.copy(provider = provider)
        settingsRepo.updateWebSearchConfig(cfg)
        _state.update { it.copy(webSearchConfig = cfg) }
    }
    fun updateSearchMaxResults(max: Int) {
        val cfg = _state.value.webSearchConfig.copy(maxResults = max)
        settingsRepo.updateWebSearchConfig(cfg)
        _state.update { it.copy(webSearchConfig = cfg) }
    }
    fun updateSearchApiKey(key: String) {
        val cfg = _state.value.webSearchConfig.copy(apiKey = key)
        settingsRepo.updateWebSearchConfig(cfg)
        _state.update { it.copy(webSearchConfig = cfg) }
    }
    fun updateSearchInstanceUrl(url: String) {
        val cfg = _state.value.webSearchConfig.copy(instanceUrl = url)
        settingsRepo.updateWebSearchConfig(cfg)
        _state.update { it.copy(webSearchConfig = cfg) }
    }

    fun setSendOnEnter(v: Boolean) { settingsRepo.sendOnEnter = v; _state.update { it.copy(sendOnEnter = v) } }
    fun setStreamingEnabled(v: Boolean) { settingsRepo.streamingEnabled = v; _state.update { it.copy(streamingEnabled = v) } }
    fun setShowThinkingDefault(v: Boolean) { settingsRepo.showThinkingDefault = v; _state.update { it.copy(showThinkingDefault = v) } }
    fun setAutoTitle(v: Boolean) { settingsRepo.autoTitle = v; _state.update { it.copy(autoTitle = v) } }
}

class SettingsViewModelFactory(
    private val gatewayRepo: GatewayRepository,
    private val settingsRepo: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(gatewayRepo, settingsRepo) as T
}
