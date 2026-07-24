package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/** Portable reel media surface; platform hosts inject image/video renderers into the slot. */
@Composable
fun ReelMediaSurfaceContent(
    background: Brush,
    contentAlignment: Alignment = Alignment.TopStart,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(background),
        contentAlignment = contentAlignment,
        content = content
    )
}
