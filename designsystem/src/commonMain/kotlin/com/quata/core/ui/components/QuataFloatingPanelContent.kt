package com.quata.core.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataFloatingPanelContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    template: QuataThemeTemplate = quataTheme(),
    isLandscape: Boolean = rememberQuataWindowLayoutInfo().isLandscape,
    portraitHeightFraction: Float = .92f,
    landscapeWidthFraction: Float = .76f,
    landscapeHeightFraction: Float = .97f,
    landscapeVerticalOffset: Dp = 0.dp,
    landscapePadding: PaddingValues = PaddingValues(horizontal = 72.dp, vertical = 10.dp),
    platformDecor: @Composable (fullscreen: Boolean) -> Unit = {},
    content: @Composable (panelModifier: Modifier, isLandscape: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.PartiallyExpanded })
    LaunchedEffect(isLandscape) { if (!isLandscape) sheetState.expand() }
    if (isLandscape) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)) {
            platformDecor(true)
            BoxWithConstraints(modifier.fillMaxSize().padding(landscapePadding), contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val width = if (maxHeight > maxWidth) landscapeHeightFraction else landscapeWidthFraction
                val height = if (maxHeight > maxWidth) landscapeWidthFraction else landscapeHeightFraction
                Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
                Surface(color = template.colors.surfaceRaised, contentColor = template.colors.textPrimary, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(width).fillMaxHeight(height).offset(y = landscapeVerticalOffset).border(1.dp, template.colors.divider.copy(alpha = .72f), RoundedCornerShape(28.dp)).clickable(interactionSource = interaction, indication = null, onClick = {})) {
                    content(Modifier.fillMaxSize(), true)
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = template.colors.surfaceRaised, contentColor = template.colors.textPrimary, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            platformDecor(false)
            Box(modifier.fillMaxWidth().fillMaxHeight(portraitHeightFraction)) {
                Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(template.colors.background))
                content(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), false)
            }
        }
    }
}
