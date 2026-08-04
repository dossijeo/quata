package com.quata.designsystem.translation

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.quata.core.designsystem.theme.quataTheme

/** Shared backdrop renderer. Platforms may supply a captured surface or the Android texture. */
@Composable
fun QuataTranslatorBackdrop(
    background: QuataTranslatorBackground?,
    frostedTexture: Painter?,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    val hasCapturedBackdrop = background != null || frostedTexture != null
    Box(
        modifier = modifier.background(
            if (hasCapturedBackdrop) template.colors.background else Color(0xB852504D),
        ),
    ) {
        background?.let {
            Image(
                bitmap = it.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.86f },
                contentScale = ContentScale.FillBounds,
            )
        }
        frostedTexture?.let {
            Image(
                painter = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.70f },
                contentScale = ContentScale.Crop,
            )
        }
        if (!hasCapturedBackdrop) {
            PortableFrostedGlassTexture(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
    }
}

/**
 * Portable fallback for Wasm/iOS. Its translucent fibres keep the live common screen visible
 * while reproducing the grey, textured glass used by Android's frosted bitmap.
 */
@Composable
private fun PortableFrostedGlassTexture(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val fineStep = 5f
        var y = -size.width
        var row = 0
        while (y < size.height + size.width) {
            val light = if (row % 2 == 0) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.045f)
            drawLine(light, Offset(0f, y), Offset(size.width, y + size.width * 0.16f), strokeWidth = 1f)
            y += fineStep
            row += 1
        }
        var x = -size.height
        var column = 0
        while (x < size.width + size.height) {
            val shade = if (column % 3 == 0) Color.White.copy(alpha = 0.028f) else Color.Black.copy(alpha = 0.024f)
            drawLine(shade, Offset(x, 0f), Offset(x + size.height * 0.22f, size.height), strokeWidth = 1f)
            x += 7f
            column += 1
        }
    }
}
