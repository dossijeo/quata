@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.media.copyImageToFileNormalizingOrientation
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.media.withQuataMediaMetadataRetriever
import com.quata.core.ui.components.CommunityEmojiPanel
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.ui.components.normalizedQuataVideoRotation
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasPatterns
import com.quata.core.ui.textCanvasTypography
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.imageeditor.QuataEditedImageFilePrefix
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private enum class ComposerStep {
    TypePicker,
    Text,
    Image,
    Video
}

private enum class CaptureTarget {
    Photo,
    Video
}

@Composable
fun CreatePostScreen(
    padding: PaddingValues,
    repository: PostComposerRepository,
    locationService: LocationService,
    permissionService: PermissionService,
    resetToken: Int,
    cancelUploadToken: Int = 0,
    canPublish: Boolean,
    onAuthRequired: () -> Unit,
    onPostCreated: (String?) -> Unit,
    onVideoEditorVisibilityChange: (Boolean) -> Unit = {},
    onUploadStateChange: (Boolean) -> Unit = {},
    viewModel: CreatePostAndroidViewModel = viewModel(factory = CreatePostAndroidViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    val template = quataTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val composerScrollState = rememberScrollState()
    var step by rememberSaveable { mutableStateOf(ComposerStep.TypePicker) }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var isEmojiPanelOpen by rememberSaveable { mutableStateOf(false) }
    var isLocationEditorOpen by rememberSaveable { mutableStateOf(false) }
    var highlightLocationEditor by rememberSaveable { mutableStateOf(false) }
    var imageLocationOffsetPx by remember { mutableStateOf(0) }
    var lastHandledResetToken by rememberSaveable { mutableStateOf(resetToken) }
    var lastHandledCancelUploadToken by rememberSaveable { mutableStateOf(cancelUploadToken) }
    var imageEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var preparedImageTempUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var editedImageTempUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var editedVideoTempUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var preparedVideoTempUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCaptureTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var cameraDialogMode by remember { mutableStateOf<QuataCameraMode?>(null) }
    var cameraDialogAudioEnabled by remember { mutableStateOf(false) }
    var isCancelUploadDialogOpen by remember { mutableStateOf(false) }
    val latestIsLoading by rememberUpdatedState(state.isLoading)

    fun deleteEditedVideoTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataEditedVideoTemp(uri)
    }

    fun deletePreparedVideoTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataPreparedVideoTemp(uri)
    }

    fun deleteEditedImageTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataEditedImageTemp(uri)
    }

    fun deletePreparedImageTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataPreparedImageTemp(uri)
    }

    fun clearPreparedImageTemp() {
        val uri = preparedImageTempUri
        preparedImageTempUri = null
        deletePreparedImageTemp(uri)
    }

    fun clearPreparedVideoTemp() {
        val uri = preparedVideoTempUri
        preparedVideoTempUri = null
        deletePreparedVideoTemp(uri)
    }

    fun clearEditedVideoTemp() {
        val uri = editedVideoTempUri
        editedVideoTempUri = null
        deleteEditedVideoTemp(uri)
    }

    fun clearEditedImageTemp() {
        val uri = editedImageTempUri
        editedImageTempUri = null
        deleteEditedImageTemp(uri)
    }

    fun keepEditedTempsForMockPost() {
        preparedImageTempUri = null
        editedImageTempUri = null
        editedVideoTempUri = null
    }

    fun submitIfAuthenticated(type: PostComposerType) {
        if (canPublish) {
            viewModel.submit(type)
        } else {
            onAuthRequired()
        }
    }

    fun requestLeaveComposer() {
        if (state.isLoading) {
            isCancelUploadDialogOpen = true
        }
    }

    fun resolveLocation(location: Location) {
        scope.launch {
            val label = context.resolvePlaceName(location)
                ?: context.getString(R.string.composer_detected_location)
            viewModel.onEvent(CreatePostUiEvent.LocationResolved(label, location.latitude, location.longitude))
        }
    }

    fun resolveLocation(latitude: Double, longitude: Double) {
        resolveLocation(Location("platform").apply {
            this.latitude = latitude
            this.longitude = longitude
        })
    }

    fun requestCurrentLocation() {
        scope.launch {
            when (val result = locationService.currentLocation()) {
                is PlatformResult.Success -> resolveLocation(result.value.latitude, result.value.longitude)
                else -> Unit
            }
        }
    }

    fun selectImageForComposer(uri: Uri?) {
        uri ?: return
        scope.launch {
            val preparedUri = withContext(Dispatchers.IO) {
                context.prepareComposerImageSource(uri)
            }
            if (preparedUri == null) {
                Toast.makeText(context, context.getString(R.string.composer_image_pick_error), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val previousPreparedUri = preparedImageTempUri
            val previousEditedUri = editedImageTempUri
            preparedImageTempUri = preparedUri
            editedImageTempUri = null
            viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(""))
            viewModel.onEvent(CreatePostUiEvent.ImageSelected(preparedUri.toString()))
            isLocationEditorOpen = false
            highlightLocationEditor = false
            if (previousPreparedUri != preparedUri) {
                deletePreparedImageTemp(previousPreparedUri)
            }
            if (previousEditedUri != preparedUri) {
                deleteEditedImageTemp(previousEditedUri)
            }
            val exifLocation = context.exifLocationFromUri(preparedUri)
            if (exifLocation != null) {
                resolveLocation(exifLocation)
            } else if (permissionService.status(PlatformPermission.Location) == PermissionStatus.Granted) {
                requestCurrentLocation()
            } else {
                if (permissionService.request(PlatformPermission.Location) == PermissionStatus.Granted) {
                    requestCurrentLocation()
                }
            }
            imageEditorUri = preparedUri
        }
    }

    fun openVideoEditorWithPreparedSource(uri: Uri?) {
        uri ?: return
        scope.launch {
            val preparedUri = withContext(Dispatchers.IO) {
                context.prepareComposerVideoSource(uri)
            }
            if (preparedUri == null) {
                Toast.makeText(context, context.getString(R.string.composer_video_capture_error), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val previousPreparedUri = preparedVideoTempUri
            preparedVideoTempUri = preparedUri
            deletePreparedVideoTemp(previousPreparedUri)
            videoEditorUri = preparedUri
        }
    }

    val galleryImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectImageForComposer(uri)
    }
    val galleryVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        openVideoEditorWithPreparedSource(uri)
    }
    fun launchPhotoCapture() {
        cameraDialogMode = QuataCameraMode.Photo
    }

    fun launchVideoCapture() {
        cameraDialogAudioEnabled = context.hasAudioRecordPermission()
        cameraDialogMode = QuataCameraMode.Video
    }

    val capturePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasCapturePermissions(pendingCaptureTarget)) {
            when (pendingCaptureTarget) {
                CaptureTarget.Photo -> launchPhotoCapture()
                CaptureTarget.Video -> launchVideoCapture()
                null -> Unit
            }
        } else {
            Toast.makeText(context, context.getString(R.string.composer_camera_permission), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(resetToken) {
        if (resetToken != lastHandledResetToken) {
            clearEditedImageTemp()
            clearPreparedImageTemp()
            clearEditedVideoTemp()
            clearPreparedVideoTemp()
            step = ComposerStep.TypePicker
            textValue = TextFieldValue("")
            isLocationEditorOpen = false
            highlightLocationEditor = false
            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
            lastHandledResetToken = resetToken
        }
    }

    LaunchedEffect(cancelUploadToken) {
        if (cancelUploadToken != lastHandledCancelUploadToken) {
            viewModel.cancelSubmit()
            isCancelUploadDialogOpen = false
            lastHandledCancelUploadToken = cancelUploadToken
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            if (AppConfig.USE_MOCK_BACKEND) {
                keepEditedTempsForMockPost()
            } else {
                clearEditedImageTemp()
                clearPreparedImageTemp()
                clearEditedVideoTemp()
                clearPreparedVideoTemp()
            }
            isLocationEditorOpen = false
            highlightLocationEditor = false
            onPostCreated(state.createdPostId)
            viewModel.onEvent(CreatePostUiEvent.ClearMessage)
        }
    }

    LaunchedEffect(imageEditorUri, videoEditorUri) {
        onVideoEditorVisibilityChange(imageEditorUri != null || videoEditorUri != null)
    }

    LaunchedEffect(state.isLoading) {
        onUploadStateChange(state.isLoading)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (latestIsLoading) {
                viewModel.cancelSubmit()
            }
            onVideoEditorVisibilityChange(false)
            onUploadStateChange(false)
        }
    }

    BackHandler(enabled = state.isLoading) {
        requestLeaveComposer()
    }

    Box(Modifier.fillMaxSize()) {
        QuataScreen(padding) {
            val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
            val composerContentPadding = if (isLandscapeLayout) {
                PaddingValues(start = 8.dp, top = 14.dp, end = 18.dp, bottom = 18.dp)
            } else {
                PaddingValues(18.dp)
            }
            val screenTitle = when (step) {
                ComposerStep.TypePicker -> stringResource(R.string.composer_title)
                ComposerStep.Text -> stringResource(R.string.composer_text_post_title)
                ComposerStep.Image -> stringResource(R.string.composer_image_post_title)
                ComposerStep.Video -> stringResource(R.string.composer_video_post_title)
            }
            ComposerScreenLayoutContent(
                title = screenTitle,
                scrollState = composerScrollState,
                modifier = Modifier.padding(composerContentPadding),
                form = {
                when (step) {
                    ComposerStep.TypePicker -> ComposerTypePickerContent(
                        isLandscapeLayout = isLandscapeLayout,
                        strings = ComposerTypePickerStrings(
                            text = stringResource(R.string.composer_text_type),
                            image = stringResource(R.string.composer_image_type),
                            video = stringResource(R.string.composer_video_type)
                        ),
                        onText = {
                            clearEditedImageTemp()
                            clearPreparedImageTemp()
                            clearEditedVideoTemp()
                            clearPreparedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Text
                        },
                        onImage = {
                            clearEditedImageTemp()
                            clearPreparedImageTemp()
                            clearEditedVideoTemp()
                            clearPreparedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Image
                        },
                        onVideo = {
                            clearEditedImageTemp()
                            clearPreparedImageTemp()
                            clearEditedVideoTemp()
                            clearPreparedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Video
                        }
                    )
                    ComposerStep.Text -> TextPostForm(
                        isLandscapeLayout = isLandscapeLayout,
                        state = state,
                        textValue = textValue,
                        isEmojiPanelOpen = isEmojiPanelOpen,
                        onTextChange = {
                            textValue = it
                            viewModel.onEvent(CreatePostUiEvent.TextChanged(it.text))
                        },
                        onToggleEmojiPanel = { isEmojiPanelOpen = !isEmojiPanelOpen },
                        onDismissEmojiPanel = { isEmojiPanelOpen = false },
                        onTextPatternSelected = { patternId ->
                            viewModel.onEvent(CreatePostUiEvent.TextPatternSelected(patternId))
                        },
                        onEmoji = { emoji ->
                            val updated = textValue.insertAtSelection(emoji)
                            textValue = updated
                            viewModel.onEvent(CreatePostUiEvent.TextChanged(updated.text))
                        },
                        onSubmit = { submitIfAuthenticated(PostComposerType.Text) }
                    )
                    ComposerStep.Image -> ImagePostForm(
                        isLandscapeLayout = isLandscapeLayout,
                        state = state,
                        isLocationEditorOpen = isLocationEditorOpen,
                        highlightLocationEditor = highlightLocationEditor,
                        onPickImage = { galleryImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onTakePhoto = {
                            pendingCaptureTarget = CaptureTarget.Photo
                            if (context.hasCapturePermissions(CaptureTarget.Photo)) {
                                launchPhotoCapture()
                            } else {
                                capturePermissionLauncher.launch(context.capturePermissions(CaptureTarget.Photo))
                            }
                        },
                        onEditImage = {
                            state.imageUri
                                ?.let(Uri::parse)
                                ?.let { imageEditorUri = it }
                        },
                        onLocationEditorOpenChange = { isOpen ->
                            isLocationEditorOpen = isOpen
                            if (isOpen) {
                                highlightLocationEditor = false
                            }
                        },
                        onLocationChange = {
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(it))
                        },
                        onLocationPositioned = { offsetPx -> imageLocationOffsetPx = offsetPx },
                        onSubmit = { submitIfAuthenticated(PostComposerType.Image) }
                    )
                    ComposerStep.Video -> VideoPostForm(
                        isLandscapeLayout = isLandscapeLayout,
                        state = state,
                        onDescriptionChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                        onPickVideo = { galleryVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        onRecordVideo = {
                            pendingCaptureTarget = CaptureTarget.Video
                            if (context.hasCapturePermissions(CaptureTarget.Video)) {
                                launchVideoCapture()
                            } else {
                                capturePermissionLauncher.launch(context.capturePermissions(CaptureTarget.Video))
                            }
                        },
                        onEditVideo = {
                            state.videoUri
                                ?.let(Uri::parse)
                                ?.let { videoEditorUri = it }
                        },
                        onSubmit = { submitIfAuthenticated(PostComposerType.Video) }
                    )
                }

                },
                feedback = {
                    state.error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    state.successMessage?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = template.colors.accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }

        imageEditorUri?.let { sourceUri ->
            QuataImageEditorDialog(
                imageUri = sourceUri,
                onDismiss = { imageEditorUri = null },
                onEdited = { editedUri ->
                    val previousEditedUri = editedImageTempUri
                    val previousPreparedUri = preparedImageTempUri
                    editedImageTempUri = editedUri
                    preparedImageTempUri = null
                    viewModel.onEvent(CreatePostUiEvent.ImageSelected(editedUri.toString()))
                    imageEditorUri = null
                    if (previousEditedUri != editedUri) {
                        deleteEditedImageTemp(previousEditedUri)
                    }
                    if (previousPreparedUri != editedUri) {
                        deletePreparedImageTemp(previousPreparedUri)
                    }
                }
            )
        }

        videoEditorUri?.let { sourceUri ->
            QuataVideoEditorDialog(
                videoUri = sourceUri,
                onDismiss = {
                    videoEditorUri = null
                    clearPreparedVideoTemp()
                },
                onExported = { editedUri ->
                    val previousEditedUri = editedVideoTempUri
                    val previousPreparedUri = preparedVideoTempUri
                    val isPreparedOutput = editedUri == previousPreparedUri
                    editedVideoTempUri = editedUri.takeUnless { isPreparedOutput }
                    preparedVideoTempUri = editedUri.takeIf { isPreparedOutput }
                    viewModel.onEvent(CreatePostUiEvent.VideoSelected(editedUri.toString()))
                    videoEditorUri = null
                    if (previousEditedUri != editedUri) {
                        deleteEditedVideoTemp(previousEditedUri)
                    }
                    if (previousPreparedUri != editedUri) {
                        deletePreparedVideoTemp(previousPreparedUri)
                    }
                }
            )
        }

        cameraDialogMode?.let { activeCameraMode ->
            QuataCameraDialog(
                mode = activeCameraMode,
                audioEnabled = cameraDialogAudioEnabled,
                onDismiss = {
                    cameraDialogMode = null
                    pendingCaptureTarget = null
                },
                onPhotoCaptured = { uri, _, _ ->
                    cameraDialogMode = null
                    pendingCaptureTarget = null
                    selectImageForComposer(uri)
                },
                onVideoCaptured = { uri, _, _ ->
                    cameraDialogMode = null
                    pendingCaptureTarget = null
                    openVideoEditorWithPreparedSource(uri)
                }
            )
        }

        if (isCancelUploadDialogOpen) {
            AlertDialog(
                onDismissRequest = { isCancelUploadDialogOpen = false },
                title = { Text(stringResource(R.string.composer_cancel_upload_title)) },
                text = { Text(stringResource(R.string.composer_cancel_upload_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelSubmit()
                            isCancelUploadDialogOpen = false
                        }
                    ) {
                        Text(stringResource(R.string.composer_cancel_upload_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isCancelUploadDialogOpen = false }) {
                        Text(stringResource(R.string.composer_cancel_upload_keep))
                    }
                }
            )
        }
    }
}

@Composable
private fun TextPostForm(
    isLandscapeLayout: Boolean,
    state: CreatePostUiState,
    textValue: TextFieldValue,
    isEmojiPanelOpen: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onToggleEmojiPanel: () -> Unit,
    onDismissEmojiPanel: () -> Unit,
    onTextPatternSelected: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val emojiDismissState = rememberCommunityEmojiPanelDismissState(onDismissEmojiPanel)
    ComposerTextPostFormContent(
        isLandscapeLayout = isLandscapeLayout,
        textValue = textValue,
        contentTitle = stringResource(R.string.composer_content),
        placeholder = stringResource(R.string.composer_text_placeholder),
        wordCountText = stringResource(R.string.composer_word_count, state.text.length),
        minLines = if (isLandscapeLayout) 4 else 5,
        onTextChange = onTextChange,
        trailingInputAction = {
            CompactIconButton(
                onClick = onToggleEmojiPanel,
                modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState)
            ) {
                CompactIcon(
                    Icons.Filled.InsertEmoticon,
                    contentDescription = stringResource(R.string.comments_show_emojis),
                    tint = Color(0xFFFFC55C)
                )
            }
        },
        emojiPanel = {
            if (isEmojiPanelOpen) {
                Spacer(Modifier.height(10.dp))
                CommunityEmojiPanel(
                    onEmojiClick = onEmoji,
                    modifier = Modifier.trackCommunityEmojiPanelBounds(emojiDismissState)
                )
                Spacer(Modifier.height(18.dp))
            }
        },
        preview = {
            TextPatternSelectorContent(
                selectedPatternId = state.textPatternId,
                title = stringResource(R.string.composer_text_background),
                onPatternSelected = onTextPatternSelected
            )
            Spacer(Modifier.height(10.dp))
            PreviewPanel(stringResource(R.string.composer_preview)) {
                TextReelPreview(text = state.text, patternId = state.textPatternId, compact = isLandscapeLayout)
            }
        },
        publish = { PublishButton(state.isLoading, onSubmit) },
        modifier = Modifier.dismissCommunityEmojiPanelOnOutsideTap(
            isVisible = isEmojiPanelOpen,
            state = emojiDismissState
        )
    )
}

@Composable
private fun ImagePostForm(
    isLandscapeLayout: Boolean,
    state: CreatePostUiState,
    isLocationEditorOpen: Boolean,
    highlightLocationEditor: Boolean,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onEditImage: () -> Unit,
    onLocationEditorOpenChange: (Boolean) -> Unit,
    onLocationChange: (String) -> Unit,
    onLocationPositioned: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()

    @Composable
    fun ImageControlsPanel() {
        ComposerPanel(stringResource(R.string.composer_image), highlighted = true) {
            if (isLandscapeLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComposerActionButton(stringResource(R.string.composer_pick_image), Icons.Filled.PhotoLibrary, onPickImage)
                    ComposerActionButton(stringResource(R.string.composer_take_photo), Icons.Filled.PhotoCamera, onTakePhoto)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComposerActionButton(stringResource(R.string.composer_pick_image), Icons.Filled.PhotoLibrary, onPickImage, Modifier.weight(1f))
                    ComposerActionButton(stringResource(R.string.composer_take_photo), Icons.Filled.PhotoCamera, onTakePhoto, Modifier.weight(1f))
                }
            }
            if (state.imageUri != null) {
                Spacer(Modifier.height(12.dp))
                ComposerActionButton(stringResource(R.string.composer_edit_image), Icons.Filled.Edit, onEditImage)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        onLocationPositioned(coordinates.positionInRoot().y.roundToInt())
                    }
            ) {
                val shouldEmphasizeLocation = highlightLocationEditor && state.locationLabel.isNullOrBlank()
                ComposerLocationSectionContent(
                    title = stringResource(R.string.composer_location),
                    locationText = state.locationLabel ?: stringResource(R.string.composer_no_location),
                    helperText = stringResource(
                        if (shouldEmphasizeLocation) R.string.composer_location_required else R.string.composer_location_helper
                    ),
                    isHighlighted = shouldEmphasizeLocation,
                    leadingIcon = {
                        CompactIcon(Icons.Filled.LocationOn, contentDescription = null, tint = template.colors.accent)
                    },
                    editAction = { actionModifier ->
                        LocationEditButton(
                            isEditing = isLocationEditorOpen,
                            highlighted = highlightLocationEditor,
                            onClick = { onLocationEditorOpenChange(!isLocationEditorOpen) },
                            modifier = actionModifier
                        )
                    },
                    editor = if (isLocationEditorOpen) {
                        {
                            OutlinedTextField(
                        value = state.locationLabel.orEmpty(),
                        onValueChange = onLocationChange,
                        placeholder = { Text(stringResource(R.string.composer_location_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = template.colors.textPrimary,
                            unfocusedTextColor = template.colors.textPrimary,
                            focusedBorderColor = template.colors.accent,
                            unfocusedBorderColor = template.colors.divider,
                            cursorColor = template.colors.accent
                        )
                    )
                        }
                    } else null
                )
            }
        }
    }

    @Composable
    fun ImagePreviewPanel() {
        PreviewPanel(stringResource(R.string.composer_preview)) {
            if (state.imageUri != null) {
                val imageBackgroundSeed by produceState(state.imageUri.orEmpty(), state.imageUri) {
                    value = withContext(Dispatchers.IO) {
                        state.imageUri?.let { context.mediaHashSeed(Uri.parse(it)) }
                    } ?: state.imageUri.orEmpty()
                }
                ComposerFeedPreviewFrame(
                    isVideo = false,
                    description = "",
                    locationLabel = state.locationLabel,
                    compact = isLandscapeLayout,
                    backgroundSeed = imageBackgroundSeed
                ) {
                    AsyncImage(
                        model = state.imageUri,
                        contentDescription = stringResource(R.string.composer_selected_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                EmptyPreview(
                    stringResource(R.string.composer_image_preview_title),
                    stringResource(R.string.composer_image_preview_tag),
                    stringResource(R.string.composer_image_preview_body),
                    compact = isLandscapeLayout
                )
            }
        }
    }

    ComposerMediaPostFormLayoutContent(
        isLandscapeLayout = isLandscapeLayout,
        controls = { ImageControlsPanel() },
        preview = { ImagePreviewPanel() },
        publish = { PublishButton(state.isLoading, onSubmit) }
    )
}

@Composable
private fun VideoPostForm(
    isLandscapeLayout: Boolean,
    state: CreatePostUiState,
    onDescriptionChange: (String) -> Unit,
    onPickVideo: () -> Unit,
    onRecordVideo: () -> Unit,
    onEditVideo: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()

    @Composable
    fun VideoControlsPanel() {
        ComposerPanel(stringResource(R.string.composer_video), highlighted = true) {
            if (isLandscapeLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComposerActionButton(stringResource(R.string.composer_pick_video), Icons.Filled.VideoLibrary, onPickVideo)
                    ComposerActionButton(stringResource(R.string.composer_record_video), Icons.Filled.Videocam, onRecordVideo)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComposerActionButton(stringResource(R.string.composer_pick_video), Icons.Filled.VideoLibrary, onPickVideo, Modifier.weight(1f))
                    ComposerActionButton(stringResource(R.string.composer_record_video), Icons.Filled.Videocam, onRecordVideo, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                state.videoUri?.let { context.displayNameFromUriString(it) } ?: stringResource(R.string.composer_no_file),
                color = template.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.videoUri != null) {
                Spacer(Modifier.height(12.dp))
                ComposerActionButton(stringResource(R.string.video_editor_edit_video), Icons.Filled.Edit, onEditVideo)
            }
        }

        ComposerPanel(stringResource(R.string.composer_description)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = onDescriptionChange,
                placeholder = { Text(stringResource(R.string.composer_description_placeholder)) },
                minLines = if (isLandscapeLayout) 2 else 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun VideoPreviewPanel() {
        PreviewPanel(stringResource(R.string.composer_preview)) {
            val videoUri = state.videoUri
            if (videoUri != null) {
                ComposerFeedPreviewFrame(
                    isVideo = true,
                    description = state.text,
                    locationLabel = null,
                    compact = isLandscapeLayout,
                    backgroundSeed = videoUri
                ) {
                    ComposerPreviewVideoPlayer(
                        videoUri = videoUri,
                        useContainLayout = isLandscapeLayout
                    )
                }
            } else {
                EmptyPreview(
                    title = stringResource(R.string.composer_video_preview_empty),
                    tag = stringResource(R.string.composer_video_preview_tag),
                    body = state.text.ifBlank { stringResource(R.string.composer_video_preview_body) },
                    compact = isLandscapeLayout
                )
            }
        }
    }

    ComposerMediaPostFormLayoutContent(
        isLandscapeLayout = isLandscapeLayout,
        controls = { VideoControlsPanel() },
        preview = { VideoPreviewPanel() },
        publish = { PublishButton(state.isLoading, onSubmit) }
    )
}

@Composable
private fun ComposerFeedPreviewFrame(
    isVideo: Boolean,
    description: String,
    locationLabel: String?,
    compact: Boolean = false,
    mediaAspectRatio: Float = 9f / 16f,
    backgroundSeed: String? = null,
    media: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    ComposerFeedPreviewFrameContent(
        compact = compact,
        mediaAspectRatio = mediaAspectRatio,
        backgroundSeed = backgroundSeed ?: description.ifBlank { locationLabel.orEmpty() },
        media = media,
        scrim = { ComposerPreviewScrims() },
        topOverlay = {
            ComposerPreviewTopChips(
                isVideo = isVideo,
                locationLabel = locationLabel,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp)
            )
        },
        actionRail = {
            ComposerPreviewActionsContent(
                showRankLiveActions = !compact,
                labels = composerPreviewActionLabels(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 18.dp)
            )
        },
        compactLeadingActions = {
            if (compact) {
                ComposerPreviewRankLiveActionsContent(
                    labels = composerPreviewActionLabels(),
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = if (isVideo) 132.dp else 88.dp)
                )
            }
        },
        authorOverlay = {
            ComposerPreviewAuthor(
                description = description,
                locationLabel = locationLabel,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 76.dp, bottom = if (isVideo) 78.dp else 18.dp)
            )
        }
    )
}

@Composable
private fun ComposerPreviewVideoPlayer(
    videoUri: String,
    useContainLayout: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by rememberSaveable(videoUri) { mutableStateOf(false) }
    var positionMs by remember(videoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }
    var hasRenderedFirstFrame by remember(videoUri) { mutableStateOf(false) }
    var isPlayerRequested by rememberSaveable(videoUri) { mutableStateOf(false) }
    var shouldAutoPlay by rememberSaveable(videoUri) { mutableStateOf(false) }
    val posterFrame by produceState<Bitmap?>(initialValue = null, videoUri) {
        value = withContext(Dispatchers.IO) {
            context.loadComposerVideoPosterFrame(Uri.parse(videoUri))
        }
    }
    val playbackRotation by produceState(initialValue = 0, videoUri) {
        value = withContext(Dispatchers.IO) {
            context.readComposerVideoRotation(Uri.parse(videoUri)) ?: 0
        }
    }
    val player = remember(videoUri, isPlayerRequested) {
        if (!isPlayerRequested) return@remember null
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = shouldAutoPlay
            volume = 1f
            prepare()
            seekTo(0L)
        }
    }

    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
            durationMs = activePlayer.duration.takeIf { it > 0 } ?: durationMs
            isPlaying = activePlayer.isPlaying
            delay(250L)
        }
    }

    LaunchedEffect(player, shouldAutoPlay) {
        val activePlayer = player ?: return@LaunchedEffect
        if (shouldAutoPlay) {
            activePlayer.play()
            isPlaying = true
        } else {
            activePlayer.pause()
            isPlaying = false
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.takeIf { it > 0 } ?: durationMs
                    if (player.currentPosition <= 0L && !hasRenderedFirstFrame) {
                        runCatching { player.seekTo(0L) }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val activePlayer = player ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                shouldAutoPlay = false
                activePlayer.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun togglePlayback() {
        val activePlayer = player
        if (activePlayer == null) {
            shouldAutoPlay = true
            isPlayerRequested = true
            isPlaying = true
            return
        }
        if (activePlayer.isPlaying) {
            shouldAutoPlay = false
            activePlayer.pause()
            isPlaying = false
        } else {
            if (activePlayer.playbackState == Player.STATE_ENDED) {
                activePlayer.seekTo(0L)
            }
            shouldAutoPlay = true
            activePlayer.play()
            isPlaying = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val videoResizeMode = if (useContainLayout) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        val posterContentScale = if (useContainLayout) {
            ContentScale.Fit
        } else {
            ContentScale.Crop
        }
        player?.let { activePlayer ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext)
                        .inflate(R.layout.quata_feed_player_texture, null, false) as PlayerView).apply {
                        this.player = activePlayer
                        useController = false
                        resizeMode = videoResizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.useController = false
                    playerView.resizeMode = videoResizeMode
                    if (playerView.player !== activePlayer) {
                        playerView.player = activePlayer
                    }
                    playerView.findQuataTextureView()
                        ?.applyQuataVideoPlaybackTransform(playbackRotation)
                },
                onRelease = { playerView ->
                    playerView.player = null
                }
            )
        }
        posterFrame?.takeUnless { hasRenderedFirstFrame }?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                contentScale = posterContentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { togglePlayback() }
        )
        ComposerPreviewVideoControls(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPause = { togglePlayback() },
            onReplay = {
                positionMs = 0L
                shouldAutoPlay = true
                if (player == null) {
                    isPlayerRequested = true
                } else {
                    player.seekTo(0L)
                    player.play()
                }
                isPlaying = true
            },
            onSeek = { targetMs ->
                player?.seekTo(targetMs)
                positionMs = targetMs
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 78.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun ComposerPreviewVideoControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp)) {
            CompactIcon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.feed_pause) else stringResource(R.string.feed_play),
                tint = Color.White
            )
        }
        CompactIconButton(onClick = onReplay, modifier = Modifier.size(34.dp)) {
            CompactIcon(
                imageVector = Icons.Filled.Replay,
                contentDescription = stringResource(R.string.video_editor_previous),
                tint = Color.White
            )
        }
        Slider(
            value = progress,
            onValueChange = { onSeek((it * duration).toLong()) },
            enabled = durationMs > 0,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        )
        Text(
            text = "${formatComposerVideoTime(positionMs)} / ${formatComposerVideoTime(durationMs)}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun ComposerPreviewScrims() {
    ComposerPreviewScrimContent()
}

@Composable
private fun ComposerPreviewTopChips(
    isVideo: Boolean,
    locationLabel: String?,
    modifier: Modifier = Modifier
) {
    val chips = locationLabel?.takeIf { it.isNotBlank() }?.let { label ->
        listOf(if (isVideo) "\uD83D\uDCDD $label" else stringResource(R.string.feed_location_chip, label))
    }.orEmpty()
    ComposerPreviewTopChipsContent(chips = chips, modifier = modifier)
}

@Composable
private fun ComposerPreviewChip(
    text: String,
    highlighted: Boolean = false
) {
    val template = quataTheme()
    val borderColor = if (highlighted) template.colors.live else Color.White.copy(alpha = 0.22f)
    val textColor = if (highlighted) template.colors.live else Color.White
    Surface(
        color = if (highlighted) template.colors.surface.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.12f),
        contentColor = textColor,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(28.dp))
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun composerPreviewActionLabels() = ComposerPreviewActionLabels(
    like = stringResource(R.string.feed_like),
    comments = stringResource(R.string.feed_comments),
    share = stringResource(R.string.feed_share),
    report = stringResource(R.string.feed_report),
    rank = stringResource(R.string.feed_rank),
    live = stringResource(R.string.common_live)
)

@Composable
private fun ComposerPreviewAuthor(
    description: String,
    locationLabel: String?,
    modifier: Modifier = Modifier
) {
    ComposerPreviewAuthorContent(
        description = description,
        authorName = "Q\u00FCata",
        subtitle = locationLabel?.takeIf { it.isNotBlank() } ?: "Feed",
        avatar = {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(QuataOrange)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("Q\u0308", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        },
        modifier = modifier
    )
}

private fun formatComposerVideoTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Context.loadComposerVideoPosterFrame(uri: Uri): Bitmap? =
    runCatching {
        withComposerMetadataRetriever { retriever ->
            retriever.setComposerVideoSource(this, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.normalizedComposerVideoRotation()
                ?: 0
            if (uri.lastPathSegment?.startsWith("quata-edited-video-") == true &&
                (rotation == 90 || rotation == 270)
            ) {
                return@withComposerMetadataRetriever null
            }
            retriever.getScaledComposerVideoFrameAtTime(
                timeUs = 0L,
                option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                maxDimension = ComposerPreviewPosterMaxDimension
            )
        }
    }.getOrNull()

private fun Context.prepareComposerImageSource(sourceUri: Uri): Uri? {
    val outputFile = createComposerPreparedImageFile()
    return runCatching {
        val preparedUri = copyImageToFileNormalizingOrientation(sourceUri, outputFile)

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inSampleSize = 1
        }
        BitmapFactory.decodeFile(outputFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("Prepared image is not decodable")
        }
        preparedUri
    }.onFailure {
        Log.w(ComposerImageLogTag, "Could not prepare image source source=$sourceUri", it)
        outputFile.delete()
    }.getOrNull()
}

private fun Context.prepareComposerVideoSource(sourceUri: Uri): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseComposerVideoSourceDirectly(sourceUri)) {
        return sourceUri
    }

    val outputFile = createComposerPreparedVideoFile()
    return runCatching {
        remuxComposerVideoForEditor(sourceUri, outputFile)
        Uri.fromFile(outputFile)
    }.onFailure {
        Log.w(ComposerVideoLogTag, "Could not prepare video source source=$sourceUri", it)
        outputFile.delete()
    }.getOrNull()
}

private fun Context.canUseComposerVideoSourceDirectly(uri: Uri): Boolean =
    runCatching {
        withComposerMetadataRetriever { retriever ->
            retriever.setComposerVideoSource(this, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@withComposerMetadataRetriever false
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@withComposerMetadataRetriever false
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return@withComposerMetadataRetriever false
            width > 0 && height > 0 && durationMs > 0L
        }
    }.getOrDefault(false)

private fun Context.remuxComposerVideoForEditor(sourceUri: Uri, outputFile: File) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var completed = false
    try {
        extractor.setDataSource(this, sourceUri, null)
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var rotationHint = readComposerVideoRotation(sourceUri)

        val muxedTracks = linkedMapOf<Int, ComposerVideoRemuxTrack>()
        var hasVideoTrack = false
        var maxInputSize = ComposerVideoRemuxDefaultBufferSize
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.composerMimeType() ?: continue
            val isVideo = mimeType.startsWith("video/")
            val isAudio = mimeType.startsWith("audio/")
            if (!isVideo && !isAudio) continue
            if (isVideo && rotationHint == null) {
                rotationHint = format.composerRotationOrNull()
            }
            val muxerFormat = composerMuxerTrackFormat(format, mimeType, isVideo) ?: continue
            val muxedTrackIndex = muxer.addTrack(muxerFormat)
            muxedTracks[trackIndex] = ComposerVideoRemuxTrack(
                muxedTrackIndex = muxedTrackIndex,
                isVideo = isVideo,
                fallbackSampleDurationUs = format.composerFallbackSampleDurationUs(mimeType, isVideo),
                forceSyntheticVideoTimestamps = format.composerVideoTimestampsNeedRepair(isVideo)
            )
            hasVideoTrack = hasVideoTrack || isVideo
            maxInputSize = maxOf(maxInputSize, format.composerMaxInputSizeOrNull() ?: ComposerVideoRemuxDefaultBufferSize)
        }
        check(hasVideoTrack) { "No video track available" }
        check(muxedTracks.isNotEmpty()) { "No audio or video tracks available" }

        rotationHint
            ?.normalizedComposerVideoRotation()
            ?.takeIf { it == 90 || it == 180 || it == 270 }
            ?.let(muxer::setOrientationHint)
        muxedTracks.keys.forEach(extractor::selectTrack)
        muxer.start()
        muxerStarted = true

        val buffer = ByteBuffer.allocateDirect(maxInputSize.coerceAtLeast(ComposerVideoRemuxDefaultBufferSize))
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            val muxedTrack = muxedTracks[trackIndex]
            if (muxedTrack == null) {
                extractor.advance()
                continue
            }
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs < 0L) break
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val presentationTimeUs = muxedTrack.presentationTimeUs(sampleTimeUs)
            buffer.position(0)
            buffer.limit(sampleSize)
            bufferInfo.set(
                0,
                sampleSize,
                presentationTimeUs,
                extractor.sampleFlags.toMediaCodecBufferFlags()
            )
            muxer.writeSampleData(muxedTrack.muxedTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }

        muxer.stop()
        completed = true
    } finally {
        extractor.release()
        runCatching {
            if (!completed && muxerStarted) {
                muxer?.stop()
            }
        }
        runCatching { muxer?.release() }
        if (!completed) {
            runCatching { outputFile.delete() }
        }
    }
}

