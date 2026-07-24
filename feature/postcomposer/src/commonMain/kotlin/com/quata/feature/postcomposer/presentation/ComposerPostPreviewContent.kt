package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Portable post-preview hierarchy. Media playback/bitmaps, localized chips, avatars and action
 * resources stay in slots, while the shared form owns the responsive overlay geometry.
 */
@Composable
fun ComposerPostPreviewContent(
    isVideo: Boolean,
    compact: Boolean,
    mediaAspectRatio: Float,
    backgroundSeed: String,
    media: @Composable BoxScope.() -> Unit,
    scrim: @Composable BoxScope.() -> Unit,
    topOverlay: @Composable (Modifier) -> Unit,
    actionRail: @Composable (Modifier) -> Unit,
    compactLeadingActions: @Composable (Modifier) -> Unit,
    authorOverlay: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerFeedPreviewFrameContent(
        compact = compact,
        mediaAspectRatio = mediaAspectRatio,
        backgroundSeed = backgroundSeed,
        media = media,
        scrim = scrim,
        topOverlay = {
            topOverlay(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 14.dp),
            )
        },
        actionRail = {
            actionRail(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 18.dp),
            )
        },
        compactLeadingActions = {
            if (compact) {
                compactLeadingActions(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = if (isVideo) 132.dp else 88.dp),
                )
            }
        },
        authorOverlay = {
            authorOverlay(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 76.dp, bottom = if (isVideo) 78.dp else 18.dp),
            )
        },
        modifier = modifier,
    )
}
