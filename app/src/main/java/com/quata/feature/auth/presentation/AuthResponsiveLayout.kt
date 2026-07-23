package com.quata.feature.auth.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.quata.core.ui.components.QuataScreen

@Composable
internal fun AuthResponsiveLayout(
    padding: PaddingValues,
    subtitle: String,
    portraitLogoSpacing: Dp,
    content: @Composable ColumnScope.(isLandscape: Boolean) -> Unit
) {
    QuataScreen(padding) {
        AuthResponsiveContent(
            subtitle = subtitle,
            portraitLogoSpacing = portraitLogoSpacing,
            content = content,
        )
    }
}
