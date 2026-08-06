package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.PlatformFile

/** Android-compatible consecutive voice-note policy shared by every Chat consumer. */
fun nextConsecutiveAudioMessage(messages: List<Message>, finishedMessageKey: String): Message? {
    val currentIndex = messages.indexOfFirst { it.composeKey() == finishedMessageKey }
    if (currentIndex < 0) return null
    val current = messages[currentIndex]
    val next = messages.getOrNull(currentIndex + 1) ?: return null
    if (next.isDeleted || next.senderId != current.senderId) return null
    val reference = next.attachmentUri?.takeIf(String::isNotBlank) ?: return null
    return next.takeIf {
        chatAttachmentKind(PlatformFile(reference, next.attachmentName, next.attachmentMimeType)) == ChatAttachmentKind.Audio
    }
}

fun didAudioPlaybackFinish(previous: AudioPlaybackState, current: AudioPlaybackState): Boolean {
    if (!previous.isPlaying || current.isPlaying || current.durationMillis <= 0L) return false
    return current.positionMillis >= (current.durationMillis - AUDIO_COMPLETION_TOLERANCE_MILLIS).coerceAtLeast(0L)
}

private const val AUDIO_COMPLETION_TOLERANCE_MILLIS = 500L
