package com.quata.designsystem.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.quata.core.designsystem.theme.QuataChatBackgroundPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Portable Compose renderer used where a cached platform bitmap is unavailable. */
@Composable
fun ProceduralChatBackgroundCanvas(
    spec: ProceduralChatBackgroundSpec,
    palettes: List<QuataChatBackgroundPalette>,
    modifier: Modifier = Modifier
) {
    val palette = palettes.getOrNull(spec.paletteIndex.floorMod(palettes.size)) ?: DefaultPalette
    Canvas(modifier.fillMaxSize()) { drawProceduralBackground(spec.seed, palette) }
}

private fun DrawScope.drawProceduralBackground(seed: Long, palette: QuataChatBackgroundPalette) {
    drawRect(palette.base)
    val normalizedSeed = (seed and 0xffffL).toFloat() / 65535f
    val maxDimension = size.maxDimension.coerceAtLeast(1f)
    repeat(18) { index ->
        val phase = normalizedSeed * 6.283185f + index * 0.741f
        val x = size.width * (0.5f + 0.43f * sin(phase * 1.7f + index))
        val y = size.height * (0.5f + 0.44f * cos(phase * 1.31f + index * 0.6f))
        val radius = maxDimension * (0.12f + abs(sin(phase + index)) * 0.25f)
        val color = when (index % 3) { 0 -> palette.a; 1 -> palette.b; else -> palette.c }
        drawCircle(color.copy(alpha = 0.035f + (index % 4) * 0.012f), radius, Offset(x, y))
    }
}

private val DefaultPalette = QuataChatBackgroundPalette(
    base = Color(0xFF030408), a = Color(0xFF2F8CFF), b = Color(0xFF7C3CFF), c = Color(0xFFFF8A1F)
)

private fun Int.floorMod(size: Int): Int = if (size <= 0) 0 else ((this % size) + size) % size
