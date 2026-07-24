package com.quata.feature.official.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/**
 * Portable Official feed/detail media frame. Platform hosts supply image/video rendering while
 * this component owns only the visual container and navigation gesture.
 */
@Composable
fun OfficialPostMediaFrameContent(
    onOpenMedia: () -> Unit,
    media: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenMedia),
        contentAlignment = Alignment.Center,
    ) {
        media(Modifier.fillMaxSize())
    }
}
