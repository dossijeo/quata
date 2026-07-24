package com.quata.core.platform

/**
 * AVFoundation bridge implemented by the iOS launcher.
 *
 * The host owns microphone permission, AVAudioSession category/activation, temporary-file
 * lifetime and AVAudioRecorder. It returns the portable recording model so shared features do
 * not need UIKit or AVFoundation imports.
 */
interface IosAudioRecorderHost {
    suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit>
    suspend fun stop(): PlatformResult<AudioRecording>
    suspend fun cancel(): PlatformResult<Unit>
}

/** AVAudioPlayer/AVPlayer bridge implemented by the iOS launcher. */
interface IosAudioPlayerHost {
    suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState>
    suspend fun play(): PlatformResult<AudioPlaybackState>
    suspend fun pause(): PlatformResult<AudioPlaybackState>
    suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState>
    suspend fun stop(): PlatformResult<Unit>
    suspend fun state(): AudioPlaybackState
}

/**
 * Injectable iOS recorder boundary. Without a host it reports Unsupported, rather than
 * pretending recording succeeded; attaching an AVFoundation host immediately enables it.
 */
class IosAudioRecorderService(
    initialHost: IosAudioRecorderHost? = null,
) : AudioRecorderService {
    private var host: IosAudioRecorderHost? = initialHost

    fun attachHost(host: IosAudioRecorderHost) {
        this.host = host
    }

    fun detachHost(host: IosAudioRecorderHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit> =
        host?.start(options) ?: PlatformResult.Unsupported

    override suspend fun stop(): PlatformResult<AudioRecording> =
        host?.stop() ?: PlatformResult.Unsupported

    override suspend fun cancel(): PlatformResult<Unit> =
        host?.cancel() ?: PlatformResult.Unsupported
}

/** Injectable iOS playback boundary paired with [IosAudioRecorderService]. */
class IosAudioPlayerService(
    initialHost: IosAudioPlayerHost? = null,
) : AudioPlayerService {
    private var host: IosAudioPlayerHost? = initialHost

    fun attachHost(host: IosAudioPlayerHost) {
        this.host = host
    }

    fun detachHost(host: IosAudioPlayerHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        if (file.reference.isBlank()) return PlatformResult.Failure("audio_file_reference_missing")
        return host?.load(file) ?: PlatformResult.Unsupported
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> =
        host?.play() ?: PlatformResult.Unsupported

    override suspend fun pause(): PlatformResult<AudioPlaybackState> =
        host?.pause() ?: PlatformResult.Unsupported

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> =
        host?.seekTo(positionMillis.coerceAtLeast(0L)) ?: PlatformResult.Unsupported

    override suspend fun stop(): PlatformResult<Unit> = host?.stop() ?: PlatformResult.Unsupported

    override suspend fun state(): AudioPlaybackState = host?.state() ?: AudioPlaybackState()
}
