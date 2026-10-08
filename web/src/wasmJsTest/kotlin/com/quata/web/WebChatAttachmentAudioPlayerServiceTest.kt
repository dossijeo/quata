package com.quata.web

import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackPhase
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.MaterializedMediaFileLease
import com.quata.core.platform.MediaFileMaterializer
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WebChatAttachmentAudioPlayerServiceTest {
    private val remote = PlatformFile(
        reference = "https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/chat-attachments/user-7/message-4/same-audio.m4a",
        displayName = "same-audio.m4a",
        mimeType = "audio/mp4",
    )

    @Test
    fun failedMaterializationCanRetryTheSameAttachmentAndLoadsOnlyRecoveredBytes() = runTest {
        val delegate = RecordingPlayer()
        var attempts = 0
        var releases = 0
        val materializer = MediaFileMaterializer {
            attempts += 1
            if (attempts == 1) {
                PlatformResult.Failure("forced_web_audio_transport_failure")
            } else {
                PlatformResult.Success(
                    MaterializedMediaFileLease(
                        PlatformFile("blob:https://localhost/recovered-audio", remote.displayName, remote.mimeType),
                    ) { releases += 1 },
                )
            }
        }
        val service = WebChatAttachmentAudioPlayerService(delegate, materializer)

        val failed = service.load(remote)
        val recovered = service.load(remote)
        val stopped = service.stop()

        assertIs<PlatformResult.Failure>(failed)
        assertEquals("forced_web_audio_transport_failure", failed.reason)
        assertIs<PlatformResult.Success<AudioPlaybackState>>(recovered)
        assertIs<PlatformResult.Success<Unit>>(stopped)
        assertEquals(2, attempts)
        assertEquals(listOf("stop", "stop", "load:blob:https://localhost/recovered-audio", "stop"), delegate.calls)
        assertEquals(1, releases, "the recovered temporary Blob lease must be released exactly once")
    }

    @Test
    fun stopFailureRetainsTheActiveLeaseUntilNativePlaybackActuallyStops() = runTest {
        val delegate = RecordingPlayer()
        var releases = 0
        val service = WebChatAttachmentAudioPlayerService(
            delegate = delegate,
            materializer = MediaFileMaterializer {
                PlatformResult.Success(
                    MaterializedMediaFileLease(
                        PlatformFile("blob:https://localhost/owned-audio", remote.displayName, remote.mimeType),
                    ) { releases += 1 },
                )
            },
        )

        assertIs<PlatformResult.Success<AudioPlaybackState>>(service.load(remote))
        delegate.stopResult = PlatformResult.Failure("forced_stop_failure")
        assertIs<PlatformResult.Failure>(service.stop())
        assertEquals(0, releases)

        delegate.stopResult = PlatformResult.Success(Unit)
        assertIs<PlatformResult.Success<Unit>>(service.stop())
        assertEquals(1, releases)
    }

    @Test
    fun cancelledNativeLoadReleasesMaterializedBlobLease() = runTest {
        val delegate = RecordingPlayer().apply {
            loadThrowable = CancellationException("forced_native_load_cancellation")
        }
        var releases = 0
        val service = WebChatAttachmentAudioPlayerService(
            delegate = delegate,
            materializer = MediaFileMaterializer {
                PlatformResult.Success(
                    MaterializedMediaFileLease(
                        PlatformFile("blob:https://localhost/cancelled-audio", remote.displayName, remote.mimeType),
                    ) { releases += 1 },
                )
            },
        )

        assertFailsWith<CancellationException> { service.load(remote) }
        assertEquals(1, releases)
    }

    private class RecordingPlayer : AudioPlayerService {
        val calls = mutableListOf<String>()
        var stopResult: PlatformResult<Unit> = PlatformResult.Success(Unit)
        var loadThrowable: Throwable? = null
        override val events: Flow<AudioPlaybackEvent> = emptyFlow()

        override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
            calls += "load:${file.reference}"
            loadThrowable?.let { throw it }
            return PlatformResult.Success(
                AudioPlaybackState(isLoaded = true, phase = AudioPlaybackPhase.Ready, sessionId = 1L),
            )
        }

        override suspend fun play(): PlatformResult<AudioPlaybackState> =
            PlatformResult.Success(AudioPlaybackState(isLoaded = true, isPlaying = true, phase = AudioPlaybackPhase.Playing))

        override suspend fun pause(): PlatformResult<AudioPlaybackState> =
            PlatformResult.Success(AudioPlaybackState(isLoaded = true, phase = AudioPlaybackPhase.Paused))

        override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> =
            PlatformResult.Success(AudioPlaybackState(isLoaded = true, positionMillis = positionMillis, phase = AudioPlaybackPhase.Paused))

        override suspend fun stop(): PlatformResult<Unit> {
            calls += "stop"
            return stopResult
        }

        override suspend fun state(): AudioPlaybackState = AudioPlaybackState()
    }
}
