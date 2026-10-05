package com.quata.core.platform

import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUUID
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Downloads selected Feed/Official media into a protected temporary file, keeps it alive until
 * UIKit reports completion or cancellation, and removes it on every terminal path. */
@OptIn(ExperimentalForeignApi::class)
class IosMediaFileExportService(
    private val shareService: ShareService,
    private val materializer: MediaFileMaterializer = IosMediaFileMaterializer(),
) : MediaFileExportService {
    override suspend fun export(
        descriptor: MediaFileExportDescriptor,
        action: MediaFileExportAction,
    ): PlatformResult<Unit> {
        val lease = when (val materialized = materializer.materialize(descriptor)) {
            is PlatformResult.Success -> materialized.value
            is PlatformResult.Failure -> return materialized
            PlatformResult.Cancelled -> return PlatformResult.Cancelled
            PlatformResult.Unsupported -> return PlatformResult.Unsupported
        }
        return try {
            shareService.share(
                SharePayload(
                    title = if (action == MediaFileExportAction.Download) descriptor.displayName else null,
                    files = listOf(lease.file),
                ),
            )
        } finally {
            lease.release()
        }
    }
}

/** Cancellable iOS materializer used by the export service before UIKit receives the file. */
@OptIn(ExperimentalForeignApi::class)
class IosMediaFileMaterializer internal constructor(
    private val transport: IosMediaExportTransport,
) : MediaFileMaterializer {
    constructor() : this(IosUrlSessionMediaExportTransport)

    override suspend fun materialize(
        descriptor: MediaFileExportDescriptor,
    ): PlatformResult<MaterializedMediaFileLease> {
        val source = descriptor.reference.iosExportUrlOrNull()
            ?: return PlatformResult.Failure("ios_media_export_url_invalid")
        val safeDisplayName = descriptor.displayName.iosExportDisplayNameOrNull()
            ?: return PlatformResult.Failure("ios_media_export_name_invalid")
        return try {
            val response = transport.download(source)
            val expectedMimeType = descriptor.mimeType.normalisedMimeType()
                ?: error("ios_media_export_mime_missing")
            val responseMimeType = response.mimeType.normalisedMimeType()
            if (!expectedMimeType.isExportableMediaMimeType() || responseMimeType != expectedMimeType) {
                error("ios_media_export_mime_invalid")
            }
            val destination = iosMediaExportDestination(safeDisplayName)
            val path = destination.path ?: error("ios_media_export_path_missing")
            if (!NSFileManager.defaultManager.createFileAtPath(path, response.data, iosMediaExportAttributes())) {
                error("ios_media_export_write_failed")
            }
            val file = PlatformFile(
                reference = destination.absoluteString ?: path,
                displayName = safeDisplayName,
                mimeType = expectedMimeType,
                sizeBytes = response.data.length.toLong(),
            )
            PlatformResult.Success(MaterializedMediaFileLease(file) {
                if (path.startsWith("${iosMediaExportDirectory()}/")) {
                    NSFileManager.defaultManager.removeItemAtPath(path.substringBeforeLast('/'), error = null)
                }
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            PlatformResult.Failure(failure.message ?: "ios_media_export_download_failed")
        }
    }
}

private fun String.iosExportUrlOrNull(): NSURL? {
    val url = NSURL(string = trim()) ?: return null
    val scheme = url.scheme?.lowercase()
    val host = url.host?.lowercase()
    return url.takeIf {
        url.user.isNullOrBlank() && url.password.isNullOrBlank() &&
            (scheme == "https" || (scheme == "http" && host in setOf("localhost", "127.0.0.1", "::1")))
    }
}

private fun String.iosExportDisplayNameOrNull(): String? = trim()
    .takeIf { name ->
        name.length in 1..128 &&
            name != "." && name != ".." &&
            !name.contains('/') && !name.contains('\\') &&
            name.none(Char::isISOControl)
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURL.downloadIosMediaExport(): IosMediaExportResponse = suspendCancellableCoroutine { continuation ->
    val request = NSMutableURLRequest.requestWithURL(this).apply {
        setHTTPMethod("GET")
        setValue("image/*,audio/*,video/*", "Accept")
        setValue("no-store", "Cache-Control")
    }
    val delegate = IosMediaExportDownloadDelegate(continuation)
    val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
        timeoutIntervalForRequest = IosMediaExportRequestTimeoutSeconds
        timeoutIntervalForResource = IosMediaExportResourceTimeoutSeconds
    }
    val session = NSURLSession.sessionWithConfiguration(configuration, delegate, null)
    val task = session.dataTaskWithRequest(request)
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

internal data class IosMediaExportResponse(val data: NSData, val mimeType: String?)

internal fun interface IosMediaExportTransport {
    suspend fun download(source: NSURL): IosMediaExportResponse
}

private object IosUrlSessionMediaExportTransport : IosMediaExportTransport {
    override suspend fun download(source: NSURL): IosMediaExportResponse = source.downloadIosMediaExport()
}

@OptIn(ExperimentalForeignApi::class)
private class IosMediaExportDownloadDelegate(
    private val continuation: CancellableContinuation<IosMediaExportResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()
    private var bytesReceived = 0L
    private var terminalReason: String? = null
    private var redirectRejected = false

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (!continuation.isActive || terminalReason != null) return
        val chunk = didReceiveData.toIosMediaExportBytes()
        if (chunk.size.toLong() > IosMediaExportMaxBytes - bytesReceived) {
            terminalReason = "ios_media_export_size_invalid"
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
        redirectRejected = true
        completionHandler(null)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val failure = terminalReason ?: if (redirectRejected) "ios_media_export_redirect_rejected" else null
        if (failure != null) {
            continuation.resumeWithException(IllegalStateException(failure))
            return
        }
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val response = task.response
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_media_export_http_${status ?: "unknown"}"))
            return
        }
        if (response.expectedContentLength > IosMediaExportMaxBytes || bytesReceived !in 1..IosMediaExportMaxBytes) {
            continuation.resumeWithException(IllegalStateException("ios_media_export_size_invalid"))
            return
        }
        continuation.resume(IosMediaExportResponse(chunks.toFoundationData(), response.MIMEType))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosMediaExportBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun iosMediaExportDestination(displayName: String): NSURL {
    val manager = NSFileManager.defaultManager
    val directory = iosMediaExportDirectory()
    if (!manager.fileExistsAtPath(directory) && !manager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = iosMediaExportAttributes(),
            error = null,
        )
    ) error("ios_media_export_directory_failed")
    pruneAbandonedIosMediaExports(manager, directory)
    val leaseDirectory = "$directory/${NSUUID.UUID().UUIDString.lowercase()}"
    if (!manager.createDirectoryAtPath(
            leaseDirectory,
            withIntermediateDirectories = false,
            attributes = iosMediaExportAttributes(),
            error = null,
        )
    ) error("ios_media_export_lease_directory_failed")
    return NSURL.fileURLWithPath("$leaseDirectory/$displayName")
}

@OptIn(ExperimentalForeignApi::class)
private fun pruneAbandonedIosMediaExports(manager: NSFileManager, directory: String) {
    val cutoff = CFAbsoluteTimeGetCurrent() - IosMediaExportAbandonedLeaseSeconds
    val names = (manager.contentsOfDirectoryAtPath(directory, error = null) as? List<*>)
        .orEmpty()
        .mapNotNull { it as? String }
    names.forEach { name ->
        if (name.isBlank() || name.contains('/') || name.contains('\\')) return@forEach
        val path = "$directory/$name"
        val modifiedAt = (manager.attributesOfItemAtPath(path, error = null)?.get(NSFileModificationDate) as? NSDate)
            ?.timeIntervalSinceReferenceDate
            ?: 0.0
        if (modifiedAt < cutoff) manager.removeItemAtPath(path, error = null)
    }
}

private fun iosMediaExportDirectory(): String =
    NSTemporaryDirectory().trimEnd('/') + "/quata_media_exports"

private fun iosMediaExportAttributes(): Map<Any?, *> =
    mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)

private fun String?.normalisedMimeType(): String? = this
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase()
    ?.takeIf(String::isNotEmpty)

private fun String.isExportableMediaMimeType(): Boolean =
    startsWith("image/") || startsWith("audio/") || startsWith("video/")

private const val IosMediaExportMaxBytes = 50L * 1024L * 1024L
private const val IosMediaExportRequestTimeoutSeconds = 15.0
private const val IosMediaExportResourceTimeoutSeconds = 30.0
private const val IosMediaExportAbandonedLeaseSeconds = 24.0 * 60.0 * 60.0
