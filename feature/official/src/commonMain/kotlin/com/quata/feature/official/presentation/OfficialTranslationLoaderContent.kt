package com.quata.feature.official.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Portable animated loader displayed while official translations are generated. */
@Composable
fun OfficialTranslationLoaderContent(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "official_translation_loader")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "official_translation_loader_step"
    )
    val flip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "official_translation_loader_flip"
    )
    val isSecondPhase = step >= 0.5f
    val containerRotation = if (isSecondPhase) -90f else 0f
    val containerScale = if (isSecondPhase) -1f else 1f
    val halfColor = if (isSecondPhase) Color(0xFF25B09B) else Color(0xFF514B82)
    val halfTravel = when {
        flip <= 0.05f -> 0.dp
        flip <= 0.33f -> ((flip - 0.05f) / 0.28f * -10f).dp
        flip <= 0.66f -> (-10).dp
        flip <= 0.95f -> (-10f + ((flip - 0.66f) / 0.29f * 10f)).dp
        else -> 0.dp
    }
    val halfRotationX = when {
        flip <= 0.33f -> 0f
        flip <= 0.66f -> ((flip - 0.33f) / 0.33f) * -180f
        else -> -180f
    }
    val leftShape = if (isSecondPhase) {
        RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    val rightShape = if (isSecondPhase) {
        RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    Row(
        modifier = modifier
            .size(60.dp)
            .rotate(containerRotation)
            .graphicsLayer { scaleX = containerScale },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(30.dp)
                .graphicsLayer {
                    translationX = halfTravel.toPx()
                    rotationX = halfRotationX
                    cameraDistance = 12f * density
                }
                .background(halfColor, leftShape)
        )
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(30.dp)
                .graphicsLayer {
                    translationX = -halfTravel.toPx()
                    rotationX = -halfRotationX
                    cameraDistance = 12f * density
                }
                .background(halfColor, rightShape)
        )
    }
}
