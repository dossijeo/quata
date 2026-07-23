package com.quata.feature.externalshare

import kotlin.random.Random

/** Platform-neutral representation of content received from a share adapter. */
data class ExternalSharePayload(
    val id: String = "share-${Random.nextLong().toString(16)}",
    val text: String = "",
    val attachments: List<ExternalShareAttachment> = emptyList(),
    val directConversationId: String? = null
)

data class ExternalShareAttachment(
    val uri: String,
    val name: String,
    val mimeType: String?
)

sealed interface ExternalShareParseResult {
    data class Accepted(val payload: ExternalSharePayload) : ExternalShareParseResult
    data object Empty : ExternalShareParseResult
    data object Unsupported : ExternalShareParseResult
    data object Unreadable : ExternalShareParseResult
    data object TooManyFiles : ExternalShareParseResult
}
