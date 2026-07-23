package com.quata.designsystem.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

data class QuataTranslatableTextBox(
    val id: String,
    val text: String,
    val displayText: String,
    val bounds: Rect,
)

enum class QuataTranslatorOverlaySource { Chat, Comments }

@Stable
class QuataTranslatableTextRegistry {
    private val boxes = mutableStateMapOf<String, QuataTranslatableTextBox>()

    val visibleBoxes: List<QuataTranslatableTextBox>
        get() = boxes.values
            .filter { it.text.isNotBlank() && it.bounds.width > 8f && it.bounds.height > 8f }
            .sortedWith(compareBy<QuataTranslatableTextBox> { it.bounds.top }.thenBy { it.bounds.left })

    fun update(id: String, text: String, displayText: String, bounds: Rect) {
        if (text.isBlank()) boxes.remove(id) else boxes[id] = QuataTranslatableTextBox(id, text, displayText, bounds)
    }

    fun unregister(id: String) {
        boxes.remove(id)
    }
}

val LocalQuataTranslatableTextRegistry = compositionLocalOf<QuataTranslatableTextRegistry?> { null }

fun Modifier.quataTranslatableText(id: String, text: String, displayText: String = text): Modifier = composed {
    val registry = LocalQuataTranslatableTextRegistry.current
    DisposableEffect(registry, id) { onDispose { registry?.unregister(id) } }
    if (registry == null) this else onGloballyPositioned { coordinates ->
        registry.update(id, text, displayText, coordinates.boundsInWindow())
    }
}
