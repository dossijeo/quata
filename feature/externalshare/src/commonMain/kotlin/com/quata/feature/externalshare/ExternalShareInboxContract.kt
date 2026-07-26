package com.quata.feature.externalshare

/** Stable, transport-free representation persisted by platform share targets. */
data class PersistedExternalShare(
    val id: String,
    val text: String,
    val attachments: List<PersistedExternalShareAttachment>,
    val directConversationId: String? = null,
)

data class PersistedExternalShareAttachment(
    val relativePath: String,
    val name: String,
    val mimeType: String?,
)

sealed interface PersistedExternalShareResult {
    data class Accepted(val payload: ExternalSharePayload) : PersistedExternalShareResult
    data object Empty : PersistedExternalShareResult
    data object Invalid : PersistedExternalShareResult
    data object TooManyFiles : PersistedExternalShareResult
    data object Unsupported : PersistedExternalShareResult
}

/**
 * Validates an untrusted extension manifest before platform code exposes local file URLs.
 * Relative paths are restricted to one generated filename so they cannot escape the claimed
 * App Group directory.
 */
fun persistedExternalSharePayload(
    persisted: PersistedExternalShare,
    attachmentUri: (String) -> String,
): PersistedExternalShareResult {
    val id = persisted.id.trim()
    val text = persisted.text.trim().take(MaxExternalShareTextChars)
    if (
        id.isEmpty() || id.length > MaxExternalShareIdChars ||
        !id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    ) {
        return PersistedExternalShareResult.Invalid
    }
    if (persisted.attachments.size > MaxExternalShareFiles) {
        return PersistedExternalShareResult.TooManyFiles
    }
    val attachments = persisted.attachments.map { attachment ->
        val relativePath = attachment.relativePath.trim()
        val name = attachment.name.trim().take(MaxExternalShareFileNameChars)
        if (
            relativePath.isEmpty() || name.isEmpty() ||
            relativePath != relativePath.substringAfterLast('/') ||
            relativePath.contains('\\') || relativePath == "." || relativePath == ".." ||
            name.any { it == '/' || it == '\\' || it.isISOControl() }
        ) {
            return PersistedExternalShareResult.Invalid
        }
        val uri = attachmentUri(relativePath)
        if (uri.isBlank() || !isSupportedSharedAttachment(uri, name, attachment.mimeType)) {
            return PersistedExternalShareResult.Unsupported
        }
        ExternalShareAttachment(uri = uri, name = name, mimeType = attachment.mimeType?.trim()?.takeIf(String::isNotEmpty))
    }
    if (text.isEmpty() && attachments.isEmpty()) return PersistedExternalShareResult.Empty
    return PersistedExternalShareResult.Accepted(
        ExternalSharePayload(
            id = id,
            text = text,
            attachments = attachments,
            directConversationId = persisted.directConversationId?.trim()?.takeIf(String::isNotEmpty),
        ),
    )
}

const val MaxExternalShareFiles = 5
const val MaxExternalShareIdChars = 120
const val MaxExternalShareTextChars = 20_000
const val MaxExternalShareFileNameChars = 255
