package com.quata.core.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Portable audio boundaries. Implementations own codecs, URI access, playback engines and caches. */
data class AudioRecordingOptions(
    val mimeType: String = "audio/mp4",
    val maxDurationMillis: Long? = null,
)

data class AudioRecording(
    val file: PlatformFile,
    val durationMillis: Long,
    val mimeType: String,
)

data class AudioPlaybackState(
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val phase: AudioPlaybackPhase = when {
        isPlaying -> AudioPlaybackPhase.Playing
        isLoaded -> AudioPlaybackPhase.Ready
        else -> AudioPlaybackPhase.Idle
    },
    val sessionId: Long = 0L,
)

enum class AudioPlaybackPhase {
    Idle,
    Loading,
    Ready,
    Playing,
    Paused,
    Ended,
    Failed,
}

sealed interface AudioPlaybackEvent {
    val state: AudioPlaybackState

    data class StateChanged(override val state: AudioPlaybackState) : AudioPlaybackEvent
    data class Ended(override val state: AudioPlaybackState) : AudioPlaybackEvent
    data class Failed(override val state: AudioPlaybackState, val reason: String?) : AudioPlaybackEvent
}

interface AudioRecorderService {
    suspend fun start(options: AudioRecordingOptions = AudioRecordingOptions()): PlatformResult<Unit>
    suspend fun stop(): PlatformResult<AudioRecording>
    suspend fun cancel(): PlatformResult<Unit>
}

/**
 * Optional owner of temporary recording references returned by an [AudioRecorderService].
 *
 * A recording can outlive the active recorder while a composer uploads it. The composition root
 * may inject this boundary to release a discarded recording only after no UI or upload still uses
 * its [PlatformFile.reference]. Implementations must never release an arbitrary caller reference.
 */
interface AudioRecordingReferenceReleaser {
    suspend fun release(recording: AudioRecording): PlatformResult<Unit>
}

interface AudioPlayerService {
    val events: Flow<AudioPlaybackEvent> get() = emptyFlow()

    suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState>
    suspend fun play(): PlatformResult<AudioPlaybackState>
    suspend fun pause(): PlatformResult<AudioPlaybackState>
    suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState>
    suspend fun stop(): PlatformResult<Unit>
    suspend fun state(): AudioPlaybackState
}
