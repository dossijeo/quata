package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Structural action bar shown while a message is selected.
 *
 * The platform keeps localized icons, clipboard, moderation and navigation in slots; sizing,
 * alignment and the compact-window reservation are portable.
 */
@Composable
fun ChatSelectedMessageActionBarContent(
    compact: Boolean,
    navigationAction: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = chatHeaderSurfaceColor()
    Surface(
        color = surfaceColor,
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor),
    ) {
        Box(Modifier.fillMaxWidth().background(surfaceColor)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                navigationAction()
                Spacer(Modifier.weight(1f))
                actions()
                if (compact) Spacer(Modifier.width(120.dp))
            }
        }
    }
}
