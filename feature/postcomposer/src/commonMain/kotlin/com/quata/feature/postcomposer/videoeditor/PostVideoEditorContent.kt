package com.quata.feature.postcomposer.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

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
    val cropEnabled: Boolean = false,
    val captionsEnabled: Boolean = false,
    val cropPanelOpen: Boolean = false,
    val captionsPanelOpen: Boolean = false,
    val cropMode: VideoCropMode = VideoCropMode.Original,
    val cropZoom: Float = 1f,
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
    onCaptionStyleChange: (String?) -> Unit = {},
    preview: @Composable (Modifier) -> Unit,
    timeline: @Composable (Modifier) -> Unit = { timelineModifier ->
        DefaultVideoTimelineControls(
            state = state,
            onTrimStartChange = onTrimStartChange,
            onTrimEndChange = onTrimEndChange,
            modifier = timelineModifier,
            strings = strings,
        )
    },
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
            onCaptionStyleChange = onCaptionStyleChange,
            preview = preview,
            timeline = timeline,
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
    onCaptionStyleChange: (String?) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    timeline: @Composable (Modifier) -> Unit,
) {
    val previewModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 220.dp, max = if (isLandscapeLayout) 520.dp else 360.dp)
        .widthIn(max = if (isLandscapeLayout) 620.dp else 420.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .testTag(PostVideoEditorPreviewTestTag)
        .semantics { contentDescription = PostVideoEditorPreviewTestTag }
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
                    onCaptionStyleChange = onCaptionStyleChange,
                    timeline = timeline,
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
                onCaptionStyleChange = onCaptionStyleChange,
                timeline = timeline,
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
    onCaptionStyleChange: (String?) -> Unit,
    timeline: @Composable (Modifier) -> Unit,
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
        timeline(
            Modifier
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEach { option ->
            val id = option.id.ifBlank { null }
            val selected = id == selectedId
            if (selected) {
                Button(onClick = { onStyleChange(id) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    CompactIcon(Icons.Filled.Subtitles, null)
                    Spacer(Modifier.width(4.dp))
                    Text(option.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { onStyleChange(id) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text(option.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${strings.exporting} ${(state.exportProgress * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
private fun DefaultVideoTimelineControls(
    state: PostVideoEditorUiState,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier,
    strings: PostVideoEditorStrings,
) {
    val trimStart = state.trimStartFraction.coerceIn(0f, 0.95f)
    val trimEnd = state.trimEndFraction.coerceIn((trimStart + 0.05f).coerceAtMost(1f), 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.trimStart, style = MaterialTheme.typography.labelSmall)
        Slider(
            value = trimStart,
            onValueChange = { onTrimStartChange(it.coerceIn(0f, trimEnd - 0.05f)) },
            valueRange = 0f..1f,
            modifier = Modifier.testTag(PostVideoEditorTimelineTestTag),
        )
        Text(strings.trimEnd, style = MaterialTheme.typography.labelSmall)
        Slider(
            value = trimEnd,
            onValueChange = { onTrimEndChange(it.coerceIn(trimStart + 0.05f, 1f)) },
            valueRange = 0f..1f,
        )
    }
}

private fun PostVideoEditorStrings.labelFor(mode: VideoCropMode): String = when (mode) {
    VideoCropMode.Original -> cropOriginal
    VideoCropMode.Square -> cropSquare
    VideoCropMode.FourFive -> cropFourFive
    VideoCropMode.Portrait -> cropPortrait
    VideoCropMode.Landscape -> cropLandscape
}
