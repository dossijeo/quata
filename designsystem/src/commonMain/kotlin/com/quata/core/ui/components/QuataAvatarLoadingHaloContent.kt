package com.quata.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Portable loading halo around an avatar. The host supplies the avatar itself so image loading,
 * presence and platform resources stay outside the design system.
 */
@Composable
fun QuataAvatarLoadingHaloContent(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    val rotation = if (isLoading) {
        val transition = rememberInfiniteTransition(label = "profile_avatar_loading")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "profile_avatar_loading_rotation",
        ).value
    } else {
        0f
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = 1.16f,
                        scaleY = 1.16f,
                        rotationZ = rotation,
                    ),
            ) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFE5D45C),
                            Color(0xFFE5D45C).copy(alpha = 0.72f),
                            Color.Transparent,
                            Color(0xFFE5D45C).copy(alpha = 0.18f),
                            Color.Transparent,
                            Color(0xFFE5D45C),
                        ),
                        center = center,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE5D45C).copy(alpha = 0.24f), Color.Transparent),
                        center = center,
                        radius = radius * 0.42f,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        avatar()
    }
}
