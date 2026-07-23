package com.quata.core.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataFloatingPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    template: QuataThemeTemplate = quataTheme(),
    isLandscape: Boolean = rememberQuataWindowLayoutInfo().isLandscape,
    portraitHeightFraction: Float = 0.92f,
    landscapeWidthFraction: Float = 0.76f,
    landscapeHeightFraction: Float = 0.97f,
    landscapeVerticalOffset: Dp = 0.dp,
    landscapePadding: PaddingValues = PaddingValues(horizontal = 72.dp, vertical = 10.dp),
    content: @Composable (panelModifier: Modifier, isLandscape: Boolean) -> Unit
) {
    QuataFloatingPanelContent(onDismiss, modifier, template, isLandscape, portraitHeightFraction, landscapeWidthFraction, landscapeHeightFraction, landscapeVerticalOffset, landscapePadding, platformDecor = { fullscreen -> ConfigureQuataFloatingPanelSystemBars(template, fullscreen) }, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataStandardFloatingPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    template: QuataThemeTemplate = quataTheme(),
    content: @Composable (panelModifier: Modifier, isLandscape: Boolean) -> Unit
) {
    QuataFloatingPanel(
        onDismiss = onDismiss,
        modifier = modifier,
        template = template,
        landscapeHeightFraction = 0.86f,
        landscapeVerticalOffset = (-24).dp,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataFeedOverlayPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    template: QuataThemeTemplate = quataTheme(),
    content: @Composable (panelModifier: Modifier, isLandscape: Boolean) -> Unit
) {
    QuataStandardFloatingPanel(
        onDismiss = onDismiss,
        modifier = modifier,
        template = template,
        content = content
    )
}

@Composable
private fun ConfigureQuataFloatingPanelSystemBars(
    template: QuataThemeTemplate,
    fullscreen: Boolean = false
) {
    val view = LocalView.current
    DisposableEffect(view, template.id, fullscreen) {
        val window = view.findDialogWindow()
        if (window == null) {
            return@DisposableEffect onDispose {}
        }

        val originalContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val originalLightNavigationBars = controller.isAppearanceLightNavigationBars
        val originalAttributes = window.attributes
        val originalGravity = originalAttributes.gravity
        val originalX = originalAttributes.x
        val originalY = originalAttributes.y
        val originalWidth = originalAttributes.width
        val originalHeight = originalAttributes.height
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            originalAttributes.layoutInDisplayCutoutMode
        } else {
            null
        }

        fun applyFullscreenLayout() {
            val attributes = window.attributes
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT
            attributes.height = WindowManager.LayoutParams.MATCH_PARENT
            attributes.gravity = Gravity.TOP or Gravity.START
            attributes.x = 0
            attributes.y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.attributes = attributes
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.decorView.setPadding(0, 0, 0, 0)
        }

        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            applyFullscreenLayout()
            window.decorView.post { applyFullscreenLayout() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightNavigationBars = template.resolvedTheme == QuataResolvedTheme.Light

        onDispose {
            if (fullscreen) {
                val attributes = window.attributes
                attributes.gravity = originalGravity
                attributes.x = originalX
                attributes.y = originalY
                attributes.width = originalWidth
                attributes.height = originalHeight
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                }
                window.attributes = attributes
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = originalContrastEnforced
            }
            controller.isAppearanceLightNavigationBars = originalLightNavigationBars
        }
    }
}

private tailrec fun View.findDialogWindow(): Window? {
    val parentView = parent
    return when (parentView) {
        is DialogWindowProvider -> parentView.window
        is View -> parentView.findDialogWindow()
        else -> null
    }
}
