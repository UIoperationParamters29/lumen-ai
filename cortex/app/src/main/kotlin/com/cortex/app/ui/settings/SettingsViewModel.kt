package com.cortex.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cortex.app.data.model.GatewayEntity
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
    val editingGateway: GatewayEntity? = null,
    val showGatewayEditor: Boolean = false
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
        _state.update { it.copy(editingGateway = gateway, showGatewayEditor = true) }
    }

    fun closeGatewayEditor() {
        _state.update { it.copy(editingGateway = null, showGatewayEditor = false) }
    }

    fun saveGateway(name: String, baseUrl: String, apiKey: String, existing: GatewayEntity?) {
        viewModelScope.launch {
            if (existing == null) {
                val isFirst = _state.value.gateways.isEmpty()
                gatewayRepo.addGateway(name, baseUrl, apiKey, makeDefault = isFirst)
            } else {
                gatewayRepo.updateGateway(existing.copy(name = name, baseUrl = baseUrl, apiKey = apiKey))
            }
            closeGatewayEditor()
        }
    }

    fun deleteGateway(gateway: GatewayEntity) {
        viewModelScope.launch { gatewayRepo.deleteGateway(gateway) }
    }

    fun setDefaultGateway(gateway: GatewayEntity) {
        viewModelScope.launch { gatewayRepo.setDefault(gateway.id) }
    }

    fun testGateway(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val result = gatewayRepo.testConnection(baseUrl, apiKey)
            result.onSuccess { models ->
                _state.update { it.copy(testing = false, testResult = "✓ ${models.size} models found") }
            }.onFailure { e ->
                _state.update { it.copy(testing = false, testResult = "✗ ${e.message}") }
            }
        }
    }

    fun clearTestResult() { _state.update { it.copy(testResult = null) } }

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
