package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

/** Compose-only scrubber for reel/video controls; playback stays injected by the host. */
@Composable
fun ReelTimelineThumbContent(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
            }
        },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .offset(x = (maxWidth - 10.dp) * progress.coerceIn(0f, 1f))
                .background(QuataOrange, CircleShape)
        )
    }
}
