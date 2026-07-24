package com.quata.feature.chat.presentation.conversations

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.QuataCard

/** Shared loading state placeholder for a conversation-list row. */
@Composable
fun ConversationListLoadingSkeletonContent(pulseDelayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "conversation_skeleton")
    val pulse = transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 880, delayMillis = pulseDelayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conversation_skeleton_alpha"
    ).value
    val surface = Color.White.copy(alpha = 0.055f + 0.055f * pulse)
    val line = Color.White.copy(alpha = 0.08f + 0.12f * pulse)
    QuataCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(QuataOrange.copy(alpha = 0.12f + 0.10f * pulse))
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.52f).height(16.dp)
                        .clip(RoundedCornerShape(8.dp)).background(line)
                )
                Box(
                    modifier = Modifier.fillMaxWidth(0.86f).height(14.dp)
                        .clip(RoundedCornerShape(8.dp)).background(surface)
                )
            }
            Box(
                modifier = Modifier.size(width = 54.dp, height = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(line.copy(alpha = line.alpha * 0.75f))
            )
        }
    }
}
