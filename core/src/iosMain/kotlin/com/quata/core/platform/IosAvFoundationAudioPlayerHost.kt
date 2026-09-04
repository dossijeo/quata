package com.quata.core.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.NSObject

/** Native iOS engine boundary. Swift installs the production AVPlayer engine from the launcher. */
interface IosNativeAudioPlaybackEngine {
    fun installListener(listener: IosNativeAudioPlaybackEngineListener?)
    fun load(path: String, displayName: String?, mimeType: String?, sizeBytes: Long): IosNativeAudioPlaybackEngineState
    fun startPlayback(): IosNativeAudioPlaybackEngineState
    fun pausePlayback(): IosNativeAudioPlaybackEngineState
    fun seekPlaybackTo(positionMillis: Long): IosNativeAudioPlaybackEngineState
    fun stopPlayback(): IosNativeAudioPlaybackEngineState
    fun state(): IosNativeAudioPlaybackEngineState
}

interface IosNativeAudioPlaybackEngineListener {
    fun playbackStateChanged()
    fun playbackEnded()
    fun playbackFailed(reason: String?)
}

data class IosNativeAudioPlaybackEngineState(
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val errorReason: String? = null,
)

/** AVFoundation playback host kept separate from recording because both contracts expose stop(). */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioPlayerHost(
    private val engine: IosNativeAudioPlaybackEngine = IosAvAudioPlayerEngine(),
) : IosAudioPlayerHost {
    private var sessionId = 0L
    private var fallbackDurationMillis = 0L
    private val eventSink = MutableSharedFlow<AudioPlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AudioPlaybackEvent> = eventSink.asSharedFlow()

    init {
        engine.installListener(
            object : IosNativeAudioPlaybackEngineListener {
                override fun playbackStateChanged() {
                    eventSink.tryEmit(AudioPlaybackEvent.StateChanged(stateValue()))
                }

                override fun playbackEnded() {
                    val terminalState = stateValue(AudioPlaybackPhase.Ended).copy(isPlaying = false)
                    eventSink.tryEmit(AudioPlaybackEvent.Ended(terminalState))
                }

                override fun playbackFailed(reason: String?) {
                    val failedState = stateValue(AudioPlaybackPhase.Failed).copy(isPlaying = false)
                    eventSink.tryEmit(AudioPlaybackEvent.Failed(failedState, reason))
                }
            },
        )
    }

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
        val url = file.toIosAudioUrl() ?: return PlatformResult.Failure("audio_file_url_invalid")
        val path = url.path ?: return PlatformResult.Failure("audio_file_path_invalid")
        val nextFallbackDurationMillis = file.containerDurationMillis(url)
            ?: file.wavDurationMillis(url)
            ?: 0L
        val loaded = engine.load(
            path = path,
            displayName = file.displayName,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes ?: 0L,
        )
        if (loaded.errorReason != null || !loaded.isLoaded) {
            return PlatformResult.Failure(loaded.errorReason ?: "audio_player_prepare_failed")
        }
        sessionId += 1L
        fallbackDurationMillis = nextFallbackDurationMillis
        return PlatformResult.Success(stateValue(AudioPlaybackPhase.Ready, loaded))
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> {
        val played = engine.startPlayback()
        if (played.errorReason != null) {
            return PlatformResult.Failure(played.errorReason)
        }
        return PlatformResult.Success(
            stateValue(if (played.isPlaying) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Loading, played)
                .copy(isPlaying = played.isPlaying),
        )
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> {
        val paused = engine.pausePlayback()
        if (paused.errorReason != null) return PlatformResult.Failure(paused.errorReason)
        return PlatformResult.Success(stateValue(AudioPlaybackPhase.Paused, paused).copy(isPlaying = false))
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> {
        val seeked = engine.seekPlaybackTo(positionMillis.coerceAtLeast(0L))
        if (seeked.errorReason != null) return PlatformResult.Failure(seeked.errorReason)
        return PlatformResult.Success(
            stateValue(if (seeked.isPlaying) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Paused, seeked),
        )
    }

    override suspend fun stop(): PlatformResult<Unit> {
        engine.stopPlayback()
        sessionId += 1L
        fallbackDurationMillis = 0L
        return PlatformResult.Success(Unit)
    }

    override suspend fun state(): AudioPlaybackState = stateValue()

    private fun stateValue(
        overridePhase: AudioPlaybackPhase? = null,
        native: IosNativeAudioPlaybackEngineState = engine.state(),
    ): AudioPlaybackState = AudioPlaybackState(
        isLoaded = native.isLoaded,
        isPlaying = native.isPlaying,
        positionMillis = native.positionMillis.coerceAtLeast(0L),
        durationMillis = native.durationMillis.takeIf { it > 0L } ?: fallbackDurationMillis,
        phase = overridePhase ?: when {
            native.errorReason != null -> AudioPlaybackPhase.Failed
            native.isPlaying -> AudioPlaybackPhase.Playing
            native.isLoaded -> AudioPlaybackPhase.Ready
            else -> AudioPlaybackPhase.Idle
        },
        sessionId = sessionId,
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosAvAudioPlayerEngine(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosNativeAudioPlaybackEngine {
    private var player: AVAudioPlayer? = null
    private var delegate: IosAudioPlayerDelegate? = null
    private var listener: IosNativeAudioPlaybackEngineListener? = null
    private var fallbackDurationMillis = 0L
    private var lastFailureReason: String? = null

    override fun installListener(listener: IosNativeAudioPlaybackEngineListener?) {
        this.listener = listener
    }

    override fun load(path: String, displayName: String?, mimeType: String?, sizeBytes: Long): IosNativeAudioPlaybackEngineState {
        lastFailureReason = null
        when (val activated = activatePlaybackSession()) {
            is PlatformResult.Failure -> return state(activated.reason ?: "audio_session_activation_failed")
            PlatformResult.Cancelled -> return state("audio_session_activation_cancelled")
            PlatformResult.Unsupported -> return state("audio_session_activation_unsupported")
            is PlatformResult.Success -> Unit
        }
        val url = NSURL.fileURLWithPath(path)
        val newPlayer = createPreparedAudioPlayer(url, sizeBytes)
            ?: return state(lastFailureReason ?: "audio_player_prepare_failed")
        val nextDelegate = IosAudioPlayerDelegate(
            emitStateChanged = { listener?.playbackStateChanged() },
            emitEnded = { listener?.playbackEnded() },
            emitFailed = { reason -> listener?.playbackFailed(reason) },
        )
        newPlayer.delegate = nextDelegate
        player?.stop()
        player?.delegate = null
        player = newPlayer
        delegate = nextDelegate
        fallbackDurationMillis = containerDurationMillis(path, displayName, mimeType)
            ?: wavDurationMillis(path, displayName, mimeType)
            ?: 0L
        return state()
    }

    override fun startPlayback(): IosNativeAudioPlaybackEngineState {
        val activePlayer = player ?: return state("audio_player_not_loaded")
        if (!activePlayer.play()) return state(lastFailureReason ?: "audio_player_play_failed")
        listener?.playbackStateChanged()
        return state()
    }

    override fun pausePlayback(): IosNativeAudioPlaybackEngineState {
        val activePlayer = player ?: return state("audio_player_not_loaded")
        activePlayer.pause()
        listener?.playbackStateChanged()
        return state()
    }

    override fun seekPlaybackTo(positionMillis: Long): IosNativeAudioPlaybackEngineState {
        val activePlayer = player ?: return state("audio_player_not_loaded")
        val boundedPositionMillis = if (activePlayer.durationMillis() > 0L) {
            positionMillis.coerceIn(0L, activePlayer.durationMillis())
        } else {
            positionMillis.coerceAtLeast(0L)
        }
        activePlayer.currentTime = boundedPositionMillis.toDouble() / 1_000
        listener?.playbackStateChanged()
        return state()
    }

    override fun stopPlayback(): IosNativeAudioPlaybackEngineState {
        player?.stop()
        player?.delegate = null
        player = null
        delegate = null
        fallbackDurationMillis = 0L
        return state()
    }

    override fun state(): IosNativeAudioPlaybackEngineState = state(null)

    private fun state(errorReason: String? = null): IosNativeAudioPlaybackEngineState {
        val activePlayer = player
        return IosNativeAudioPlaybackEngineState(
            isLoaded = activePlayer != null,
            isPlaying = activePlayer?.playing == true,
            positionMillis = ((activePlayer?.currentTime ?: 0.0) * 1_000).toLong().coerceAtLeast(0L),
            durationMillis = activePlayer?.durationMillis() ?: fallbackDurationMillis,
            errorReason = errorReason,
        )
    }

    private fun AVAudioPlayer.durationMillis(): Long =
        (duration * 1_000).toLong().takeIf { it > 0L } ?: fallbackDurationMillis

    private fun createPreparedAudioPlayer(url: NSURL, sizeBytes: Long): AVAudioPlayer? {
        val dataBackedPlayer = dataBackedAudioPlayer(url, sizeBytes)
        if (dataBackedPlayer?.prepareToPlay() == true) return dataBackedPlayer
        val urlPlayer = createAudioPlayer(url) ?: return dataBackedPlayer
        return if (urlPlayer.prepareToPlay()) urlPlayer else dataBackedPlayer ?: urlPlayer
    }

    private fun createAudioPlayer(url: NSURL): AVAudioPlayer? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        AVAudioPlayer(url, error.ptr).also {
            lastFailureReason = error.value?.audioReason("audio_player_create_failed")
        }
    }

    private fun dataBackedAudioPlayer(url: NSURL, sizeBytes: Long): AVAudioPlayer? {
        if (sizeBytes <= 0L || sizeBytes > DATA_BACKED_PLAYER_MAX_BYTES) return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioPlayer(data, error.ptr).also {
                lastFailureReason = error.value?.audioReason("audio_player_data_create_failed")
            }
        }
    }

    private fun activatePlaybackSession(): PlatformResult<Unit> = memScoped {
        val categoryError = alloc<ObjCObjectVar<NSError?>>()
        if (!audioSession.setCategory(AVAudioSessionCategoryPlayback, error = categoryError.ptr)) {
            return PlatformResult.Failure(categoryError.value.audioReason("audio_session_category_failed"))
        }
        PlatformResult.Success(Unit)
    }
}

private class IosAudioPlayerDelegate(
    private val emitStateChanged: () -> Unit,
    private val emitEnded: () -> Unit,
    private val emitFailed: (String?) -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        if (successfully) {
            emitEnded()
        } else {
            emitFailed("audio_player_finish_failed")
        }
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        emitFailed(error?.localizedDescription)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.toIosAudioUrl(): NSURL? = when {
    reference.startsWith("file://") -> NSURL.URLWithString(reference)
    reference.isNotBlank() -> NSURL.fileURLWithPath(reference)
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.wavDurationMillis(url: NSURL): Long? {
    val path = url.path ?: return null
    return wavDurationMillis(path, displayName, mimeType)
}

@OptIn(ExperimentalForeignApi::class)
private fun wavDurationMillis(path: String, displayName: String?, mimeType: String?): Long? {
    val referenceText = "${path.lowercase()} ${displayName.orEmpty().lowercase()} ${mimeType.orEmpty().lowercase()}"
    if (!referenceText.contains(".wav") && !referenceText.contains("audio/wav") && !referenceText.contains("audio/x-wav")) {
        return null
    }
    val url = NSURL.fileURLWithPath(path)
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return null
    val declaredSize = (attributes[NSFileSize] as? Number)?.toLong() ?: return null
    if (declaredSize <= 0L || declaredSize > WAV_METADATA_FALLBACK_MAX_BYTES) return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    val byteCount = data.length.toLong()
    if (byteCount <= 0L || byteCount > WAV_METADATA_FALLBACK_MAX_BYTES) return null
    val bytes = data.bytes?.readBytes(byteCount.toInt()) ?: return null
    if (bytes.size < 12 || bytes.ascii(0, 4) != "RIFF" || bytes.ascii(8, 4) != "WAVE") return null
    var offset = 12
    var byteRate: Long? = null
    var dataSize: Long? = null
    while (offset + 8 <= bytes.size) {
        val chunkId = bytes.ascii(offset, 4)
        val chunkSize = bytes.uint32Le(offset + 4)
        val chunkDataOffset = offset + 8
        val chunkEnd = chunkDataOffset.toLong() + chunkSize
        if (chunkEnd > bytes.size.toLong()) return null
        val chunkSizeInt = chunkSize.toInt()
        when (chunkId) {
            "fmt " -> if (chunkSizeInt >= 16) byteRate = bytes.uint32Le(chunkDataOffset + 8).takeIf { it > 0L }
            "data" -> dataSize = chunkSize.takeIf { it > 0L }
        }
        if (byteRate != null && dataSize != null) break
        offset = (chunkEnd + (chunkSize and 1L)).toInt()
    }
    val rate = byteRate ?: return null
    val size = dataSize ?: return null
    return ((size * 1_000L) / rate).takeIf { it > 0L }
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.containerDurationMillis(url: NSURL): Long? {
    val path = url.path ?: return null
    return containerDurationMillis(path, displayName, mimeType)
}

@OptIn(ExperimentalForeignApi::class)
private fun containerDurationMillis(path: String, displayName: String?, mimeType: String?): Long? {
    val referenceText = "${path.lowercase()} ${displayName.orEmpty().lowercase()} ${mimeType.orEmpty().lowercase()}"
    val isContainerAudio = referenceText.contains(".m4a") ||
        referenceText.contains(".mp4") ||
        referenceText.contains(".aac") ||
        referenceText.contains("audio/mp4") ||
        referenceText.contains("audio/aac")
    if (!isContainerAudio) return null
    val durationSeconds = CMTimeGetSeconds(AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null).duration)
    return (durationSeconds * 1_000).toLong().takeIf { it > 0L }
}

private const val WAV_METADATA_FALLBACK_MAX_BYTES = 2L * 1024L * 1024L
private const val DATA_BACKED_PLAYER_MAX_BYTES = 50L * 1024L * 1024L

private fun NSError?.audioReason(fallback: String): String =
    this?.let { error ->
        val description = error.localizedDescription.takeUnless { it.isNullOrBlank() }
        val code = error.code
        listOfNotNull(fallback, description, "code=$code").joinToString(":")
    } ?: fallback

private fun ByteArray.ascii(offset: Int, length: Int): String =
    decodeToString(offset, offset + length)

private fun ByteArray.uint32Le(offset: Int): Long =
    ((this[offset].toLong() and 0xffL)) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
