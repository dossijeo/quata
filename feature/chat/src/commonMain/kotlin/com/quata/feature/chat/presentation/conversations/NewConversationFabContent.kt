package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon

/** Portable floating action used to open the new-conversation flow. */
@Composable
fun NewConversationFabContent(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    androidx.compose.material3.Surface(
        color = template.colors.accent,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(64.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompactIcon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = contentDescription,
                tint = template.colors.accentContent,
                modifier = Modifier.size(34.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceRaised)
                    .border(1.dp, template.colors.accent.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CompactIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = template.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
