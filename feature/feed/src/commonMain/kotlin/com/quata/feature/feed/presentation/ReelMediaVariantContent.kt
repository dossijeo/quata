package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared media-variant decision for a reel. Platform media renderers are slots while the
 * deterministic fallback stays portable.
 */
@Composable
fun ReelMediaVariantContent(
    hasVideo: Boolean,
    hasImage: Boolean,
    hasText: Boolean,
    video: @Composable BoxScope.() -> Unit,
    image: @Composable BoxScope.() -> Unit,
    text: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        when {
            hasVideo -> video()
            hasImage -> image()
            hasText -> text()
            else -> ReelMediaFallbackContent()
        }
    }
}

@Composable
private fun ReelMediaFallbackContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF78B7E8), Color(0xFF2E6F95), Color(0xFF16202D))
                )
            )
    )
}
