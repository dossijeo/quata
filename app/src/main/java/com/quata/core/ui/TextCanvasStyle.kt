package com.quata.core.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

data class TextCanvasPattern(
    val id: String,
    val label: String,
    val start: Color,
    val end: Color
)

data class TextCanvasTypography(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val maxLines: Int
)

const val DEFAULT_TEXT_CANVAS_PATTERN_ID = "midnight-blue"

val textCanvasPatterns = listOf(
    TextCanvasPattern(DEFAULT_TEXT_CANVAS_PATTERN_ID, "Azul noche", Color(0xFF0F172A), Color(0xFF1D4ED8)),
    TextCanvasPattern("violet-ink", "Violeta tinta", Color(0xFF111827), Color(0xFF7C3AED)),
    TextCanvasPattern("rose-graphite", "Rosa grafito", Color(0xFF1F2937), Color(0xFFDB2777)),
    TextCanvasPattern("teal-depth", "Turquesa profundo", Color(0xFF172554), Color(0xFF0891B2)),
    TextCanvasPattern("garnet-teal", "Granate verde", Color(0xFF3F0D12), Color(0xFF0F766E)),
    TextCanvasPattern("ember-indigo", "Ambar indigo", Color(0xFF1E1B4B), Color(0xFFEA580C))
)

fun textCanvasBrush(seedText: String?, patternId: String? = null): Brush {
    val pattern = textCanvasPattern(seedText, patternId)
    return Brush.linearGradient(
        colors = listOf(pattern.start, pattern.end),
        start = Offset.Zero,
        end = Offset.Infinite
    )
}

fun textCanvasPattern(seedText: String?, patternId: String? = null): TextCanvasPattern {
    patternId?.let { selectedId ->
        textCanvasPatterns.firstOrNull { it.id == selectedId }?.let { return it }
    }
    var hash = 0
    (seedText?.takeIf { it.isNotEmpty() } ?: "Q").forEach { ch ->
        hash = ((hash shl 5) - hash + ch.code)
    }
    return textCanvasPatterns[hash.toLong().absoluteValue.mod(textCanvasPatterns.size)]
}

fun textCanvasTypography(text: String, compact: Boolean = false): TextCanvasTypography {
    val length = text.length
    val base = when {
        text.isBlank() -> if (compact) 18.sp else 20.sp
        length <= 60 -> if (compact) 28.sp else 34.sp
        length <= 140 -> if (compact) 24.sp else 29.sp
        length <= 260 -> if (compact) 20.sp else 24.sp
        else -> if (compact) 17.sp else 20.sp
    }
    return TextCanvasTypography(
        fontSize = base,
        lineHeight = (base.value * 1.22f).sp,
        maxLines = if (compact) 8 else 10
    )
}
