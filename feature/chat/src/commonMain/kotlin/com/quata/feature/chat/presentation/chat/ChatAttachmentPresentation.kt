package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.platform.DocumentSupport
import com.quata.core.platform.PlatformFile

/** Product classification shared by the three Chat consumers. */
enum class ChatAttachmentKind { Image, Video, Audio, Document, File }

fun chatAttachmentKind(file: PlatformFile): ChatAttachmentKind {
    val mime = file.mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> ChatAttachmentKind.Image
        mime.startsWith("video/") -> ChatAttachmentKind.Video
        mime.startsWith("audio/") -> ChatAttachmentKind.Audio
        DocumentSupport.canPreview(file.reference, file.displayName, file.mimeType) -> ChatAttachmentKind.Document
        else -> chatAttachmentKindFromExtension(file.displayName ?: file.reference)
    }
}

private fun chatAttachmentKindFromExtension(value: String): ChatAttachmentKind =
    when (value.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> ChatAttachmentKind.Image
        "mp4", "mov", "m4v", "webm", "avi", "mkv" -> ChatAttachmentKind.Video
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "webm" -> ChatAttachmentKind.Audio
        else -> ChatAttachmentKind.File
    }

/** Decoder-only seams. Product framing and full-screen navigation remain in common Compose. */
data class ChatMediaPlatformSlots(
    val preview: @Composable (PlatformFile, ChatAttachmentKind, Modifier) -> Unit,
    val viewer: @Composable (PlatformFile, ChatAttachmentKind, Modifier) -> Unit,
    val showCommonMediaClose: Boolean = true,
    val nativeClose: @Composable BoxScope.(onDismiss: () -> Unit) -> Unit = {},
)

const val ChatMediaAttachmentTestTag = "chat.attachment.media"
const val ChatImageAttachmentContentDescription = "chat.attachment.media.image"
const val ChatVideoAttachmentContentDescription = "chat.attachment.media.video"
const val ChatMediaAttachmentOpenTestTagSuffix = ".open"

private fun chatMediaAttachmentSemanticAnchor(kind: ChatAttachmentKind): String =
    when (kind) {
        ChatAttachmentKind.Video -> ChatVideoAttachmentContentDescription
        ChatAttachmentKind.Image -> ChatImageAttachmentContentDescription
        else -> ChatMediaAttachmentTestTag
    }

@Composable
fun ChatMediaAttachmentContent(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    media: @Composable (PlatformFile, ChatAttachmentKind, Modifier) -> Unit,
    onOpen: () -> Unit,
    playVideoLabel: String,
    modifier: Modifier = Modifier,
    semanticTestTag: String = chatMediaAttachmentSemanticAnchor(kind),
) {
    val semanticAnchor = chatMediaAttachmentSemanticAnchor(kind)
    val openButtonTestTag = "$semanticTestTag$ChatMediaAttachmentOpenTestTagSuffix"
    val mediaOwnsOpen = kind == ChatAttachmentKind.Video || kind == ChatAttachmentKind.Image
    val primaryTestTag = if (mediaOwnsOpen) openButtonTestTag else semanticTestTag
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = false) {
                testTag = primaryTestTag
                contentDescription = semanticAnchor
                role = Role.Button
                onClick(label = playVideoLabel) {
                    onOpen()
                    true
                }
            }
            .clickable(role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        media(file, kind, Modifier.fillMaxSize())
        if (mediaOwnsOpen) {
            Surface(
                color = Color.Black.copy(alpha = 0.38f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(62.dp)
                    .clearAndSetSemantics {},
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (kind == ChatAttachmentKind.Video) Icons.Filled.PlayArrow else Icons.Filled.OpenInFull,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(38.dp),
                    )
                }
            }
        }
    }
}

/** Shared terminal media state used by platform decoders; retry remains product-owned Compose. */
@Composable
fun ChatMediaLoadFailureContent(
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) { Text(retryLabel) }
    }
}
