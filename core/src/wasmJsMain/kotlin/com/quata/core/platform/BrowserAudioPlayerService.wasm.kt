package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Browser-backed audio player for portable attachment references (including Blob URLs).
 *
 * Kotlin/Wasm does not ship the legacy Kotlin/JS DOM bindings, therefore the element stays in a
 * small browser-side registry addressed by a generated id. The bridge still uses a real
 * `HTMLAudioElement`; it merely keeps DOM types out of the portable Kotlin signature.
 */
class BrowserAudioPlayerService : AudioPlayerService {
    private var audioId: String? = null
    private var nextId = 0L

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        if (file.reference.isBlank()) return PlatformResult.Failure("web_audio_reference_missing")
        stop()
        val id = "quata-audio-${++nextId}"
        return browserAudioResult { onResult -> browserAudioLoad(id, file.reference, onResult) }
            .also { result -> if (result is PlatformResult.Success) audioId = id }
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = withAudioId { id, onResult ->
        browserAudioPlay(id, onResult)
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = withAudioId { id, onResult ->
        browserAudioPause(id, onResult)
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = withAudioId { id, onResult ->
        browserAudioSeek(id, positionMillis.coerceAtLeast(0L), onResult)
    }

    override suspend fun stop(): PlatformResult<Unit> {
        val id = audioId ?: return PlatformResult.Success(Unit)
        audioId = null
        return suspendCoroutine { continuation ->
            browserAudioStop(id) { continuation.resume(PlatformResult.Success(Unit)) }
        }
    }

    override suspend fun state(): AudioPlaybackState {
        val id = audioId ?: return AudioPlaybackState()
        return when (val result = browserAudioResult { onResult -> browserAudioState(id, onResult) }) {
            is PlatformResult.Success -> result.value
            else -> AudioPlaybackState()
        }
    }

    private suspend fun withAudioId(
        action: (String, (String) -> Unit) -> Unit,
    ): PlatformResult<AudioPlaybackState> {
        val id = audioId ?: return PlatformResult.Failure("web_audio_not_loaded")
        return browserAudioResult { onResult -> action(id, onResult) }
    }
}

private suspend fun browserAudioResult(
    request: ((String) -> Unit) -> Unit,
): PlatformResult<AudioPlaybackState> = suspendCoroutine { continuation ->
    request { result -> continuation.resume(result.toAudioResult()) }
}

private fun String.toAudioResult(): PlatformResult<AudioPlaybackState> = when {
    this == BrowserAudioUnsupported -> PlatformResult.Unsupported
    startsWith(BrowserAudioFailurePrefix) -> PlatformResult.Failure(removePrefix(BrowserAudioFailurePrefix))
    else -> runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        PlatformResult.Success(
            AudioPlaybackState(
                isLoaded = value["isLoaded"]?.jsonPrimitive?.booleanOrNull == true,
                isPlaying = value["isPlaying"]?.jsonPrimitive?.booleanOrNull == true,
                positionMillis = value["positionMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
                durationMillis = value["durationMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
            ),
        )
    }.getOrElse { PlatformResult.Failure("web_audio_state_invalid") }
}

private fun browserAudioLoad(id: String, source: String, onResult: (String) -> Unit): Unit = js(
    """
    (() => {
      try {
        const document = globalThis.document;
        if (!document || typeof document.createElement !== 'function') return onResult('unsupported');
        const element = document.createElement('audio');
        if (typeof element.play !== 'function' || typeof element.pause !== 'function') return onResult('unsupported');
        const store = globalThis.__quataAudioPlayers || (globalThis.__quataAudioPlayers = new Map());
        store.set(id, element);
        const state = () => JSON.stringify({
          isLoaded: element.readyState > 0,
          isPlaying: !element.paused && !element.ended,
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
        });
        element.preload = 'metadata';
        element.onloadedmetadata = () => onResult(state());
        element.onerror = () => onResult('failure:web_audio_load_failed');
        element.src = source;
        element.load();
      } catch (_) { onResult('unsupported'); }
    })();
    """,
)

private fun browserAudioPlay(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => {
      const element = globalThis.__quataAudioPlayers?.get(id);
      if (!element) return onResult('failure:web_audio_not_loaded');
      const state = () => JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0});
      try { const result = element.play(); if (result?.then) result.then(() => onResult(state())).catch((error) => onResult('failure:' + (error?.name || 'web_audio_play_failed'))); else onResult(state()); } catch (error) { onResult('failure:' + (error?.name || 'web_audio_play_failed')); }
    })();""",
)

private fun browserAudioPause(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); element.pause(); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: false, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0})); })();""",
)

private fun browserAudioSeek(id: String, positionMillis: Long, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); const duration = Number.isFinite(element.duration) && element.duration >= 0 ? element.duration : Number.MAX_VALUE; element.currentTime = Math.min(duration, positionMillis / 1000); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0})); })();""",
)

private fun browserAudioState(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0})); })();""",
)

private fun browserAudioStop(id: String, onComplete: () -> Unit): Unit = js(
    """(() => { const store = globalThis.__quataAudioPlayers; const element = store?.get(id); if (element) { element.pause(); element.removeAttribute('src'); element.load(); store.delete(id); } onComplete(); })();""",
)

private const val BrowserAudioUnsupported = "unsupported"
private const val BrowserAudioFailurePrefix = "failure:"
