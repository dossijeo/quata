package com.quata.feature.chat.data

import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession

/**
 * iOS boundary for opening a remote Chat document without passing its network URL to UIKit.
 *
 * The downloader validates the Supabase object origin, uses the active authenticated session,
 * rejects redirects, bounds the body and writes a uniquely named temporary file. Only that local
 * sandbox reference is handed to [DocumentOpenService], whose iOS implementation presents Quick
 * Look. Thus Quick Look never receives credentials or an untrusted remote URL.
 */
class IosChatAttachmentPreviewService(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    private val documentOpener: DocumentOpenService,
    private val downloader: IosChatAttachmentDownloader = IosChatAttachmentDownloader(
        configuration = configuration,
        authSession = authSession,
    ),
) {
    suspend fun openRemoteAttachment(attachment: PlatformFile): PlatformResult<Unit> {
        val downloaded = downloader.download(
            publicUrl = attachment.reference,
            displayName = attachment.displayName,
        )
        val localFile = when (downloaded) {
            is PlatformResult.Success -> downloaded.value
            is PlatformResult.Failure -> return PlatformResult.Failure(downloaded.reason)
            PlatformResult.Cancelled -> return PlatformResult.Failure("ios_chat_attachment_download_cancelled")
            PlatformResult.Unsupported -> return PlatformResult.Failure("ios_chat_attachment_download_unsupported")
        }
        return when (val opened = documentOpener.open(localFile)) {
            is PlatformResult.Success -> opened
            is PlatformResult.Failure -> PlatformResult.Failure(
                opened.reason ?: "ios_chat_attachment_preview_failed",
            )
            PlatformResult.Cancelled -> PlatformResult.Failure("ios_chat_attachment_preview_cancelled")
            PlatformResult.Unsupported -> PlatformResult.Failure("ios_chat_attachment_preview_unsupported")
        }
    }
}
