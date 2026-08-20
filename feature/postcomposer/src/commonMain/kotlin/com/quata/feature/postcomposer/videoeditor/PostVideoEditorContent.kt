package com.quata.feature.postcomposer.videoeditor

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon

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
    val captions: String = "Subtitulos",
    val reset: String = "Restablecer",
    val cancel: String = "Cancelar",
    val export: String = "Guardar",
    val exporting: String = "Exportando...",
)

data class PostVideoEditorUiState(
    val isMuted: Boolean = false,
    val isPlaying: Boolean = false,
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    val cropEnabled: Boolean = false,
    val captionsEnabled: Boolean = false,
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
    preview: @Composable (Modifier) -> Unit,
) {
    val trimStart = state.trimStartFraction.coerceIn(0f, 0.95f)
    val trimEnd = state.trimEndFraction.coerceIn((trimStart + 0.05f).coerceAtMost(1f), 1f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.helper)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 340.dp)
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag(PostVideoEditorPreviewTestTag)
                        .semantics { contentDescription = PostVideoEditorPreviewTestTag },
                    contentAlignment = Alignment.Center,
                ) {
                    preview(Modifier.fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPlayPause,
                        modifier = Modifier.testTag(PostVideoEditorPlayPauseTestTag),
                    ) {
                        CompactIcon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                        Text(" ${if (state.isPlaying) strings.pause else strings.play}")
                    }
                    OutlinedButton(
                        onClick = { onMutedChange(!state.isMuted) },
                        modifier = Modifier.testTag(PostVideoEditorMuteTestTag),
                    ) {
                        CompactIcon(if (state.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, null)
                        Text(" ${if (state.isMuted) strings.unmute else strings.mute}")
                    }
                }
                Text(strings.trimStart)
                Slider(
                    value = trimStart,
                    onValueChange = { onTrimStartChange(it.coerceIn(0f, trimEnd - 0.05f)) },
                    valueRange = 0f..1f,
                    modifier = Modifier.testTag(PostVideoEditorTimelineTestTag),
                )
                Text(strings.trimEnd)
                Slider(
                    value = trimEnd,
                    onValueChange = { onTrimEndChange(it.coerceIn(trimStart + 0.05f, 1f)) },
                    valueRange = 0f..1f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCropToggle,
                        modifier = Modifier.testTag(PostVideoEditorCropTestTag),
                    ) {
                        CompactIcon(if (state.cropEnabled) Icons.Filled.Check else Icons.Filled.Crop, null)
                        Text(" ${strings.crop}")
                    }
                    OutlinedButton(
                        onClick = onCaptionsToggle,
                        modifier = Modifier.testTag(PostVideoEditorCaptionsTestTag),
                    ) {
                        CompactIcon(if (state.captionsEnabled) Icons.Filled.Check else Icons.Filled.Subtitles, null)
                        Text(" ${strings.captions}")
                    }
                    OutlinedButton(onClick = onReset) {
                        CompactIcon(Icons.Filled.Replay, null)
                        Text(" ${strings.reset}")
                    }
                }
                if (state.isExporting) {
                    LinearProgressIndicator(
                        progress = { state.exportProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(strings.exporting, style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
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
                CompactIcon(Icons.Filled.Check, null)
                Text(" ${strings.export}")
            }
        },
        modifier = modifier
            .heightIn(max = 820.dp)
            .testTag(PostVideoEditorRootTestTag),
    )
}
