package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.compactButtonMinSize
import kotlinx.coroutines.delay

/** Portable publishing action with its visual progress affordance. */
@Composable
fun ComposerPublishButtonContent(
    isLoading: Boolean,
    publishLabel: String,
    publishingLabel: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isLoading) {
        progress = 0f
        while (isLoading) {
            delay(180L)
            progress = (progress + 0.018f).coerceAtMost(0.92f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .compactButtonMinSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (isLoading) Color(0xFFFFB45E) else QuataOrange)
            .clickable(enabled = !isLoading, onClick = onSubmit),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .align(Alignment.CenterStart)
                    .background(Color(0xFFE86F12))
            )
        }
        Text(
            if (isLoading) publishingLabel else publishLabel,
            color = Color.Black,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
