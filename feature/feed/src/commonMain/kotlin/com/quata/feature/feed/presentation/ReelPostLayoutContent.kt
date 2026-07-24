package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

/**
 * Shared reel/card composition. Platform hosts provide media, navigation and system-backed
 * controls through slots while the portable overlay hierarchy remains in commonMain.
 */
@Composable
fun ReelPostLayoutContent(
    media: @Composable BoxScope.() -> Unit,
    topOverlay: @Composable BoxScope.() -> Unit,
    actionRail: @Composable BoxScope.() -> Unit,
    author: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    overflowAction: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        media()
        topOverlay()
        actionRail()
        overflowAction?.invoke(this)
        author()
    }
}
