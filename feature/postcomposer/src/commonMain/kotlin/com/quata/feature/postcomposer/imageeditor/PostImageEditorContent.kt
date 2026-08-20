package com.quata.feature.postcomposer.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon

data class PostImageEditorStrings(
    val title: String = "Editar imagen",
    val helper: String = "Arrastra la imagen, ajusta el zoom o gírala antes de guardar.",
    val zoom: String = "Zoom",
    val rotate: String = "Girar",
    val reset: String = "Restablecer",
    val cancel: String = "Cancelar",
    val save: String = "Guardar",
)

@Composable
fun PostImageEditorDialogContent(
    transform: PostImageEditorTransform,
    geometry: PostImageEditorGeometry?,
    outputSpec: ImageEditorOutputSpec,
    onTransformChange: (PostImageEditorTransform) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    strings: PostImageEditorStrings = PostImageEditorStrings(),
    preview: @Composable (PostImageEditorTransform, PostImageEditorGeometry?, Modifier) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.helper)
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 340.dp)
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag(PostImageEditorPreviewTestTag)
                        .pointerInput(geometry) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                geometry?.let { current ->
                                    onTransformChange(postImageEditorPanAfterDrag(transform, current, dragAmount.x, dragAmount.y))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    preview(transform, geometry, Modifier.fillMaxSize())
                }
                Text(strings.zoom)
                Slider(
                    value = transform.zoom,
                    onValueChange = { onTransformChange(transform.withZoom(it)) },
                    valueRange = MinimumPostImageEditorZoom..MaximumPostImageEditorZoom,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onTransformChange(transform.rotateClockwise()) },
                        modifier = Modifier.testTag(PostImageEditorRotateTestTag),
                    ) {
                        CompactIcon(Icons.Filled.Rotate90DegreesCw, null)
                        Text(" ${strings.rotate}")
                    }
                    OutlinedButton(
                        onClick = { onTransformChange(PostImageEditorTransform.Default) },
                        modifier = Modifier.testTag(PostImageEditorResetTestTag),
                    ) {
                        CompactIcon(Icons.Filled.Replay, null)
                        Text(" ${strings.reset}")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag(PostImageEditorCancelTestTag)
                    .semantics { contentDescription = strings.cancel },
            ) {
                Text(strings.cancel)
            }
        },
        confirmButton = {
            Button(onClick = onSave, modifier = Modifier.testTag(PostImageEditorSaveTestTag)) {
                CompactIcon(Icons.Filled.Check, null)
                Text(" ${strings.save}")
            }
        },
        modifier = modifier
            .heightIn(max = 780.dp)
            .testTag(PostImageEditorRootTestTag),
    )
}
