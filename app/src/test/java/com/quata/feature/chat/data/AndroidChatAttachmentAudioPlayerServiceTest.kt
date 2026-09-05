package com.quata.feature.chat.data

import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidChatAttachmentAudioPlayerServiceTest {
    @Test
    fun loadStopsBeforeResolvingRemoteAttachmentAndPassesOnlyLocalFileToDelegate() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeAudioPlayer(events)
        val service = AndroidChatAttachmentAudioPlayerService(
            delegate = delegate,
            resolver = AndroidChatAttachmentFileResolver { file ->
                events += "resolve:${file.displayName}"
                PlatformResult.Success(localFile("cached.m4a"))
            },
        )

        val result = service.load(remoteFile("voice.m4a"))

        assertTrue(result is PlatformResult.Success)
        assertEquals(listOf("stop", "resolve:voice.m4a", "load:file:///cache/cached.m4a"), events)
    }

    @Test
    fun resolverFailureDoesNotInvokeNativeLoad() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeAudioPlayer(events)
        val service = AndroidChatAttachmentAudioPlayerService(
            delegate = delegate,
            resolver = AndroidChatAttachmentFileResolver {
                events += "resolve"
                PlatformResult.Failure("download_failed")
            },
        )

        val result = service.load(remoteFile("voice.m4a"))

        assertTrue(result is PlatformResult.Failure)
        assertEquals(listOf("stop", "resolve"), events)
    }

    @Test
    fun stopPausePlaySeekAndEventsRemainNativeDelegateOwned() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeAudioPlayer(events)
        val service = AndroidChatAttachmentAudioPlayerService(
            delegate = delegate,
            resolver = AndroidChatAttachmentFileResolver { PlatformResult.Success(it) },
        )

        service.play()
        service.pause()
        service.seekTo(800)
        service.stop()

        assertEquals(delegate.events, service.events)
        assertEquals(listOf("play", "pause", "seek:800", "stop"), events)
    }

    @Test
    fun localFilesPassThroughWithoutChangingReferences() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FakeAudioPlayer(events)
        val service = AndroidChatAttachmentAudioPlayerService(
            delegate = delegate,
            resolver = AndroidChatAttachmentFileResolver { PlatformResult.Success(it) },
        )

        service.load(localFile("recording.m4a"))

        assertEquals(listOf("stop", "load:file:///cache/recording.m4a"), events)
    }

    @Test
    fun documentOpenResolvesRemoteAttachmentBeforeNativeViewer() = runBlocking {
        val events = mutableListOf<String>()
        val service = AndroidChatAttachmentDocumentOpenService(
            delegate = FakeDocumentOpener(events),
            resolver = AndroidChatAttachmentFileResolver { file ->
                events += "resolve:${file.displayName}"
                PlatformResult.Success(localFile("cached.pdf"))
            },
        )

        val result = service.open(remoteFile("report.pdf", "application/pdf"))

        assertTrue(result is PlatformResult.Success)
        assertEquals(listOf("resolve:report.pdf", "open:file:///cache/cached.pdf"), events)
    }

    @Test
    fun documentOpenResolverFailureDoesNotLaunchNativeViewer() = runBlocking {
        val events = mutableListOf<String>()
        val service = AndroidChatAttachmentDocumentOpenService(
            delegate = FakeDocumentOpener(events),
            resolver = AndroidChatAttachmentFileResolver {
                events += "resolve"
                PlatformResult.Failure("download_failed")
            },
        )

        val result = service.open(remoteFile("report.pdf", "application/pdf"))

        assertTrue(result is PlatformResult.Failure)
        assertEquals(listOf("resolve"), events)
    }

    private class FakeAudioPlayer(private val calls: MutableList<String>) : AudioPlayerService {
        override val events: Flow<AudioPlaybackEvent> = emptyFlow()

        override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
            calls += "load:${file.reference}"
            return PlatformResult.Success(AudioPlaybackState(isLoaded = true))
        }

        override suspend fun play(): PlatformResult<AudioPlaybackState> {
            calls += "play"
            return PlatformResult.Success(AudioPlaybackState(isLoaded = true, isPlaying = true))
        }

        override suspend fun pause(): PlatformResult<AudioPlaybackState> {
            calls += "pause"
            return PlatformResult.Success(AudioPlaybackState(isLoaded = true, isPlaying = false))
        }

        override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> {
            calls += "seek:$positionMillis"
            return PlatformResult.Success(AudioPlaybackState(isLoaded = true, positionMillis = positionMillis))
        }

        override suspend fun stop(): PlatformResult<Unit> {
            calls += "stop"
            return PlatformResult.Success(Unit)
        }

        override suspend fun state(): AudioPlaybackState = AudioPlaybackState(isLoaded = true)
    }

    private class FakeDocumentOpener(private val calls: MutableList<String>) : DocumentOpenService {
        override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
            calls += "open:${file.reference}"
            return PlatformResult.Success(Unit)
        }
    }

    private fun remoteFile(name: String, mimeType: String = "audio/mp4") =
        PlatformFile("https://project.supabase.co/storage/v1/object/public/chat-attachments/$name", name, mimeType)
    private fun localFile(name: String) = PlatformFile("file:///cache/$name", name, "audio/mp4")
}
