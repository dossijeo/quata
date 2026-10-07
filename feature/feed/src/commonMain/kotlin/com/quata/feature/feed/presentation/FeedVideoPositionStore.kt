package com.quata.feature.feed.presentation

import com.quata.core.platform.PreferenceStore
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

internal const val FeedVideoPositionStoragePrefix = "quata.feed.video_positions.v1."
internal const val FeedVideoPositionEntryLimit = 64

private data class FeedVideoPositionEntry(
    val mediaId: String,
    val positionMs: Long,
)

/** Durable, actor-scoped playback state. Media IDs combine the stable post ID and video URL. */
class FeedVideoPositionStore(
    private val preferences: PreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun restore(actorId: String?): Map<String, Long> =
        actorLock(actorId).withLock { restoreUnlocked(actorId) }

    suspend fun persistPosition(actorId: String?, mediaId: String, positionMs: Long) {
        if (mediaId.isBlank() || positionMs < 0L) return
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
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyMap()
        if (root["version"]?.jsonPrimitive?.longOrNull != 1L) return emptyMap()
        val entries = runCatching {
            root["entries"]?.jsonArray.orEmpty().mapNotNull { element ->
                val entry = element.jsonObject
                val mediaId = entry["mediaId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val positionMs = entry["positionMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                FeedVideoPositionEntry(mediaId, positionMs)
            }
        }.getOrElse { return emptyMap() }
        return entries
            .asSequence()
            .filter { it.mediaId.isNotBlank() && it.positionMs >= 0L }
            .toList()
            .takeLast(FeedVideoPositionEntryLimit)
            .associate { it.mediaId to it.positionMs }
    }

    private suspend fun writeUnlocked(actorId: String?, positions: Map<String, Long>) {
        val entries = positions.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.value >= 0L }
            .map { FeedVideoPositionEntry(mediaId = it.key, positionMs = it.value) }
            .toList()
            .takeLast(FeedVideoPositionEntryLimit)
        if (entries.isEmpty()) {
            runCatching { preferences.remove(storageKey(actorId)) }
        } else {
            val encoded = buildJsonObject {
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
            runCatching {
                preferences.putString(storageKey(actorId), encoded)
            }
        }
    }

    private fun storageKey(actorId: String?): String =
        FeedVideoPositionStoragePrefix + (actorId?.trim()?.takeIf { it.isNotEmpty() } ?: "public")

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

internal fun feedVideoPositionMediaId(postId: String, videoUrl: String): String =
    "$postId\u001f${videoUrl.trim()}"
