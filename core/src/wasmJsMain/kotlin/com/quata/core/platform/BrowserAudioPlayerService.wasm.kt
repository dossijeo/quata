@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
    private var sessionId = 0L
    private val eventSink = MutableSharedFlow<AudioPlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AudioPlaybackEvent> = eventSink.asSharedFlow()

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        if (file.reference.isBlank()) return PlatformResult.Failure("web_audio_reference_missing")
        stop()
        val id = "quata-audio-${++nextId}"
        val nextSessionId = ++sessionId
        audioId = id
        val result = browserAudioResult(nextSessionId) { onResult ->
            browserAudioLoad(id, nextSessionId, file.reference, onResult, ::emitBrowserAudioEvent)
        }
        if (result !is PlatformResult.Success && audioId == id) {
            audioId = null
        }
        return result
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
        sessionId += 1L
        return suspendCoroutine { continuation ->
            browserAudioStop(id) { continuation.resume(PlatformResult.Success(Unit)) }
        }
    }

    override suspend fun state(): AudioPlaybackState {
        val id = audioId ?: return AudioPlaybackState()
        return when (val result = browserAudioResult(sessionId) { onResult -> browserAudioState(id, onResult) }) {
            is PlatformResult.Success -> result.value
            else -> AudioPlaybackState()
        }
    }

    private suspend fun withAudioId(
        action: (String, (String) -> Unit) -> Unit,
    ): PlatformResult<AudioPlaybackState> {
        val id = audioId ?: return PlatformResult.Failure("web_audio_not_loaded")
        return browserAudioResult(sessionId) { onResult -> action(id, onResult) }
    }

    private fun emitBrowserAudioEvent(value: String) {
        value.toAudioEvent()?.let { eventSink.tryEmit(it) }
    }
}

private suspend fun browserAudioResult(
    sessionId: Long,
    request: ((String) -> Unit) -> Unit,
): PlatformResult<AudioPlaybackState> = suspendCoroutine { continuation ->
    request { result -> continuation.resume(result.toAudioResult(sessionId)) }
}

private fun String.toAudioResult(sessionId: Long): PlatformResult<AudioPlaybackState> = when {
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
                phase = value["phase"]?.jsonPrimitive?.contentOrNull?.toAudioPlaybackPhase()
                    ?: if (value["isPlaying"]?.jsonPrimitive?.booleanOrNull == true) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Ready,
                sessionId = value["sessionId"]?.jsonPrimitive?.longOrNull ?: sessionId,
            ),
        )
    }.getOrElse { PlatformResult.Failure("web_audio_state_invalid") }
}

