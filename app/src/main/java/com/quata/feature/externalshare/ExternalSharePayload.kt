package com.quata.feature.externalshare

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.pm.ShortcutManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
object ExternalShareIntentParser {
    suspend fun parse(context: Context, intent: Intent): ExternalShareParseResult = withContext(Dispatchers.IO) {
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            return@withContext ExternalShareParseResult.Empty
        }

        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            .ifBlank { intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty() }
        val directConversationId = ShareConversationShortcuts.conversationIdFromShortcut(
            intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        )
        val streams = intent.sharedStreamUris()
        if (streams.isEmpty()) {
            return@withContext if (sharedText.isBlank()) {
                ExternalShareParseResult.Empty
            } else {
                ExternalShareParseResult.Accepted(
                    ExternalSharePayload(text = sharedText, directConversationId = directConversationId)
                )
            }
        }

        if (streams.size > MAX_SHARED_FILES) {
            return@withContext ExternalShareParseResult.TooManyFiles
        }
        if (streams.any { it.scheme?.lowercase(Locale.US) !in SUPPORTED_URI_SCHEMES }) {
            return@withContext ExternalShareParseResult.Unsupported
        }

        val attachments = streams.map { stream ->
            val fileName = context.displayName(stream)
            val mimeType = context.resolveSharedMimeType(stream, fileName, intent.type)
            if (!isSupportedSharedAttachment(stream.toString(), fileName, mimeType)) {
                return@withContext ExternalShareParseResult.Unsupported
            }
            if (!context.canRead(stream)) {
                return@withContext ExternalShareParseResult.Unreadable
            }
            ExternalShareAttachment(
                uri = stream.toString(),
                name = fileName,
                mimeType = mimeType
            )
        }

        ExternalShareParseResult.Accepted(
            ExternalSharePayload(
                text = sharedText,
                attachments = attachments,
                directConversationId = directConversationId
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.sharedStreamUris(): List<Uri> {
        val extras = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty() +
                listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        } else {
            (getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()) +
                listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        }
        val clipped = clipData?.let { data ->
            (0 until data.itemCount).mapNotNull { data.getItemAt(it).uri }
        }.orEmpty()
        return (extras + clipped).distinctBy(Uri::toString)
    }

    private fun Context.resolveSharedMimeType(uri: Uri, fileName: String, declaredMime: String?): String? {
        val declared = declaredMime?.substringBefore(';')?.lowercase(Locale.US)
        val provider = contentResolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.US)
        val extensionMime = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
        return sequenceOf(declared, provider, extensionMime)
            .filterNotNull()
            .firstOrNull { it !in NON_SPECIFIC_MIME_TYPES }
            ?: declared
            ?: provider
            ?: extensionMime
    }

    private fun Context.displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let(::File)?.name.orEmpty().ifBlank { DEFAULT_FILE_NAME }
        }
        val providerName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> cursor.firstDisplayName() }
        }.getOrNull()
        return providerName.orEmpty()
            .ifBlank { uri.lastPathSegment?.substringAfterLast('/').orEmpty() }
            .ifBlank { DEFAULT_FILE_NAME }
            .replace(Regex("[\\r\\n]"), "_")
    }

    private fun Cursor.firstDisplayName(): String? {
        if (!moveToFirst()) return null
        val index = getColumnIndex(OpenableColumns.DISPLAY_NAME)
        return if (index >= 0) getString(index) else null
    }

    private fun Context.canRead(uri: Uri): Boolean = runCatching {
        when (uri.scheme?.lowercase(Locale.US)) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.isFile == true
            else -> contentResolver.openInputStream(uri)?.use { true } == true
        }
    }.getOrDefault(false)

    private const val GENERIC_BINARY_MIME = "application/octet-stream"
    private val NON_SPECIFIC_MIME_TYPES = setOf(GENERIC_BINARY_MIME, "*/*")
    private const val DEFAULT_FILE_NAME = "archivo_compartido"
    const val MAX_SHARED_FILES = 10
    private val SUPPORTED_URI_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)
}
