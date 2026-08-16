package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.PlatformFile

/** Android-compatible consecutive voice-note policy shared by every Chat consumer. */
fun nextConsecutiveAudioMessage(messages: List<Message>, finishedMessageKey: String): Message? {
    val currentIndex = messages.indexOfFirst { it.composeKey() == finishedMessageKey }
    if (currentIndex < 0) return null
    val current = messages[currentIndex]
    return listOf(currentIndex + 1, currentIndex - 1)
        .mapNotNull(messages::getOrNull)
        .firstOrNull { candidate -> candidate.isConsecutiveAudioFrom(current) }
}

private fun Message.isConsecutiveAudioFrom(current: Message): Boolean {
    if (isDeleted || senderId != current.senderId) return false
    val reference = attachmentUri?.takeIf(String::isNotBlank) ?: return false
    return chatAttachmentKind(PlatformFile(reference, attachmentName, attachmentMimeType)) == ChatAttachmentKind.Audio
}

fun didAudioPlaybackFinish(previous: AudioPlaybackState, current: AudioPlaybackState): Boolean {
    if (!previous.isPlaying) return false
    if (current.isPlaying) return false
    return current.isNearEnd() || previous.isNearEnd(current.durationMillis)
}

private const val AUDIO_COMPLETION_TOLERANCE_MILLIS = 500L

private fun AudioPlaybackState.isNearEnd(fallbackDurationMillis: Long = durationMillis): Boolean {
    val duration = durationMillis.takeIf { it > 0L } ?: fallbackDurationMillis
    if (duration <= 0L) return false
    return positionMillis >= (duration - AUDIO_COMPLETION_TOLERANCE_MILLIS).coerceAtLeast(0L)
}