private fun String.toAudioEvent(): AudioPlaybackEvent? = runCatching {
    val root = Json.parseToJsonElement(this).jsonObject
    val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val sessionId = root["sessionId"]?.jsonPrimitive?.longOrNull ?: 0L
    val state = root["state"]?.jsonObject ?: return null
    val playback = AudioPlaybackState(
        isLoaded = state["isLoaded"]?.jsonPrimitive?.booleanOrNull == true,
        isPlaying = state["isPlaying"]?.jsonPrimitive?.booleanOrNull == true,
        positionMillis = state["positionMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        durationMillis = state["durationMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
        phase = state["phase"]?.jsonPrimitive?.contentOrNull?.toAudioPlaybackPhase()
            ?: if (state["isPlaying"]?.jsonPrimitive?.booleanOrNull == true) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Ready,
        sessionId = sessionId,
    )
    when (type) {
        "ended" -> AudioPlaybackEvent.Ended(playback)
        "failed" -> AudioPlaybackEvent.Failed(playback, root["reason"]?.jsonPrimitive?.contentOrNull)
        else -> AudioPlaybackEvent.StateChanged(playback)
    }
}.getOrNull()

private fun String.toAudioPlaybackPhase(): AudioPlaybackPhase? = when (this) {
    "idle" -> AudioPlaybackPhase.Idle
    "loading" -> AudioPlaybackPhase.Loading
    "ready" -> AudioPlaybackPhase.Ready
    "playing" -> AudioPlaybackPhase.Playing
    "paused" -> AudioPlaybackPhase.Paused
    "ended" -> AudioPlaybackPhase.Ended
    "failed" -> AudioPlaybackPhase.Failed
    else -> null
}

private fun browserAudioLoad(
    id: String,
    sessionId: Long,
    source: String,
    onResult: (String) -> Unit,
    onEvent: (String) -> Unit,
): Unit = js(
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
          element.onplaying = null;
          element.onpause = null;
          element.onended = null;
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
        const stateObject = (phase) => ({
          isLoaded: element.readyState > 0,
          isPlaying: !element.paused && !element.ended,
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
          phase: phase || (element.ended ? 'ended' : (!element.paused ? 'playing' : (element.readyState > 0 ? 'ready' : 'loading'))),
          sessionId: Number(sessionId),
        });
        const state = (phase) => JSON.stringify(stateObject(phase));
        const emit = (type, phase, reason) => onEvent(JSON.stringify({
          type,
          sessionId: Number(sessionId),
          state: stateObject(phase),
          ...(reason ? { reason } : {}),
        }));
        element.onloadedmetadata = () => { emit('state', 'ready'); complete(state('ready')); };
        element.oncanplay = () => { emit('state', 'ready'); complete(state('ready')); };
        element.onplaying = () => emit('state', 'playing');
        element.onpause = () => { if (!element.ended) emit('state', 'paused'); };
        element.onended = () => emit('ended', 'ended');
        element.onerror = () => {
          cleanup();
          emit('failed', 'failed', 'web_audio_load_failed');
          complete('failure:web_audio_load_failed');
        };
        const resolveSource = () => {
          if (/^blob:/i.test(source)) return Promise.resolve(source);
          return Promise.reject(new Error('web_audio_reference_remote_unsupported'));
        };
        resolveSource().then((playableSource) => {
          if (completed || !playableSource) return;
          element.preload = 'metadata';
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
      const state = () => JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0, phase: !element.paused && !element.ended ? 'playing' : (element.ended ? 'ended' : 'ready')});
      try { const result = element.play(); if (result?.then) result.then(() => onResult(state())).catch((error) => onResult('failure:' + (error?.name || 'web_audio_play_failed'))); else onResult(state()); } catch (error) { onResult('failure:' + (error?.name || 'web_audio_play_failed')); }
    })()""",
)

private fun browserAudioPause(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); element.pause(); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: false, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0, phase: element.ended ? 'ended' : 'paused'})); })()""",
)

private fun browserAudioSeek(id: String, positionMillis: Long, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); const duration = Number.isFinite(element.duration) && element.duration >= 0 ? element.duration : Number.MAX_VALUE; const targetMillis = Number(positionMillis); const targetSeconds = Math.max(0, Number.isFinite(targetMillis) ? targetMillis : 0) / 1000; element.currentTime = Math.min(duration, targetSeconds); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)), durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0, phase: element.ended ? 'ended' : (!element.paused ? 'playing' : 'paused')})); })()""",
)

private fun browserAudioState(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => { const element = globalThis.__quataAudioPlayers?.get(id); if (!element) return onResult('failure:web_audio_not_loaded'); const durationMillis = Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0; const positionMillis = element.ended && durationMillis > 0 ? durationMillis : Math.max(0, Math.floor((element.currentTime || 0) * 1000)); onResult(JSON.stringify({isLoaded: element.readyState > 0, isPlaying: !element.paused && !element.ended, positionMillis, durationMillis, phase: element.ended ? 'ended' : (!element.paused ? 'playing' : (element.readyState > 0 ? 'ready' : 'idle'))})); })()""",
)

private fun browserAudioStop(id: String, onComplete: () -> Unit): Unit = js(
    """(() => { const store = globalThis.__quataAudioPlayers; const element = store?.get(id); if (element) { const source = element.currentSrc || element.src || ''; element.pause(); element.removeAttribute('src'); element.load(); if (/^blob:/i.test(source) && globalThis.URL?.revokeObjectURL) globalThis.URL.revokeObjectURL(source); store.delete(id); } onComplete(); })()""",
)

private const val BrowserAudioUnsupported = "unsupported"
private const val BrowserAudioFailurePrefix = "failure:"
