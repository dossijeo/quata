package com.quata.core.ui.components

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate

fun ComponentActivity.applyQuataSystemBars(template: QuataThemeTemplate) {
    val isLight = template.resolvedTheme == QuataResolvedTheme.Light
    val background = template.colors.background.toArgb()
    val statusStyle = if (isLight) {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    } else {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    }
    val navigationStyle = if (isLight) {
        SystemBarStyle.light(background, AndroidColor.BLACK)
    } else {
        SystemBarStyle.dark(background)
    }
    enableEdgeToEdge(
        statusBarStyle = statusStyle,
        navigationBarStyle = navigationStyle
    )
}

fun ComponentActivity.applyQuataDarkSystemBars() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
    )
}
