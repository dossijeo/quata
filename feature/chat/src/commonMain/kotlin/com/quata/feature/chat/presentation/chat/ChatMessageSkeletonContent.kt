package com.quata.feature.chat.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Portable loading placeholder for an incoming or outgoing chat message. */
@Composable
fun ChatMessageSkeletonContent(
    isMine: Boolean,
    pulseDelayMillis: Int,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val transition = rememberInfiniteTransition(label = "chat_message_skeleton")
    val pulse = transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 860, delayMillis = pulseDelayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_message_skeleton_alpha"
    ).value
    val bubbleColor = if (isMine) {
        template.colors.accent.copy(alpha = 0.12f + 0.12f * pulse)
    } else {
        template.colors.surface.copy(alpha = 0.28f + 0.20f * pulse)
    }
    val lineColor = template.colors.textPrimary.copy(alpha = 0.07f + 0.13f * pulse)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(34.dp)
                    .background(template.colors.surface.copy(alpha = 0.28f + 0.20f * pulse), CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .background(bubbleColor, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonLine(0.42f, 14.dp, lineColor)
            SkeletonLine(1f, 18.dp, lineColor)
            SkeletonLine(0.68f, 18.dp, lineColor.copy(alpha = lineColor.alpha * 0.82f))
        }
        if (isMine) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(34.dp)
                    .background(template.colors.accent.copy(alpha = 0.08f + 0.10f * pulse), CircleShape)
            )
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(color, RoundedCornerShape(8.dp))
    )
}
