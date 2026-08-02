package com.quata.web

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon
import com.quata.feature.postcomposer.imageeditor.AvatarImageEditorTransform
import com.quata.feature.postcomposer.imageeditor.MaximumAvatarEditorZoom

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
                        .pointerInput(sourceReference, transform.zoom) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // The preview is 280dp wide.  Normalized state is clamped by the
                                // common model and maps to the available overflow on export.
                                transform = transform.withPan(
                                    transform.panX + dragAmount.x / 140f,
                                    transform.panY + dragAmount.y / 140f,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BrowserCanvasImage(
                        url = sourceReference,
                        contentDescription = "Vista previa de la foto de perfil",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = transform.zoom
                                scaleY = transform.zoom
                                rotationZ = transform.quarterTurns * 90f
                                // Translation only has an effect when zoom exposes an overflow.
                                translationX = transform.panX * 140f * (transform.zoom - 1f)
                                translationY = transform.panY * 140f * (transform.zoom - 1f)
                            },
                    )
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
