package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.model.Message

/** Localized values used by the portable message-bubble composition. */
data class ChatMessageBubbleFrameStrings(
    val edited: String,
    val deletedMessage: String,
    val forwarded: String,
)

/**
 * Complete portable message-bubble composition.
 *
 * Platform code injects avatar/profile navigation, pointer/translation modifiers, rich text,
 * audio/media/document URI renderers, maps and any contextual actions. The common frame owns the
 * message hierarchy, metadata, reply/forward markers, deletion semantics and attachment spacing.
 */
@Composable
fun ChatMessageBubbleFrameContent(
    message: Message,
    timestamp: String,
    isSelected: Boolean,
    showSenderAvatar: Boolean,
    strings: ChatMessageBubbleFrameStrings,
    textColor: Color,
    platformSlots: ChatMessageBubblePlatformSlots,
    bubbleModifier: Modifier = Modifier,
    deliveryIndicator: (@Composable () -> Unit)? = null,
    favoriteMarker: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ChatMessageBubbleLayoutContent(
        isMine = message.isMine,
        isSelected = isSelected,
        showSenderAvatar = showSenderAvatar,
        avatar = platformSlots.avatar,
        bubbleModifier = platformSlots.translatedTextModifier(bubbleModifier),
        modifier = modifier,
    ) {
        ChatMessageBubbleContent(
            header = {
                ChatMessageHeaderContent(
                    senderName = message.senderName,
                    timestamp = timestamp,
                    isMine = message.isMine,
                    isEdited = message.isEdited,
                    isFavorite = message.isFavorite,
                    editedLabel = strings.edited,
                    textColor = textColor,
                    deliveryIndicator = deliveryIndicator,
                    favoriteMarker = favoriteMarker,
                )
            },
            forwardedMarker = message.forwardedFromSenderName?.let {
                { ChatForwardedMarkerContent(strings.forwarded, textColor) }
            },
            replyQuote = message.replyToText?.let {
                {
                    ChatReplyQuoteContent(
                        senderName = message.replyToSenderName.orEmpty(),
                        text = it,
                        textColor = textColor,
                    )
                }
            },
            body = {
                if (message.isDeleted) {
                    Text(strings.deletedMessage, color = textColor.copy(alpha = 0.72f))
                } else {
                    if (message.text.isNotBlank()) {
                        Column {
                            platformSlots.richText(this, textColor)
                        }
                    }
                    ChatMessageBubbleAttachmentSlotsContent(
                        hasText = message.text.isNotBlank(),
                        textColor = textColor,
                        hasMediaAttachment = platformSlots.hasMediaAttachment,
                        mediaAttachment = platformSlots.mediaAttachment,
                        hasAudioAttachment = platformSlots.hasAudioAttachment,
                        audioAttachment = platformSlots.audioAttachment,
                        hasUriAttachment = platformSlots.hasUriAttachment,
                        uriAttachment = platformSlots.uriAttachment,
                    )
                }
            },
            mapAction = platformSlots.mapAction.takeIf { platformSlots.hasMapAction },
        )
        if (platformSlots.hasActions) {
            platformSlots.actions(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun ChatMessageBubbleAttachmentSlotsContent(
    hasText: Boolean,
    textColor: Color,
    hasMediaAttachment: Boolean,
    mediaAttachment: @Composable ColumnScope.(Color) -> Unit,
    hasAudioAttachment: Boolean,
    audioAttachment: @Composable ColumnScope.(Color) -> Unit,
    hasUriAttachment: Boolean,
    uriAttachment: @Composable ColumnScope.(Color) -> Unit,
) {
    if (hasMediaAttachment || hasAudioAttachment || hasUriAttachment) {
        Column {
            if (hasText) Spacer(Modifier.padding(4.dp))
            if (hasMediaAttachment) mediaAttachment(textColor)
            if (hasAudioAttachment) audioAttachment(textColor)
            if (hasUriAttachment) uriAttachment(textColor)
        }
    }
}
