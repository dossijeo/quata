package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.ColumnScope
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
    avatar: @Composable () -> Unit,
    bubbleModifier: Modifier = Modifier,
    deliveryIndicator: (@Composable () -> Unit)? = null,
    favoriteMarker: (@Composable () -> Unit)? = null,
    richText: (@Composable ColumnScope.(Color) -> Unit)? = null,
    attachment: (@Composable ColumnScope.(Color) -> Unit)? = null,
    mapAction: (@Composable () -> Unit)? = null,
    actions: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ChatMessageBubbleLayoutContent(
        isMine = message.isMine,
        isSelected = isSelected,
        showSenderAvatar = showSenderAvatar,
        avatar = avatar,
        bubbleModifier = bubbleModifier,
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
                    if (message.text.isNotBlank()) richText?.let { renderer -> renderer(textColor) }
                    attachment?.let { renderer ->
                        if (message.text.isNotBlank()) Spacer(Modifier.padding(4.dp))
                        renderer(textColor)
                    }
                }
            },
            mapAction = mapAction,
        )
        actions?.invoke(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
