package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.NSObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** AVFoundation playback host kept separate from recording because both contracts expose stop(). */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioPlayerHost(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosAudioPlayerHost {
    private var player: AVAudioPlayer? = null
    private var delegate: IosAudioPlayerDelegate? = null
    private var sessionId = 0L
    private var playbackClockStartTimeSeconds: Double? = null
    private var playbackClockStartPositionMillis = 0L
    private var fallbackDurationMillis = 0L
    private val eventSink = MutableSharedFlow<AudioPlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AudioPlaybackEvent> = eventSink.asSharedFlow()

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        val url = file.toIosAudioUrl() ?: return PlatformResult.Failure("audio_file_url_invalid")
        if (!activate()) return PlatformResult.Failure("audio_session_activation_failed")
        val newPlayer = runCatching { AVAudioPlayer(url, null) }
            .getOrElse { return PlatformResult.Failure(it.message ?: "audio_player_load_failed") }
        val nextSessionId = ++sessionId
        val nextDelegate = IosAudioPlayerDelegate(
            sessionId = nextSessionId,
            emit = { event -> eventSink.tryEmit(event) },
            state = { phase -> stateValue(phase) },
        )
        newPlayer.delegate = nextDelegate
        // Some simulator/format combinations report a failed prebuffer while still exposing a
        // valid duration and deferring the real terminal decision to play().
        newPlayer.prepareToPlay()
        player?.stop()
        player?.delegate = null
        player = newPlayer
        delegate = nextDelegate
        fallbackDurationMillis = file.wavDurationMillis(url) ?: 0L
        clearPlaybackClock()
        return PlatformResult.Success(stateValue(AudioPlaybackPhase.Ready))
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        if (!player.play()) return@playerOrFailure PlatformResult.Failure("audio_player_play_failed")
        startPlaybackClock(player)
        PlatformResult.Success(stateValue(AudioPlaybackPhase.Playing))
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { player ->
        val positionMillis = stateValue().positionMillis
        player.pause()
        player.currentTime = positionMillis.toDouble() / 1_000
        clearPlaybackClock()
        PlatformResult.Success(stateValue(AudioPlaybackPhase.Paused).copy(positionMillis = positionMillis, isPlaying = false))
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
        PlatformResult.Success(stateValue(if (wasPlaying) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Paused).copy(positionMillis = boundedPositionMillis))
    }

    override suspend fun stop(): PlatformResult<Unit> {
        player?.stop()
        player?.delegate = null
        player = null
        delegate = null
        sessionId += 1L
        fallbackDurationMillis = 0L
        clearPlaybackClock()
        return PlatformResult.Success(Unit)
    }
    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun activate(): Boolean = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)

    private fun playerOrFailure(block: (AVAudioPlayer) -> PlatformResult<AudioPlaybackState>): PlatformResult<AudioPlaybackState> =
        player?.let(block) ?: PlatformResult.Failure("audio_player_not_loaded")

    private fun stateValue(overridePhase: AudioPlaybackPhase? = null): AudioPlaybackState = player?.let {
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
        AudioPlaybackState(
            isLoaded = true,
            isPlaying = it.playing,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            phase = overridePhase ?: if (it.playing) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Ready,
            sessionId = sessionId,
        )
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

private class IosAudioPlayerDelegate(
    private val sessionId: Long,
    private val emit: (AudioPlaybackEvent) -> Unit,
    private val state: (AudioPlaybackPhase) -> AudioPlaybackState,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        val terminalState = state(if (successfully) AudioPlaybackPhase.Ended else AudioPlaybackPhase.Failed)
            .copy(isPlaying = false, sessionId = sessionId)
        if (successfully) {
            emit(AudioPlaybackEvent.Ended(terminalState))
        } else {
            emit(AudioPlaybackEvent.Failed(terminalState, "audio_player_finish_failed"))
        }
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        emit(AudioPlaybackEvent.Failed(state(AudioPlaybackPhase.Failed).copy(isPlaying = false, sessionId = sessionId), error?.localizedDescription))
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
    val bytes = data.bytes?.readBytes(data.length.toInt()) ?: return null
    if (bytes.size < 12 || bytes.ascii(0, 4) != "RIFF" || bytes.ascii(8, 4) != "WAVE") return null
    var offset = 12
    var byteRate: Long? = null
    var dataSize: Long? = null
    while (offset + 8 <= bytes.size) {
        val chunkId = bytes.ascii(offset, 4)
        val chunkSize = bytes.uint32Le(offset + 4)
        val chunkDataOffset = offset + 8
        if (chunkSize > Int.MAX_VALUE) return null
        val chunkSizeInt = chunkSize.toInt()
        if (chunkDataOffset + chunkSizeInt > bytes.size) return null
        when (chunkId) {
            "fmt " -> if (chunkSizeInt >= 16) byteRate = bytes.uint32Le(chunkDataOffset + 8).takeIf { it > 0L }
            "data" -> dataSize = chunkSize.takeIf { it > 0L }
        }
        if (byteRate != null && dataSize != null) break
        offset = chunkDataOffset + chunkSizeInt + (chunkSizeInt and 1)
    }
    val rate = byteRate ?: return null
    val size = dataSize ?: return null
    return ((size * 1_000L) / rate).takeIf { it > 0L }
}

private fun ByteArray.ascii(offset: Int, length: Int): String =
    decodeToString(offset, offset + length)

private fun ByteArray.uint32Le(offset: Int): Long =
    ((this[offset].toLong() and 0xffL)) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
