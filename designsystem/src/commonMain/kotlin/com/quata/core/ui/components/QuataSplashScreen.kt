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
import kotlin.math.PI
import kotlin.math.sin

/** Compose Multiplatform application splash; launchers only decide when it is shown. */
@Composable
fun QuataSplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val brand = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val transition = rememberInfiniteTransition(label = "quata_splash")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = easing), RepeatMode.Reverse), label = "intro_float")
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { delay(180); brand.animateTo(1f, tween(900, easing = easing)) }
            launch { delay(900); line.animateTo(1f, tween(900)) }
            launch { delay(1550); tagline.animateTo(1f, tween(900, easing = easing)) }
        }
        delay(450); onFinished()
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF020617), Color(0xFF0B1220), Color(0xFF111827)), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height)))
            val wave = sin(shift * PI.toFloat())
            glow(Color(0x3DF97316), Offset(size.width * .20f, size.height * .20f), size.minDimension * .42f)
            glow(Color(0x38EA580C), Offset(size.width * .80f, size.height * .80f), size.minDimension * .48f)
            glow(Color(0x5CF97316), Offset(size.width * (-.08f + wave * .04f), size.height * (-.06f + wave * .03f)), size.minDimension * .54f, Color(0x14EA580C))
            glow(Color(0x59F97316), Offset(size.width * (1.08f - wave * .04f), size.height * (1.10f - wave * .03f)), size.minDimension * .46f, Color(0x14EA580C))
        }
        IntroStage(brand.value, line.value, tagline.value, Modifier.align(Alignment.Center).padding(32.dp))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.glow(first: Color, center: Offset, radius: Float, second: Color = Color.Transparent) {
    drawCircle(Brush.radialGradient(listOf(first, second), center, radius), radius, center)
}

@Composable
private fun IntroStage(brand: Float, line: Float, tagline: Float, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.widthIn(240.dp, 320.dp)) {
        QuataMarkSymbol(Color(0xFFFB923C), compact = false, modifier = Modifier.graphicsLayer {
            alpha = brand; translationY = (1f - brand) * 28.dp.toPx(); val scale = .94f + brand * .06f; scaleX = scale; scaleY = scale
        })
        Canvas(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).graphicsLayer { alpha = line; scaleX = .4f + line * .6f }) {
            drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xF2F97316), Color(0xF2EA580C), Color.Transparent)), cornerRadius = CornerRadius(size.height, size.height))
        }
        Text("QÜATA", color = Color(0xFFFFEDD5), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 12.sp, textAlign = TextAlign.Center, style = TextStyle(shadow = Shadow(Color(0x2EF97316), blurRadius = 24f)), modifier = Modifier.padding(top = 18.dp).graphicsLayer {
            alpha = tagline; translationY = (1f - tagline) * 28.dp.toPx(); val scale = .94f + tagline * .06f; scaleX = scale; scaleY = scale
        })
    }
}
