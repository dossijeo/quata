package com.quata.designsystem.chat

import androidx.compose.ui.graphics.ImageBitmap
import com.quata.core.designsystem.theme.QuataChatBackgroundPalette

/** Deterministic, platform-neutral input for rendering a conversation background. */
data class ProceduralChatBackgroundSpec(
    val seedText: String,
    val seed: Long,
    val cacheKey: String,
    val paletteIndex: Int,
)

/** Platform boundary for rendering a shared procedural background specification. */
interface ProceduralChatBackgroundRenderer {
    suspend fun render(
        spec: ProceduralChatBackgroundSpec,
        palettes: List<QuataChatBackgroundPalette>,
        width: Int,
        height: Int,
    ): ImageBitmap?
}

fun proceduralChatBackgroundSpec(
    conversationName: String,
    templateId: String,
    paletteCount: Int,
): ProceduralChatBackgroundSpec {
    val safeName = conversationName.ifBlank { "quata" }
    val seed = fnv1a32(safeName)
    val cacheHash = fnv1a32("$templateId:$safeName")
    return ProceduralChatBackgroundSpec(
        seedText = safeName,
        seed = seed,
        cacheKey = cacheHash.toString(),
        paletteIndex = if (paletteCount > 0) (seed % paletteCount).toInt() else 0,
    )
}

/** Stable unsigned 32-bit FNV-1a hash represented in a Long for common Kotlin targets. */
fun fnv1a32(value: String): Long {
    var hash = 0x811c9dc5L
    value.forEach { char ->
        hash = hash xor char.code.toLong()
        hash = (hash + (hash shl 1) + (hash shl 4) + (hash shl 7) + (hash shl 8) + (hash shl 24)) and 0xffffffffL
    }
    return hash
}
