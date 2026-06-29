package com.quata.core.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable

@Composable
fun QuataVideoRecorderDialog(
    audioEnabled: Boolean,
    onDismiss: () -> Unit,
    onVideoCaptured: (Uri, String, String) -> Unit
) {
    QuataCameraDialog(
        mode = QuataCameraMode.Video,
        audioEnabled = audioEnabled,
        onDismiss = onDismiss,
        onVideoCaptured = onVideoCaptured
    )
}
