package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.ui.textCanvasBrush

/**
 * Shared profile-post preview shell. Media, metadata and interactions are injected so hosts can
 * retain Coil, Media3, navigation and authorization policies.
 */
@Composable
fun ProfilePostPreviewFrameContent(
    backgroundSeed: String,
    media: @Composable BoxScope.() -> Unit,
    metadata: @Composable ColumnScope.() -> Unit,
    actionRail: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(430.dp)
            .background(textCanvasBrush(backgroundSeed), RoundedCornerShape(20.dp)),
    ) {
        media()
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(content = metadata)
            actionRail()
        }
    }
}
