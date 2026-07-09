package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo

@Composable
fun QuataScreen(
    padding: PaddingValues = PaddingValues(),
    applyLandscapeSafeDrawing: Boolean = true,
    content: @Composable () -> Unit
) {
    val template = quataTheme()
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    val safeDrawingModifier = if (applyLandscapeSafeDrawing && isLandscape) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
            )
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(template.colors.background)
            .padding(padding)
            .then(safeDrawingModifier)
    ) {
        content()
    }
}
