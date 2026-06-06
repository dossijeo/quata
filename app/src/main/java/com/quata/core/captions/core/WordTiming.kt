package com.quata.core.captions.core

data class WordTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1f
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    fun shifted(deltaMs: Long): WordTiming =
        copy(startMs = startMs + deltaMs, endMs = endMs + deltaMs)
}
