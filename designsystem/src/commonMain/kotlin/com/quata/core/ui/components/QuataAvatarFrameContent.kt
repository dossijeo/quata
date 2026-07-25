package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Portable avatar structure shared by platform image loaders.
 *
 * Platforms provide [avatar] when they can render an image. When they cannot,
 * the deterministic common fallback is rendered instead. A null [isOnline]
 * means that the platform has no presence source, so no presence badge is
 * shown.
 */
@Composable
fun QuataAvatarFrameContent(
    name: String,
    stableId: String = name,
    isOfficial: Boolean = false,
    isOnline: Boolean? = null,
    modifier: Modifier = Modifier,
    avatar: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        if (avatar == null) {
            QuataAvatarFallback(
                name = name,
                stableId = stableId,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            avatar()
        }
        if (isOfficial) {
            QuataOfficialBadge(Modifier.align(Alignment.BottomEnd))
        }
        isOnline?.let { online ->
            QuataAvatarPresenceBadgeContent(
                isOnline = online,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
