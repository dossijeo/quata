package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.User
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent

enum class ConversationAvatarKind { Sos, Group, Private }

data class ConversationAvatarPresentation(
    val kind: ConversationAvatarKind,
    val name: String,
    val stableId: String,
    val avatarUrl: String?,
    val profileId: String?,
    val isMuted: Boolean,
    val isLoading: Boolean,
)

fun resolveConversationAvatarPresentation(
    conversation: Conversation,
    currentUser: User?,
    usersById: Map<String, User>,
    displayTitle: String,
    openingProfileUserId: String?,
): ConversationAvatarPresentation {
    val peerId = if (!conversation.isGroup && !conversation.isEmergency) {
        conversation.participantIds.firstOrNull { it != currentUser?.id }
    } else null
    val privateUser = peerId?.let(usersById::get)
    val peerIndex = peerId?.let(conversation.participantIds::indexOf) ?: -1
    val kind = when { conversation.isEmergency -> ConversationAvatarKind.Sos; conversation.isGroup -> ConversationAvatarKind.Group; else -> ConversationAvatarKind.Private }
    return ConversationAvatarPresentation(
        kind = kind,
        name = when (kind) { ConversationAvatarKind.Sos -> "SOS"; ConversationAvatarKind.Group -> displayTitle; ConversationAvatarKind.Private -> privateUser?.displayName ?: displayTitle },
        stableId = when (kind) { ConversationAvatarKind.Private -> privateUser?.id ?: conversation.id; else -> conversation.id },
        avatarUrl = when (kind) { ConversationAvatarKind.Sos -> null; ConversationAvatarKind.Group -> conversation.avatarUrl; ConversationAvatarKind.Private -> privateUser?.avatarUrl ?: conversation.participantAvatarUrls.getOrNull(peerIndex) ?: conversation.avatarUrl },
        profileId = privateUser?.id,
        isMuted = conversation.isMuted,
        isLoading = privateUser?.id?.let { it == openingProfileUserId } == true,
    )
}

@Composable
fun ConversationAvatarContent(
    presentation: ConversationAvatarPresentation,
    onOpenUserProfile: (String) -> Unit,
    remoteAvatar: @Composable (ConversationAvatarPresentation, Modifier) -> Unit,
    modifier: Modifier = Modifier.size(52.dp),
) {
    val template = quataTheme()
    Box(modifier, contentAlignment = Alignment.Center) {
        if (presentation.kind == ConversationAvatarKind.Sos) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(template.colors.sosSurface).border(1.dp, template.colors.accent.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
                Text("SOS", color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption)
            }
        } else {
            QuataAvatarLoadingHaloContent(isLoading = presentation.isLoading, modifier = Modifier.size(46.dp)) {
                remoteAvatar(
                    presentation,
                    Modifier.size(46.dp).then(presentation.profileId?.let { id -> Modifier.clickable { onOpenUserProfile(id) } } ?: Modifier),
                )
            }
        }
        if (presentation.isMuted) {
            Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(template.colors.surfaceRaised).border(1.dp, template.colors.divider, CircleShape), contentAlignment = Alignment.Center) {
                Text("🔕", fontSize = template.textSizes.caption)
            }
        }
    }
}
