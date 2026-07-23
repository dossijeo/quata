package com.quata.core.text

import com.quata.core.model.PostComment

data class ParsedPostCommentBody(
    val message: String,
    val commentId: String? = null,
    val authorName: String? = null
)

fun String.parsePostCommentBody(): ParsedPostCommentBody {
    val match = ReplyShortcodeRegex.find(this)
    if (match == null) return ParsedPostCommentBody(message = trim())
    return ParsedPostCommentBody(
        commentId = match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
        authorName = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
        message = removeRange(match.range).trim()
    )
}

fun PostComment.toRemoteCommentBody(): String {
    val cleanMessage = message.trim()
    val replyId = replyToCommentId?.takeIf { it.isNotBlank() && !it.startsWith("local_") }
    val replyAuthor = replyToAuthorName
        ?.takeIf { it.isNotBlank() }
        ?.replace("]", "")
        ?.replace("\n", " ")
    return if (replyId != null && replyAuthor != null) {
        "[reply:$replyId:$replyAuthor] $cleanMessage"
    } else {
        cleanMessage
    }
}

private val ReplyShortcodeRegex = Regex("""^\s*\[reply:([^:\]]+):([^\]]+)]\s*""")