private fun Int.toMediaCodecBufferFlags(): Int {
    var codecFlags = 0
    if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    }
    return codecFlags
}

private fun Context.readComposerVideoRotation(uri: Uri): Int? =
    runCatching {
        withComposerMetadataRetriever { retriever ->
            retriever.setComposerVideoSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.normalizedComposerVideoRotation()
        }
    }.getOrNull()

private inline fun <T> withComposerMetadataRetriever(block: (MediaMetadataRetriever) -> T): T {
    return withQuataMediaMetadataRetriever(block)
}

private class ComposerVideoRemuxTrack(
    val muxedTrackIndex: Int,
    private val isVideo: Boolean,
    private val fallbackSampleDurationUs: Long,
    private val forceSyntheticVideoTimestamps: Boolean
) {
    private var lastSourceTimeUs: Long? = null
    private var lastPresentationTimeUs = 0L
    private var nextVideoPresentationTimeUs = 0L

    fun presentationTimeUs(sourceTimeUs: Long): Long {
        if (isVideo && forceSyntheticVideoTimestamps) {
            val presentationTimeUs = nextVideoPresentationTimeUs
            nextVideoPresentationTimeUs += fallbackSampleDurationUs
            return presentationTimeUs
        }

        val previousSourceTimeUs = lastSourceTimeUs
        val presentationTimeUs = if (previousSourceTimeUs == null) {
            0L
        } else {
            val sourceDeltaUs = sourceTimeUs - previousSourceTimeUs
            val sampleDurationUs = sourceDeltaUs.takeIf {
                it in 1L..ComposerVideoRemuxMaxTrustedSampleDeltaUs
            } ?: fallbackSampleDurationUs
            lastPresentationTimeUs + sampleDurationUs
        }
        lastSourceTimeUs = sourceTimeUs
        lastPresentationTimeUs = presentationTimeUs
        return presentationTimeUs
    }
}

