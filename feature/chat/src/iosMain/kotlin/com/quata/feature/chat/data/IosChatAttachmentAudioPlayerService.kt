package com.quata.feature.chat.data

import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val transitions = Mutex()
    private var cachedFile: PlatformFile? = null

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> = transitions.withLock {
        // AVAudioPlayer can retain its input after a new load begins. Stop it before deleting a
        // previous temporary file *and* before starting the next network operation. If stopping
        // is not confirmed, retain the file and fail closed instead of racing native playback.
        when (val stopped = delegate.stop()) {
            is PlatformResult.Success -> releaseCachedFile()
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
                    cachedFile = localFile
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
            // Cancellation can escape delegate.load without producing a PlatformResult. A file
            // is owned by this service only after a successful load; every other terminal path
            // removes it exactly once.
            if (!adopted) downloads.discard(localFile)
        }
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = delegate.play()
    override suspend fun pause(): PlatformResult<AudioPlaybackState> = delegate.pause()
    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = delegate.seekTo(positionMillis)

    override suspend fun stop(): PlatformResult<Unit> = transitions.withLock {
        val result = delegate.stop()
        if (result is PlatformResult.Success) releaseCachedFile()
        return result
    }

    override suspend fun state(): AudioPlaybackState = delegate.state()

    private fun releaseCachedFile() {
        cachedFile?.let(downloads::discard)
        cachedFile = null
    }
}

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
