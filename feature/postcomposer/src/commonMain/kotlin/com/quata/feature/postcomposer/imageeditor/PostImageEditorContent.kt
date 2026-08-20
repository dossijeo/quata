package com.quata.feature.postcomposer.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.components.CompactIcon

data class PostImageEditorStrings(
    val title: String = "Editar imagen",
    val helper: String = "Arrastra la imagen, ajusta el zoom o gírala antes de guardar.",
    val zoom: String = "Zoom",
    val crop: String = "Recortar",
    val cropDone: String = "Aplicar",
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
    onSave: (cropToOutputAspect: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cropLocked: Boolean = false,
    strings: PostImageEditorStrings = PostImageEditorStrings(),
    preview: @Composable (PostImageEditorTransform, PostImageEditorGeometry?, Boolean, Boolean, Modifier) -> Unit,
) {
    var cropPanelOpen by remember(outputSpec, cropLocked) { mutableStateOf(cropLocked) }
    var cropApplied by remember(outputSpec, cropLocked) { mutableStateOf(cropLocked) }
    val cropToOutputAspect = cropLocked || cropPanelOpen || cropApplied
    val previewModifier = Modifier
        .padding(top = 12.dp, bottom = 12.dp)
        .fillMaxWidth()
        .heightIn(min = 240.dp, max = 340.dp)
        .widthIn(max = 360.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .testTag(PostImageEditorPreviewTestTag)
        .pointerInput(geometry, cropPanelOpen) {
            detectDragGestures { change, dragAmount ->
                if (cropToOutputAspect) {
                    change.consume()
                    geometry?.let { current ->
                        onTransformChange(postImageEditorPanAfterDrag(transform, current, dragAmount.x, dragAmount.y))
                    }
                }
            }
        }

    fun reset() {
        onTransformChange(PostImageEditorTransform.Default)
        cropPanelOpen = cropLocked
        cropApplied = cropLocked
    }

    fun rotate() {
        onTransformChange(PostImageEditorTransform.Default.copy(quarterTurns = (transform.quarterTurns + 1) % 4))
        cropPanelOpen = cropLocked
        cropApplied = cropLocked
    }

    fun save() {
        val shouldCrop = cropLocked || cropPanelOpen || cropApplied
        if (cropPanelOpen) {
            cropPanelOpen = cropLocked
            cropApplied = shouldCrop
        }
        onSave(shouldCrop)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.title) },
        text = {
            BoxWithConstraints {
                val landscape = maxWidth > 560.dp
                if (landscape && cropPanelOpen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PostImageEditorPreviewFrame(
                            transform = transform,
                            geometry = geometry,
                            cropPanelOpen = cropPanelOpen,
                            cropApplied = cropApplied,
                            previewModifier = previewModifier.weight(1f),
                            preview = preview,
                        )
                        PostImageEditorControls(
                            transform = transform,
                            cropLocked = cropLocked,
                            cropPanelOpen = cropPanelOpen,
                            strings = strings,
                            onZoomChange = { onTransformChange(transform.withZoom(it)) },
                            onCrop = {
                                cropApplied = true
                                cropPanelOpen = false
                            },
                            onRotate = ::rotate,
                            onReset = ::reset,
                            modifier = Modifier.width(260.dp),
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.helper)
                        PostImageEditorPreviewFrame(
                            transform = transform,
                            geometry = geometry,
                            cropPanelOpen = cropPanelOpen,
                            cropApplied = cropApplied,
                            previewModifier = previewModifier,
                            preview = preview,
                        )
                        PostImageEditorControls(
                            transform = transform,
                            cropLocked = cropLocked,
                            cropPanelOpen = cropPanelOpen,
                            strings = strings,
                            onZoomChange = { onTransformChange(transform.withZoom(it)) },
                            onCrop = {
                                if (cropPanelOpen) {
                                    cropApplied = true
                                    cropPanelOpen = false
                                } else {
                                    cropPanelOpen = true
                                }
                            },
                            onRotate = ::rotate,
                            onReset = ::reset,
                        )
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
            Button(onClick = ::save, modifier = Modifier.testTag(PostImageEditorSaveTestTag)) {
                CompactIcon(Icons.Filled.Check, null)
                Text(" ${strings.save}")
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 920.dp)
            .heightIn(max = 780.dp)
            .testTag(PostImageEditorRootTestTag),
    )
}

@Composable
private fun PostImageEditorPreviewFrame(
    transform: PostImageEditorTransform,
    geometry: PostImageEditorGeometry?,
    cropPanelOpen: Boolean,
    cropApplied: Boolean,
    previewModifier: Modifier,
    preview: @Composable (PostImageEditorTransform, PostImageEditorGeometry?, Boolean, Boolean, Modifier) -> Unit,
) {
    Box(modifier = previewModifier, contentAlignment = Alignment.Center) {
        preview(transform, geometry, cropPanelOpen, cropApplied, Modifier.fillMaxSize())
        if (cropPanelOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(8.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            )
        }
    }
}

@Composable
private fun PostImageEditorControls(
    transform: PostImageEditorTransform,
    cropLocked: Boolean,
    cropPanelOpen: Boolean,
    strings: PostImageEditorStrings,
    onZoomChange: (Float) -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (cropPanelOpen) {
            Text(strings.zoom)
            Slider(
                value = transform.zoom,
                onValueChange = onZoomChange,
                valueRange = MinimumPostImageEditorZoom..MaximumPostImageEditorZoom,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!cropLocked) {
                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.testTag(PostImageEditorCropTestTag),
                ) {
                    CompactIcon(if (cropPanelOpen) Icons.Filled.Check else Icons.Filled.Crop, null)
                    Text(" ${if (cropPanelOpen) strings.cropDone else strings.crop}")
                }
            }
            OutlinedButton(
                onClick = onRotate,
                modifier = Modifier.testTag(PostImageEditorRotateTestTag),
            ) {
                CompactIcon(Icons.Filled.Rotate90DegreesCw, null)
                Text(" ${strings.rotate}")
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.testTag(PostImageEditorResetTestTag),
            ) {
                CompactIcon(Icons.Filled.Replay, null)
                Text(" ${strings.reset}")
            }
        }
    }
}
