package com.quata.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun QuataSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "quata_splash")
    val glowShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_shift"
    )
    val logoBreath by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_breath"
    )
    val logoAlpha by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_alpha"
    )

    LaunchedEffect(Unit) {
        delay(1850L)
        onFinished()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF050A16),
                        Color(0xFF071020),
                        Color(0xFF0B111E),
                        Color(0xFF111018)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            val slowWave = sin(glowShift * Math.PI.toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x6658261D),
                        Color(0x332B1212),
                        Color.Transparent
                    ),
                    center = Offset(size.width * (0.10f + slowWave * 0.04f), size.height * 0.05f),
                    radius = size.minDimension * (0.52f + slowWave * 0.06f)
                ),
                radius = size.minDimension * (0.52f + slowWave * 0.06f),
                center = Offset(size.width * (0.10f + slowWave * 0.04f), size.height * 0.05f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x704B241B),
                        Color(0x26321818),
                        Color.Transparent
                    ),
                    center = Offset(size.width * (0.92f - slowWave * 0.03f), size.height * 0.94f),
                    radius = size.minDimension * (0.42f + glowShift * 0.05f)
                ),
                radius = size.minDimension * (0.42f + glowShift * 0.05f),
                center = Offset(size.width * (0.92f - slowWave * 0.03f), size.height * 0.94f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x22FF7A1A),
                        Color(0x103D1A16),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.50f, size.height * (0.54f + slowWave * 0.015f)),
                    radius = size.minDimension * 0.34f
                ),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.50f, size.height * (0.54f + slowWave * 0.015f))
            )
        }

        QuataSplashLogo(
            modifier = Modifier
                .offset(y = 56.dp)
                .graphicsLayer {
                    alpha = logoAlpha
                    scaleX = logoBreath
                    scaleY = logoBreath
                }
        )
    }
}

@Composable
private fun QuataSplashLogo(
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(220.dp)
    ) {
        Text(
            text = "QÜ",
            color = Color(0xFFFF8A2A),
            fontSize = 72.sp,
            lineHeight = 72.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Canvas(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(184.dp)
                .height(2.dp)
        ) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x66FF7A1A),
                        Color(0xFFFF7A1A),
                        Color(0x66FF7A1A),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height
            )
        }
        Text(
            text = "Q Ü A T A",
            color = Color(0xFFFFF1DF),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}
