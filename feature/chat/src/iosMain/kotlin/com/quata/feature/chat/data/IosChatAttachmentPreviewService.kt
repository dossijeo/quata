package com.quata.feature.chat.data

import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentPreviewAdmission
import com.quata.core.platform.DocumentPreviewAdmissions
import com.quata.core.platform.IosDismissAwareDocumentOpenService
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
    /** Authenticated local lease used by inline media previews; callers must release it on disposal. */
    suspend fun downloadRemoteAttachment(attachment: PlatformFile): PlatformResult<PlatformFile> =
        downloader.download(attachment.reference, attachment.displayName)

    fun releaseDownloadedAttachment(attachment: PlatformFile) {
        downloader.discard(attachment)
    }

    /** Shared admission check used by the UIKit callback before starting a network operation. */
    fun supportsQuickLook(attachment: PlatformFile): Boolean =
        (DocumentPreviewAdmissions.admit(attachment, DocumentPreviewAdmissions.QuickLook)
            is DocumentPreviewAdmission.Open)

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
        val lease = TemporaryPreviewLease { downloader.discard(localFile) }
        val opened = if (documentOpener is IosDismissAwareDocumentOpenService) {
            documentOpener.open(localFile, lease::release)
        } else {
            documentOpener.open(localFile)
        }
        return when (opened) {
            is PlatformResult.Success -> opened
            is PlatformResult.Failure -> {
                lease.release()
                PlatformResult.Failure(opened.reason ?: "ios_chat_attachment_preview_failed")
            }
            PlatformResult.Cancelled -> {
                lease.release()
                PlatformResult.Failure("ios_chat_attachment_preview_cancelled")
            }
            PlatformResult.Unsupported -> {
                lease.release()
                PlatformResult.Failure("ios_chat_attachment_preview_unsupported")
            }
        }
    }

    /**
     * Objective-C/Swift-friendly variant: Kotlin/Native maps a thrown failure to the completion
     * handler's NSError, so the UIKit edge does not need to inspect a generic sealed result.
     */
    suspend fun openRemoteAttachmentOrThrow(attachment: PlatformFile) {
        when (val result = openRemoteAttachment(attachment)) {
            is PlatformResult.Success -> Unit
            is PlatformResult.Failure -> error(result.reason ?: "ios_chat_attachment_preview_failed")
            PlatformResult.Cancelled -> error("ios_chat_attachment_preview_cancelled")
            PlatformResult.Unsupported -> error("ios_chat_attachment_preview_unsupported")
        }
    }
}
