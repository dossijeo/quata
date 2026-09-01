package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSURL

/** AVFoundation playback host kept separate from recording because both contracts expose stop(). */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioPlayerHost(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosAudioPlayerHost {
    private var player: AVAudioPlayer? = null

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        val url = file.toIosAudioUrl() ?: return PlatformResult.Failure("audio_file_url_invalid")
        if (!activate()) return PlatformResult.Failure("audio_session_activation_failed")
        val newPlayer = runCatching { AVAudioPlayer(url, null) }
            .getOrElse { return PlatformResult.Failure(it.message ?: "audio_player_load_failed") }
        // Some simulator/format combinations report a failed prebuffer while still exposing a
        // valid duration and deferring the real terminal decision to play().
        newPlayer.prepareToPlay()
        player?.stop()
        player = newPlayer
        return PlatformResult.Success(stateValue())
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        if (!player.play()) return@playerOrFailure PlatformResult.Failure("audio_player_play_failed")
        PlatformResult.Success(stateValue())
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        player.pause()
        PlatformResult.Success(stateValue())
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val wasPlaying = player.playing
        val boundedPositionMillis = positionMillis.coerceIn(0L, (player.duration * 1_000).toLong())
        player.currentTime = boundedPositionMillis.toDouble() / 1_000
        if (wasPlaying && !player.play()) {
            return@playerOrFailure PlatformResult.Failure("audio_player_play_failed")
        }
        PlatformResult.Success(stateValue().copy(positionMillis = boundedPositionMillis))
    }

    override suspend fun stop(): PlatformResult<Unit> {
        player?.stop()
        player = null
        return PlatformResult.Success(Unit)
    }
    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun activate(): Boolean = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)

    private fun playerOrFailure(block: (AVAudioPlayer) -> PlatformResult<AudioPlaybackState>): PlatformResult<AudioPlaybackState> =
        player?.let(block) ?: PlatformResult.Failure("audio_player_not_loaded")

    private fun stateValue(): AudioPlaybackState = player?.let {
        val nativePositionMillis = (it.currentTime * 1_000).toLong().coerceAtLeast(0L)
        val durationMillis = (it.duration * 1_000).toLong()
        AudioPlaybackState(true, it.playing, nativePositionMillis, durationMillis)
    } ?: AudioPlaybackState()
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.toIosAudioUrl(): NSURL? = when {
    reference.startsWith("file://") -> NSURL.URLWithString(reference)
    reference.isNotBlank() -> NSURL.fileURLWithPath(reference)
    else -> null
}