private fun MediaFormat.composerFallbackSampleDurationUs(mimeType: String, isVideo: Boolean): Long {
    if (isVideo) {
        val frameRate = composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
            ?.takeIf { it in 12..120 }
            ?: ComposerVideoRemuxFallbackFrameRate
        return (1_000_000L / frameRate).coerceAtLeast(1L)
    }

    if (mimeType.contains("amr", ignoreCase = true) || mimeType == "audio/3gpp") {
        return 20_000L
    }
    val sampleRate = composerIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 }
    return sampleRate?.let { (1024L * 1_000_000L / it).coerceAtLeast(1L) } ?: 23_000L
}

private fun MediaFormat.composerVideoTimestampsNeedRepair(isVideo: Boolean): Boolean {
    if (!isVideo) return false
    val frameRate = composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it in 1..240 }
    if (frameRate != null) {
        return true
    }
    val durationUs = composerLongOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L } ?: return true
    val expectedMinDurationUs = frameRate?.let { 1_000_000L / it } ?: 1L
    return durationUs < expectedMinDurationUs
}

private fun composerMuxerTrackFormat(format: MediaFormat, mimeType: String, isVideo: Boolean): MediaFormat? =
    runCatching {
        val target = if (isVideo) {
            val width = format.composerIntegerOrNull(MediaFormat.KEY_WIDTH) ?: return@runCatching null
            val height = format.composerIntegerOrNull(MediaFormat.KEY_HEIGHT) ?: return@runCatching null
            MediaFormat.createVideoFormat(mimeType, width, height)
        } else {
            val sampleRate = format.composerIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return@runCatching null
            val channelCount = format.composerIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: return@runCatching null
            MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
        }

        format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_MAX_INPUT_SIZE)
        format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_BIT_RATE)
        format.composerCopyLongKeyTo(target, MediaFormat.KEY_DURATION)
        if (isVideo) {
            format.composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE)
                ?.takeIf { it in 1..240 }
                ?.let { target.setInteger(MediaFormat.KEY_FRAME_RATE, it) }
        } else {
            format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_AAC_PROFILE)
            format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_CHANNEL_MASK)
            format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_PCM_ENCODING)
        }
        format.composerCopyStringKeyTo(target, MediaFormat.KEY_LANGUAGE)
        for (index in 0..3) {
            format.composerCopyByteBufferKeyTo(target, "csd-$index")
        }
        target
    }.getOrNull()

