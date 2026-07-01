package com.quata.core.ui.components

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import androidx.core.content.FileProvider
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QuataAudioRecorderDialog(
    onDismiss: () -> Unit,
    onAudioRecorded: (Uri, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember(context) { QuataCompressedAudioRecorder(context) }
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

    DisposableEffect(recorder) {
        onDispose {
            scope.launch { recorder.discard() }
        }
    }

    fun startRecording() {
        errorText = null
        runCatching {
            recorder.start()
        }.onSuccess {
            isRecording = true
            startedAtMillis = System.currentTimeMillis()
        }.onFailure {
            errorText = context.getString(R.string.conversation_audio_record_error)
        }
    }

    fun stopAndAttach() {
        scope.launch {
            val recording = runCatching { recorder.finish() }.getOrNull()
            isRecording = false
            startedAtMillis = null
            val file = recording?.file
            if (file == null || file.length() < MIN_COMPRESSED_AUDIO_BYTES) {
                errorText = context.getString(R.string.conversation_audio_record_error)
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            onAudioRecorded(uri, file.name, recording.mimeType)
        }
    }

    fun cancel() {
        scope.launch {
            recorder.discard()
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
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(4.dp, QuataOrange.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 34.dp else 58.dp)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(if (isRecording) Color(0xFFE53935) else QuataOrange)
        )
    }
}

private class QuataCompressedAudioRecorder(
    private val context: Context
) {
    private var outputFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFormat: QuataAudioRecordingFormat? = null

    fun start(): File {
        discardBlocking()
        val format = selectQuataAudioRecordingFormat()
        val file = context.createQuataAudioFile(format.extension)
        val recorder = newQuataMediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(format.outputFormat)
            setAudioEncoder(format.audioEncoder)
            setAudioSamplingRate(format.sampleRate)
            setAudioEncodingBitRate(format.bitRate)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile = file
        mediaRecorder = recorder
        recordingFormat = format
        return file
    }

    suspend fun finish(): QuataRecordedAudioFile? =
        stopInternal(deleteFile = false)

    suspend fun discard() {
        stopInternal(deleteFile = true)
    }

    private suspend fun stopInternal(deleteFile: Boolean): QuataRecordedAudioFile? = withContext(Dispatchers.IO) {
        val recorder = mediaRecorder
        val file = outputFile
        val format = recordingFormat
        var completed = false
        try {
            if (recorder != null) {
                recorder.stop()
                completed = true
            }
        } finally {
            runCatching { recorder?.release() }
            mediaRecorder = null
            outputFile = null
            recordingFormat = null
        }
        if (deleteFile || !completed || file == null || format == null || file.length() < MIN_COMPRESSED_AUDIO_BYTES) {
            runCatching { file?.delete() }
            null
        } else {
            QuataRecordedAudioFile(file = file, mimeType = format.mimeType)
        }
    }

    private fun discardBlocking() {
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        runCatching { outputFile?.delete() }
        mediaRecorder = null
        outputFile = null
        recordingFormat = null
    }
}

private data class QuataRecordedAudioFile(
    val file: File,
    val mimeType: String
)

private data class QuataAudioRecordingFormat(
    val extension: String,
    val mimeType: String,
    val outputFormat: Int,
    val audioEncoder: Int,
    val sampleRate: Int,
    val bitRate: Int
)

private fun selectQuataAudioRecordingFormat(): QuataAudioRecordingFormat =
    QuataAudioRecordingFormat(
        extension = "m4a",
        mimeType = "audio/mp4",
        outputFormat = MediaRecorder.OutputFormat.MPEG_4,
        audioEncoder = MediaRecorder.AudioEncoder.AAC,
        sampleRate = 44_100,
        bitRate = 64_000
    )

private fun newQuataMediaRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder::class.java.getDeclaredConstructor().newInstance()
    }

private fun Context.createQuataAudioFile(extension: String): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
    return File(cacheDir, "quata_audio_$timestamp.$extension").apply {
        parentFile?.mkdirs()
        if (!exists()) createNewFile()
    }
}

private fun formatQuataAudioDuration(seconds: Long): String {
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private const val MIN_COMPRESSED_AUDIO_BYTES = 512L
