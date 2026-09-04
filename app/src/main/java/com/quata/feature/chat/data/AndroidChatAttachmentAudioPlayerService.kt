package com.quata.feature.chat.data

import com.quata.core.platform.AudioPlaybackEvent
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidChatAttachmentAudioPlayerService(
    private val delegate: AudioPlayerService,
    private val resolver: AndroidChatAttachmentFileResolver,
) : AudioPlayerService {
    private val transitions = Mutex()
    override val events: Flow<AudioPlaybackEvent>
        get() = delegate.events

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> = transitions.withLock {
        when (val stopped = delegate.stop()) {
            is PlatformResult.Success -> Unit
            is PlatformResult.Failure -> return PlatformResult.Failure(stopped.reason ?: "android_chat_audio_stop_failed")
            PlatformResult.Cancelled -> return PlatformResult.Failure("android_chat_audio_stop_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("android_chat_audio_stop_unsupported")
        }
        val resolvedFile = when (val resolved = resolver.resolve(file)) {
            is PlatformResult.Success -> resolved.value
            is PlatformResult.Failure -> return PlatformResult.Failure(resolved.reason ?: "android_chat_audio_resolve_failed")
            PlatformResult.Cancelled -> return PlatformResult.Failure("android_chat_audio_resolve_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("android_chat_audio_resolve_unsupported")
        }
        delegate.load(resolvedFile)
    }

    override suspend fun play(): PlatformResult<AudioPlaybackState> = delegate.play()
    override suspend fun pause(): PlatformResult<AudioPlaybackState> = delegate.pause()
    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> = delegate.seekTo(positionMillis)
    override suspend fun stop(): PlatformResult<Unit> = transitions.withLock { delegate.stop() }
    override suspend fun state(): AudioPlaybackState = delegate.state()
}

class AndroidChatAttachmentDocumentOpenService(
    private val delegate: DocumentOpenService,
    private val resolver: AndroidChatAttachmentFileResolver,
) : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val resolvedFile = when (val resolved = resolver.resolve(file)) {
            is PlatformResult.Success -> resolved.value
            is PlatformResult.Failure -> return PlatformResult.Failure(resolved.reason ?: "android_chat_document_resolve_failed")
            PlatformResult.Cancelled -> return PlatformResult.Failure("android_chat_document_resolve_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("android_chat_document_resolve_unsupported")
        }
        return delegate.open(resolvedFile)
    }
}

fun interface AndroidChatAttachmentFileResolver {
    suspend fun resolve(file: PlatformFile): PlatformResult<PlatformFile>
}

internal class AndroidChatAttachmentFileCacheResolver(
    private val sessionManager: SessionManager,
    private val cache: ChatAttachmentFileCache,
) : AndroidChatAttachmentFileResolver {
    override suspend fun resolve(file: PlatformFile): PlatformResult<PlatformFile> {
        if (!file.reference.isHttpReference()) return PlatformResult.Success(file)
        val session = sessionManager.currentSession()
            ?: return PlatformResult.Failure("android_chat_attachment_session_missing")
        val resolved = cache.resolveCachedAttachment(session.userId, file)
            ?: return PlatformResult.Failure("android_chat_attachment_download_failed")
        return PlatformResult.Success(resolved)
    }

    private fun String.isHttpReference(): Boolean {
        val trimmed = trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }
}
