package com.quata.feature.feed.presentation

import com.quata.core.model.PostComment

/** Shared textual payload captured by the Fang overlay for a Feed comment. */
fun feedCommentTranslatorDisplayText(
    comment: PostComment,
    timestamp: String,
    replyLabel: String?,
): String = buildString {
    append(comment.authorName)
    if (timestamp.isNotBlank()) append(" - ").append(timestamp)
    replyLabel?.let { append('\n').append(it) }
    comment.replyToMessage?.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
    comment.message.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
}
