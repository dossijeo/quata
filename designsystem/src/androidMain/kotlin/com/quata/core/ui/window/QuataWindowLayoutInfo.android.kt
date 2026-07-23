package com.quata.core.ui.window

import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize

@Composable
actual fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var rootSize by remember(view) { mutableStateOf(IntSize.Zero) }
    DisposableEffect(view) {
        val rootView = view.rootView ?: view
        fun updateSize(target: View) {
            val size = IntSize(target.width, target.height)
            if (size.width > 0 && size.height > 0 && size != rootSize) rootSize = size
        }
        val listener = View.OnLayoutChangeListener { changedView, left, top, right, bottom, _, _, _, _ ->
            val size = IntSize(right - left, bottom - top)
            if (size.width > 0 && size.height > 0 && size != rootSize) rootSize = size else updateSize(changedView)
        }
        rootView.addOnLayoutChangeListener(listener)
        rootView.post { updateSize(rootView) }
        onDispose { rootView.removeOnLayoutChangeListener(listener) }
    }
    val fallbackLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val measured = rootSize.width > 0 && rootSize.height > 0
    val landscape = if (measured) rootSize.width > rootSize.height else fallbackLandscape
    val key = if (measured) "${rootSize.width}:${rootSize.height}" else "config:${configuration.orientation}:${configuration.screenWidthDp}:${configuration.screenHeightDp}"
    return remember(rootSize, landscape, key) { QuataWindowLayoutInfo(rootSize.width, rootSize.height, landscape, key) }
}
