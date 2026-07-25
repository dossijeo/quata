package com.quata.core.platform

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android-owned hand-off to the document UI. The UI itself is deliberately supplied by the app:
 * `:core` must not depend on the legacy Android document-reader module.
 *
 * The service accepts content and HTTPS references directly. App-owned files are converted to
 * a short-lived FileProvider content URI before the host receives them; a raw `file://` URI never
 * crosses an Activity boundary. Hosts are responsible for launching their Activity with
 * [android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION] when the URI is shared.
 */
class AndroidDocumentOpenService(
    context: Context,
    private val host: AndroidDocumentOpenHost = UnsupportedAndroidDocumentOpenHost,
    private val fileProviderAuthority: String = "${context.packageName}.fileprovider",
) : DocumentOpenService {
    private val applicationContext = context.applicationContext

    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val reference = file.reference.trim()
        if (reference.isEmpty()) return PlatformResult.Failure("document_reference_missing")

        val uri = reference.toSafeDocumentUri() ?: return PlatformResult.Failure("document_uri_invalid")
        val resolvedMime = resolveMimeType(uri, file)
        val descriptor = DocumentSupport.describe(reference, file.displayName, resolvedMime)
        if (!descriptor.isPreviewable) return PlatformResult.Unsupported

        return host.open(
            AndroidDocumentOpenRequest(
                uri = uri,
                displayName = file.displayName,
                mimeType = resolvedMime,
            ),
        )
    }

    private fun String.toSafeDocumentUri(): Uri? {
        val parsed = Uri.parse(this)
        return when (parsed.scheme?.lowercase()) {
            "content" -> parsed.takeIf { !it.authority.isNullOrBlank() }
            // Do not let an attachment trigger clear-text network traffic from the reader.
            "https" -> parsed.takeIf { !it.host.isNullOrBlank() }
            "file", null -> appOwnedFileUri(parsed.path ?: takeIf { parsed.scheme == null })
            else -> null
        }
    }

    private fun appOwnedFileUri(path: String?): Uri? {
        val candidate = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val canonicalFile = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!canonicalFile.isFile || !canonicalFile.isInside(applicationContext.cacheDir) &&
            !canonicalFile.isInside(applicationContext.filesDir)
        ) return null

        return runCatching {
            FileProvider.getUriForFile(applicationContext, fileProviderAuthority, canonicalFile)
        }.getOrNull()
    }

    private fun resolveMimeType(uri: Uri, file: PlatformFile): String? {
        val claimedMime = file.mimeType.normalizedMime()
        val resolverMime = if (uri.scheme == "content") {
            applicationContext.contentResolver.getType(uri).normalizedMime()
        } else {
            null
        }
        return claimedMime ?: resolverMime ?: AndroidDocumentOpenPolicy.inferMimeType(file)
    }
}

/** Android UI adapter injected by the application composition root. */
fun interface AndroidDocumentOpenHost {
    suspend fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit>
}

data class AndroidDocumentOpenRequest(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
)

object UnsupportedAndroidDocumentOpenHost : AndroidDocumentOpenHost {
    override suspend fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> = PlatformResult.Unsupported
}

private fun File.isInside(directory: File): Boolean = runCatching {
    val root = directory.canonicalFile.path.let { if (it.endsWith(File.separator)) it else "$it${File.separator}" }
    canonicalPath.startsWith(root)
}.getOrDefault(false)

private fun String?.normalizedMime(): String? = this
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.contains('/') }

/** Pure Android-side policy, intentionally visible to local unit tests without a Context. */
internal object AndroidDocumentOpenPolicy {
    fun inferMimeType(file: PlatformFile): String? = when (
        DocumentSupport.describe(file.reference, file.displayName, null).extension
    ) {
        "pdf" -> "application/pdf"
        "rtf" -> "application/rtf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "csv" -> "text/csv"
        else -> null
    }
}
