package com.quata.core.ui.components

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate

fun ComponentActivity.applyQuataSystemBars(template: QuataThemeTemplate) {
    val isLight = template.resolvedTheme == QuataResolvedTheme.Light
    WindowCompat.getInsetsController(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.navigationBars())
        isAppearanceLightStatusBars = isLight
        // API 26-28 has no navigation-bar contrast enforcement. With an
        // edge-to-edge window its navigation surface remains black, so asking
        // for dark ("light navigation bar") icons makes the 3 buttons invisible.
        isAppearanceLightNavigationBars = isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

fun ComponentActivity.applyQuataDarkSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.navigationBars())
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}
