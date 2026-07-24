package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.platform.DocumentPreviewDescriptor

/** Portable render state; a platform renderer owns bitmap/PDF/text generation. */
sealed interface DocumentPreviewRenderState {
    data object Loading : DocumentPreviewRenderState
    data object Ready : DocumentPreviewRenderState
    data object Unavailable : DocumentPreviewRenderState
}

/**
 * Shared document-preview frame. It owns stable document-preview geometry and state selection,
 * while bitmap/text/PDF rendering and its lifecycle remain injected platform concerns.
 */
@Composable
fun DocumentPreviewFrameContent(
    descriptor: DocumentPreviewDescriptor,
    renderState: DocumentPreviewRenderState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    preview: @Composable () -> Unit,
    placeholder: @Composable (DocumentPreviewRenderState) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (renderState == DocumentPreviewRenderState.Ready && descriptor.isPreviewable) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { preview() }
        } else {
            placeholder(renderState)
        }
    }
}