private fun MediaFormat.composerMimeType(): String? =
    if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null

private fun MediaFormat.composerMaxInputSizeOrNull(): Int? =
    if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
        runCatching { getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrNull()
    } else {
        null
    }

private fun MediaFormat.composerIntegerOrNull(key: String): Int? =
    if (containsKey(key)) {
        runCatching { getInteger(key) }.getOrNull()
    } else {
        null
    }

private fun MediaFormat.composerLongOrNull(key: String): Long? =
    if (containsKey(key)) {
        runCatching { getLong(key) }.getOrNull()
    } else {
        null
    }

private fun MediaFormat.composerRotationOrNull(): Int? =
    composerIntegerOrNull(MediaFormat.KEY_ROTATION)
        ?.normalizedComposerVideoRotation()
        ?.takeIf { it == 90 || it == 180 || it == 270 }

private fun MediaFormat.composerCopyIntegerKeyTo(target: MediaFormat, key: String) {
    composerIntegerOrNull(key)?.let { target.setInteger(key, it) }
}

private fun MediaFormat.composerCopyLongKeyTo(target: MediaFormat, key: String) {
    composerLongOrNull(key)?.let { target.setLong(key, it) }
}

private fun MediaFormat.composerCopyStringKeyTo(target: MediaFormat, key: String) {
    if (!containsKey(key)) return
    runCatching { getString(key) }
        .getOrNull()
        ?.let { target.setString(key, it) }
}

