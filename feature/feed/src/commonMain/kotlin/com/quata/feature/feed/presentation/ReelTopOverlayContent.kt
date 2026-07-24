package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared top overlay arrangement for reels, combining scrim and metadata chips. */
@Composable
fun ReelTopOverlayContent(
    showTopScrim: Boolean,
    documentText: String?,
    mediaBadgeText: String,
    isVideo: Boolean,
    locationLabel: @Composable (String) -> String,
    modifier: Modifier = Modifier
) {
    ReelScrimContent(showTopScrim = showTopScrim, modifier = modifier.fillMaxSize())
    ReelTopChipsContent(
        documentText = documentText,
        mediaBadgeText = mediaBadgeText,
        isVideo = isVideo,
        locationLabel = locationLabel
    )
}
