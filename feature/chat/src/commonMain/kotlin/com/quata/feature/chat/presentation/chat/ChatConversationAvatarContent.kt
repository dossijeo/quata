package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/**
 * Portable conversation-avatar frame.
 *
 * The host resolves participant identity and supplies image/profile navigation. Group imagery,
 * the SOS label and the muted-state geometry stay common so every host presents the same
 * conversation affordance without needing an Android image or navigation dependency.
 */
@Composable
fun ChatConversationAvatarContent(
    isGroup: Boolean,
    isEmergency: Boolean,
    isMuted: Boolean,
    emergencyLabel: String,
    privateAvatar: @Composable () -> Unit,
    groupIcon: @Composable () -> Unit,
    mutedBadge: @Composable () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = quataTheme()
    val containerSize = if (compact) 44.dp else 52.dp
    val avatarSize = if (compact) 38.dp else 46.dp

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center,
    ) {
        if (isGroup || isEmergency) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(
                        if (isEmergency) theme.colors.sosSurface
                        else theme.colors.accent.copy(alpha = 0.22f),
                    )
                    .border(1.dp, theme.colors.accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isEmergency) {
                    Text(
                        text = emergencyLabel,
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else {
                    groupIcon()
                }
            }
        } else {
            privateAvatar()
        }
        if (isMuted) {
            Box(Modifier.align(Alignment.TopEnd)) { mutedBadge() }
        }
    }
}

/** Shared muted-state marker; hosts can replace it when their platform needs custom semantics. */
@Composable
fun ChatMutedConversationBadgeContent(
    modifier: Modifier = Modifier,
) {
    val theme = quataTheme()
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(theme.colors.surfaceRaised)
            .border(1.dp, theme.colors.divider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.NotificationsOff,
            contentDescription = null,
            tint = theme.colors.textSecondary,
            modifier = Modifier.size(10.dp),
        )
    }
}
