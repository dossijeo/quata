package com.quata.feature.neighborhoods.presentation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
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

enum class ProfileAttachmentVisualKind { Image, Video, Audio, Document, File }

fun ProfileAttachment.visualKind(): ProfileAttachmentVisualKind {
    val mime = mimeType.orEmpty().lowercase()
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "heic") -> ProfileAttachmentVisualKind.Image
        mime.startsWith("video/") || extension in setOf("mp4", "mov", "m4v", "webm") -> ProfileAttachmentVisualKind.Video
        mime.startsWith("audio/") || extension in setOf("mp3", "m4a", "aac", "wav", "ogg") -> ProfileAttachmentVisualKind.Audio
        mime.contains("pdf") || mime.contains("document") || mime.contains("word") || mime.contains("sheet") || mime.contains("presentation") || extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt") -> ProfileAttachmentVisualKind.Document
        else -> ProfileAttachmentVisualKind.File
    }
}

@Composable
fun ProfileAttachmentThumbnailContent(attachment: ProfileAttachment, strings: CommunityProfileRuntimeStrings) {
    val (icon, label) = when (attachment.visualKind()) {
        ProfileAttachmentVisualKind.Image -> Icons.Filled.Image to strings.image
        ProfileAttachmentVisualKind.Video -> Icons.Filled.Movie to strings.video
        ProfileAttachmentVisualKind.Audio -> Icons.Filled.Mic to strings.audio
        ProfileAttachmentVisualKind.Document -> Icons.Filled.Description to strings.document
        ProfileAttachmentVisualKind.File -> Icons.AutoMirrored.Filled.InsertDriveFile to strings.genericFile
    }
    Box(
        modifier = Modifier.size(58.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CompactIcon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun ProfileAttachmentAudioLauncherContent(
    attachment: ProfileAttachment,
    strings: CommunityProfileRuntimeStrings,
    onOpen: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CompactIconButton(onClick = onOpen) {
                CompactIcon(Icons.Filled.PlayArrow, contentDescription = strings.playAudio, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(attachment.name, style = MaterialTheme.typography.bodyMedium)
    }
}
