package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

interface IosChatAudioSeekAccessibilityFactory {
    fun create(
        action: IosChatAudioSeekAccessibilityAction,
        accessibilityIdentifier: String,
        accessibilityLabel: String,
        progress: Float,
    ): UIView

    fun update(view: UIView, accessibilityLabel: String, progress: Float)
}

interface IosChatAudioSeekAccessibilityAction {
    fun seekToFraction(fraction: Float)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosChatAudioSeekAccessibilitySlider(
    actions: ChatAudioAttachmentActions,
    factory: IosChatAudioSeekAccessibilityFactory,
) {
    val action = remember { IosChatAudioSeekAccessibilityActionAdapter() }
    action.onSeek = actions.seekToFraction
    val progress = if (actions.playback.durationMillis > 0L) {
        actions.playback.positionMillis.toFloat() / actions.playback.durationMillis.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val audioName = actions.file.displayName ?: actions.file.reference
    val accessibilityLabel = "${ChatAudioAttachmentProgressTestTag} $audioName"
    UIKitView(
        factory = {
            factory.create(
                action = action,
                accessibilityIdentifier = ChatAudioAttachmentProgressTestTag,
                accessibilityLabel = accessibilityLabel,
                progress = progress,
            )
        },
        update = { view ->
            action.onSeek = actions.seekToFraction
            factory.update(view, accessibilityLabel, progress)
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
        modifier = Modifier.fillMaxWidth().height(32.dp),
    )
}

private class IosChatAudioSeekAccessibilityActionAdapter : IosChatAudioSeekAccessibilityAction {
    var onSeek: (Float) -> Unit = {}

    override fun seekToFraction(fraction: Float) {
        onSeek(fraction.coerceIn(0f, 1f))
    }
}
