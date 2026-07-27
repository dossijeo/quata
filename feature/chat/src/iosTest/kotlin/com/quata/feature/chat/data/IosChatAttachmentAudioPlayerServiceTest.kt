package com.quata.feature.chat.data

import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.feature.chat.presentation.chat.ChatAudioPlaybackLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IosChatAttachmentAudioPlayerServiceTest {
    @Test
    fun swiftFacingConstructorUsesSecureDownloaderByDefault() = runBlocking {
        val events = mutableListOf<String>()
        val player = FakePlayer(events)
        val service = IosChatAttachmentAudioPlayerService(
            delegate = player,
            configuration = IosChatRuntimeConfiguration("https://project.supabase.co", "key"),
            authSession = authSession(),
        )

        assertIs<PlatformResult.Failure>(service.load(remoteFile("not-allow-listed")))
        assertEquals(listOf("stop"), events)
    }

    @Test
    fun load_stopsBeforeDiscardingPreviousFileAndDownloadingReplacement() = runBlocking {
        val events = mutableListOf<String>()
        val downloads = FakeDownloads(events, listOf(localFile("first"), localFile("second")))
        val player = FakePlayer(events)
        val service = service(player, downloads)

        assertIs<PlatformResult.Success<AudioPlaybackState>>(service.load(remoteFile("first")))
        events.clear()
        assertIs<PlatformResult.Success<AudioPlaybackState>>(service.load(remoteFile("second")))

        assertEquals(listOf("stop", "discard:first", "download:second", "load:second"), events)
    }

    @Test
    fun load_doesNotDeleteOrDownloadWhenStoppingPlaybackFails() = runBlocking {
        val events = mutableListOf<String>()
        val downloads = FakeDownloads(events, listOf(localFile("first"), localFile("second")))
        val player = FakePlayer(events)
        val service = service(player, downloads)
        service.load(remoteFile("first"))
        events.clear()
        player.stopResult = PlatformResult.Failure("native_stop_failed")

        val result = service.load(remoteFile("second"))

        assertIs<PlatformResult.Failure>(result)
        assertEquals(listOf("stop"), events)
    }

    @Test
    fun load_discardsPreviousFileOnlyAfterStopWhenNextDownloadFails() = runBlocking {
        val events = mutableListOf<String>()
        val downloads = FakeDownloads(events, listOf(localFile("first"), null))
        val player = FakePlayer(events)
        val service = service(player, downloads)
        service.load(remoteFile("first"))
        events.clear()

        val result = service.load(remoteFile("second"))

        assertIs<PlatformResult.Failure>(result)
        assertEquals(listOf("stop", "discard:first", "download:second"), events)
    }

    @Test
    fun stop_releasesCachedFileOnlyOnSuccess() = runBlocking {
        val events = mutableListOf<String>(); val downloads = FakeDownloads(events, listOf(localFile("first")))
        val player = FakePlayer(events); val service = service(player, downloads)
        service.load(remoteFile("first")); events.clear()
        assertIs<PlatformResult.Success<Unit>>(service.stop())
        assertEquals(listOf("stop", "discard:first"), events)
    }

    @Test
    fun stop_failureCancelledAndUnsupportedRetainCachedFile() = runBlocking {
        listOf<PlatformResult<Unit>>(PlatformResult.Failure("x"), PlatformResult.Cancelled, PlatformResult.Unsupported).forEach { outcome ->
            val events = mutableListOf<String>(); val downloads = FakeDownloads(events, listOf(localFile("first")))
            val player = FakePlayer(events); val service = service(player, downloads)
            service.load(remoteFile("first")); events.clear(); player.stopResult = outcome
            service.stop(); assertEquals(listOf("stop"), events)
        }
    }

    @Test
    fun concurrentLoadsAreSerializedWithoutOrphanedFiles() = runBlocking {
        val events = mutableListOf<String>(); val downloads = FakeDownloads(events, listOf(localFile("one"), localFile("two")), delayMillis = 20)
        val player = FakePlayer(events); val service = service(player, downloads)
        coroutineScope { listOf(async { service.load(remoteFile("one")) }, async { service.load(remoteFile("two")) }).forEach { it.await() } }
        service.stop()
        assertEquals(2, events.count { it.startsWith("discard:") })
    }

    @Test
    fun downloaderTerminalOutcomesDoNotInvokeDelegateLoad() = runBlocking {
        listOf<PlatformResult<PlatformFile>>(PlatformResult.Failure("x"), PlatformResult.Cancelled, PlatformResult.Unsupported).forEach { outcome ->
            val events = mutableListOf<String>(); val downloads = OutcomeDownloads(events, outcome)
            val player = FakePlayer(events); val result = service(player, downloads).load(remoteFile("x"))
            assertIs<PlatformResult.Failure>(result); assertEquals(listOf("stop", "download:x"), events)
        }
    }

    @Test
    fun delegateLoadTerminalOutcomesDiscardDownloadedFile() = runBlocking {
        listOf<PlatformResult<AudioPlaybackState>>(PlatformResult.Failure("x"), PlatformResult.Cancelled, PlatformResult.Unsupported).forEach { outcome ->
            val events = mutableListOf<String>(); val downloads = FakeDownloads(events, listOf(localFile("x")))
            val player = FakePlayer(events).apply { loadResult = outcome }
            val result = service(player, downloads).load(remoteFile("x"))
            assertIs<PlatformResult.Failure>(result); assertEquals(listOf("stop", "download:x", "load:x", "discard:x"), events)
        }
    }

    @Test
    fun dispose_cancelsInFlightLoadStopsPlayerAndLeavesNoTemporaryFile() = runBlocking {
        val events = mutableListOf<String>()
        val downloads = FakeDownloads(events, listOf(localFile("pending")))
        val player = FakePlayer(events).apply { suspendLoad = true }
        val service = service(player, downloads)
        val lifecycle = ChatAudioPlaybackLifecycleOwner(service, Dispatchers.Unconfined)

        lifecycle.launch { service.load(remoteFile("pending")) }
        player.loadStarted.await()
        lifecycle.dispose()
        player.terminalStop.await()

        assertEquals(
            listOf("stop", "download:pending", "load:pending", "discard:pending", "stop"),
            events,
        )
        assertEquals(1, events.count { it == "discard:pending" })
    }

    private fun service(player: FakePlayer, downloads: IosChatAttachmentAudioDownloads) = IosChatAttachmentAudioPlayerService(
        delegate = player,
        configuration = IosChatRuntimeConfiguration("https://project.supabase.co", "key"),
        authSession = authSession(),
        downloads = downloads,
    )

    private fun authSession() = IosRenewableAuthSession(
        IosSupabaseAuthSessionRefresher(
            IosSupabaseAuthRuntimeConfiguration("https://project.supabase.co", "key"),
        ),
    )

    private fun remoteFile(name: String) = PlatformFile("https://remote.invalid/$name", name, "audio/mp4")
    private fun localFile(name: String) = PlatformFile("file:///tmp/$name.m4a", name, "audio/mp4")

    private class FakeDownloads(
        private val events: MutableList<String>,
        private val files: List<PlatformFile?>,
        private val delayMillis: Long = 0,
    ) : IosChatAttachmentAudioDownloads {
        private var index = 0
        override suspend fun download(publicUrl: String, displayName: String?): PlatformResult<PlatformFile> {
            events += "download:$displayName"
            if (delayMillis > 0) delay(delayMillis)
            val file = files[index++]
            return if (file == null) PlatformResult.Failure("download_failed") else PlatformResult.Success(file)
        }
        override fun discard(file: PlatformFile) { events += "discard:${file.displayName}" }
    }

    private class OutcomeDownloads(private val events: MutableList<String>, private val outcome: PlatformResult<PlatformFile>) : IosChatAttachmentAudioDownloads {
        override suspend fun download(publicUrl: String, displayName: String?): PlatformResult<PlatformFile> { events += "download:$displayName"; return outcome }
        override fun discard(file: PlatformFile) { events += "discard:${file.displayName}" }
    }

    private class FakePlayer(private val events: MutableList<String>) : AudioPlayerService {
        var stopResult: PlatformResult<Unit> = PlatformResult.Success(Unit)
        var loadResult: PlatformResult<AudioPlaybackState>? = null
        var suspendLoad = false
        val loadStarted = CompletableDeferred<Unit>()
        val terminalStop = CompletableDeferred<Unit>()
        override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> {
            events += "load:${file.displayName}"
            loadStarted.complete(Unit)
            if (suspendLoad) awaitCancellation()
            return loadResult ?: PlatformResult.Success(AudioPlaybackState(isLoaded = true))
        }
        override suspend fun play() = PlatformResult.Success(AudioPlaybackState())
        override suspend fun pause() = PlatformResult.Success(AudioPlaybackState())
        override suspend fun seekTo(positionMillis: Long) = PlatformResult.Success(AudioPlaybackState())
        override suspend fun stop(): PlatformResult<Unit> {
            events += "stop"
            if (suspendLoad && events.count { it == "stop" } > 1) terminalStop.complete(Unit)
            return stopResult
        }
        override suspend fun state() = AudioPlaybackState()
    }
}
