package com.quata.core.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

private val textCanvasPalettes = listOf(
    Color(0xFF0F172A) to Color(0xFF1D4ED8),
    Color(0xFF111827) to Color(0xFF7C3AED),
    Color(0xFF1F2937) to Color(0xFFDB2777),
    Color(0xFF172554) to Color(0xFF0891B2),
    Color(0xFF3F0D12) to Color(0xFF0F766E),
    Color(0xFF1E1B4B) to Color(0xFFEA580C)
)

fun textCanvasBrush(seedText: String?): Brush {
    val (start, end) = textCanvasPalette(seedText)
    return Brush.linearGradient(listOf(start, end))
}

private fun textCanvasPalette(seedText: String?): Pair<Color, Color> {
    var hash = 0
    (seedText ?: "Q").forEach { ch ->
        hash = ((hash shl 5) - hash + ch.code)
    }
    return textCanvasPalettes[hash.toLong().absoluteValue.mod(textCanvasPalettes.size)]
}
