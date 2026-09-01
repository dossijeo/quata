package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSData
import platform.Foundation.NSURL

/** AVFoundation playback host kept separate from recording because both contracts expose stop(). */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioPlayerHost(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosAudioPlayerHost {
    private var player: AVAudioPlayer? = null
    private var playbackClockStartTimeSeconds: Double? = null
    private var playbackClockStartPositionMillis = 0L
    private var fallbackDurationMillis = 0L

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
        fallbackDurationMillis = file.wavDurationMillis(url) ?: 0L
        clearPlaybackClock()
        return PlatformResult.Success(stateValue())
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        if (!player.play()) return@playerOrFailure PlatformResult.Failure("audio_player_play_failed")
        startPlaybackClock(player)
        PlatformResult.Success(stateValue())
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val positionMillis = stateValue().positionMillis
        player.pause()
        player.currentTime = positionMillis.toDouble() / 1_000
        clearPlaybackClock()
        PlatformResult.Success(stateValue().copy(positionMillis = positionMillis, isPlaying = false))
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val wasPlaying = player.playing
        val durationMillis = player.durationMillis()
        val boundedPositionMillis = if (durationMillis > 0L) {
            positionMillis.coerceIn(0L, durationMillis)
        } else {
            positionMillis.coerceAtLeast(0L)
        }
        player.currentTime = boundedPositionMillis.toDouble() / 1_000
        if (wasPlaying && !player.play()) {
            return@playerOrFailure PlatformResult.Failure("audio_player_play_failed")
        }
        if (wasPlaying) {
            startPlaybackClock(player, boundedPositionMillis)
        } else {
            clearPlaybackClock()
        }
        PlatformResult.Success(stateValue().copy(positionMillis = boundedPositionMillis))
    }

    override suspend fun stop(): PlatformResult<Unit> {
        player?.stop()
        player = null
        fallbackDurationMillis = 0L
        clearPlaybackClock()
        return PlatformResult.Success(Unit)
    }
    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun activate(): Boolean = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)

    private fun playerOrFailure(block: (AVAudioPlayer) -> PlatformResult<AudioPlaybackState>): PlatformResult<AudioPlaybackState> =
        player?.let(block) ?: PlatformResult.Failure("audio_player_not_loaded")

    private fun stateValue(): AudioPlaybackState = player?.let {
        val nativePositionMillis = (it.currentTime * 1_000).toLong().coerceAtLeast(0L)
        val durationMillis = it.durationMillis()
        val clockPositionMillis = if (it.playing && durationMillis > 0L) {
            playbackClockStartTimeSeconds
                ?.let { started -> playbackClockStartPositionMillis + ((nowSeconds() - started) * 1_000).toLong().coerceAtLeast(0L) }
                ?.coerceIn(0L, durationMillis)
        } else {
            null
        }
        val positionMillis = maxOf(nativePositionMillis, clockPositionMillis ?: nativePositionMillis)
        AudioPlaybackState(true, it.playing, positionMillis, durationMillis)
    } ?: AudioPlaybackState()

    private fun AVAudioPlayer.durationMillis(): Long =
        (duration * 1_000).toLong().takeIf { it > 0L } ?: fallbackDurationMillis

    private fun startPlaybackClock(player: AVAudioPlayer, positionMillis: Long = (player.currentTime * 1_000).toLong().coerceAtLeast(0L)) {
        playbackClockStartTimeSeconds = nowSeconds()
        playbackClockStartPositionMillis = positionMillis
    }

    private fun clearPlaybackClock() {
        playbackClockStartTimeSeconds = null
        playbackClockStartPositionMillis = 0L
    }
}

private fun nowSeconds(): Double = CFAbsoluteTimeGetCurrent()

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.toIosAudioUrl(): NSURL? = when {
    reference.startsWith("file://") -> NSURL.URLWithString(reference)
    reference.isNotBlank() -> NSURL.fileURLWithPath(reference)
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.wavDurationMillis(url: NSURL): Long? {
    val referenceText = "${reference.lowercase()} ${displayName.orEmpty().lowercase()} ${mimeType.orEmpty().lowercase()}"
    if (!referenceText.contains(".wav") && !referenceText.contains("audio/wav") && !referenceText.contains("audio/x-wav")) {
        return null
    }
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    val header = data.bytes?.readBytes(minOf(data.length.toInt(), 44)) ?: return null
    if (header.size < 44) return null
    if (header.ascii(0, 4) != "RIFF" || header.ascii(8, 4) != "WAVE" || header.ascii(36, 4) != "data") {
        return null
    }
    val byteRate = header.uint32Le(28).takeIf { it > 0L } ?: return null
    val dataSize = header.uint32Le(40).takeIf { it > 0L } ?: return null
    return ((dataSize * 1_000L) / byteRate).takeIf { it > 0L }
}

private fun ByteArray.ascii(offset: Int, length: Int): String =
    decodeToString(offset, offset + length)

private fun ByteArray.uint32Le(offset: Int): Long =
    ((this[offset].toLong() and 0xffL)) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
