package com.quata.core.media

import android.media.MediaMetadataRetriever
import android.util.Log

private const val MetadataRetrieverTag = "QuataMetadataRetriever"

inline fun <T> withQuataMediaMetadataRetriever(block: (MediaMetadataRetriever) -> T): T {
    val retriever = MediaMetadataRetriever()
    return try {
        block(retriever)
    } finally {
        retriever.releaseQuataSafely()
    }
}

fun MediaMetadataRetriever.releaseQuataSafely() {
    runCatching { release() }
        .onFailure { Log.w(MetadataRetrieverTag, "MediaMetadataRetriever release failed", it) }
}
