package com.quata.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun QuataSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val revealEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val brandReveal = remember { Animatable(0f) }
    val lineReveal = remember { Animatable(0f) }
    val taglineReveal = remember { Animatable(0f) }
    val transition = rememberInfiniteTransition(label = "quata_splash")
    val floatShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = revealEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "intro_float"
    )

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                delay(180L)
                brandReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 900, easing = revealEasing)
                )
            }
            launch {
                delay(900L)
                lineReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 900)
                )
            }
            launch {
                delay(1550L)
                taglineReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 900, easing = revealEasing)
                )
            }
        }
        delay(450L)
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
                        Color(0xFF020617),
                        Color(0xFF0B1220),
                        Color(0xFF111827)
                    ),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height)
                )
            )

            val slowWave = sin(floatShift * Math.PI.toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x3DF97316),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.20f, size.height * 0.20f),
                    radius = size.minDimension * 0.42f
                ),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.20f, size.height * 0.20f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x38EA580C),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.80f, size.height * 0.80f),
                    radius = size.minDimension * 0.48f
                ),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.80f, size.height * 0.80f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x5CF97316),
                        Color(0x14EA580C),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width * (-0.08f + slowWave * 0.04f),
                        size.height * (-0.06f + slowWave * 0.03f)
                    ),
                    radius = size.minDimension * 0.54f
                ),
                radius = size.minDimension * 0.54f,
                center = Offset(
                    size.width * (-0.08f + slowWave * 0.04f),
                    size.height * (-0.06f + slowWave * 0.03f)
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x59F97316),
                        Color(0x14EA580C),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width * (1.08f - slowWave * 0.04f),
                        size.height * (1.10f - slowWave * 0.03f)
                    ),
                    radius = size.minDimension * 0.46f
                ),
                radius = size.minDimension * 0.46f,
                center = Offset(
                    size.width * (1.08f - slowWave * 0.04f),
                    size.height * (1.10f - slowWave * 0.03f)
                )
            )
        }

        IntroStage(
            brandReveal = brandReveal.value,
            lineReveal = lineReveal.value,
            taglineReveal = taglineReveal.value,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )
    }
}

@Composable
private fun IntroStage(
    brandReveal: Float,
    lineReveal: Float,
    taglineReveal: Float,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(min = 240.dp, max = 320.dp)
    ) {
        Text(
            text = "Q\u00DC",
            color = Color(0xFFFB923C),
            fontSize = 96.sp,
            lineHeight = 96.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 14.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0x47F97316),
                    offset = Offset(0f, 10f),
                    blurRadius = 30f
                )
            ),
            modifier = Modifier.graphicsLayer {
                alpha = brandReveal
                translationY = (1f - brandReveal) * 28.dp.toPx()
                val scale = 0.94f + (brandReveal * 0.06f)
                scaleX = scale
                scaleY = scale
            }
        )
        Canvas(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(4.dp)
                .graphicsLayer {
                    alpha = lineReveal
                    scaleX = 0.4f + (lineReveal * 0.6f)
                }
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xF2F97316),
                        Color(0xF2EA580C),
                        Color.Transparent
                    )
                ),
                cornerRadius = CornerRadius(size.height, size.height)
            )
        }
        Text(
            text = "Q\u00DCATA",
            color = Color(0xFFFFEDD5),
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 12.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0x2EF97316),
                    blurRadius = 24f
                )
            ),
            modifier = Modifier
                .padding(top = 18.dp)
                .graphicsLayer {
                    alpha = taglineReveal
                    translationY = (1f - taglineReveal) * 28.dp.toPx()
                    val scale = 0.94f + (taglineReveal * 0.06f)
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}
