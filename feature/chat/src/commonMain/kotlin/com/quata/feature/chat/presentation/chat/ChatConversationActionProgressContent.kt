package com.quata.feature.chat.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

/** Portable indeterminate progress treatment for conversation-level operations. */
@Composable
fun ChatConversationActionProgressContent(
    modifier: Modifier = Modifier,
    activeColor: Color = QuataOrange,
) {
    val transition = rememberInfiniteTransition(label = "conversation_action_progress")
    val progress = transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 960),
            repeatMode = RepeatMode.Restart,
        ),
        label = "conversation_action_progress_offset",
    ).value
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(activeColor.copy(alpha = 0.10f)),
    ) {
        val segmentWidth = maxWidth * 0.46f
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .height(3.dp)
                .offset(x = maxWidth * progress)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            activeColor.copy(alpha = 0.95f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
