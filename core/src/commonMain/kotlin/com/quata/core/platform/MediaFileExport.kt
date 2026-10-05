package com.quata.core.platform

/** Immutable identity of one remote media object selected for a user export action. */
data class MediaFileExportDescriptor(
    val reference: String,
    val displayName: String,
    val mimeType: String,
    /** Chat keeps compatibility with historical objects whose stored MIME can be stale. */
    val allowResponseMimeOverride: Boolean = false,
)

enum class MediaFileExportAction { Download, Share }

/**
 * One caller-owned materialized file. [release] is idempotent and is the only supported way to
 * relinquish the private temporary resource; a platform cache may keep its underlying bytes when
 * it has a longer, independently bounded lifetime.
 */
class MaterializedMediaFileLease(
    val file: PlatformFile,
    private val releaseAction: () -> Unit,
) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        releaseAction()
    }
}

/** Portable boundary shared by Chat, Feed and Official before any download/share side effect. */
fun interface MediaFileMaterializer {
    suspend fun materialize(descriptor: MediaFileExportDescriptor): PlatformResult<MaterializedMediaFileLease>
}

/**
 * Platform boundary that materializes one descriptor, performs the requested user action and
 * releases every temporary resource before returning. A retry receives the same descriptor.
 */
fun interface MediaFileExportService {
    suspend fun export(
        descriptor: MediaFileExportDescriptor,
        action: MediaFileExportAction,
    ): PlatformResult<Unit>
}

object UnsupportedMediaFileExportService : MediaFileExportService {
    override suspend fun export(
        descriptor: MediaFileExportDescriptor,
        action: MediaFileExportAction,
    ): PlatformResult<Unit> = PlatformResult.Unsupported
}

fun mediaFileExportDescriptorOrNull(
    reference: String?,
    title: String,
    mimeType: String,
): MediaFileExportDescriptor? {
    val normalizedReference = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val declaredMime = mimeType.substringBefore(';').trim().lowercase()
        .takeIf(String::isNotEmpty) ?: return null
    val inferredMime = normalizedReference.substringBefore('#').substringBefore('?')
        .substringAfterLast('/', missingDelimiterValue = normalizedReference)
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .let { extension ->
            when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4", "m4v" -> "video/mp4"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                else -> null
            }
        }
    // Feed and Official persist the media family, not the concrete MIME. Storage object names retain
    // the original extension, so prefer it when it refines the same declared family.
    val normalizedMime = inferredMime
        ?.takeIf { it.substringBefore('/') == declaredMime.substringBefore('/') }
        ?: declaredMime
    val extension = when (normalizedMime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> normalizedMime.substringAfter('/', "bin").filter(Char::isLetterOrDigit).take(12).ifBlank { "bin" }
    }
    val safeStem = title.trim()
        .map { character ->
            if (character.isLetterOrDigit() || character == '.' || character == '_' || character == '-' || character == ' ') {
                character
            } else {
                '_'
            }
        }
        .joinToString(separator = "")
        .trim('.', ' ', '_')
        .take(96)
        .ifBlank { "quata-media" }
    val displayName = if (safeStem.substringAfterLast('.', "").equals(extension, ignoreCase = true)) {
        safeStem
    } else {
        "$safeStem.$extension"
    }
    return MediaFileExportDescriptor(normalizedReference, displayName, normalizedMime)
}
