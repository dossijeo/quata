package com.quata.core.ui.window

import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize

@Immutable
data class QuataWindowLayoutInfo(
    val widthPx: Int,
    val heightPx: Int,
    val isLandscape: Boolean,
    val viewportKey: String
)

@Composable
fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var rootSize by remember(view) { mutableStateOf(IntSize.Zero) }

    DisposableEffect(view) {
        val rootView = view.rootView ?: view

        fun updateSize(target: View) {
            val width = target.width
            val height = target.height
            if (width > 0 && height > 0) {
                val next = IntSize(width, height)
                if (next != rootSize) {
                    rootSize = next
                }
            }
        }

        val listener = View.OnLayoutChangeListener { changedView, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0) {
                val next = IntSize(width, height)
                if (next != rootSize) {
                    rootSize = next
                }
            } else {
                updateSize(changedView)
            }
        }

        rootView.addOnLayoutChangeListener(listener)
        rootView.post { updateSize(rootView) }

        onDispose {
            rootView.removeOnLayoutChangeListener(listener)
        }
    }

    val fallbackIsLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hasMeasuredSize = rootSize.width > 0 && rootSize.height > 0
    val isLandscape = if (hasMeasuredSize) {
        rootSize.width > rootSize.height
    } else {
        fallbackIsLandscape
    }
    val viewportKey = if (hasMeasuredSize) {
        "${rootSize.width}:${rootSize.height}"
    } else {
        "config:${configuration.orientation}:${configuration.screenWidthDp}:${configuration.screenHeightDp}"
    }
    return remember(rootSize, isLandscape, viewportKey) {
        QuataWindowLayoutInfo(
            widthPx = rootSize.width,
            heightPx = rootSize.height,
            isLandscape = isLandscape,
            viewportKey = viewportKey
        )
    }
}
