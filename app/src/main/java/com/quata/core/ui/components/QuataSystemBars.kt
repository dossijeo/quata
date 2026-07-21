package com.quata.core.ui.components

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate

fun ComponentActivity.applyQuataSystemBars(template: QuataThemeTemplate) {
    val isLight = template.resolvedTheme == QuataResolvedTheme.Light
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = isLight
        isAppearanceLightNavigationBars = isLight
    }
}

fun ComponentActivity.applyQuataDarkSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}
