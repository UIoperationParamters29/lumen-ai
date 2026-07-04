package com.cortex.app

import android.app.Application
import android.util.Log
import com.cortex.app.data.model.ChatEntity
import com.cortex.app.data.model.GatewayEntity
import com.cortex.app.data.model.MessageEntity
import com.cortex.app.data.prefs.SettingsStore
import com.cortex.app.data.remote.GatewayClient
import com.cortex.app.data.remote.WebSearchProvider
import com.cortex.app.data.repo.ChatRepository
import com.cortex.app.data.repo.GatewayRepository
import com.cortex.app.data.repo.SettingsRepository
import com.cortex.app.data.store.FileStore

class CortexApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var gatewayClient: GatewayClient
        private set
    lateinit var webSearchProvider: WebSearchProvider
        private set

    lateinit var gatewayStore: FileStore<GatewayEntity>
        private set
    lateinit var chatStore: FileStore<ChatEntity>
        private set
    lateinit var messageStore: FileStore<MessageEntity>
        private set

    lateinit var gatewayRepo: GatewayRepository
        private set
    lateinit var chatRepo: ChatRepository
        private set
    lateinit var settingsRepo: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            Log.d("Cortex", "CortexApp.onCreate() starting")
            settingsStore = SettingsStore(this)
            gatewayClient = GatewayClient()
            webSearchProvider = WebSearchProvider()

            gatewayStore = FileStore(this, "gateways.json", GatewayEntity.serializer())
            chatStore = FileStore(this, "chats.json", ChatEntity.serializer())
            messageStore = FileStore(this, "messages.json", MessageEntity.serializer())

            gatewayRepo = GatewayRepository(settingsStore, gatewayClient, gatewayStore)
            settingsRepo = SettingsRepository(settingsStore)
            chatRepo = ChatRepository(chatStore, messageStore, gatewayStore, gatewayClient, webSearchProvider, settingsStore)
            Log.d("Cortex", "CortexApp.onCreate() complete")
        } catch (e: Exception) {
            Log.e("Cortex", "FATAL: CortexApp.onCreate() failed", e)
            throw e
        }
    }

    companion object {
        lateinit var instance: CortexApp
            private set
    }
}
