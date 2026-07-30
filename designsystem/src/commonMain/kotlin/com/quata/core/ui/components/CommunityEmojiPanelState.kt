package com.quata.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Shared hit-testing state: the launcher and picker are deliberately two distinct bounds. */
@Composable
fun rememberCommunityEmojiPanelDismissState(
    onDismissRequest: () -> Unit,
): CommunityEmojiPanelDismissState {
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    return remember { CommunityEmojiPanelDismissState { latestOnDismissRequest() } }
}

fun Modifier.dismissCommunityEmojiPanelOnOutsideTap(
    isVisible: Boolean,
    state: CommunityEmojiPanelDismissState,
): Modifier {
    if (!isVisible) return this
    return onGloballyPositioned { state.rootCoordinates = it }
        .pointerInput(isVisible, state.panelBounds, state.triggerBounds) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.dismissIfOutside(down.position)
            }
        }
}

fun Modifier.trackCommunityEmojiPanelBounds(state: CommunityEmojiPanelDismissState): Modifier =
    onGloballyPositioned { state.panelBounds = it.boundsInWindow() }

fun Modifier.trackCommunityEmojiTriggerBounds(state: CommunityEmojiPanelDismissState): Modifier =
    onGloballyPositioned { state.triggerBounds = it.boundsInWindow() }

@Stable
class CommunityEmojiPanelDismissState internal constructor(
    private val onDismissRequest: () -> Unit,
) {
    internal var rootCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var panelBounds: Rect? by mutableStateOf(null)
    internal var triggerBounds: Rect? by mutableStateOf(null)

    internal fun dismissIfOutside(positionInRoot: Offset) {
        val windowPosition = rootCoordinates?.localToWindow(positionInRoot) ?: positionInRoot
        if (panelBounds?.contains(windowPosition) != true && triggerBounds?.contains(windowPosition) != true) {
            onDismissRequest()
        }
    }
}
