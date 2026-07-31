package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quata.core.ui.components.ClickableProfileAvatar

@Composable
internal fun AndroidChatProfileAvatar(
    presentation: ChatAvatarPresentation,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    ClickableProfileAvatar(
        name = presentation.name,
        avatarUrl = presentation.avatarUrl,
        profileId = presentation.profileId,
        isLoading = presentation.isLoading,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun AndroidChatMediaAttachment(
    presentation: ChatMediaPresentation,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (presentation.kind == ChatMediaKind.Image) {
        AsyncImage(
            model = presentation.file.reference,
            contentDescription = presentation.file.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.height(180.dp).clickable(onClick = onClick),
        )
    } else {
        Surface(modifier.clickable(onClick = onClick)) { Text("Reproducir vídeo") }
    }
}
