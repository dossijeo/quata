package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackPhase
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChatAudioPlaybackUiState(
    val activeReference: String? = null,
    val activeMessageKey: String? = null,
    val playback: AudioPlaybackState = AudioPlaybackState(),
    val failed: Boolean = false,
    val failureReason: String? = null,
    val operationInFlight: Boolean = false,
)

internal class ChatAudioPlaybackController(
    private val audioPlayer: AudioPlayerService,
    private val messages: () -> List<Message>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val operations = Mutex()
    private val _state = MutableStateFlow(ChatAudioPlaybackUiState())
    private val ownerToken = Any()
    private var generation = 0L
    private val activeOperations = mutableSetOf<Job>()
    private var activeOperation: Job? = null
    private var seekOperation: Job? = null
    private var disposed = false

    val state: StateFlow<ChatAudioPlaybackUiState> = _state.asStateFlow()

    init {
        scope.launch {
            audioPlayer.events.collect { event -> handlePlayerEvent(event) }
        }
        scope.launch(Dispatchers.Default) {
            while (!disposed) {
                delay(250L)
                withContext(dispatcher) {
                    refreshPosition()
                }
            }
        }
    }

    fun toggle(message: Message, file: PlatformFile) {
        val reference = file.reference.takeIf { it.isNotBlank() } ?: return
        val messageKey = message.composeKey()
        val current = _state.value
        if (current.activeReference == reference && current.operationInFlight) return
        seekOperation?.cancel()
        val replaceActive = current.activeReference != reference
        if (replaceActive) {
            requestNewPlaybackGeneration()
        }
        launchSerial(cancelActive = replaceActive) {
            val current = _state.value
            when {
                current.activeReference != reference -> startNewPlayback(reference, messageKey, file)
                current.playback.phase == AudioPlaybackPhase.Playing || current.playback.isPlaying -> pauseActive()
                current.playback.phase == AudioPlaybackPhase.Failed || !current.playback.isLoaded -> startNewPlayback(reference, messageKey, file)
                else -> resumeActive()
            }
        }
    }

    fun seekToFraction(reference: String, fraction: Float) {
        seekOperation?.cancel()
        seekOperation = launchSerial(cancelActive = false) {
            val current = _state.value
            val durationMillis = current.playback.durationMillis
            if (current.activeReference != reference || durationMillis <= 0L) return@launchSerial
            val requestGeneration = generation
            updateOperationIfCurrent(requestGeneration, true)
            when (
                val result = withOwnedAudio {
                    audioPlayer.seekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
                } ?: run {
                    updateOperationIfCurrent(requestGeneration, false)
                    return@launchSerial
                }
            ) {
                is PlatformResult.Success -> applyStateIfCurrent(requestGeneration, result.value, failed = false)
                is PlatformResult.Failure -> failIfCurrent(requestGeneration, result.reason)
                PlatformResult.Cancelled -> failIfCurrent(requestGeneration, "audio_seek_cancelled")
                PlatformResult.Unsupported -> failIfCurrent(requestGeneration, "audio_seek_unsupported")
            }
            updateOperationIfCurrent(requestGeneration, false)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        generation += 1L
        val operationsToCancel = activeOperations.toList()
        activeOperations.clear()
        activeOperation = null
        seekOperation = null
        operationsToCancel.forEach { it.cancel() }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                operationsToCancel.forEach { it.join() }
                releaseOwnedPlayer()
            } finally {
                scope.cancel()
            }
        }
    }

    private fun launchSerial(cancelActive: Boolean = true, block: suspend () -> Unit): Job? {
        if (disposed) return null
        if (cancelActive) {
            activeOperation?.cancel()
            seekOperation?.cancel()
        }
        val job = scope.launch {
            operations.withLock { block() }
        }
        activeOperations += job
        if (cancelActive) {
            activeOperation = job
        }
        job.invokeOnCompletion {
            activeOperations -= job
            if (activeOperation === job) {
                activeOperation = null
            }
            if (seekOperation === job) {
                seekOperation = null
            }
        }
        return job
    }

    private fun requestNewPlaybackGeneration() {
        generation += 1L
    }

    private suspend fun startNewPlayback(reference: String, messageKey: String, file: PlatformFile) {
        claimPlaybackOwner()
        generation += 1L
        val requestGeneration = generation
        _state.value = ChatAudioPlaybackUiState(
            activeReference = reference,
            activeMessageKey = messageKey,
            playback = AudioPlaybackState(isLoaded = false, isPlaying = false, phase = AudioPlaybackPhase.Loading),
            failed = false,
            failureReason = null,
            operationInFlight = true,
        )
        val loaded = withOwnedAudio { audioPlayer.load(file) } ?: run {
            updateOperationIfCurrent(requestGeneration, false)
            return
        }
        if (generation != requestGeneration) {
            return
        }
        when (loaded) {
            is PlatformResult.Success -> applyStateIfCurrent(requestGeneration, loaded.value.copy(phase = AudioPlaybackPhase.Ready), failed = false)
            is PlatformResult.Failure -> {
                failIfCurrent(requestGeneration, loaded.reason)
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
            PlatformResult.Cancelled -> {
                failIfCurrent(requestGeneration, "audio_load_cancelled")
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
            PlatformResult.Unsupported -> {
                failIfCurrent(requestGeneration, "audio_load_unsupported")
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
        }
        when (
            val played = withOwnedAudio { audioPlayer.play() } ?: run {
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
        ) {
            is PlatformResult.Success -> applyStateIfCurrent(
                requestGeneration,
                played.value.copy(phase = if (played.value.isPlaying) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Ready),
                failed = false,
            )
            is PlatformResult.Failure -> failIfCurrent(requestGeneration, played.reason)
            PlatformResult.Cancelled -> failIfCurrent(requestGeneration, "audio_play_cancelled")
            PlatformResult.Unsupported -> failIfCurrent(requestGeneration, "audio_play_unsupported")
        }
        updateOperationIfCurrent(requestGeneration, false)
    }

    private suspend fun pauseActive() {
        val requestGeneration = generation
        updateOperationIfCurrent(requestGeneration, true)
        when (
            val result = withOwnedAudio { audioPlayer.pause() } ?: run {
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
        ) {
            is PlatformResult.Success -> applyStateIfCurrent(requestGeneration, result.value.copy(isPlaying = false, phase = AudioPlaybackPhase.Paused), failed = false)
            is PlatformResult.Failure -> failIfCurrent(requestGeneration, result.reason)
            PlatformResult.Cancelled -> failIfCurrent(requestGeneration, "audio_pause_cancelled")
            PlatformResult.Unsupported -> failIfCurrent(requestGeneration, "audio_pause_unsupported")
        }
        updateOperationIfCurrent(requestGeneration, false)
    }

    private suspend fun resumeActive() {
        val requestGeneration = generation
        updateOperationIfCurrent(requestGeneration, true)
        when (
            val result = withOwnedAudio { audioPlayer.play() } ?: run {
                updateOperationIfCurrent(requestGeneration, false)
                return
            }
        ) {
            is PlatformResult.Success -> applyStateIfCurrent(
                requestGeneration,
                result.value.copy(phase = if (result.value.isPlaying) AudioPlaybackPhase.Playing else AudioPlaybackPhase.Ready),
                failed = false,
            )
            is PlatformResult.Failure -> failIfCurrent(requestGeneration, result.reason)
            PlatformResult.Cancelled -> failIfCurrent(requestGeneration, "audio_play_cancelled")
            PlatformResult.Unsupported -> failIfCurrent(requestGeneration, "audio_play_unsupported")
        }
        updateOperationIfCurrent(requestGeneration, false)
    }

    private suspend fun handlePlayerEvent(event: AudioPlaybackEvent) {
        operations.withLock {
            if (!ownsPlayback()) return
            val current = _state.value
            if (current.activeReference == null || event.state.sessionId != 0L && event.state.sessionId != current.playback.sessionId) return
            when (event) {
                is AudioPlaybackEvent.StateChanged -> if (!current.isTerminalPlaybackFailure()) {
                    _state.value = current.copy(playback = current.stabilizeNonPlayingState(event.state), failed = false)
                }
                is AudioPlaybackEvent.Failed -> {
                    _state.value = current.copy(
                        playback = event.state.copy(isPlaying = false, phase = AudioPlaybackPhase.Failed),
                        failed = true,
                        failureReason = event.reason,
                        operationInFlight = false,
                    )
                    releaseOwnedPlayer()
                }
                is AudioPlaybackEvent.Ended -> handleEnded(event.state)
            }
        }
    }

    private suspend fun handleEnded(endedState: AudioPlaybackState) {
        val current = _state.value
        _state.value = current.copy(playback = endedState.copy(isPlaying = false, phase = AudioPlaybackPhase.Ended), failed = false, operationInFlight = false)
        val next = current.activeMessageKey?.let { key -> nextConsecutiveAudioMessage(messages(), key) }
        val nextReference = next?.attachmentUri?.takeIf { it.isNotBlank() }
        if (next != null && nextReference != null) {
            startNewPlayback(
                reference = nextReference,
                messageKey = next.composeKey(),
                file = PlatformFile(nextReference, next.attachmentName, next.attachmentMimeType),
            )
        } else {
            releaseOwnedPlayer()
            generation += 1L
            _state.value = ChatAudioPlaybackUiState()
        }
    }

    private suspend fun refreshPosition() {
        val current = _state.value
        if (current.activeReference == null || current.operationInFlight) return
        if (current.playback.phase != AudioPlaybackPhase.Playing && !current.playback.isPlaying) return
        val requestGeneration = generation
        val next = withOwnedAudio { audioPlayer.state() } ?: return
        if (generation != requestGeneration) return
        if (next.phase != AudioPlaybackPhase.Ended) {
            applyStateIfCurrent(requestGeneration, next, failed = false)
        }
    }

    private fun applyStateIfCurrent(requestGeneration: Long, playback: AudioPlaybackState, failed: Boolean) {
        if (generation != requestGeneration) return
        val current = _state.value
        _state.value = current.copy(
            playback = playback,
            failed = failed,
            failureReason = if (failed) current.failureReason else null,
            operationInFlight = current.operationInFlight,
        )
    }

    private suspend fun failIfCurrent(requestGeneration: Long, reason: String?) {
        if (generation != requestGeneration) return
        val current = _state.value
        _state.value = current.copy(
            playback = current.playback.copy(isPlaying = false, phase = AudioPlaybackPhase.Failed),
            failed = true,
            failureReason = reason,
            operationInFlight = false,
        )
        releaseOwnedPlayer()
    }

    private fun updateOperation(inFlight: Boolean) {
        _state.value = _state.value.copy(operationInFlight = inFlight)
    }

    private fun updateOperationIfCurrent(requestGeneration: Long, inFlight: Boolean) {
        if (generation != requestGeneration) return
        updateOperation(inFlight)
    }

    private fun ChatAudioPlaybackUiState.isTerminalPlaybackFailure(): Boolean =
        failed || playback.phase == AudioPlaybackPhase.Failed

    private fun ChatAudioPlaybackUiState.stabilizeNonPlayingState(next: AudioPlaybackState): AudioPlaybackState =
        if (playback.phase == AudioPlaybackPhase.Paused &&
            next.phase == AudioPlaybackPhase.Ready &&
            !next.isPlaying
        ) {
            next.copy(phase = AudioPlaybackPhase.Paused)
        } else {
            next
        }

    private suspend fun claimPlaybackOwner() {
        ownerMutex.withLock {
            activeOwner = ownerToken
        }
    }

    private suspend fun ownsPlayback(): Boolean =
        ownerMutex.withLock { activeOwner === ownerToken }

    private suspend fun <T> withOwnedAudio(block: suspend () -> T): T? {
        globalAudioMutex.withLock {
            if (!ownsPlayback() || disposed) return null
            val result = block()
            return if (ownsPlayback() && !disposed) result else null
        }
    }

    private suspend fun releaseOwnedPlayer() {
        globalAudioMutex.withLock {
            if (!ownsPlayback()) return
            withContext(NonCancellable) { audioPlayer.stop() }
            ownerMutex.withLock {
                if (activeOwner === ownerToken) {
                    activeOwner = null
                }
            }
        }
    }

    private companion object {
        val ownerMutex = Mutex()
        val globalAudioMutex = Mutex()
        var activeOwner: Any? = null
    }
}