private fun MediaFormat.composerCopyByteBufferKeyTo(target: MediaFormat, key: String) {
    if (!containsKey(key)) return
    val sourceBuffer = runCatching { getByteBuffer(key) }.getOrNull() ?: return
    val duplicate = sourceBuffer.duplicate()
    val copy = ByteBuffer.allocate(duplicate.remaining())
    copy.put(duplicate)
    copy.flip()
    target.setByteBuffer(key, copy)
}

private fun MediaMetadataRetriever.setComposerVideoSource(context: Context, uri: Uri) {
    when (uri.scheme) {
        "content" -> {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0L) {
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                } else {
                    setDataSource(descriptor.fileDescriptor)
                }
                return
            }
            setDataSource(context, uri)
        }
        "file" -> setDataSource(uri.path)
        else -> setDataSource(uri.toString(), emptyMap())
    }
}

private fun MediaMetadataRetriever.getScaledComposerVideoFrameAtTime(
    timeUs: Long,
    option: Int,
    maxDimension: Int
): Bitmap? {
    val targetSize = scaledComposerVideoFrameSize(maxDimension)
    val rawWidth = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    val rawHeight = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?.normalizedComposerVideoRotation()
        ?: 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
        runCatching {
            getScaledFrameAtTime(timeUs, option, targetSize.first, targetSize.second)
        }.getOrNull()
            ?.orientComposerVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
            ?.let { return it }
    }
    return getFrameAtTime(timeUs, option)
        ?.scaleComposerVideoPoster(maxDimension)
        ?.orientComposerVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
}

