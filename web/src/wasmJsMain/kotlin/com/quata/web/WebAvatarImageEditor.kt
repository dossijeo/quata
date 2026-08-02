package com.quata.web

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon
import com.quata.feature.postcomposer.imageeditor.AvatarImageEditorTransform
import com.quata.feature.postcomposer.imageeditor.MaximumAvatarEditorZoom
import kotlin.math.roundToInt

/**
 * Compose/Wasm counterpart to Android's locked-avatar [QuataImageEditorDialog] flow.  Decode and
 * JPEG export are deliberately owned by the browser adapter, while all user-visible decisions are
 * made before `onConfirm` receives the common transform state.
 */
@Composable
internal fun WebAvatarImageEditor(
    sourceReference: String,
    initialTransform: AvatarImageEditorTransform,
    onDismiss: () -> Unit,
    onConfirm: (AvatarImageEditorTransform) -> Unit,
) {
    var transform by remember(sourceReference) { mutableStateOf(initialTransform) }
    var frameSidePx by remember(sourceReference) { mutableIntStateOf(0) }
    val imageState = rememberBrowserCanvasImage(sourceReference)
    val geometry = (imageState as? BrowserCanvasImageState.Ready)
        ?.takeIf { frameSidePx > 0 }
        ?.let { ready ->
            webProfileAvatarExportGeometry(
                sourceWidth = ready.bitmap.width,
                sourceHeight = ready.bitmap.height,
                transform = transform,
                outputSide = frameSidePx,
            )
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar foto de perfil") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Arrastra la foto, ajusta el zoom o gírala antes de guardar.")
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 12.dp)
                        .size(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { frameSidePx = minOf(it.width, it.height) }
                        .pointerInput(sourceReference, geometry) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // `pan * maxPan` is identical in the preview and the 1080 export.
                                // No axis may move when that axis has no cover overflow.
                                geometry?.let { current ->
                                    transform = webProfileAvatarPanAfterDrag(transform, current, dragAmount.x, dragAmount.y)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AvatarEditorCanvasPreview(imageState, geometry, transform)
                }
                Text("Zoom")
                Slider(
                    value = transform.zoom,
                    onValueChange = { transform = transform.withZoom(it) },
                    valueRange = 1f..MaximumAvatarEditorZoom,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { transform = transform.rotateClockwise() }) {
                        CompactIcon(Icons.Filled.Rotate90DegreesCw, null)
                        Text(" Girar")
                    }
                    OutlinedButton(onClick = { transform = AvatarImageEditorTransform.Default }) {
                        CompactIcon(Icons.Filled.Replay, null)
                        Text(" Restablecer")
                    }
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(onClick = { onConfirm(transform) }) {
                CompactIcon(Icons.Filled.Check, null)
                Text(" Guardar")
            }
        },
        modifier = Modifier.heightIn(max = 700.dp),
    )
}

@Composable
private fun AvatarEditorCanvasPreview(
    imageState: BrowserCanvasImageState,
    geometry: WebProfileAvatarExportGeometry?,
    transform: AvatarImageEditorTransform,
) {
    Canvas(Modifier.fillMaxSize()) {
        val ready = imageState as? BrowserCanvasImageState.Ready ?: return@Canvas
        val current = geometry ?: return@Canvas
        withTransform({
            translate(
                left = size.width / 2f + transform.panX * current.maxPanX,
                top = size.height / 2f + transform.panY * current.maxPanY,
            )
            rotate(degrees = transform.quarterTurns * 90f)
        }) {
            drawImage(
                image = ready.bitmap,
                dstOffset = IntOffset(-current.sourceDrawnWidth.roundToInt() / 2, -current.sourceDrawnHeight.roundToInt() / 2),
                dstSize = IntSize(current.sourceDrawnWidth.roundToInt(), current.sourceDrawnHeight.roundToInt()),
            )
        }
    }
}
