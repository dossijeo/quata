package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Portable video-control row. Player-specific actions and scrubber interaction are host slots. */
@Composable
fun ReelVideoControlsContent(
    durationText: String,
    playPauseAction: @Composable () -> Unit,
    timeline: @Composable RowScope.() -> Unit,
    muteAction: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        playPauseAction()
        timeline()
        Text(
            text = durationText,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(82.dp)
        )
        muteAction?.invoke()
    }
}
