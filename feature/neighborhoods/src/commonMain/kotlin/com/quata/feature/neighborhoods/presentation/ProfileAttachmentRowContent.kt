package com.quata.feature.neighborhoods.presentation

import androidx.compose.runtime.Composable
import com.quata.feature.neighborhoods.domain.ProfileAttachment

/**
 * Shared attachment row decision for a community profile.
 *
 * Audio playback and attachment previews remain platform slots because they own media players,
 * URI handling and thumbnail loading. The profile layout itself is portable.
 */
@Composable
fun ProfileAttachmentRowContent(
    attachment: ProfileAttachment,
    audioPlayer: @Composable () -> Unit,
    thumbnail: @Composable () -> Unit,
    onOpen: () -> Unit,
) {
    if (attachment.mimeType?.startsWith("audio/", ignoreCase = true) == true) {
        audioPlayer()
    } else {
        ProfileAttachmentCardContent(
            name = attachment.name,
            senderName = attachment.senderName,
            thumbnail = thumbnail,
            onClick = onOpen,
        )
    }
}
