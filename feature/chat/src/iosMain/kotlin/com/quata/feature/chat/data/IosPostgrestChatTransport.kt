package com.quata.feature.chat.data

import com.quata.core.platform.PlatformFile
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUUID
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe deployment settings for the iOS chat boundary. */
data class IosChatRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/**
 * URLSession implementation of the common authenticated PostgREST RPC protocol.
 *
 * The session is resolved for every request so a refresh performed by the existing Keychain
 * session owner is immediately used. This adapter deliberately does not retain credentials or
 * create an anonymous Chat identity.
 */
@OptIn(ExperimentalForeignApi::class)
class IosChatPostgrestTransport(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : ChatPostgrestTransport {
    override suspend fun post(functionName: String, body: String): ChatPostgrestResponse = runCatching {
        require(functionName.matches(IosRpcName)) { "ios_chat_rpc_name_invalid" }
        val request = authenticatedRequest("${configuration.restBaseUrl()}/rpc/$functionName").apply {
            setHTTPMethod("POST")
            setHTTPBody(body.encodeToByteArray().toIosData())
            setValue("application/json", "Content-Type")
        }
        request.execute().body.toIosString()
    }.fold(
        onSuccess = ChatPostgrestResponse::Success,
        onFailure = ChatPostgrestResponse::Failure,
    )

    private suspend fun authenticatedRequest(urlString: String): NSMutableURLRequest {
        val url = NSURL(string = urlString) ?: error("ios_chat_url_invalid")
        val session = authSession.currentSession()
            ?.takeIf { it.bearerToken.isNotBlank() }
            ?: error("ios_chat_session_missing")
        return NSMutableURLRequest.requestWithURL(url).apply {
            setValue(configuration.publishableKey(), "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
            setValue("application/json", "Accept")
        }
    }
}

/** Reads the same renewable Keychain session used by interactive Auth and Feed. */
class IosChatAuthenticatedUserProvider(
    private val authSession: IosRenewableAuthSession,
) : ChatAuthenticatedUserProvider {
    override suspend fun currentUserId(): String? = authSession.currentSession()
        ?.userId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

/**
 * Uploads local iOS picker/camera/recorder files directly to the RLS-protected chat bucket.
 *
 * A [PlatformFile] must be a local `file://` URL or absolute path. Remote references are not
 * fetched implicitly, avoiding accidental re-upload of arbitrary network content.
 */
@OptIn(ExperimentalForeignApi::class)
class IosChatAttachmentUploader(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : ChatAttachmentUploader {
    override suspend fun upload(profileId: String, file: PlatformFile): UploadedChatAttachment {
        val cleanProfileId = profileId.trim().takeIf { it.matches(IosStorageSegment) }
            ?: error("ios_chat_attachment_profile_id_invalid")
        val localUrl = file.localFileUrlOrNull() ?: error("ios_chat_attachment_local_file_required")
        val localPath = localUrl.path ?: error("ios_chat_attachment_local_path_missing")
        val data = NSFileManager.defaultManager.contentsAtPath(localPath)
            ?: error("ios_chat_attachment_read_failed")
        if (data.length == 0uL) error("ios_chat_attachment_empty")

        val name = file.displayName.safeFileName()
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val mimeType = file.mimeType?.trim()?.takeIf(String::isNotEmpty) ?: extension.defaultMimeType()
        val storagePath = "$cleanProfileId/${NSUUID.UUID().UUIDString.lowercase()}/$name"
        val request = authenticatedUploadRequest(
            "${configuration.storageBaseUrl()}/object/$ChatAttachmentsBucket/${storagePath.iosPathComponent()}",
            data,
            mimeType,
        )
        request.execute()
        return UploadedChatAttachment(
            storagePath = storagePath,
            publicUrl = "${configuration.storageBaseUrl()}/object/public/$ChatAttachmentsBucket/${storagePath.iosPathComponent()}",
            mimeType = mimeType,
            sizeBytes = data.length.toLong(),
            name = name,
            extension = extension,
        )
    }

    private suspend fun authenticatedUploadRequest(urlString: String, data: NSData, mimeType: String): NSMutableURLRequest {
        val url = NSURL(string = urlString) ?: error("ios_chat_storage_url_invalid")
        val session = authSession.currentSession()
            ?.takeIf { it.bearerToken.isNotBlank() }
            ?: error("ios_chat_session_missing")
        return NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            setHTTPBody(data)
            setValue(configuration.publishableKey(), "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
            setValue(mimeType, "Content-Type")
            setValue("false", "x-upsert")
        }
    }
}

private data class IosChatHttpResponse(val body: NSData)

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSMutableURLRequest.execute(): IosChatHttpResponse =
    withTimeout(IosChatRequestTimeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
                timeoutIntervalForRequest = IosChatRequestTimeoutSeconds
                timeoutIntervalForResource = IosChatRequestTimeoutSeconds
            }
            val delegate = IosChatDataTaskDelegate(continuation)
            val session = NSURLSession.sessionWithConfiguration(configuration, delegate, null)
            val task = session.dataTaskWithRequest(this@execute)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }

@OptIn(ExperimentalForeignApi::class)
private class IosChatDataTaskDelegate(
    private val continuation: CancellableContinuation<IosChatHttpResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_chat_http_${status ?: "unknown"}"))
            return
        }
        continuation.resume(IosChatHttpResponse(chunks.toIosData()))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosData(): NSData {
    return toFoundationData()
}

private fun ByteArray.toIosData(): NSData = toFoundationData()

private fun NSData.toIosString(): String = toIosBytes().decodeToString()

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localFileUrlOrNull(): NSURL? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }
    return url?.takeIf { it.isFileURL() }
}

private fun String?.safeFileName(): String = this?.trim()
    ?.substringAfterLast('/')
    ?.replace(IosUnsafeFilename, "_")
    ?.take(128)
    ?.takeIf(String::isNotEmpty)
    ?: "attachment"

private fun String.defaultMimeType(): String = when (this) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}

private fun IosChatRuntimeConfiguration.restBaseUrl(): String = "${supabaseBaseUrl()}/rest/v1"
private fun IosChatRuntimeConfiguration.storageBaseUrl(): String = "${supabaseBaseUrl()}/storage/v1"
private fun IosChatRuntimeConfiguration.supabaseBaseUrl(): String = supabaseUrl.trim().trimEnd('/')
    .takeIf(String::isNotEmpty)
    ?: error("ios_chat_supabase_url_missing")
private fun IosChatRuntimeConfiguration.publishableKey(): String = supabasePublishableKey.trim()
    .takeIf(String::isNotEmpty)
    ?: error("ios_chat_supabase_publishable_key_missing")
private fun String.iosPathComponent(): String = split('/').joinToString("/") { segment ->
    segment.encodeToByteArray().joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) value.toChar().toString()
        else "%${value.toString(16).padStart(2, '0').uppercase()}"
    }
}

private const val ChatAttachmentsBucket = "chat-attachments"
private const val IosChatRequestTimeoutMillis = 15_000L
private const val IosChatRequestTimeoutSeconds = 15.0
private val IosRpcName = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val IosStorageSegment = Regex("[A-Za-z0-9_-]+")
private val IosUnsafeFilename = Regex("[^A-Za-z0-9._-]")
