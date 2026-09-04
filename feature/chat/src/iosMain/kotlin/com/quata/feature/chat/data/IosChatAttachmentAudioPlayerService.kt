package com.quata.feature.chat.data

import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resolves a Chat audio attachment to a validated local file before handing it to AVFoundation.
 *
 * A message attachment is remote data controlled by another user. The underlying iOS audio
 * player deliberately only understands local files, so this adapter never converts an arbitrary
 * URL into a file URL or lets AVFoundation issue an unauthenticated network request. Downloads
 * use the same authenticated, redirect-rejecting and size-bounded boundary as Quick Look.
 */
class IosChatAttachmentAudioPlayerService(
    private val delegate: AudioPlayerService,
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    private val downloads: IosChatAttachmentAudioDownloads,
    private val leaseStore: IosChatAttachmentAudioLeaseStore = SharedIosChatAttachmentAudioLeaseStore,
) : AudioPlayerService {
    /**
     * Explicit Swift-facing constructor. Kotlin default arguments are not emitted as Swift
     * overloads, so the host needs a real three-argument initializer that always installs the
     * authenticated, allow-listed downloader.
     */
    constructor(
        delegate: AudioPlayerService,
        configuration: IosChatRuntimeConfiguration,
        authSession: IosRenewableAuthSession,
    ) : this(
        delegate = delegate,
        configuration = configuration,
        authSession = authSession,
        downloads = IosChatAttachmentDownloaderAudioDownloads(
            IosChatAttachmentDownloader(
            configuration = configuration,
            authSession = authSession,
            ),
        ),
    )

    override val events: Flow<AudioPlaybackEvent>
        get() = delegate.events

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> =
        withContext(Dispatchers.Default) {
            leaseStore.loadReplacing(delegate, downloads, file)
        }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = delegate.play()
    override suspend fun pause(): PlatformResult<AudioPlaybackState> = delegate.pause()
    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = delegate.seekTo(positionMillis)

    override suspend fun stop(): PlatformResult<Unit> = leaseStore.stopAndRelease(delegate)

    override suspend fun state(): AudioPlaybackState = delegate.state()
}

open class IosChatAttachmentAudioLeaseStore {
    private data class Lease(
        val file: PlatformFile,
        val downloads: IosChatAttachmentAudioDownloads,
    )

    private val transitions = Mutex()
    private var cachedLease: Lease? = null

    suspend fun loadReplacing(
        delegate: AudioPlayerService,
        downloads: IosChatAttachmentAudioDownloads,
        file: PlatformFile,
    ): PlatformResult<AudioPlaybackState> = transitions.withLock {
        // AVFoundation can retain its input after a new load begins. Stop it before deleting the
        // previous temporary file and serialize this across host wrappers sharing the same player.
        when (val stopped = delegate.stop()) {
            is PlatformResult.Success -> releaseCachedLease()
            is PlatformResult.Failure -> return PlatformResult.Failure(stopped.reason ?: "ios_chat_audio_stop_failed")
            PlatformResult.Cancelled -> return PlatformResult.Failure("ios_chat_audio_stop_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("ios_chat_audio_stop_unsupported")
        }
        val downloaded = downloads.download(file.reference, file.displayName)
        val localFile = when (downloaded) {
            is PlatformResult.Success -> downloaded.value
            is PlatformResult.Failure -> return PlatformResult.Failure(downloaded.reason)
            PlatformResult.Cancelled -> return PlatformResult.Failure("ios_chat_audio_download_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("ios_chat_audio_download_unsupported")
        }
        var adopted = false
        try {
            return when (val result = delegate.load(localFile)) {
                is PlatformResult.Success -> {
                    cachedLease = Lease(localFile, downloads)
                    adopted = true
                    result
                }
                is PlatformResult.Failure ->
                    PlatformResult.Failure(result.reason ?: "ios_chat_audio_load_failed")
                PlatformResult.Cancelled ->
                    PlatformResult.Failure("ios_chat_audio_load_cancelled")
                PlatformResult.Unsupported ->
                    PlatformResult.Failure("ios_chat_audio_load_unsupported")
            }
        } finally {
            // Cancellation can escape delegate.load without producing a PlatformResult. A file is
            // adopted only after successful native load; every other terminal path removes it once.
            if (!adopted) downloads.discard(localFile)
        }
    }

    suspend fun stopAndRelease(delegate: AudioPlayerService): PlatformResult<Unit> = transitions.withLock {
        val result = delegate.stop()
        if (result is PlatformResult.Success) releaseCachedLease()
        result
    }

    private fun releaseCachedLease() {
        cachedLease?.let { it.downloads.discard(it.file) }
        cachedLease = null
    }
}

private object SharedIosChatAttachmentAudioLeaseStore : IosChatAttachmentAudioLeaseStore()

/** Narrow seam for deterministic lifecycle tests; production delegates to the secure downloader. */
interface IosChatAttachmentAudioDownloads {
    suspend fun download(publicUrl: String, displayName: String?): PlatformResult<PlatformFile>
    fun discard(file: PlatformFile)
}

private class IosChatAttachmentDownloaderAudioDownloads(
    private val downloader: IosChatAttachmentDownloader,
) : IosChatAttachmentAudioDownloads {
    override suspend fun download(publicUrl: String, displayName: String?): PlatformResult<PlatformFile> =
        downloader.download(publicUrl, displayName)

    override fun discard(file: PlatformFile) = downloader.discard(file)
}
