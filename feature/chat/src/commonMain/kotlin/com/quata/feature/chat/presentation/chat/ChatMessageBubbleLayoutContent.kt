package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/**
 * Portable message-bubble shell. Hosts retain gesture handling, translation and system-backed
 * media in [bubbleModifier], [avatar] and [content] slots.
 */
@Composable
fun ChatMessageBubbleLayoutContent(
    isMine: Boolean,
    isSelected: Boolean,
    showSenderAvatar: Boolean,
    avatar: @Composable () -> Unit,
    bubbleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val template = quataTheme()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (showSenderAvatar) {
            avatar()
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (showSenderAvatar) 0.72f else 0.78f)
                .background(
                    when {
                        isSelected -> template.colors.chatSelected
                        isMine -> template.colors.chatMine
                        else -> template.colors.chatOther
                    },
                    RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isSelected -> template.colors.accent.copy(alpha = 0.45f)
                        isMine -> Color.Transparent
                        else -> template.colors.divider
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                .then(bubbleModifier)
                .padding(14.dp),
            content = content
        )
    }
}
