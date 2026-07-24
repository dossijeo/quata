package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared reel overlay structure. Media, avatars and interaction/navigation details remain host
 * slots, while commonMain owns the stable placement of the reel controls and author block.
 */
@Composable
fun ReelPostOverlayContent(
    isVideo: Boolean,
    isLandscape: Boolean,
    media: @Composable BoxScope.() -> Unit,
    topOverlay: @Composable BoxScope.() -> Unit,
    actionRail: @Composable () -> Unit,
    author: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overflowAction: (@Composable () -> Unit)? = null
) {
    ReelPostLayoutContent(
        media = media,
        topOverlay = topOverlay,
        actionRail = {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 22.dp)
            ) {
                actionRail()
            }
        },
        overflowAction = overflowAction?.takeIf { isLandscape }?.let { content ->
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = if (isVideo) 148.dp else 86.dp)
                ) {
                    content()
                }
            }
        },
        author = {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 96.dp, bottom = if (isVideo) 82.dp else 20.dp)
            ) {
                author()
            }
        },
        modifier = modifier
    )
}
