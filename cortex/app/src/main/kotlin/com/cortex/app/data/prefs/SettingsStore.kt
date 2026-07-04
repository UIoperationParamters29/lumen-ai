package com.cortex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cortex.app.data.model.SearchProvider
import com.cortex.app.data.model.WebSearchConfig

class SettingsStore(context: Context) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "cortex_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Default gateway id (cached)
    var defaultGatewayId: Long
        get() = prefs.getLong(KEY_DEFAULT_GATEWAY, -1L)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_GATEWAY, value).apply()

    // Web search config
    var webSearchConfig: WebSearchConfig
        get() {
            val name = prefs.getString(KEY_SEARCH_PROVIDER, SearchProvider.DUCK_DUCK_GO.name) ?: SearchProvider.DUCK_DUCK_GO.name
            val provider = runCatching { SearchProvider.valueOf(name) }.getOrDefault(SearchProvider.DUCK_DUCK_GO)
            return WebSearchConfig(
                provider = provider,
                maxResults = prefs.getInt(KEY_SEARCH_MAX, 5),
                apiKey = prefs.getString(KEY_SEARCH_KEY, "") ?: "",
                instanceUrl = prefs.getString(KEY_SEARCH_URL, "") ?: ""
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_SEARCH_PROVIDER, value.provider.name)
                .putInt(KEY_SEARCH_MAX, value.maxResults)
                .putString(KEY_SEARCH_KEY, value.apiKey)
                .putString(KEY_SEARCH_URL, value.instanceUrl)
                .apply()
        }

    // Default model per gateway (gatewayId -> modelId)
    fun getDefaultModel(gatewayId: Long): String? = prefs.getString("$KEY_DEFAULT_MODEL$gatewayId", null)
    fun setDefaultModel(gatewayId: Long, model: String) = prefs.edit().putString("$KEY_DEFAULT_MODEL$gatewayId", model).apply()

    // Theme accent (cached for perf)
    var accentColor: String
        get() = prefs.getString(KEY_ACCENT, "blue") ?: "blue"
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    // Send on enter
    var sendOnEnter: Boolean
        get() = prefs.getBoolean(KEY_SEND_ENTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEND_ENTER, value).apply()

    // Streaming enabled
    var streamingEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAMING, true)
        set(value) = prefs.edit().putBoolean(KEY_STREAMING, value).apply()

    // Show thinking by default
    var showThinkingDefault: Boolean
        get() = prefs.getBoolean(KEY_SHOW_THINKING, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_THINKING, value).apply()

    // Dynamic title generation
    var autoTitle: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TITLE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TITLE, value).apply()

    // Cached models per gateway (JSON string)
    fun getCachedModels(gatewayId: Long): String? = prefs.getString("$KEY_CACHED_MODELS$gatewayId", null)
    fun setCachedModels(gatewayId: Long, json: String) = prefs.edit().putString("$KEY_CACHED_MODELS$gatewayId", json).apply()

    companion object {
        private const val KEY_DEFAULT_GATEWAY = "default_gateway"
        private const val KEY_SEARCH_PROVIDER = "search_provider"
        private const val KEY_SEARCH_MAX = "search_max"
        private const val KEY_SEARCH_KEY = "search_key"
        private const val KEY_SEARCH_URL = "search_url"
        private const val KEY_DEFAULT_MODEL = "default_model_"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_SEND_ENTER = "send_enter"
        private const val KEY_STREAMING = "streaming"
        private const val KEY_SHOW_THINKING = "show_thinking"
        private const val KEY_AUTO_TITLE = "auto_title"
        private const val KEY_CACHED_MODELS = "cached_models_"
    }
}
