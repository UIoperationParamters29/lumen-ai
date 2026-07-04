package com.cortex.app.data.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple JSON file-based persistence.
 * Each entity type is stored as a JSON array in its own file.
 * Thread-safe via Mutex. Observability via StateFlow.
 */
class FileStore<T>(
    private val context: Context,
    private val fileName: String,
    private val serializer: KSerializer<T>
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val listSerializer = ListSerializer(serializer)
    private val mutex = Mutex()
    private val _flow = MutableStateFlow<List<T>>(emptyList())
    val flow = _flow

    private fun file(): File = File(context.filesDir, fileName)

    suspend fun load(): List<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file()
            if (!f.exists()) {
                _flow.value = emptyList()
                return@withContext emptyList()
            }
            val data = runCatching {
                json.decodeFromString(listSerializer, f.readText())
            }.getOrDefault(emptyList())
            _flow.value = data
            data
        }
    }

    suspend fun saveAll(items: List<T>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file().writeText(json.encodeToString(listSerializer, items))
            _flow.value = items
        }
    }

    suspend fun mutate(transform: (List<T>) -> List<T>): List<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _flow.value.let {
                if (it.isEmpty() && file().exists()) {
                    runCatching { json.decodeFromString(listSerializer, file().readText()) }.getOrDefault(emptyList())
                } else it
            }
            val updated = transform(current)
            file().writeText(json.encodeToString(listSerializer, updated))
            _flow.value = updated
            updated
        }
    }

    suspend fun add(item: T): List<T> = mutate { it + item }
    suspend fun update(predicate: (T) -> Boolean, transform: (T) -> T): List<T> =
        mutate { list -> list.map { if (predicate(it)) transform(it) else it } }
    suspend fun remove(predicate: (T) -> Boolean): List<T> =
        mutate { list -> list.filterNot(predicate) }
}
