package com.quata.feature.postcomposer.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.designsystem.theme.quataTheme
import kotlin.math.roundToLong

data class PostVideoEditorStrings(
    val title: String = "Editar video",
    val helper: String = "Revisa el video, ajusta el tramo y guarda una copia editada.",
    val trimStart: String = "Inicio",
    val trimEnd: String = "Fin",
    val mute: String = "Silenciar",
    val unmute: String = "Activar sonido",
    val play: String = "Reproducir",
    val pause: String = "Pausar",
    val crop: String = "Recorte",
    val cropDone: String = "Aplicar",
    val captions: String = "Subtitulos",
    val captionsDone: String = "Aplicar",
    val reset: String = "Restablecer",
    val cancel: String = "Cancelar",
    val export: String = "Guardar",
    val exporting: String = "Exportando...",
    val currentTime: String = "Actual",
    val selectedDuration: String = "Duracion",
    val maxDurationWarning: String = "El video supera la duracion maxima. Ajusta el tramo antes de guardar.",
    val cropOriginal: String = "Original",
    val cropSquare: String = "1:1",
    val cropFourFive: String = "4:5",
    val cropPortrait: String = "9:16",
    val cropLandscape: String = "16:9",
    val cropZoom: String = "Zoom",
    val captionsNone: String = "Sin subtitulos",
)

data class CaptionStyleOption(
    val id: String,
    val label: String,
)

data class PostVideoEditorUiState(
    val isMuted: Boolean = false,
    val isPlaying: Boolean = false,
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    val currentPositionFraction: Float = 0f,
    val cropEnabled: Boolean = false,
    val captionsEnabled: Boolean = false,
    val cropPanelOpen: Boolean = false,
    val captionsPanelOpen: Boolean = false,
    val cropMode: VideoCropMode = VideoCropMode.Original,
    val cropZoom: Float = 1f,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.5f,
    val selectedCaptionStyleId: String? = null,
    val currentPositionLabel: String? = null,
    val selectedDurationLabel: String? = null,
    val showMaxDurationWarning: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
)

@Composable
fun PostVideoEditorDialogContent(
    state: PostVideoEditorUiState,
    onMutedChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onCropToggle: () -> Unit,
    onCaptionsToggle: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    strings: PostVideoEditorStrings = PostVideoEditorStrings(),
    isLandscapeLayout: Boolean = false,
    captionOptions: List<CaptionStyleOption> = emptyList(),
    onCropModeChange: (VideoCropMode) -> Unit = {},
    onCropZoomChange: (Float) -> Unit = {},
    onCropPanChange: (Float, Float) -> Unit = { _, _ -> },
    onCaptionStyleChange: (String?) -> Unit = {},
    onSeekChange: (Float) -> Unit = {},
    timelineFrameCount: Int = 0,
    timelineFrameContent: @Composable (Int, Modifier) -> Unit = { _, frameModifier -> CommonTimelineFramePlaceholder(frameModifier) },
    preview: @Composable (Modifier) -> Unit,
) {
    val content: @Composable () -> Unit = {
        PostVideoEditorBody(
            state = state,
            strings = strings,
            isLandscapeLayout = isLandscapeLayout,
            captionOptions = captionOptions,
            onMutedChange = onMutedChange,
            onPlayPause = onPlayPause,
            onCropToggle = onCropToggle,
            onCaptionsToggle = onCaptionsToggle,
            onReset = onReset,
            onCropModeChange = onCropModeChange,
            onCropZoomChange = onCropZoomChange,
            onCropPanChange = onCropPanChange,
            onCaptionStyleChange = onCaptionStyleChange,
            onTrimStartChange = onTrimStartChange,
            onTrimEndChange = onTrimEndChange,
            onSeekChange = onSeekChange,
            timelineFrameCount = timelineFrameCount,
            timelineFrameContent = timelineFrameContent,
            preview = preview,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.title) },
        text = content,
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = strings.cancel },
            ) {
                Text(strings.cancel)
            }
        },
        confirmButton = {
            Button(
                enabled = !state.isExporting,
                onClick = onExport,
                modifier = Modifier.testTag(PostVideoEditorExportTestTag),
            ) {
                CompactIcon(Icons.Filled.Save, null)
                Text(" ${strings.export}")
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .fillMaxWidth(0.96f)
            .widthIn(max = if (isLandscapeLayout) 1040.dp else 620.dp)
            .heightIn(max = 860.dp)
            .testTag(PostVideoEditorRootTestTag),
    )
}

