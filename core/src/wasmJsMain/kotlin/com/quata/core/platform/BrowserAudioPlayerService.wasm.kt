@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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
        let objectUrl = null;
        store.set(id, element);
        let completed = false;
        let loadTimer = null;
        const controller = typeof globalThis.AbortController === 'function' ? new globalThis.AbortController() : null;
        const complete = (value) => {
          if (completed) return;
          completed = true;
          if (loadTimer != null) globalThis.clearTimeout(loadTimer);
          onResult(value);
        };
        const cleanup = () => {
          if (controller) controller.abort();
          element.onloadedmetadata = null;
          element.oncanplay = null;
          element.onerror = null;
          element.pause();
          element.removeAttribute('src');
          element.load();
          if (objectUrl && globalThis.URL?.revokeObjectURL) globalThis.URL.revokeObjectURL(objectUrl);
          objectUrl = null;
          store.delete(id);
        };
        loadTimer = globalThis.setTimeout(() => {
          cleanup();
          complete('failure:web_audio_load_timeout');
        }, 5000);
        const state = () => JSON.stringify({
          isLoaded: element.readyState > 0,
          isPlaying: !element.paused && !element.ended,
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
        });
        const resolveSource = () => {
          if (/^blob:/i.test(source)) return Promise.resolve(source);
          if (!/^https?:/i.test(source) || typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
            return Promise.reject(new Error('web_audio_reference_unsupported'));
          }
          return globalThis.fetch(source, { credentials: 'omit', cache: 'no-store', ...(controller ? { signal: controller.signal } : {}) })
            .then(async (response) => {
              if (!response.ok) throw new Error(`web_audio_http_${'$'}{response.status}`);
              const blob = await response.blob();
              if (completed) return null;
              if (!blob || !Number.isFinite(blob.size) || blob.size <= 0) throw new Error('web_audio_blob_empty');
              const nextObjectUrl = globalThis.URL.createObjectURL(blob);
              if (completed) {
                if (globalThis.URL?.revokeObjectURL) globalThis.URL.revokeObjectURL(nextObjectUrl);
                return null;
              }
              objectUrl = nextObjectUrl;
              return nextObjectUrl;
            });
        };
        resolveSource().then((playableSource) => {
          if (completed || !playableSource) return;
          element.preload = 'metadata';
          element.onloadedmetadata = () => complete(state());
          element.oncanplay = () => complete(state());
          element.onerror = () => {
            cleanup();
            complete('failure:web_audio_load_failed');
          };
          element.src = playableSource;
          element.load();
        }).catch((error) => {
          if (completed) return;
          cleanup();
          complete('failure:' + (error?.message || 'web_audio_load_failed'));
        });
      } catch (_) { onResult('unsupported'); }
    })()
    """,
)

private fun browserAudioPlay(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => {
      const element = globalThis.__quataAudioPlayers?.get(id);
      if (!element) return onResult('failure:web_audio_not_loaded');
      const state = () => JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0});
      try { const result = element.play(); if (result?.then) result.then(() => onResult(state())).catch((error) => onResult('failure:' + (error?.name || 'web_audio_play_failed'))); else onResult(state()); } catch (error) { onResult('failure:' + (error?.name || 'web_audio_play_failed')); }
    })()""",
)

private fun browserAudioPause(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); element.pause(); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: false, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0})); })()""",
)

private fun browserAudioSeek(id: String, positionMillis: Long, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); const duration = Number.isFinite(element.duration) && element.duration >= 0 ? element.duration : Number.MAX_VALUE; element.currentTime = Math.min(duration, positionMillis / 1000); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0})); })()""",
)

private fun browserAudioState(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); const durationMillis = Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0; const positionMillis = element.ended && durationMillis > 0 ? durationMillis : Math.max(0, Math.floor((element.currentTime || 0) * 1000)); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis, durationMillis})); })()""",
)

private fun browserAudioStop(id: String, onComplete: () -> Unit): Unit = js(
    """(() => { const store = globalThis.__quataAudioPlayers; const element = store?.get(id); if (element) { const source = element.currentSrc || element.src || ''; element.pause(); element.removeAttribute('src'); element.load(); if (/^blob:/i.test(source) && globalThis.URL?.revokeObjectURL) globalThis.URL.revokeObjectURL(source); store.delete(id); } onComplete(); })()""",
)

private const val BrowserAudioUnsupported = "unsupported"
private const val BrowserAudioFailurePrefix = "failure:"