private fun Bitmap.orientComposerVideoFrameIfNeeded(
    rawWidth: Int,
    rawHeight: Int,
    rotationDegrees: Int
): Bitmap {
    val rotation = rotationDegrees.normalizedComposerVideoRotation()
    if (rotation != 90 && rotation != 270) return this
    if (rawWidth <= 0 || rawHeight <= 0 || width <= 0 || height <= 0) return this
    val expectedPortrait = rawHeight < rawWidth
    val bitmapPortrait = height > width
    if (expectedPortrait == bitmapPortrait) return this
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}

private fun MediaMetadataRetriever.scaledComposerVideoFrameSize(maxDimension: Int): Pair<Int, Int>? {
    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?.normalizedComposerVideoRotation()
        ?: 0
    val displayWidth = if (rotation == 90 || rotation == 270) height else width
    val displayHeight = if (rotation == 90 || rotation == 270) width else height
    val largestDimension = maxOf(displayWidth, displayHeight)
    if (largestDimension <= 0) return null
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    val targetDisplayWidth = (displayWidth * scale).roundToInt().coerceAtLeast(1)
    val targetDisplayHeight = (displayHeight * scale).roundToInt().coerceAtLeast(1)
    return if (rotation == 90 || rotation == 270) {
        targetDisplayHeight to targetDisplayWidth
    } else {
        targetDisplayWidth to targetDisplayHeight
    }
}

