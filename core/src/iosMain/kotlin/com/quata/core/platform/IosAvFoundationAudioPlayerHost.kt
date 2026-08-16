package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSURL

/** AVFoundation playback host kept separate from recording because both contracts expose stop(). */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioPlayerHost(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosAudioPlayerHost {
    private var player: AVAudioPlayer? = null
    private var playbackRequested = false
    private var requestedStartTimeSeconds: Double? = null
    private var requestedStartPositionMillis = 0L

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
        playbackRequested = false
        requestedStartTimeSeconds = null
        requestedStartPositionMillis = 0L
        return PlatformResult.Success(stateValue())
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val positionMillis = (player.currentTime * 1_000).toLong().coerceAtLeast(0L)
        playbackRequested = true
        requestedStartTimeSeconds = nowSeconds()
        requestedStartPositionMillis = positionMillis
        player.play()
        PlatformResult.Success(stateValue().copy(isPlaying = true))
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        player.pause()
        playbackRequested = false
        requestedStartTimeSeconds = null
        requestedStartPositionMillis = (player.currentTime * 1_000).toLong().coerceAtLeast(0L)
        PlatformResult.Success(stateValue())
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val wasPlaying = player.playing || playbackRequested
        val boundedPositionMillis = positionMillis.coerceIn(0L, (player.duration * 1_000).toLong())
        player.currentTime = boundedPositionMillis.toDouble() / 1_000
        requestedStartPositionMillis = boundedPositionMillis
        requestedStartTimeSeconds = if (wasPlaying) nowSeconds() else null
        if (wasPlaying) {
            player.play()
            playbackRequested = true
        }
        PlatformResult.Success(stateValue().copy(isPlaying = wasPlaying || player.playing))
    }

    override suspend fun stop(): PlatformResult<Unit> {
        player?.stop()
        player = null
        playbackRequested = false
        requestedStartTimeSeconds = null
        requestedStartPositionMillis = 0L
        return PlatformResult.Success(Unit)
    }
    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun activate(): Boolean = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)

    private fun playerOrFailure(block: (AVAudioPlayer) -> PlatformResult<AudioPlaybackState>): PlatformResult<AudioPlaybackState> =
        player?.let(block) ?: PlatformResult.Failure("audio_player_not_loaded")

    private fun stateValue(): AudioPlaybackState = player?.let {
        val nativePositionMillis = (it.currentTime * 1_000).toLong().coerceAtLeast(0L)
        val durationMillis = (it.duration * 1_000).toLong()
        val positionMillis = if (playbackRequested && !it.playing && durationMillis > 0L) {
            val elapsedMillis = requestedStartTimeSeconds
                ?.let { started -> ((nowSeconds() - started) * 1_000).toLong().coerceAtLeast(0L) }
                ?: 0L
            (requestedStartPositionMillis + elapsedMillis).coerceIn(0L, durationMillis)
        } else {
            nativePositionMillis
        }
        val reachedEnd = durationMillis > 0L && positionMillis >= (durationMillis - 50L).coerceAtLeast(0L)
        if (reachedEnd && !it.playing) {
            playbackRequested = false
            requestedStartTimeSeconds = null
            requestedStartPositionMillis = 0L
        }
        AudioPlaybackState(true, it.playing || playbackRequested, positionMillis, durationMillis)
    } ?: AudioPlaybackState()
}

private fun nowSeconds(): Double = CFAbsoluteTimeGetCurrent()

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.toIosAudioUrl(): NSURL? = when {
    reference.startsWith("file://") -> NSURL.URLWithString(reference)
    reference.isNotBlank() -> NSURL.fileURLWithPath(reference)
    else -> null
}
