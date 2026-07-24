package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.User
import com.quata.core.presence.LocalUserPresence

@Composable
fun AvatarImage(
    name: String,
    avatarUrl: String?,
    isOfficial: Boolean = false,
    profileId: String? = null,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val template = quataTheme()
    val imageModel = rememberCachedRemoteImageRequest(avatarUrl)
    val presenceRepository = LocalUserPresence.current
    val onlineProfileIds by presenceRepository?.onlineProfileIds?.collectAsState(emptySet())
        ?: remember { androidx.compose.runtime.mutableStateOf(emptySet()) }
    LaunchedEffect(profileId) {
        profileId?.let { presenceRepository?.observeProfiles(listOf(it)) }
    }
    Box(modifier = modifier) {
        if (avatarUrl.isNullOrBlank()) {
            QuataAvatarFallback(
                name = name,
                stableId = profileId ?: name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = imageModel,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, template.colors.accent.copy(alpha = 0.42f), CircleShape)
            )
        }
        if (isOfficial) {
            QuataOfficialBadge(Modifier.align(Alignment.BottomEnd))
        }
        if (profileId != null && presenceRepository != null) {
            QuataAvatarPresenceBadgeContent(
                isOnline = profileId in onlineProfileIds,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
fun UserAvatar(user: User, modifier: Modifier = Modifier.size(44.dp)) {
    AvatarImage(
        name = user.displayName,
        avatarUrl = user.avatarUrl,
        isOfficial = user.isOfficial,
        profileId = user.id,
        modifier = modifier
    )
}

@Composable
fun ClickableProfileAvatar(
    name: String,
    avatarUrl: String?,
    isOfficial: Boolean = false,
    profileId: String? = null,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp)
) {
    ProfileAvatarWithLoadingHalo(
        name = name,
        avatarUrl = avatarUrl,
        isOfficial = isOfficial,
        profileId = profileId,
        isLoading = isLoading,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
}

@Composable
fun ProfileAvatarWithLoadingHalo(
    name: String,
    avatarUrl: String?,
    isOfficial: Boolean = false,
    profileId: String? = null,
    isLoading: Boolean,
    modifier: Modifier = Modifier.size(44.dp)
) {
    QuataAvatarLoadingHaloContent(isLoading = isLoading, modifier = modifier) {
        AvatarImage(
            name = name,
            avatarUrl = avatarUrl,
            isOfficial = isOfficial,
            profileId = profileId,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun UserHeader(name: String, meta: String, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        QuataAvatarFallback(name)
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(name, fontWeight = FontWeight.Bold)
            Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = template.textSizes.caption)
        }
    }
}
