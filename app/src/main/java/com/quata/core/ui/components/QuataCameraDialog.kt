package com.quata.core.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QuataCameraMode {
    Photo,
    Video,
    Dual
}

private enum class ActiveQuataCameraMode {
    Photo,
    Video
}

@Composable
fun QuataCameraDialog(
    mode: QuataCameraMode,
    audioEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onPhotoCaptured: (Uri, String, String) -> Unit = { _, _, _ -> },
    onVideoCaptured: (Uri, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val isDualMode = mode == QuataCameraMode.Dual
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controlsHeight = when {
        isDualMode && isLandscape -> 184.dp
        isDualMode -> 168.dp
        isLandscape -> 152.dp
        else -> 132.dp
    }
    val controlsTopPadding = if (isDualMode) 10.dp else 12.dp
    val controlsBottomPadding = if (isDualMode) 8.dp else 12.dp
    var activeMode by remember(mode) {
        mutableStateOf(if (mode == QuataCameraMode.Video) ActiveQuataCameraMode.Video else ActiveQuataCameraMode.Photo)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var discardRecording by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(true) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedSeconds by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRecording, recordingStartedAtMillis) {
        val startedAt = recordingStartedAtMillis
        if (!isRecording || startedAt == null) {
            recordingElapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (isRecording) {
            recordingElapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
            delay(250L)
        }
    }

    fun closeOrCancel() {
        val activeRecording = recording
        if (activeRecording != null) {
            discardRecording = true
            activeRecording.stop()
        } else if (!isCapturingPhoto) {
            onDismiss()
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        val view = previewView
        val file = context.createQuataCameraPhotoFile()
        val targetRotation = view?.display?.rotation ?: Surface.ROTATION_0
        isCapturingPhoto = true
        errorText = null
        capture.targetRotation = targetRotation
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    file.normalizeCameraXJpegIfNeeded(targetRotation)
                    isCapturingPhoto = false
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    onPhotoCaptured(uri, file.name, "image/jpeg")
                }

                override fun onError(exception: ImageCaptureException) {
                    runCatching { file.delete() }
                    isCapturingPhoto = false
                    errorText = context.getString(R.string.conversation_photo_capture_error)
                }
            }
        )
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val targetRotation = previewView?.display?.rotation ?: Surface.ROTATION_0
        val file = context.createQuataCameraVideoFile()
        activeFile = file
        discardRecording = false
        capture.targetRotation = targetRotation
        val outputOptions = FileOutputOptions.Builder(file).build()
        val prepared = capture.output.prepareRecording(context, outputOptions)
        val withAudio = audioEnabled &&
            PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED
        @SuppressLint("MissingPermission")
        val pendingRecording = if (withAudio) prepared.withAudioEnabled() else prepared
        recording = pendingRecording.start(executor) recordListener@ { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    recordingStartedAtMillis = System.currentTimeMillis()
                    errorText = null
                }
                is VideoRecordEvent.Finalize -> {
                    val finishedFile = activeFile
                    recording = null
                    activeFile = null
                    isRecording = false
                    recordingStartedAtMillis = null
                    if (discardRecording) {
                        runCatching { finishedFile?.delete() }
                        discardRecording = false
                        onDismiss()
                        return@recordListener
                    }
                    if (!event.hasError() && finishedFile != null && finishedFile.length() > 0L) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finishedFile)
                        onVideoCaptured(uri, finishedFile.name, "video/mp4")
                    } else {
                        runCatching { finishedFile?.delete() }
                        errorText = context.getString(R.string.conversation_video_record_error)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = ::closeOrCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureQuataCameraSystemBars()
        val navigationBarsPadding = rememberQuataCameraNavigationBarsPadding()
        val controlsContainerHeight = controlsHeight + navigationBarsPadding.bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(previewView, lifecycleOwner, activeMode) {
                val view = previewView
                if (view == null) {
                    onDispose { }
                } else {
                    isPreparing = true
                    imageCapture = null
                    videoCapture = null
                    errorText = null
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener(
                        {
                            runCatching {
                                val cameraProvider = cameraProviderFuture.get()
                                val targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                                val preview = Preview.Builder()
                                    .setTargetRotation(targetRotation)
                                    .build()
                                    .also { it.setSurfaceProvider(view.surfaceProvider) }
                                val selector = cameraProvider.bestAvailableQuataCameraSelector()
                                cameraProvider.unbindAll()
                                if (activeMode == ActiveQuataCameraMode.Photo) {
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetRotation(targetRotation)
                                        .build()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                    imageCapture = capture
                                    videoCapture = null
                                } else {
                                    val recorder = Recorder.Builder()
                                        .setQualitySelector(
                                            QualitySelector.from(
                                                Quality.SD,
                                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                                            )
                                        )
                                        .build()
                                    val capture = VideoCapture.Builder(recorder)
                                        .setTargetRotation(targetRotation)
                                        .build()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                    videoCapture = capture
                                    imageCapture = null
                                }
                                isPreparing = false
                            }.onFailure {
                                isPreparing = false
                                errorText = context.getString(
                                    if (activeMode == ActiveQuataCameraMode.Photo) {
                                        R.string.conversation_photo_camera_error
                                    } else {
                                        R.string.conversation_video_camera_error
                                    }
                                )
                            }
                        },
                        executor
                    )
                    onDispose {
                        discardRecording = true
                        runCatching { recording?.stop() }
                        runCatching { cameraProviderFuture.get().unbindAll() }
                    }
                }
            }

            if (isPreparing || isCapturingPhoto) {
                CircularProgressIndicator(
                    color = QuataOrange,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            errorText?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            if (isRecording) {
                RecordingTimerPill(
                    elapsedSeconds = recordingElapsedSeconds,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(controlsContainerHeight)
                    .background(Color(0x99000000))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlsContainerHeight)
                        .padding(
                            start = 24.dp + navigationBarsPadding.start,
                            top = controlsTopPadding,
                            end = 24.dp + navigationBarsPadding.end,
                            bottom = controlsBottomPadding + navigationBarsPadding.bottom
                        )
                ) {
                    if (isDualMode) {
                        CameraModeSwitch(
                            selectedMode = activeMode,
                            enabled = !isRecording && !isCapturingPhoto,
                            onSelected = { selectedMode ->
                                if (activeMode != selectedMode) {
                                    activeMode = selectedMode
                                }
                            },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    TextButton(
                        onClick = ::closeOrCancel,
                        enabled = !isCapturingPhoto,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text(stringResource(R.string.conversation_video_record_cancel), color = Color.White)
                    }

                    QuataCameraControlButton(
                        activeMode = activeMode,
                        isRecording = isRecording,
                        enabled = when (activeMode) {
                            ActiveQuataCameraMode.Photo -> imageCapture != null && !isPreparing && !isCapturingPhoto
                            ActiveQuataCameraMode.Video -> videoCapture != null && !isPreparing
                        },
                        onClick = {
                            if (activeMode == ActiveQuataCameraMode.Photo) {
                                takePhoto()
                            } else if (isRecording) {
                                recording?.stop()
                            } else {
                                startRecording()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(
                                x = 0.dp,
                                y = if (isLandscape) (-40).dp else 0.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigureQuataCameraSystemBars() {
    val context = LocalContext.current
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    val activity = context.findQuataCameraActivity() as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        dialogWindow?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }
    DisposableEffect(activity) {
        if (activity == null) {
            return@DisposableEffect onDispose {}
        }
        onDispose {
            activity.enableEdgeToEdge()
        }
    }
}

@Composable
private fun rememberQuataCameraNavigationBarsPadding(): QuataCameraNavigationBarsPadding {
    val context = LocalContext.current
    val dialogView = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val activity = remember(context) { context.findQuataCameraActivity() }
    var paddingPx by remember(dialogView, activity) {
        mutableStateOf(resolveQuataCameraNavigationBarsPaddingPx(dialogView, activity))
    }

    DisposableEffect(dialogView, activity) {
        fun updatePadding() {
            paddingPx = resolveQuataCameraNavigationBarsPaddingPx(dialogView, activity)
        }

        val dialogLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePadding() }
        val activityDecorView = activity?.window?.decorView
        val activityLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePadding() }
        dialogView.addOnLayoutChangeListener(dialogLayoutListener)
        activityDecorView?.addOnLayoutChangeListener(activityLayoutListener)
        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { _, insets ->
            updatePadding()
            insets
        }
        updatePadding()
        ViewCompat.requestApplyInsets(dialogView)
        activityDecorView?.let(ViewCompat::requestApplyInsets)

        // Dialog windows can report zero insets on their first layout pass on
        // newer edge-to-edge Android versions, so retry on the next frames.
        val retryRunnables = mutableListOf<Runnable>()
        repeat(3) { attempt ->
            val delayMs = 32L * (attempt + 1)
            val runnable = Runnable {
                updatePadding()
                ViewCompat.requestApplyInsets(dialogView)
                activityDecorView?.let(ViewCompat::requestApplyInsets)
            }
            retryRunnables += runnable
            dialogView.postDelayed(runnable, delayMs)
        }

        onDispose {
            dialogView.removeOnLayoutChangeListener(dialogLayoutListener)
            activityDecorView?.removeOnLayoutChangeListener(activityLayoutListener)
            ViewCompat.setOnApplyWindowInsetsListener(dialogView, null)
            retryRunnables.forEach(dialogView::removeCallbacks)
        }
    }

    return with(density) {
        val left = paddingPx.left.toDp()
        val right = paddingPx.right.toDp()
        QuataCameraNavigationBarsPadding(
            start = if (layoutDirection == LayoutDirection.Rtl) right else left,
            end = if (layoutDirection == LayoutDirection.Rtl) left else right,
            bottom = paddingPx.bottom.toDp()
        )
    }
}

private data class QuataCameraNavigationBarsPadding(
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp
)

private data class QuataCameraNavigationBarsPaddingPx(
    val left: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

private fun resolveQuataCameraNavigationBarsPaddingPx(
    dialogView: View,
    activity: Activity?
): QuataCameraNavigationBarsPaddingPx {
    val dialogInsets = dialogView.quataCameraNavigationBarInsetsPx()
    val activityInsets = activity?.window?.decorView?.quataCameraNavigationBarInsetsPx()
        ?: QuataCameraNavigationBarsPaddingPx()
    val navigationInsets = QuataCameraNavigationBarsPaddingPx(
        left = maxOf(dialogInsets.left, activityInsets.left),
        right = maxOf(dialogInsets.right, activityInsets.right),
        bottom = maxOf(dialogInsets.bottom, activityInsets.bottom)
    )
    if (navigationInsets == QuataCameraNavigationBarsPaddingPx()) {
        return navigationInsets
    }

    val rootView = dialogView.rootView ?: dialogView
    val location = IntArray(2)
    rootView.getLocationOnScreen(location)
    val activityRoot = activity?.window?.decorView?.rootView
    val displayMetrics = (activity ?: dialogView.context).resources.displayMetrics
    val displayWidth = maxOf(displayMetrics.widthPixels, activityRoot?.width ?: 0)
    val displayHeight = maxOf(displayMetrics.heightPixels, activityRoot?.height ?: 0)
    val rootLeft = location[0]
    val rootRight = location[0] + rootView.width
    val rootBottom = location[1] + rootView.height
    return QuataCameraNavigationBarsPaddingPx(
        left = if (navigationInsets.left > 0) {
            (navigationInsets.left - rootLeft).coerceAtLeast(0)
        } else {
            0
        },
        right = if (navigationInsets.right > 0) {
            (rootRight - (displayWidth - navigationInsets.right)).coerceAtLeast(0)
        } else {
            0
        },
        bottom = if (navigationInsets.bottom > 0) {
            (rootBottom - (displayHeight - navigationInsets.bottom)).coerceAtLeast(0)
        } else {
            0
        }
    )
}

/**
 * Resolve navigation bar insets for the dialog, falling back to system
 * navigation-bar dimensions when the dialog has not received real insets yet.
 */
private fun View.quataCameraNavigationBarInsetsPx(): QuataCameraNavigationBarsPaddingPx {
    nativeQuataCameraNavigationBarInsetsPx()?.takeIf { it != QuataCameraNavigationBarsPaddingPx() }?.let { return it }
    val compatInsets = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())
    if (compatInsets != null) {
        val padding = QuataCameraNavigationBarsPaddingPx(
            left = compatInsets.left,
            right = compatInsets.right,
            bottom = compatInsets.bottom
        )
        if (padding != QuataCameraNavigationBarsPaddingPx()) {
            return padding
        }
    }
    return context.quataCameraNavigationBarResourceInsetsPx(display?.rotation)
}

private fun View.nativeQuataCameraNavigationBarInsetsPx(): QuataCameraNavigationBarsPaddingPx? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return null
    }
    val rootInsets = rootWindowInsets ?: return null
    val visibleInsets = rootInsets.getInsets(android.view.WindowInsets.Type.navigationBars())
    val fallbackInsets = rootInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
    val insets = if (
        visibleInsets.left != 0 ||
        visibleInsets.right != 0 ||
        visibleInsets.bottom != 0
    ) {
        visibleInsets
    } else {
        fallbackInsets
    }
    return QuataCameraNavigationBarsPaddingPx(
        left = insets.left,
        right = insets.right,
        bottom = insets.bottom
    )
}

/**
 * Resource-based navigation bar estimate used only when both native and compat
 * insets are still unavailable.
 */
private fun Context.quataCameraNavigationBarResourceInsetsPx(rotation: Int?): QuataCameraNavigationBarsPaddingPx {
    val navigationBarHeight = androidSystemDimensionPx("navigation_bar_height")
    val navigationBarWidth = androidSystemDimensionPx("navigation_bar_width").takeIf { it > 0 } ?: navigationBarHeight
    val padding = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        when (rotation) {
            Surface.ROTATION_270 -> QuataCameraNavigationBarsPaddingPx(left = navigationBarWidth)
            else -> QuataCameraNavigationBarsPaddingPx(right = navigationBarWidth)
        }
    } else {
        QuataCameraNavigationBarsPaddingPx(bottom = navigationBarHeight)
    }
    return padding
}

private fun Context.androidSystemDimensionPx(name: String): Int {
    val resourceId = resources.getIdentifier(name, "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
}

private tailrec fun Context.findQuataCameraActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findQuataCameraActivity()
        else -> null
    }

@Composable
private fun CameraModeSwitch(
    selectedMode: ActiveQuataCameraMode,
    enabled: Boolean,
    onSelected: (ActiveQuataCameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraModeSwitchItem(
            text = stringResource(R.string.quata_camera_mode_photo),
            selected = selectedMode == ActiveQuataCameraMode.Photo,
            enabled = enabled,
            onClick = { onSelected(ActiveQuataCameraMode.Photo) }
        )
        CameraModeSwitchItem(
            text = stringResource(R.string.quata_camera_mode_video),
            selected = selectedMode == ActiveQuataCameraMode.Video,
            enabled = enabled,
            onClick = { onSelected(ActiveQuataCameraMode.Video) }
        )
    }
}

@Composable
private fun CameraModeSwitchItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) QuataOrange else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.84f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun RecordingTimerPill(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xAA000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatRecordingDuration(elapsedSeconds),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun QuataCameraControlButton(
    activeMode: ActiveQuataCameraMode,
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(
        when {
            activeMode == ActiveQuataCameraMode.Photo -> R.string.conversation_attach_take_photo
            isRecording -> R.string.conversation_video_record_stop
            else -> R.string.conversation_video_record_start
        }
    )
    Box(
        modifier = modifier
            .size(86.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.96f else 0.5f))
            .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    when {
                        activeMode == ActiveQuataCameraMode.Video && isRecording -> 34.dp
                        activeMode == ActiveQuataCameraMode.Photo -> 58.dp
                        else -> 56.dp
                    }
                )
                .clip(
                    if (activeMode == ActiveQuataCameraMode.Video && isRecording) {
                        RoundedCornerShape(8.dp)
                    } else {
                        CircleShape
                    }
                )
                .background(
                    when {
                        !enabled -> Color(0xFF9E9E9E)
                        activeMode == ActiveQuataCameraMode.Photo -> QuataOrange
                        else -> Color(0xFFE53935)
                    }
                )
        )
    }
}

private fun Context.createQuataCameraPhotoFile(): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
    return File(cacheDir, "quata_$timestamp.jpg").apply {
        parentFile?.mkdirs()
        if (!exists()) createNewFile()
    }
}

private fun Context.createQuataCameraVideoFile(): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
    return File(cacheDir, "quata_$timestamp.mp4").apply {
        parentFile?.mkdirs()
        if (!exists()) createNewFile()
    }
}

private fun File.normalizeCameraXJpegIfNeeded(targetRotation: Int) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching

        val exif = ExifInterface(absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> {
                val isPortraitDisplayRotation = targetRotation == Surface.ROTATION_0 ||
                    targetRotation == Surface.ROTATION_180
                if (isPortraitDisplayRotation && bounds.outWidth > bounds.outHeight) 90f else 0f
            }
        }
        if (rotationDegrees == 0f && orientation == ExifInterface.ORIENTATION_NORMAL) return@runCatching

        val decoded = BitmapFactory.decodeFile(
            absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: return@runCatching
        val rotated = if (rotationDegrees == 0f) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotationDegrees) },
                true
            )
        }
        if (rotated !== decoded) decoded.recycle()
        outputStream().use { output ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        if (!rotated.isRecycled) rotated.recycle()
        exif.apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
    }
}

private fun ProcessCameraProvider.bestAvailableQuataCameraSelector(): CameraSelector =
    when {
        runCatching { hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
        runCatching { hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> error("No camera available")
    }

private fun formatRecordingDuration(seconds: Long): String {
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return "%02d:%02d".format(minutes, remainingSeconds)
}