private fun Int.normalizedComposerVideoRotation(): Int = normalizedQuataVideoRotation()

private fun Context.mediaHashSeed(uri: Uri): String? =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return@runCatching null
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }.getOrNull()

private fun Bitmap.scaleComposerVideoPoster(maxDimension: Int): Bitmap {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) recycle()
    return scaled
}

@Composable
private fun ComposerPanel(
    title: String,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ComposerSectionPanelContent(
        title = title,
        highlighted = highlighted,
        content = content,
    )
}

@Composable
private fun PreviewPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    ComposerPanel(title = title, content = content)
}

@Composable
private fun ComposerActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ComposerActionButtonContent(
        label = text,
        icon = { CompactIcon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun LocationEditButton(
    isEditing: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    ComposerLocationEditButtonContent(
        highlighted = highlighted,
        label = stringResource(if (isEditing) R.string.composer_save_location else R.string.composer_edit_location),
        icon = {
            CompactIcon(
                if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = null,
                tint = if (highlighted) template.colors.accent else template.colors.textPrimary
            )
        },
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun TextReelPreview(text: String, patternId: String? = null, compact: Boolean = false) {
    ComposerFeedPreviewFrame(
        isVideo = false,
        description = "",
        locationLabel = null,
        compact = compact,
        backgroundSeed = text
    ) {
        ComposerTextCanvasContent(
            text = text,
            patternId = patternId,
            compact = compact,
            emptyText = stringResource(R.string.composer_text_preview_empty),
            readMoreText = stringResource(R.string.feed_read_more),
            readerDismissButton = { modifier, onDismiss ->
                CompactIconButton(onClick = onDismiss, modifier = modifier) {
                    CompactIcon(Icons.Filled.Close, stringResource(R.string.common_close), tint = Color.White)
                }
            }
        )
    }
}

@Composable
private fun EmptyPreview(title: String, tag: String, body: String, compact: Boolean = false) {
    ComposerEmptyPreviewContent(title = title, tag = tag, body = body, compact = compact)
}

@Composable
private fun PublishButton(isLoading: Boolean, onSubmit: () -> Unit) {
    ComposerPublishButtonContent(
        isLoading = isLoading,
        publishLabel = stringResource(R.string.nav_publish),
        publishingLabel = stringResource(R.string.composer_publishing),
        onSubmit = onSubmit
    )
}

private fun TextFieldValue.insertAtSelection(value: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val replaceStart = minOf(start, end)
    val replaceEnd = maxOf(start, end)
    val updatedText = text.replaceRange(replaceStart, replaceEnd, value)
    val cursor = replaceStart + value.length
    return TextFieldValue(updatedText, TextRange(cursor))
}

private fun Context.displayNameFromUriString(uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    val displayName = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return displayName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: uriString.substringAfterLast('/')
}

private fun Context.exifLocationFromUri(uri: Uri): Location? {
    return runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val latLong = ExifInterface(input).latLong ?: return null
            Location("exif").apply {
                latitude = latLong[0]
                longitude = latLong[1]
            }
        }
    }.getOrNull()
}

private fun Context.hasCapturePermissions(target: CaptureTarget?): Boolean =
    hasCameraPermission() &&
        (target != CaptureTarget.Video || hasAudioRecordPermission())

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.hasAudioRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Context.capturePermissions(target: CaptureTarget?): Array<String> =
    when {
        target == CaptureTarget.Video ->
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        else ->
            arrayOf(Manifest.permission.CAMERA)
    }

private suspend fun Context.resolvePlaceName(location: Location): String? =
    runCatching {
        val geocoder = Geocoder(this@resolvePlaceName, Locale.getDefault())
        val address = geocoder.firstAddress(location) ?: return@runCatching null
        address.readableLocationLabel()
    }.getOrNull()

private suspend fun Geocoder.firstAddress(location: Location): Address? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses.firstOrNull())
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            )
        }
    } else {
        withContext(Dispatchers.IO) {
            firstAddressBlocking(location)
        }
    }

