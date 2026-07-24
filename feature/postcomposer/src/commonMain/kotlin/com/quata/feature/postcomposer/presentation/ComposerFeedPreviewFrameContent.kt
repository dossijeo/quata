package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.ui.textCanvasBrush

/**
 * Portable structural shell for Composer reel previews. The host injects the media and
 * each overlay because those can depend on resource strings, navigation or platform playback.
 */
@Composable
fun ComposerFeedPreviewFrameContent(
    compact: Boolean,
    mediaAspectRatio: Float,
    backgroundSeed: String,
    media: @Composable BoxScope.() -> Unit,
    scrim: @Composable BoxScope.() -> Unit,
    topOverlay: @Composable BoxScope.() -> Unit,
    actionRail: @Composable BoxScope.() -> Unit,
    compactLeadingActions: @Composable BoxScope.() -> Unit,
    authorOverlay: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .then(if (compact) Modifier.widthIn(max = 280.dp) else Modifier)
                .fillMaxWidth()
                .aspectRatio(mediaAspectRatio.coerceIn(0.35f, 2.4f))
                .clip(RoundedCornerShape(24.dp))
                .background(textCanvasBrush(backgroundSeed))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        ) {
            media()
            scrim()
            topOverlay()
            actionRail()
            compactLeadingActions()
            authorOverlay()
        }
    }
}
