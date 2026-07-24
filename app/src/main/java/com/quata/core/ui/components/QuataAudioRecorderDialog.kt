package com.quata.core.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuataAudioRecorderDialog(
    recorderService: AudioRecorderService,
    onDismiss: () -> Unit,
    onAudioRecorded: (Uri, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var startedAtMillis by remember { mutableStateOf<Long?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRecording, startedAtMillis) {
        val startedAt = startedAtMillis
        if (!isRecording || startedAt == null) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (isRecording) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
            delay(250L)
        }
    }

    DisposableEffect(recorderService) {
        onDispose {
            scope.launch { recorderService.cancel() }
        }
    }

    fun startRecording() {
        errorText = null
        scope.launch {
            when (recorderService.start()) {
                is PlatformResult.Success -> {
                    isRecording = true
                    startedAtMillis = System.currentTimeMillis()
                }
                else -> errorText = context.getString(R.string.conversation_audio_record_error)
            }
        }
    }

    fun stopAndAttach() {
        scope.launch {
            val recording = when (val result = recorderService.stop()) {
                is PlatformResult.Success -> result.value
                else -> null
            }
            isRecording = false
            startedAtMillis = null
            if (recording == null || (recording.file.sizeBytes ?: 0L) < MIN_COMPRESSED_AUDIO_BYTES) {
                errorText = context.getString(R.string.conversation_audio_record_error)
                return@launch
            }
            onAudioRecorded(
                Uri.parse(recording.file.reference),
                recording.file.displayName ?: "audio.m4a",
                recording.mimeType,
            )
        }
    }

    fun cancel() {
        scope.launch {
            recorderService.cancel()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = ::cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KeepQuataAudioRecorderScreenAwake()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.conversation_audio_record_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = formatQuataAudioDuration(elapsedSeconds),
                    color = if (isRecording) QuataOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                )
                QuataAudioRecordButton(
                    isRecording = isRecording,
                    onClick = {
                        if (isRecording) {
                            stopAndAttach()
                        } else {
                            startRecording()
                        }
                    }
                )
                Text(
                    text = stringResource(
                        if (isRecording) {
                            R.string.conversation_audio_record_stop_hint
                        } else {
                            R.string.conversation_audio_record_start_hint
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    fontSize = 13.sp
                )
                errorText?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = ::cancel) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepQuataAudioRecorderScreenAwake() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

@Composable
private fun QuataAudioRecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    QuataAudioRecordButtonContent(isRecording = isRecording, onClick = onClick)
}

private fun formatQuataAudioDuration(seconds: Long): String {
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private const val MIN_COMPRESSED_AUDIO_BYTES = 512L
