package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
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
        if (!newPlayer.prepareToPlay()) return PlatformResult.Failure("audio_player_prepare_failed")
        player?.stop()
        player = newPlayer
        return PlatformResult.Success(stateValue())
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        if (!player.play()) PlatformResult.Failure("audio_player_play_failed") else PlatformResult.Success(stateValue())
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        player.pause(); PlatformResult.Success(stateValue())
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        player.currentTime = positionMillis.coerceIn(0L, (player.duration * 1_000).toLong()).toDouble() / 1_000
        PlatformResult.Success(stateValue())
    }

    override suspend fun stop(): PlatformResult<Unit> { player?.stop(); player = null; return PlatformResult.Success(Unit) }
    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun activate(): Boolean = audioSession.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)

    private fun playerOrFailure(block: (AVAudioPlayer) -> PlatformResult<AudioPlaybackState>): PlatformResult<AudioPlaybackState> =
        player?.let(block) ?: PlatformResult.Failure("audio_player_not_loaded")

    private fun stateValue(): AudioPlaybackState = player?.let {
        AudioPlaybackState(true, it.playing, (it.currentTime * 1_000).toLong(), (it.duration * 1_000).toLong())
    } ?: AudioPlaybackState()
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.toIosAudioUrl(): NSURL? = when {
    reference.startsWith("file://") -> NSURL.URLWithString(reference)
    reference.isNotBlank() -> NSURL.fileURLWithPath(reference)
    else -> null
}
