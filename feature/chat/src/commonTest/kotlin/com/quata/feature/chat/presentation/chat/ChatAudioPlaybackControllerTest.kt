package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackPhase
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAudioPlaybackControllerTest {
    @Test
    fun playRequestDoesNotBecomePlayingBeforePlayerConfirmsIt() = runTest {
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Ready, isPlaying = false) })
        val controller = ChatAudioPlaybackController(player, { listOf(message("1", 1_000)) }, StandardTestDispatcher(testScheduler))

        controller.toggle(message("1", 1_000), file("1"))
        runCurrent()

        assertEquals(AudioPlaybackPhase.Ready, controller.state.value.playback.phase)
        assertFalse(controller.state.value.playback.isPlaying)
        assertFalse(controller.state.value.failed)
        controller.dispose()
    }

    @Test
    fun playFailureNeverLeavesUiPlaying() = runTest {
        val player = RecordingAudioPlayer(playResult = { PlatformResult.Failure("boom") })
        val controller = ChatAudioPlaybackController(player, { listOf(message("1", 1_000)) }, StandardTestDispatcher(testScheduler))

        controller.toggle(message("1", 1_000), file("1"))
        runCurrent()

        assertEquals(AudioPlaybackPhase.Failed, controller.state.value.playback.phase)
        assertFalse(controller.state.value.playback.isPlaying)
        assertTrue(controller.state.value.failed)
        assertEquals("boom", controller.state.value.failureReason)
        assertTrue("stop" in player.calls)
        controller.dispose()
    }

    @Test
    fun lateReadyAfterPauseDoesNotOverwritePaused() = runTest {
        val first = message("1", 1_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val session = controller.state.value.playback.sessionId
        controller.toggle(first, file("1"))
        runCurrent()
        player.emitStateChanged(session, AudioPlaybackPhase.Ready, isPlaying = false)
        runCurrent()

        assertEquals(AudioPlaybackPhase.Paused, controller.state.value.playback.phase)
        assertFalse(controller.state.value.playback.isPlaying)
        controller.dispose()
    }

    @Test
    fun nativeEndedAdvancesForwardOnceAndThenStops() = runTest {
        val first = message("1", 1_000)
        val second = message("2", 2_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(second, first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val firstSession = controller.state.value.playback.sessionId
        player.emitEnded(firstSession)
        runCurrent()

        assertEquals(second.composeKey(), controller.state.value.activeMessageKey)
        val secondSession = controller.state.value.playback.sessionId
        player.emitEnded(secondSession)
        runCurrent()

        assertEquals(null, controller.state.value.activeMessageKey)
        assertEquals(listOf("load:1", "play:1", "load:2", "play:2", "stop"), player.calls)
        controller.dispose()
    }

    @Test
    fun nativeEndedWithoutNextStopsPlayerBeforeClearingState() = runTest {
        val first = message("1", 1_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val session = controller.state.value.playback.sessionId
        player.emitEnded(session)
        runCurrent()

        assertEquals(null, controller.state.value.activeMessageKey)
        assertEquals(listOf("load:1", "play:1", "stop"), player.calls)
        controller.dispose()
    }

    @Test
    fun lateEndedFromPreviousSessionCannotReplaceCurrentPlayback() = runTest {
        val first = message("1", 1_000)
        val second = message("2", 2_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(first, second) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val firstSession = controller.state.value.playback.sessionId
        controller.toggle(second, file("2"))
        runCurrent()
        val activeSecond = controller.state.value.activeMessageKey

        player.emitEnded(firstSession)
        runCurrent()

        assertEquals(activeSecond, controller.state.value.activeMessageKey)
        assertEquals(second.composeKey(), controller.state.value.activeMessageKey)
        controller.dispose()
    }

    @Test
    fun stateFromAutoAdvancedPreviousSessionCannotOverwriteNextPlayback() = runTest {
        val first = message("1", 1_000)
        val second = message("2", 2_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(first, second) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val firstSession = controller.state.value.playback.sessionId
        player.emitEnded(firstSession)
        runCurrent()
        val secondSession = controller.state.value.playback.sessionId

        player.emitStateChanged(firstSession, AudioPlaybackPhase.Ready, isPlaying = false)
        runCurrent()

        assertEquals(second.composeKey(), controller.state.value.activeMessageKey)
        assertEquals(secondSession, controller.state.value.playback.sessionId)
        assertEquals(AudioPlaybackPhase.Playing, controller.state.value.playback.phase)
        controller.dispose()
    }

    @Test
    fun replacingPendingAudioPreventsFirstLoadFromPlayingLater() = runTest {
        val first = message("1", 1_000)
        val second = message("2", 2_000)
        val firstLoadGate = CompletableDeferred<PlatformResult<AudioPlaybackState>>()
        val player = RecordingAudioPlayer(
            loadResult = { player, file ->
                if (file.reference.endsWith("/1")) {
                    firstLoadGate.await()
                } else {
                    PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false))
                }
            },
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
        )
        val controller = ChatAudioPlaybackController(player, { listOf(first, second) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        controller.toggle(second, file("2"))
        runCurrent()
        firstLoadGate.complete(PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false)))
        runCurrent()

        assertEquals(second.composeKey(), controller.state.value.activeMessageKey)
        assertEquals(listOf("load:1", "load:2", "play:2"), player.calls.filter { it != "stop" })
        controller.dispose()
    }

    @Test
    fun newerControllerOwnsSharedPlayerAndOldControllerCannotStopIt() = runTest {
        val first = message("1", 1_000)
        val second = message("2", 2_000)
        val firstLoadGate = CompletableDeferred<PlatformResult<AudioPlaybackState>>()
        val player = RecordingAudioPlayer(
            loadResult = { player, file ->
                if (file.reference.endsWith("/1")) {
                    firstLoadGate.await()
                } else {
                    PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false))
                }
            },
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
        )
        val oldController = ChatAudioPlaybackController(player, { listOf(first, second) }, StandardTestDispatcher(testScheduler))
        val newController = ChatAudioPlaybackController(player, { listOf(first, second) }, StandardTestDispatcher(testScheduler))

        oldController.toggle(first, file("1"))
        runCurrent()
        newController.toggle(second, file("2"))
        runCurrent()
        firstLoadGate.complete(PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false)))
        runCurrent()
        oldController.dispose()
        runCurrent()

        assertEquals(second.composeKey(), newController.state.value.activeMessageKey)
        assertEquals(AudioPlaybackPhase.Playing, newController.state.value.playback.phase)
        assertEquals(listOf("load:1", "load:2", "play:2"), player.calls)
        newController.dispose()
    }

    @Test
    fun queuedReadyAfterPlayFailureCannotClearFailure() = runTest {
        val first = message("1", 1_000)
        val player = RecordingAudioPlayer(playResult = { PlatformResult.Failure("boom") })
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val failedSession = player.lastLoadedSessionId
        player.emitStateChanged(failedSession, AudioPlaybackPhase.Ready, isPlaying = false)
        runCurrent()

        assertEquals(AudioPlaybackPhase.Failed, controller.state.value.playback.phase)
        assertTrue(controller.state.value.failed)
        assertTrue("stop" in player.calls)
        controller.dispose()
    }

    @Test
    fun tapAfterLoadFailureRetriesLoadInsteadOfPlayingMissingPlayer() = runTest {
        val first = message("1", 1_000)
        var attempt = 0
        val player = RecordingAudioPlayer(
            loadResult = { player, _ ->
                attempt += 1
                if (attempt == 1) {
                    PlatformResult.Failure("network")
                } else {
                    PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false))
                }
            },
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
        )
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        controller.toggle(first, file("1"))
        runCurrent()

        assertEquals(AudioPlaybackPhase.Playing, controller.state.value.playback.phase)
        assertEquals(listOf("load:1", "stop", "load:1", "play:1"), player.calls)
        controller.dispose()
    }

    @Test
    fun nativeFailedEventStopsPlayerAndKeepsFailureTerminal() = runTest {
        val first = message("1", 1_000)
        val player = RecordingAudioPlayer(playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) })
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        val session = player.currentSessionId
        player.emitFailed(session)
        runCurrent()
        player.emitStateChanged(session, AudioPlaybackPhase.Ready, isPlaying = false)
        runCurrent()

        assertEquals(AudioPlaybackPhase.Failed, controller.state.value.playback.phase)
        assertTrue(controller.state.value.failed)
        assertTrue("stop" in player.calls)
        controller.dispose()
    }

    @Test
    fun secondTapWhileLoadingDoesNotCancelIntoResume() = runTest {
        val first = message("1", 1_000)
        val loadGate = CompletableDeferred<PlatformResult<AudioPlaybackState>>()
        val player = RecordingAudioPlayer(
            loadResult = { player, _ ->
                loadGate.await().also { player.state(AudioPlaybackPhase.Ready, isPlaying = false) }
            },
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
        )
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        controller.toggle(first, file("1"))
        runCurrent()

        assertEquals(AudioPlaybackPhase.Loading, controller.state.value.playback.phase)
        loadGate.complete(PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false)))
        runCurrent()

        assertEquals(listOf("load:1", "play:1"), player.calls)
        controller.dispose()
    }

    @Test
    fun rapidSeekKeepsOnlyLatestRequestedPosition() = runTest {
        val first = message("1", 1_000)
        val firstSeekGate = CompletableDeferred<Unit>()
        val player = RecordingAudioPlayer(
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
            seekResult = { player, positionMillis ->
                if (positionMillis == 2_000L) firstSeekGate.await()
                PlatformResult.Success(player.state(AudioPlaybackPhase.Playing, isPlaying = true, positionMillis = positionMillis))
            },
        )
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        controller.seekToFraction(first.attachmentUri.orEmpty(), 0.2f)
        runCurrent()
        controller.seekToFraction(first.attachmentUri.orEmpty(), 0.8f)
        runCurrent()
        firstSeekGate.complete(Unit)
        runCurrent()

        assertTrue("seek:8000" in player.calls)
        assertEquals(8_000L, controller.state.value.playback.positionMillis)
        controller.dispose()
    }

    @Test
    fun disposeCancelsQueuedSeekOperations() = runTest {
        val first = message("1", 1_000)
        val seekGate = CompletableDeferred<Unit>()
        val player = RecordingAudioPlayer(
            playState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
            seekResult = { player, positionMillis ->
                seekGate.await()
                PlatformResult.Success(player.state(AudioPlaybackPhase.Playing, isPlaying = true, positionMillis = positionMillis))
            },
        )
        val controller = ChatAudioPlaybackController(player, { listOf(first) }, StandardTestDispatcher(testScheduler))

        controller.toggle(first, file("1"))
        runCurrent()
        controller.seekToFraction(first.attachmentUri.orEmpty(), 0.8f)
        runCurrent()
        controller.dispose()
        seekGate.complete(Unit)
        runCurrent()

        assertTrue("stop" in player.calls)
        assertEquals(AudioPlaybackPhase.Playing, controller.state.value.playback.phase)
    }

    private class RecordingAudioPlayer(
        private val loadResult: suspend (RecordingAudioPlayer, PlatformFile) -> PlatformResult<AudioPlaybackState> = { player, _ ->
            PlatformResult.Success(player.state(AudioPlaybackPhase.Ready, isPlaying = false))
        },
        private val playState: (RecordingAudioPlayer) -> AudioPlaybackState = { it.state(AudioPlaybackPhase.Playing, isPlaying = true) },
        private val playResult: (RecordingAudioPlayer) -> PlatformResult<AudioPlaybackState> = { PlatformResult.Success(it.playState(it)) },
        private val seekResult: suspend (RecordingAudioPlayer, Long) -> PlatformResult<AudioPlaybackState> = { player, positionMillis ->
            PlatformResult.Success(player.state(AudioPlaybackPhase.Playing, isPlaying = true, positionMillis = positionMillis))
        },
    ) : AudioPlayerService {
        private val eventSink = MutableSharedFlow<AudioPlaybackEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AudioPlaybackEvent> = eventSink
        val calls = mutableListOf<String>()
        private var sessionId = 0L
        private var activeId: String? = null
        val currentSessionId: Long get() = sessionId
        var lastLoadedSessionId: Long = 0L
            private set

        override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
            activeId = file.reference.substringAfterLast("/")
            calls += "load:$activeId"
            sessionId += 1L
            lastLoadedSessionId = sessionId
            return loadResult(this, file)
        }

        override suspend fun play(): PlatformResult<AudioPlaybackState> {
            calls += "play:$activeId"
            return playResult(this)
        }

        override suspend fun pause(): PlatformResult<AudioPlaybackState> =
            PlatformResult.Success(state(AudioPlaybackPhase.Paused, isPlaying = false))

        override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> {
            calls += "seek:$positionMillis"
            return seekResult(this, positionMillis)
        }

        override suspend fun stop(): PlatformResult<Unit> {
            calls += "stop"
            sessionId += 1L
            return PlatformResult.Success(Unit)
        }

        override suspend fun state(): AudioPlaybackState = state(AudioPlaybackPhase.Playing, isPlaying = true)

        fun state(
            phase: AudioPlaybackPhase,
            isPlaying: Boolean,
            positionMillis: Long = 0L,
            durationMillis: Long = 10_000L,
        ): AudioPlaybackState = AudioPlaybackState(
            isLoaded = true,
            isPlaying = isPlaying,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            phase = phase,
            sessionId = sessionId,
        )

        fun emitEnded(sessionId: Long) {
            eventSink.tryEmit(
                AudioPlaybackEvent.Ended(
                    AudioPlaybackState(
                        isLoaded = true,
                        isPlaying = false,
                        positionMillis = 10_000L,
                        durationMillis = 10_000L,
                        phase = AudioPlaybackPhase.Ended,
                        sessionId = sessionId,
                    ),
                ),
            )
        }

        fun emitStateChanged(sessionId: Long, phase: AudioPlaybackPhase, isPlaying: Boolean) {
            eventSink.tryEmit(AudioPlaybackEvent.StateChanged(state(phase, isPlaying).copy(sessionId = sessionId)))
        }

        fun emitFailed(sessionId: Long) {
            eventSink.tryEmit(
                AudioPlaybackEvent.Failed(
                    state(AudioPlaybackPhase.Failed, isPlaying = false).copy(sessionId = sessionId),
                    "decode",
                ),
            )
        }
    }

    private fun message(id: String, sentAtMillis: Long) = Message(
        id = id,
        conversationId = "conversation",
        senderId = "sender-a",
        senderName = "sender-a",
        text = "",
        sentAt = "",
        sentAtMillis = sentAtMillis,
        attachmentUri = "https://example.test/$id",
        attachmentName = "$id.m4a",
        attachmentMimeType = "audio/mp4",
    )

    private fun file(id: String) = PlatformFile("https://example.test/$id", "$id.m4a", "audio/mp4")
}
