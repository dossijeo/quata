package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared composer row geometry. The host injects text input and platform-bound camera/send
 * actions while this component keeps spacing and touch targets consistent across platforms.
 */
@Composable
fun ChatComposerInputRowContent(
    textInput: @Composable (Modifier) -> Unit,
    cameraAction: @Composable (Modifier) -> Unit,
    primaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 78.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        textInput(Modifier.weight(1f).requiredHeightIn(min = 62.dp))
        Spacer(Modifier.width(8.dp))
        cameraAction(Modifier)
        Spacer(Modifier.width(6.dp))
        primaryAction()
    }
}
