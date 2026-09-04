package com.quata.feature.chat.data

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads a Chat attachment into the app's temporary sandbox.
 *
 * This is intentionally separate from [IosChatAttachmentUploader]: message attachment URLs are
 * untrusted remote data, while uploads use the authenticated storage endpoint. Although the
 * Storage object URL is public for backwards compatibility with the shipped Web client, the iOS
 * request is still authenticated with the active renewable session. This keeps the request tied
 * to the signed-in user, lets a future private-bucket migration retain this boundary, and never
 * exposes the bearer token to Quick Look or to a redirected host.
 */
@OptIn(ExperimentalForeignApi::class)
class IosChatAttachmentDownloader(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) {
    suspend fun download(
        publicUrl: String,
        displayName: String? = null,
    ): PlatformResult<PlatformFile> {
        val canonicalUrl = ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(
            supabaseUrl = configuration.supabaseUrl,
            publicUrl = publicUrl,
        ) ?: return PlatformResult.Failure("ios_chat_attachment_url_invalid")

        val localFile = runCatching {
            val response = canonicalUrl.downloadChatAttachment(configuration, authSession)
            val mimeType = response.mimeType?.normalisedChatMimeType()
                ?.takeIf(::isAllowedChatAttachmentMimeType)
                ?: error("ios_chat_attachment_mime_invalid")
            if (response.data.length.toLong() !in 1..MaxAttachmentBytes) {
                error("ios_chat_attachment_size_invalid")
            }
            val sourceName = displayName.safeChatAttachmentName()
                ?: canonicalUrl.substringAfterLast('/').safeChatAttachmentName()
                ?: "attachment"
            val destination = cacheDestination(sourceName)
            val destinationPath = destination.path ?: error("ios_chat_attachment_cache_path_missing")
            if (!NSFileManager.defaultManager.createFileAtPath(destinationPath, response.data, protectedFileAttributes())) {
                error("ios_chat_attachment_cache_write_failed")
            }
            PlatformFile(
                reference = destination.absoluteString ?: destinationPath,
                displayName = sourceName,
                mimeType = mimeType,
                sizeBytes = response.data.length.toLong(),
            )
        }.getOrElse { failure ->
            return PlatformResult.Failure(failure.message ?: "ios_chat_attachment_download_failed")
        }
        return PlatformResult.Success(localFile)
    }

    /** Removes only a file generated in this downloader's own temporary directory. */
    internal fun discard(file: PlatformFile) {
        val cacheDirectory = chatAttachmentCacheDirectory()
        val path = NSURL(string = file.reference)?.path ?: file.reference
        if (!path.startsWith("$cacheDirectory/")) return
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun cacheDestination(sourceName: String): NSURL {
        val manager = NSFileManager.defaultManager
        val cacheDirectory = chatAttachmentCacheDirectory()
        if (!manager.fileExistsAtPath(cacheDirectory) && !manager.createDirectoryAtPath(
                cacheDirectory,
                withIntermediateDirectories = true,
                attributes = protectedFileAttributes(),
                error = null,
            )
        ) {
            error("ios_chat_attachment_cache_create_failed")
        }
        val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.matches(SafeExtension) }
            ?.let { ".${it.lowercase()}" }
            .orEmpty()
        val safeName = "${NSUUID.UUID().UUIDString.lowercase()}$extension"
        return NSURL.fileURLWithPath("$cacheDirectory/$safeName")
    }
}

private fun protectedFileAttributes(): Map<Any?, *> =
    mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)

private fun chatAttachmentCacheDirectory(): String =
    NSTemporaryDirectory().trimEnd('/') + "/quata_chat_attachments"

private data class IosPublicChatAttachmentResponse(
    val data: NSData,
    val mimeType: String?,
)

@OptIn(ExperimentalForeignApi::class)
private suspend fun String.downloadChatAttachment(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
): IosPublicChatAttachmentResponse {
    val session = authSession.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() }
        ?: error("ios_chat_session_missing")
    val publishableKey = configuration.supabasePublishableKey.trim()
        .takeIf(String::isNotEmpty)
        ?: error("ios_chat_publishable_key_missing")
    return suspendCancellableCoroutine { continuation ->
        val url = NSURL(string = this) ?: run {
            continuation.resumeWithException(IllegalArgumentException("ios_chat_attachment_url_invalid"))
            return@suspendCancellableCoroutine
        }
        // The URL is already constrained to this deployment's canonical public bucket. Do not
        // follow redirects: otherwise these credentials could be sent to an attacker-controlled
        // destination through a message supplied by another user.
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("GET")
            setValue(ChatAttachmentAcceptHeader, "Accept")
            setValue("no-store", "Cache-Control")
            setValue(publishableKey, "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
        }
        val delegate = IosPublicChatAttachmentDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.ephemeralSessionConfiguration(),
            delegate,
            null,
        )
        val task = session.dataTaskWithRequest(request)
        continuation.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
        task.resume()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPublicChatAttachmentDelegate(
    private val continuation: CancellableContinuation<IosPublicChatAttachmentResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()
    private var bytesReceived = 0L
    private var terminalReason: String? = null
    private var redirectRejected = false

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (!continuation.isActive || terminalReason != null) return
        val chunk = didReceiveData.toChatAttachmentBytes()
        if (chunk.size.toLong() > MaxAttachmentBytes - bytesReceived) {
            terminalReason = "ios_chat_attachment_size_invalid"
            dataTask.cancel()
            return
        }
        bytesReceived += chunk.size
        chunks += chunk
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        // Re-validating a redirect target would still turn a message attachment into a redirect
        // oracle. Public Chat objects are canonical and never need redirects, so fail closed.
        redirectRejected = true
        completionHandler(null)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val failure = terminalReason
            ?: if (redirectRejected) "ios_chat_attachment_redirect_rejected" else null
        if (failure != null) {
            continuation.resumeWithException(IllegalStateException(failure))
            return
        }
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val response = task.response ?: run {
            continuation.resumeWithException(IllegalStateException("ios_chat_attachment_response_missing"))
            return
        }
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_chat_attachment_http_${status ?: "unknown"}"))
            return
        }
        val declaredLength = response.expectedContentLength
        if (declaredLength > MaxAttachmentBytes || bytesReceived !in 1..MaxAttachmentBytes) {
            continuation.resumeWithException(IllegalStateException("ios_chat_attachment_size_invalid"))
            return
        }
        continuation.resume(
            IosPublicChatAttachmentResponse(
                data = chunks.toChatAttachmentData(),
                mimeType = response.MIMEType,
            ),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toChatAttachmentBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toChatAttachmentData(): NSData {
    return toFoundationData()
}

private fun String?.normalisedChatMimeType(): String? = this
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase()
    ?.takeIf(String::isNotEmpty)

private fun isAllowedChatAttachmentMimeType(value: String): Boolean =
    value.startsWith("image/") || value.startsWith("audio/") || value.startsWith("video/") ||
        value in AllowedDocumentMimeTypes

private fun String?.safeChatAttachmentName(): String? = this
    ?.trim()
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.takeIf { it.length in 1..128 && SafeAttachmentName.matches(it) }

private const val MaxAttachmentBytes = 50L * 1024L * 1024L
private const val ChatAttachmentAcceptHeader = "image/*,audio/*,video/*,application/pdf,text/plain,text/rtf,application/rtf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
private val SafeAttachmentName = Regex("[A-Za-z0-9._-]+")
private val SafeExtension = Regex("[A-Za-z0-9]{1,16}")
private val AllowedDocumentMimeTypes = setOf(
    "application/pdf",
    "text/plain",
    "text/rtf",
    "application/rtf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)
