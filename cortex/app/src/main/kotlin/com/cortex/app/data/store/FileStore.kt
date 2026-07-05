package com.cortex.app.data.store

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple JSON file-based persistence.
 * Auto-loads from disk on init so flows always have data.
 */
class FileStore<T>(
    context: Context,
    private val fileName: String,
    private val serializer: KSerializer<T>
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val listSerializer = ListSerializer(serializer)
    private val _flow = MutableStateFlow<List<T>>(emptyList())
    val flow = _flow.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var loaded = false

    init {
        // Auto-load from disk on creation
        scope.launch { load() }
    }

    private fun file(): File = File(appContext.filesDir, fileName)

    suspend fun load(): List<T> {
        if (loaded) return _flow.value
        return mutex.withLock {
            if (loaded) return@withLock _flow.value
            val f = file()
            val data = if (!f.exists()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString(listSerializer, f.readText())
                }.getOrDefault(emptyList())
            }
            _flow.value = data
            loaded = true
            data
        }
    }

    suspend fun saveAll(items: List<T>) = mutex.withLock {
        file().writeText(json.encodeToString(listSerializer, items))
        _flow.value = items
    }

    suspend fun mutate(transform: (List<T>) -> List<T>): List<T> = mutex.withLock {
        // Ensure loaded
        if (!loaded) {
            val f = file()
            val data = if (!f.exists()) emptyList()
            else runCatching { json.decodeFromString(listSerializer, f.readText()) }.getOrDefault(emptyList())
            _flow.value = data
            loaded = true
        }
        val current = _flow.value
        val updated = transform(current)
        val jsonStr = json.encodeToString(listSerializer, updated)
        file().writeText(jsonStr)
        _flow.value = updated
        android.util.Log.d("Cortex", "FileStore[$fileName]: saved ${updated.size} items, ${jsonStr.length} bytes")
        updated
    }

    suspend fun add(item: T): List<T> = mutate { it + item }
    suspend fun update(predicate: (T) -> Boolean, transform: (T) -> T): List<T> =
        mutate { list -> list.map { if (predicate(it)) transform(it) else it } }
    suspend fun remove(predicate: (T) -> Boolean): List<T> =
        mutate { list -> list.filterNot(predicate) }
}
