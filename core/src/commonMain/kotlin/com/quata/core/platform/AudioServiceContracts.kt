package com.quata.core.platform

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
)

interface AudioRecorderService {
    suspend fun start(options: AudioRecordingOptions = AudioRecordingOptions()): PlatformResult<Unit>
    suspend fun stop(): PlatformResult<AudioRecording>
    suspend fun cancel(): PlatformResult<Unit>
}

interface AudioPlayerService {
    suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState>
    suspend fun play(): PlatformResult<AudioPlaybackState>
    suspend fun pause(): PlatformResult<AudioPlaybackState>
    suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState>
    suspend fun stop(): PlatformResult<Unit>
    suspend fun state(): AudioPlaybackState
}