@Composable
private fun PostVideoEditorBody(
    state: PostVideoEditorUiState,
    strings: PostVideoEditorStrings,
    isLandscapeLayout: Boolean,
    captionOptions: List<CaptionStyleOption>,
    onMutedChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onCropToggle: () -> Unit,
    onCaptionsToggle: () -> Unit,
    onReset: () -> Unit,
    onCropModeChange: (VideoCropMode) -> Unit,
    onCropZoomChange: (Float) -> Unit,
    onCropPanChange: (Float, Float) -> Unit,
    onCaptionStyleChange: (String?) -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onSeekChange: (Float) -> Unit,
    timelineFrameCount: Int,
    timelineFrameContent: @Composable (Int, Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    val previewModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 220.dp, max = if (isLandscapeLayout) 520.dp else 360.dp)
        .widthIn(max = if (isLandscapeLayout) 620.dp else 420.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .testTag(PostVideoEditorPreviewTestTag)
        .semantics { contentDescription = PostVideoEditorPreviewTestTag }
        .then(
            if (state.cropPanelOpen && state.cropMode != VideoCropMode.Original && !state.isExporting) {
                Modifier.pointerInput(state.cropMode, state.cropZoom, state.cropCenterX, state.cropCenterY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        onCropPanChange(dragAmount.x / width, dragAmount.y / height)
                    }
                }
            } else {
                Modifier
            }
        )
    if (isLandscapeLayout) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = previewModifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                preview(Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier
                    .width(330.dp)
                    .fillMaxHeight()
                    .heightIn(max = 660.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PostVideoEditorControls(
                    state = state,
                    strings = strings,
                    captionOptions = captionOptions,
                    onMutedChange = onMutedChange,
                    onPlayPause = onPlayPause,
                    onCropToggle = onCropToggle,
                    onCaptionsToggle = onCaptionsToggle,
                    onReset = onReset,
                    onCropModeChange = onCropModeChange,
                    onCropZoomChange = onCropZoomChange,
                    onCropPanChange = onCropPanChange,
                    onCaptionStyleChange = onCaptionStyleChange,
                    onTrimStartChange = onTrimStartChange,
                    onTrimEndChange = onTrimEndChange,
                    onSeekChange = onSeekChange,
                    timelineFrameCount = timelineFrameCount,
                    timelineFrameContent = timelineFrameContent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.helper)
            Box(
                modifier = previewModifier,
                contentAlignment = Alignment.Center,
            ) {
                preview(Modifier.fillMaxSize())
            }
            PostVideoEditorControls(
                state = state,
                strings = strings,
                captionOptions = captionOptions,
                onMutedChange = onMutedChange,
                onPlayPause = onPlayPause,
                onCropToggle = onCropToggle,
                onCaptionsToggle = onCaptionsToggle,
                onReset = onReset,
                onCropModeChange = onCropModeChange,
                onCropZoomChange = onCropZoomChange,
                onCropPanChange = onCropPanChange,
                onCaptionStyleChange = onCaptionStyleChange,
                onTrimStartChange = onTrimStartChange,
                onTrimEndChange = onTrimEndChange,
                onSeekChange = onSeekChange,
                timelineFrameCount = timelineFrameCount,
                timelineFrameContent = timelineFrameContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PostVideoEditorControls(
    state: PostVideoEditorUiState,
    strings: PostVideoEditorStrings,
    captionOptions: List<CaptionStyleOption>,
    onMutedChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onCropToggle: () -> Unit,
    onCaptionsToggle: () -> Unit,
    onReset: () -> Unit,
    onCropModeChange: (VideoCropMode) -> Unit,
    onCropZoomChange: (Float) -> Unit,
    onCropPanChange: (Float, Float) -> Unit,
    onCaptionStyleChange: (String?) -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onSeekChange: (Float) -> Unit,
    timelineFrameCount: Int,
    timelineFrameContent: @Composable (Int, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.showMaxDurationWarning) {
            Text(strings.maxDurationWarning, color = MaterialTheme.colorScheme.error)
        }
        PostVideoEditorTimelineContent(
            state = state,
            onTrimStartChange = onTrimStartChange,
            onTrimEndChange = onTrimEndChange,
            onSeekChange = onSeekChange,
            frameCount = timelineFrameCount,
            frameContent = timelineFrameContent,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .testTag(PostVideoEditorTimelineTestTag),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onPlayPause,
                enabled = !state.isExporting,
                modifier = Modifier.testTag(PostVideoEditorPlayPauseTestTag),
            ) {
                CompactIcon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                Text(" ${if (state.isPlaying) strings.pause else strings.play}")
            }
            OutlinedButton(
                onClick = { onMutedChange(!state.isMuted) },
                enabled = !state.isExporting,
                modifier = Modifier.testTag(PostVideoEditorMuteTestTag),
            ) {
                CompactIcon(if (state.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, null)
                Text(" ${if (state.isMuted) strings.unmute else strings.mute}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCropToggle,
                enabled = !state.isExporting,
                modifier = Modifier.testTag(PostVideoEditorCropTestTag),
            ) {
                CompactIcon(if (state.cropPanelOpen || state.cropEnabled) Icons.Filled.Check else Icons.Filled.Crop, null)
                Text(" ${if (state.cropPanelOpen) strings.cropDone else strings.crop}")
            }
            OutlinedButton(
                onClick = onCaptionsToggle,
                enabled = !state.isExporting,
                modifier = Modifier.testTag(PostVideoEditorCaptionsTestTag),
            ) {
                CompactIcon(if (state.captionsPanelOpen || state.captionsEnabled) Icons.Filled.Check else Icons.Filled.Subtitles, null)
                Text(" ${if (state.captionsPanelOpen) strings.captionsDone else strings.captions}")
            }
            OutlinedButton(onClick = onReset, enabled = !state.isExporting) {
                CompactIcon(Icons.Filled.Replay, null)
                Text(" ${strings.reset}")
            }
        }
        if (state.cropPanelOpen && !state.isExporting) {
            CommonCropControls(
                mode = state.cropMode,
                zoom = state.cropZoom,
                strings = strings,
                onModeChange = onCropModeChange,
                onZoomChange = onCropZoomChange,
            )
        }
        if (state.captionsPanelOpen && !state.isExporting) {
            CommonCaptionControls(
                options = captionOptions,
                selectedId = state.selectedCaptionStyleId,
                strings = strings,
                onStyleChange = onCaptionStyleChange,
            )
        }
        PostVideoEditorInfoBar(state, strings, onPlayPause)
    }
}

@Composable
private fun CommonCropControls(
    mode: VideoCropMode,
    zoom: Float,
    strings: PostVideoEditorStrings,
    onModeChange: (VideoCropMode) -> Unit,
    onZoomChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoCropMode.entries.forEach { option ->
                val selected = option == mode
                val label = strings.labelFor(option)
                val shape = RoundedCornerShape(9.dp)
                if (selected) {
                    Button(onClick = { onModeChange(option) }, shape = shape, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        CompactIcon(Icons.Filled.AspectRatio, null)
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onModeChange(option) }, shape = shape, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        if (mode != VideoCropMode.Original) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.cropZoom, modifier = Modifier.width(64.dp))
                Slider(
                    value = zoom.coerceIn(1f, 3f),
                    onValueChange = { onZoomChange(it.coerceIn(1f, 3f)) },
                    valueRange = 1f..3f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CommonCaptionControls(
    options: List<CaptionStyleOption>,
    selectedId: String?,
    strings: PostVideoEditorStrings,
    onStyleChange: (String?) -> Unit,
) {
    val values = listOf(CaptionStyleOption("", strings.captionsNone)) + options
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { option ->
                    val id = option.id.ifBlank { null }
                    val styleTag = "post-video-editor.caption-style.${id ?: "none"}"
                    val selectedStyleTag = "post-video-editor.caption-style-selected.${id ?: "none"}"
                    val selected = id == selectedId
                    val itemModifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag(styleTag)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = styleTag
                            this.selected = selected
                            onClick(label = styleTag) {
                                onStyleChange(id)
                                true
                            }
                        }
                    Surface(
                        onClick = { onStyleChange(id) },
                        modifier = itemModifier,
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (selected) null else ButtonDefaults.outlinedButtonBorder(enabled = true),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                if (selected) {
                                    CompactIcon(Icons.Filled.Subtitles, null)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    option.label,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (selected) {
                                        Modifier
                                            .testTag(selectedStyleTag)
                                            .semantics { contentDescription = selectedStyleTag }
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PostVideoEditorInfoBar(
    state: PostVideoEditorUiState,
    strings: PostVideoEditorStrings,
    onPlayPause: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (state.isExporting) {
            LinearProgressIndicator(
                progress = { state.exportProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PostVideoEditorExportProgressTestTag),
            )
            Text("${strings.exporting} ${(state.exportProgress * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(
                it,
                modifier = Modifier
                    .testTag(PostVideoEditorErrorTestTag)
                    .semantics { contentDescription = PostVideoEditorErrorTestTag },
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeReadout(strings.currentTime, state.currentPositionLabel ?: "--:--", Modifier.weight(1f), Alignment.Start)
            if (state.isExporting) {
                Spacer(Modifier.size(44.dp))
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
                ) {
                    CompactIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(PostVideoEditorPlayPauseTestTag),
                    ) {
                        CompactIcon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                    }
                }
            }
            TimeReadout(strings.selectedDuration, state.selectedDurationLabel ?: "--:--", Modifier.weight(1f), Alignment.End)
        }
    }
}

@Composable
private fun TimeReadout(label: String, value: String, modifier: Modifier, alignment: Alignment.Horizontal) {
    Column(modifier = modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun PostVideoEditorTimelineContent(
    state: PostVideoEditorUiState,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onSeekChange: (Float) -> Unit,
    frameCount: Int,
    frameContent: @Composable (Int, Modifier) -> Unit,
    modifier: Modifier,
) {
    val template = quataTheme()
    val trimStart = state.trimStartFraction.coerceIn(0f, 0.95f)
    val trimEnd = state.trimEndFraction.coerceIn((trimStart + 0.05f).coerceAtMost(1f), 1f)
    val currentPosition = state.currentPositionFraction.coerceIn(trimStart, trimEnd)
    val handleWidth = 30.dp
    val handleHitWidth = 64.dp
    val handleHitPadding = (handleHitWidth - handleWidth) / 2f
    val currentTrimStart by rememberUpdatedState(trimStart)
    val currentTrimEnd by rememberUpdatedState(trimEnd)
    val baseModifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(template.colors.surfaceAlt)
        .testTag(PostVideoEditorTimelineTestTag)
    val interactiveModifier = if (state.isExporting) {
        baseModifier
    } else {
        baseModifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val width = size.width.toFloat().coerceAtLeast(1f)
                onSeekChange((offset.x / width).coerceIn(0f, 1f))
            }
        }
    }

    BoxWithConstraints(modifier = interactiveModifier) {
        val timelineWidthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }
        val handleWidthPx = with(LocalDensity.current) { handleWidth.toPx() }
        val frameSlots = frameCount.coerceAtLeast(6)
        fun playheadX(widthPx: Float): Float {
            val startX = trimStart * widthPx + handleWidthPx
            val endX = (trimEnd * widthPx - handleWidthPx).coerceAtLeast(startX)
            val selectedFraction = ((currentPosition - trimStart) / (trimEnd - trimStart).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
            return startX + (endX - startX) * selectedFraction
        }

        fun Modifier.handleDrag(startHandle: Boolean): Modifier =
            if (state.isExporting) {
                this
            } else {
                pointerInput(timelineWidthPx, startHandle) {
                    var initialStart = 0f
                    var initialEnd = 1f
                    var accumulatedDeltaX = 0f
                    fun updateTrimFromDelta() {
                        val delta = accumulatedDeltaX / timelineWidthPx
                        if (startHandle) {
                            onTrimStartChange((initialStart + delta).coerceIn(0f, currentTrimEnd - 0.05f))
                        } else {
                            onTrimEndChange((initialEnd + delta).coerceIn(currentTrimStart + 0.05f, 1f))
                        }
                    }
                    detectDragGestures(
                        onDragStart = {
                            initialStart = currentTrimStart
                            initialEnd = currentTrimEnd
                            accumulatedDeltaX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDeltaX += dragAmount.x
                            updateTrimFromDelta()
                        },
                    )
                }
            }

        Row(Modifier.fillMaxSize()) {
            repeat(frameSlots) { index ->
                frameContent(
                    index.coerceAtMost((frameCount - 1).coerceAtLeast(0)),
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp),
                )
            }
        }

        if (!state.isExporting) {
            Canvas(Modifier.fillMaxSize()) {
                val startX = trimStart * size.width
                val endX = trimEnd * size.width
                drawRect(Color.Black.copy(alpha = 0.48f), topLeft = Offset.Zero, size = Size(startX, size.height))
                drawRect(Color.Black.copy(alpha = 0.48f), topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))
                drawRect(
                    color = template.colors.accent,
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, size.height),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(x = with(LocalDensity.current) { playheadX(timelineWidthPx).toDp() } - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(template.colors.textPrimary.copy(alpha = 0.88f)),
        )

        if (!state.isExporting) {
            CommonTimelineHandle(
                modifier = Modifier
                    .offset(x = maxWidth * trimStart)
                    .width(handleWidth)
                    .fillMaxHeight(),
                alignStart = true,
            )
            CommonTimelineHandle(
                modifier = Modifier
                    .offset(x = maxWidth * trimEnd - handleWidth)
                    .width(handleWidth)
                    .fillMaxHeight(),
                alignStart = false,
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * trimStart - handleHitPadding)
                    .width(handleHitWidth)
                    .fillMaxHeight()
                    .handleDrag(startHandle = true),
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * trimEnd - handleWidth - handleHitPadding)
                    .width(handleHitWidth)
                    .fillMaxHeight()
                    .handleDrag(startHandle = false),
            )
        }
    }
}

@Composable
private fun CommonTimelineHandle(
    modifier: Modifier,
    alignStart: Boolean,
) {
    val template = quataTheme()
    Box(
        modifier = modifier.background(template.colors.accent),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(template.colors.surface.copy(alpha = 0.92f)),
        )
    }
}

@Composable
private fun CommonTimelineFramePlaceholder(modifier: Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.36f)))
}

private fun PostVideoEditorStrings.labelFor(mode: VideoCropMode): String = when (mode) {
    VideoCropMode.Original -> cropOriginal
    VideoCropMode.Square -> cropSquare
    VideoCropMode.FourFive -> cropFourFive
    VideoCropMode.Portrait -> cropPortrait
    VideoCropMode.Landscape -> cropLandscape
}