// Android 12L and older only expose the blocking geocoder call.
@Suppress("DEPRECATION")
private fun Geocoder.firstAddressBlocking(location: Location): Address? =
    getFromLocation(location.latitude, location.longitude, 1)
        .orEmpty()
        .firstOrNull()

private fun android.location.Address.readableLocationLabel(): String? {
    val representativePlace = listOf(premises, featureName)
        .mapNotNull { it.toReadableLocationText() }
        .firstOrNull { !it.looksLikeStreetAddress() }
    val neighborhood = subLocality.toReadableLocationText()
    val city = locality.toReadableLocationText()
    val area = subAdminArea.toReadableLocationText()
    val region = adminArea.toReadableLocationText()

    val areaParts = when {
        representativePlace != null -> listOf(representativePlace, city ?: area ?: region)
        neighborhood != null -> listOf(neighborhood, city ?: area ?: region)
        city != null -> listOf(city, region ?: area)
        area != null -> listOf(area, region)
        else -> listOf(region)
    }
        .filterNotNull()
        .distinctBy { it.lowercase(Locale.ROOT) }

    return areaParts
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
        ?: countryName.toReadableLocationText()
}

private fun String?.toReadableLocationText(): String? {
    val value = this?.trim().orEmpty()
    return value.takeIf { it.isNotBlank() && !it.matches(Regex("""[-+\d.,\s]+""")) }
}

private fun String.looksLikeStreetAddress(): Boolean {
    val normalized = lowercase(Locale.ROOT)
    if (normalized.firstOrNull()?.isDigit() == true) return true
    return listOf(
        "street",
        "st.",
        "avenue",
        "ave",
        "road",
        "rd.",
        "boulevard",
        "blvd",
        "drive",
        "dr.",
        "parkway",
        "pkwy",
        "calle",
        "avenida",
        "av.",
        "carretera",
        "camino",
        "rue",
        "route",
        "chemin"
    ).any { marker -> normalized.contains(marker) }
}

private fun Context.createComposerPreparedVideoFile(): File {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
    return File(cacheDir, "$QuataPreparedVideoFilePrefix$timestamp.mp4")
}

private fun Context.createComposerPreparedImageFile(): File {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
    return File(cacheDir, "$QuataPreparedImageFilePrefix$timestamp")
}

private fun Context.deleteQuataPreparedImageTemp(uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    runCatching {
        val cache = cacheDir.canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == cache && file.name.startsWith(QuataPreparedImageFilePrefix)) {
            file.delete()
        }
    }
}

private fun Context.deleteQuataPreparedVideoTemp(uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    runCatching {
        val cache = cacheDir.canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == cache && file.name.startsWith(QuataPreparedVideoFilePrefix)) {
            file.delete()
        }
    }
}

private fun Context.deleteQuataEditedVideoTemp(uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    runCatching {
        val cache = cacheDir.canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == cache && file.name.startsWith(QuataEditedVideoFilePrefix)) {
            file.delete()
        }
    }
}

private fun Context.deleteQuataEditedImageTemp(uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    runCatching {
        val cache = cacheDir.canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == cache && file.name.startsWith(QuataEditedImageFilePrefix)) {
            file.delete()
        }
    }
}

private const val QuataPreparedImageFilePrefix = "quata-prepared-image-"
private const val QuataPreparedVideoFilePrefix = "quata-prepared-video-"
private const val QuataEditedVideoFilePrefix = "quata-edited-video-"
private const val ComposerImageLogTag = "QuataComposerImage"
private const val ComposerVideoLogTag = "QuataComposerVideo"
private const val ComposerPreviewPosterMaxDimension = 720
private const val ComposerVideoRemuxDefaultBufferSize = 1024 * 1024
private const val ComposerVideoRemuxFallbackFrameRate = 30
private const val ComposerVideoRemuxMaxTrustedSampleDeltaUs = 1_000_000L
