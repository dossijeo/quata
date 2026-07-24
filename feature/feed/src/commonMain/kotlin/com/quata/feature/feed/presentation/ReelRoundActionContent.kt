package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Portable circular reel action. The host supplies platform/resource-specific icon content. */
@Composable
fun ReelRoundActionContent(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Box(
        modifier = modifier
            .size(56.dp)
            .background(template.colors.surface.copy(alpha = 0.74f), CircleShape)
            .border(1.dp, template.colors.live, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
