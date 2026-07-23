package com.quata.designsystem.translation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.quata.core.designsystem.theme.quataTheme

/** Shared backdrop renderer. Platforms may supply a local frosted texture or omit it. */
@Composable
fun QuataTranslatorBackdrop(
    background: QuataTranslatorBackground?,
    frostedTexture: Painter?,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Box(modifier = modifier.background(template.colors.background)) {
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))
    }
}
