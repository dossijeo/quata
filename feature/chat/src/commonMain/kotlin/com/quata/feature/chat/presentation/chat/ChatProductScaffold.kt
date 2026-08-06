package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.quataTheme
import com.quata.designsystem.chat.ProceduralChatBackgroundCanvas
import com.quata.designsystem.chat.proceduralChatBackgroundSpec

/**
 * Shared product root for `SCR-CHAT` background, scrim and overlay ordering.
 *
 * Android may provide its cached bitmap renderer as a platform optimization. When it does not,
 * every platform renders the same deterministic Compose background from commonMain.
 */
@Composable
fun ChatProductScaffold(
    conversationName: String?,
    modifier: Modifier = Modifier,
    renderedBackground: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val template = quataTheme()
    Box(modifier.fillMaxSize().background(template.colors.background)) {
        if (conversationName != null) {
            if (renderedBackground != null) {
                renderedBackground()
            } else {
                ProceduralChatBackgroundCanvas(
                    spec = proceduralChatBackgroundSpec(
                        conversationName = conversationName,
                        templateId = "${template.id}-clouds-v3",
                        paletteCount = template.colors.chatBackgroundPalettes.size,
                    ),
                    palettes = template.colors.chatBackgroundPalettes,
                )
            }
            Box(Modifier.fillMaxSize().background(template.colors.scrim))
        }
        content()
    }
}
