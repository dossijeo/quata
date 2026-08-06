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
import org.jetbrains.compose.resources.painterResource
import quata.designsystem.generated.resources.Res
import quata.designsystem.generated.resources.quata_translator_frosted_texture

/** Shared backdrop renderer. Platforms may supply a captured surface or the Android texture. */
@Composable
fun QuataTranslatorBackdrop(
    background: QuataTranslatorBackground?,
    frostedTexture: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    val sharedFrostedTexture = frostedTexture ?: painterResource(Res.drawable.quata_translator_frosted_texture)
    Box(
        modifier = modifier.background(
            if (background != null) template.colors.background else Color(0x6652504D),
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
        Image(
            painter = sharedFrostedTexture,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.70f },
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
    }
}
