package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Complete portable overlay hierarchy for an image or video post preview.
 *
 * The host supplies rendered media (Coil, a browser image or a native player), localized chip
 * labels and optionally an identity avatar. Everything that determines the preview's structure
 * and visual fallback is owned by common presentation.
 */
@Composable
fun ComposerMediaPostPreviewContent(
    isVideo: Boolean,
    description: String,
    subtitle: String,
    topChips: List<String>,
    actionLabels: ComposerPreviewActionLabels,
    authorName: String,
    compact: Boolean,
    backgroundSeed: String,
    media: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    mediaAspectRatio: Float = 9f / 16f,
    avatar: @Composable () -> Unit = { ComposerPreviewDefaultAvatarContent() },
) {
    ComposerPostPreviewContent(
        isVideo = isVideo,
        compact = compact,
        mediaAspectRatio = mediaAspectRatio,
        backgroundSeed = backgroundSeed,
        media = media,
        scrim = { ComposerPreviewScrimContent() },
        topOverlay = { overlayModifier ->
            ComposerPreviewTopChipsContent(chips = topChips, modifier = overlayModifier)
        },
        actionRail = { railModifier ->
            ComposerPreviewActionsContent(
                showRankLiveActions = !compact,
                labels = actionLabels,
                modifier = railModifier,
            )
        },
        compactLeadingActions = { leadingModifier ->
            ComposerPreviewRankLiveActionsContent(
                labels = actionLabels,
                modifier = leadingModifier,
            )
        },
        authorOverlay = { authorModifier ->
            ComposerPreviewAuthorContent(
                description = description,
                authorName = authorName,
                subtitle = subtitle,
                avatar = avatar,
                modifier = authorModifier,
            )
        },
        modifier = modifier,
    )
}
