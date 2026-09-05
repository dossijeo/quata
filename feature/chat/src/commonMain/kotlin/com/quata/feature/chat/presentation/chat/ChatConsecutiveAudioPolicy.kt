package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.PlatformFile
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/** Android-compatible consecutive voice-note policy shared by every Chat consumer. */
fun nextConsecutiveAudioMessage(messages: List<Message>, finishedMessageKey: String): Message? {
    val ordered = messages.sortedWith(compareBy<Message> { it.temporalSortMillis() }.thenBy { it.sentAt }.thenBy { it.id })
    val currentIndex = ordered.indexOfFirst { it.composeKey() == finishedMessageKey }
    if (currentIndex < 0) return null
    val current = ordered[currentIndex]
    if (current.isDeleted) return null
    val next = ordered.getOrNull(currentIndex + 1) ?: return null
    return next.takeIf { it.isConsecutiveAudioFrom(current) }
}

@OptIn(ExperimentalTime::class)
private fun Message.temporalSortMillis(): Long =
    sentAtMillis ?: runCatching { Instant.parse(sentAt).toEpochMilliseconds() }.getOrNull() ?: Long.MAX_VALUE

private fun Message.isConsecutiveAudioFrom(current: Message): Boolean {
    if (isDeleted || senderId != current.senderId) return false
    val reference = attachmentUri?.takeIf(String::isNotBlank) ?: return false
    return chatAttachmentKind(PlatformFile(reference, attachmentName, attachmentMimeType)) == ChatAttachmentKind.Audio
}
