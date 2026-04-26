package com.quata.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QuataDarkColors = darkColorScheme(
    primary = QuataOrange,
    onPrimary = Color.Black,
    secondary = QuataOrangeSoft,
    background = QuataBackground,
    onBackground = QuataTextPrimary,
    surface = QuataSurface,
    onSurface = QuataTextPrimary,
    surfaceVariant = QuataSurfaceAlt,
    onSurfaceVariant = QuataTextSecondary,
    error = QuataError
)

@Composable
fun QuataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QuataDarkColors,
        typography = QuataTypography,
        content = content
    )
}
