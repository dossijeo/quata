package com.quata.feature.feed.presentation

import com.quata.core.platform.DurableMediaPositionStore
import com.quata.core.platform.PreferenceStore
import kotlinx.serialization.json.Json

internal const val FeedVideoPositionStoragePrefix = "quata.feed.video_positions.v1."
internal const val FeedVideoPositionEntryLimit = 64

/** Durable, actor-scoped playback state. Media IDs combine the stable post ID and video URL. */
class FeedVideoPositionStore(
    preferences: PreferenceStore,
    json: Json = Json { ignoreUnknownKeys = true },
) {
    private val delegate = DurableMediaPositionStore(
        preferences = preferences,
        storagePrefix = FeedVideoPositionStoragePrefix,
        entryLimit = FeedVideoPositionEntryLimit,
        json = json,
    )

    suspend fun restore(actorId: String?): Map<String, Long> = delegate.restore(actorId)

    suspend fun persistPosition(actorId: String?, mediaId: String, positionMs: Long) =
        delegate.persistPosition(actorId, mediaId, positionMs)
}

internal fun feedVideoPositionMediaId(postId: String, videoUrl: String): String =
    "$postId\u001f${videoUrl.trim()}"
