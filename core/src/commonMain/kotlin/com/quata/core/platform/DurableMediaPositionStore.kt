package com.quata.core.platform

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private data class DurableMediaPositionEntry(
    val mediaId: String,
    val positionMs: Long,
)

/** Actor-scoped playback checkpoints shared by Feed and Official without coupling features. */
class DurableMediaPositionStore(
    private val preferences: PreferenceStore,
    private val storagePrefix: String,
    private val entryLimit: Int = 64,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    init {
        require(storagePrefix.isNotBlank())
        require(entryLimit > 0)
    }

    suspend fun restore(actorId: String?): Map<String, Long> =
        actorLock(actorId).withLock { restoreUnlocked(actorId) }

    suspend fun persistPosition(actorId: String?, mediaId: String, positionMs: Long) {
        if (mediaId.isBlank() || positionMs < 0L) return
        val key = storageKey(actorId)
        (preferences as? AtomicPreferenceStore)?.let { atomicPreferences ->
            runCatching {
                atomicPreferences.updateStringAtomically(key) { raw ->
                    encodeUpdatedPositions(raw, mediaId, positionMs)
                }
            }
            return
        }
        actorLock(actorId).withLock {
            val positions = restoreUnlocked(actorId).toMutableMap()
            positions.remove(mediaId)
            positions[mediaId] = positionMs
            writeUnlocked(actorId, positions)
        }
    }

    private suspend fun restoreUnlocked(actorId: String?): Map<String, Long> {
        val raw = runCatching { preferences.getString(storageKey(actorId)) }.getOrNull()
            ?: return emptyMap()
        return decodePositions(raw)
    }

    private suspend fun writeUnlocked(actorId: String?, positions: Map<String, Long>) {
        val encoded = encodePositions(positions)
        if (encoded == null) {
            runCatching { preferences.remove(storageKey(actorId)) }
        } else {
            runCatching { preferences.putString(storageKey(actorId), encoded) }
        }
    }

    private fun encodeUpdatedPositions(raw: String?, mediaId: String, positionMs: Long): String? {
        val positions = decodePositions(raw).toMutableMap()
        positions.remove(mediaId)
        positions[mediaId] = positionMs
        return encodePositions(positions)
    }

    private fun decodePositions(raw: String?): Map<String, Long> {
        if (raw == null) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyMap()
        if (root["version"]?.jsonPrimitive?.longOrNull != 1L) return emptyMap()
        val entries = runCatching {
            root["entries"]?.jsonArray.orEmpty().mapNotNull { element ->
                val entry = element.jsonObject
                val mediaId = entry["mediaId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val positionMs = entry["positionMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                DurableMediaPositionEntry(mediaId, positionMs)
            }
        }.getOrElse { return emptyMap() }
        return entries
            .asSequence()
            .filter { it.mediaId.isNotBlank() && it.positionMs >= 0L }
            .toList()
            .takeLast(entryLimit)
            .associate { it.mediaId to it.positionMs }
    }

    private fun encodePositions(positions: Map<String, Long>): String? {
        val entries = positions.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.value >= 0L }
            .map { DurableMediaPositionEntry(mediaId = it.key, positionMs = it.value) }
            .toList()
            .takeLast(entryLimit)
        return if (entries.isEmpty()) {
            null
        } else {
            buildJsonObject {
                put("version", 1)
                put("entries", buildJsonArray {
                    entries.forEach { entry ->
                        add(buildJsonObject {
                            put("mediaId", entry.mediaId)
                            put("positionMs", entry.positionMs)
                        })
                    }
                })
            }.toString()
        }
    }

    private fun storageKey(actorId: String?): String =
        storagePrefix + (actorId?.trim()?.takeIf { it.isNotEmpty() } ?: "public")

    private suspend fun actorLock(actorId: String?): Mutex {
        val key = storageKey(actorId)
        return lockRegistryGuard.withLock {
            lockRegistry.getOrPut(preferences) { mutableMapOf() }.getOrPut(key) { Mutex() }
        }
    }

    private companion object {
        val lockRegistryGuard = Mutex()
        val lockRegistry = mutableMapOf<PreferenceStore, MutableMap<String, Mutex>>()
    }
}
