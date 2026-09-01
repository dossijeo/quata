package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.PlatformFile

/** Android-compatible consecutive voice-note policy shared by every Chat consumer. */
fun nextConsecutiveAudioMessage(messages: List<Message>, finishedMessageKey: String): Message? {
    val ordered = messages.sortedWith(compareBy<Message> { it.sentAtMillis ?: Long.MAX_VALUE }.thenBy { it.sentAt }.thenBy { it.id })
    val currentIndex = ordered.indexOfFirst { it.composeKey() == finishedMessageKey }
    if (currentIndex < 0) return null
    val current = ordered[currentIndex]
    val next = ordered.getOrNull(currentIndex + 1) ?: return null
    return next.takeIf { it.isConsecutiveAudioFrom(current) }
}

private fun Message.isConsecutiveAudioFrom(current: Message): Boolean {
    if (isDeleted || senderId != current.senderId) return false
    val reference = attachmentUri?.takeIf(String::isNotBlank) ?: return false
    return chatAttachmentKind(PlatformFile(reference, attachmentName, attachmentMimeType)) == ChatAttachmentKind.Audio
}
