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
    private val contentUriFactory: AndroidDocumentContentUriFactory = AndroidXDocumentContentUriFactory,
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
            "content" -> parsed.takeIf { AndroidDocumentOpenPolicy.allowsDirectReference(this) }
            // Do not let an attachment trigger clear-text network traffic from the reader.
            "https" -> parsed.takeIf { AndroidDocumentOpenPolicy.allowsDirectReference(this) }
            "file", null -> appOwnedFileUri(parsed.path ?: takeIf { parsed.scheme == null })
            else -> null
        }
    }

    private fun appOwnedFileUri(path: String?): Uri? {
        val candidate = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val canonicalFile = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!AndroidDocumentOpenPolicy.isAppOwnedFile(
                file = canonicalFile,
                cacheDirectory = applicationContext.cacheDir,
                filesDirectory = applicationContext.filesDir,
            )
        ) return null

        return contentUriFactory.create(applicationContext, fileProviderAuthority, canonicalFile)
            ?.takeIf { AndroidDocumentOpenPolicy.isContentReference(it.toString()) }
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

/**
 * Narrow seam around AndroidX's FileProvider. It keeps raw app-private file paths out of the
 * reader boundary and lets host/device tests prove the URI hand-off without replacing the
 * document reader itself.
 */
fun interface AndroidDocumentContentUriFactory {
    fun create(context: Context, authority: String, file: File): Uri?
}

internal object AndroidXDocumentContentUriFactory : AndroidDocumentContentUriFactory {
    override fun create(context: Context, authority: String, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, authority, file)
    }.getOrNull()
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
    /** Only content and HTTPS are allowed to cross the boundary unchanged. */
    fun allowsDirectReference(reference: String): Boolean {
        val trimmed = reference.trim()
        val schemeEnd = trimmed.indexOf(':')
        if (schemeEnd <= 0) return false
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val remainder = trimmed.substring(schemeEnd + 1)
        val authority = remainder.removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
        return when (scheme) {
            "content" -> remainder.startsWith("//") && authority.isNotBlank()
            "https" -> remainder.startsWith("//") && authority.isNotBlank()
            else -> false
        }
    }

    /** A FileProvider seam is never permitted to hand a raw file URI to the reader. */
    fun isContentReference(reference: String): Boolean =
        allowsDirectReference(reference) && reference.trim().substringBefore(':').equals("content", ignoreCase = true)

    /** App-private files can only be converted by [AndroidDocumentContentUriFactory]. */
    fun isAppOwnedFile(file: File, cacheDirectory: File, filesDirectory: File): Boolean =
        file.isFile && (file.isInside(cacheDirectory) || file.isInside(filesDirectory))

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
