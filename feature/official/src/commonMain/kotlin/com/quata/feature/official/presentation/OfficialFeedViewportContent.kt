package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.ui.components.QuataScreen

/** Shared official-feed viewport; host paging, refresh and loading layers are supplied as content. */
@Composable
fun OfficialFeedViewportContent(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    QuataScreen(padding = padding, applyLandscapeSafeDrawing = false) {
        Box(modifier = modifier.fillMaxSize(), content = content)
    }
}
