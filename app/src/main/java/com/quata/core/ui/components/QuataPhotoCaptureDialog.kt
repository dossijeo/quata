package com.quata.core.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable

@Composable
fun QuataPhotoCaptureDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Uri, String, String) -> Unit
) {
    QuataCameraDialog(
        mode = QuataCameraMode.Photo,
        onDismiss = onDismiss,
        onPhotoCaptured = onPhotoCaptured
    )
}
