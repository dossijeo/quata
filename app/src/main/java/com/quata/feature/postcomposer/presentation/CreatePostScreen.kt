package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
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
import com.quata.core.ui.components.CommunityEmojiPanel
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.imageeditor.QuataEditedImageFilePrefix
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    resetToken: Int,
    cancelUploadToken: Int = 0,
    canPublish: Boolean,
    onAuthRequired: () -> Unit,
    onPostCreated: (String?) -> Unit,
    onVideoEditorVisibilityChange: (Boolean) -> Unit = {},
    onUploadStateChange: (Boolean) -> Unit = {},
    viewModel: CreatePostViewModel = viewModel(factory = CreatePostViewModel.factory(repository))
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
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var imageEditorUri by remember { mutableStateOf<Uri?>(null) }
    var editedImageTempUri by remember { mutableStateOf<Uri?>(null) }
    var videoEditorUri by remember { mutableStateOf<Uri?>(null) }
    var editedVideoTempUri by remember { mutableStateOf<Uri?>(null) }
    val latestEditedImageTempUri by rememberUpdatedState(editedImageTempUri)
    val latestEditedVideoTempUri by rememberUpdatedState(editedVideoTempUri)
    var pendingCaptureTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var isCancelUploadDialogOpen by remember { mutableStateOf(false) }
    val latestIsLoading by rememberUpdatedState(state.isLoading)

    fun deleteEditedVideoTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataEditedVideoTemp(uri)
    }

    fun deleteEditedImageTemp(uri: Uri?) {
        uri ?: return
        context.deleteQuataEditedImageTemp(uri)
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
        editedImageTempUri = null
        editedVideoTempUri = null
    }

    fun submitIfAuthenticated(type: PostComposerType) {
        if (type == PostComposerType.Image && state.imageUri != null && state.locationLabel.isNullOrBlank()) {
            isLocationEditorOpen = true
            highlightLocationEditor = true
            scope.launch {
                val target = (composerScrollState.value + imageLocationOffsetPx - 120).coerceAtLeast(0)
                composerScrollState.animateScrollTo(target)
            }
            return
        }
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
            val label = context.resolvePlaceName(location) ?: location.toLocationLabel()
            viewModel.onEvent(CreatePostUiEvent.LocationResolved(label, location.latitude, location.longitude))
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            context.lastKnownLocation()?.let { resolveLocation(it) }
        }
    }

    fun openImageEditorWithOriginal(uri: Uri?) {
        uri ?: return
        viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(""))
        isLocationEditorOpen = false
        highlightLocationEditor = false
        val exifLocation = context.exifLocationFromUri(uri)
        if (exifLocation != null) {
            resolveLocation(exifLocation)
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        imageEditorUri = uri
    }

    val galleryImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        openImageEditorWithOriginal(uri)
    }
    val cameraImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            openImageEditorWithOriginal(pendingImageUri)
        }
    }
    val galleryVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { videoEditorUri = it }
    }
    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        if (saved) pendingVideoUri?.let { videoEditorUri = it }
    }
    val launchPhotoCapture = {
        val uri = context.createMediaUri("quata_photo_", "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pendingImageUri = uri
        uri?.let { cameraImageLauncher.launch(it) }
    }
    val launchVideoCapture = {
        val uri = context.createMediaUri("quata_video_", "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        pendingVideoUri = uri
        uri?.let { cameraVideoLauncher.launch(it) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
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
            clearEditedVideoTemp()
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
                clearEditedVideoTemp()
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
            deleteEditedImageTemp(latestEditedImageTempUri)
            deleteEditedVideoTemp(latestEditedVideoTempUri)
        }
    }

    BackHandler(enabled = state.isLoading) {
        requestLeaveComposer()
    }

    Box(Modifier.fillMaxSize()) {
        QuataScreen(padding) {
            val screenTitle = when (step) {
                ComposerStep.TypePicker -> stringResource(R.string.composer_title)
                ComposerStep.Text -> stringResource(R.string.composer_text_post_title)
                ComposerStep.Image -> stringResource(R.string.composer_image_post_title)
                ComposerStep.Video -> stringResource(R.string.composer_video_post_title)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(composerScrollState)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = screenTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = template.colors.textPrimary
                )
                Spacer(Modifier.height(28.dp))

                when (step) {
                    ComposerStep.TypePicker -> TypePicker(
                        onText = {
                            clearEditedImageTemp()
                            clearEditedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Text
                        },
                        onImage = {
                            clearEditedImageTemp()
                            clearEditedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Image
                        },
                        onVideo = {
                            clearEditedImageTemp()
                            clearEditedVideoTemp()
                            textValue = TextFieldValue("")
                            isLocationEditorOpen = false
                            highlightLocationEditor = false
                            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                            step = ComposerStep.Video
                        }
                    )
                    ComposerStep.Text -> TextPostForm(
                        state = state,
                        textValue = textValue,
                        isEmojiPanelOpen = isEmojiPanelOpen,
                        onTextChange = {
                            textValue = it
                            viewModel.onEvent(CreatePostUiEvent.TextChanged(it.text))
                        },
                        onToggleEmojiPanel = { isEmojiPanelOpen = !isEmojiPanelOpen },
                        onDismissEmojiPanel = { isEmojiPanelOpen = false },
                        onEmoji = { emoji ->
                            val updated = textValue.insertAtSelection(emoji)
                            textValue = updated
                            viewModel.onEvent(CreatePostUiEvent.TextChanged(updated.text))
                        },
                        onSubmit = { submitIfAuthenticated(PostComposerType.Text) }
                    )
                    ComposerStep.Image -> ImagePostForm(
                        state = state,
                        isLocationEditorOpen = isLocationEditorOpen,
                        highlightLocationEditor = highlightLocationEditor,
                        onPickImage = { galleryImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onTakePhoto = {
                            pendingCaptureTarget = CaptureTarget.Photo
                            if (context.hasCameraPermission()) {
                                launchPhotoCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                        state = state,
                        onDescriptionChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                        onPickVideo = { galleryVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        onRecordVideo = {
                            pendingCaptureTarget = CaptureTarget.Video
                            if (context.hasCameraPermission()) {
                                launchVideoCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

                state.error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                state.successMessage?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = template.colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        imageEditorUri?.let { sourceUri ->
            QuataImageEditorDialog(
                imageUri = sourceUri,
                onDismiss = { imageEditorUri = null },
                onEdited = { editedUri ->
                    val previousEditedUri = editedImageTempUri
                    editedImageTempUri = editedUri
                    viewModel.onEvent(CreatePostUiEvent.ImageSelected(editedUri.toString()))
                    imageEditorUri = null
                    if (previousEditedUri != editedUri) {
                        deleteEditedImageTemp(previousEditedUri)
                    }
                }
            )
        }

        videoEditorUri?.let { sourceUri ->
            QuataVideoEditorDialog(
                videoUri = sourceUri,
                onDismiss = { videoEditorUri = null },
                onExported = { editedUri ->
                    val previousEditedUri = editedVideoTempUri
                    editedVideoTempUri = editedUri
                    viewModel.onEvent(CreatePostUiEvent.VideoSelected(editedUri.toString()))
                    videoEditorUri = null
                    if (previousEditedUri != editedUri) {
                        deleteEditedVideoTemp(previousEditedUri)
                    }
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
private fun TypePicker(
    onText: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TypeCard(stringResource(R.string.composer_text_type), Icons.Filled.Edit, onText)
        TypeCard(stringResource(R.string.composer_image_type), Icons.Filled.PhotoCamera, onImage)
        TypeCard(stringResource(R.string.composer_video_type), Icons.Filled.Videocam, onVideo)
    }
}

@Composable
private fun TypeCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .border(1.dp, template.colors.divider, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .border(1.dp, template.colors.selectedBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(icon, contentDescription = null, tint = template.colors.accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text(label.uppercase(), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.title)
        }
    }
}

@Composable
private fun TextPostForm(
    state: CreatePostUiState,
    textValue: TextFieldValue,
    isEmojiPanelOpen: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onToggleEmojiPanel: () -> Unit,
    onDismissEmojiPanel: () -> Unit,
    onEmoji: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val emojiDismissState = rememberCommunityEmojiPanelDismissState(onDismissEmojiPanel)
    val template = quataTheme()
    Column(
        modifier = Modifier.dismissCommunityEmojiPanelOnOutsideTap(
            isVisible = isEmojiPanelOpen,
            state = emojiDismissState
        )
    ) {
        ComposerPanel(stringResource(R.string.composer_content), highlighted = true) {
            OutlinedTextField(
                value = textValue,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        stringResource(R.string.composer_text_placeholder),
                        color = template.colors.textSecondary
                    )
                },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = template.colors.textPrimary,
                    unfocusedTextColor = template.colors.textPrimary,
                    focusedBorderColor = template.colors.accent,
                    unfocusedBorderColor = template.colors.divider,
                    cursorColor = template.colors.accent
                ),
                trailingIcon = {
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
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(stringResource(R.string.composer_word_count, state.text.length), color = template.colors.textSecondary)
            }
        }
        if (isEmojiPanelOpen) {
            Spacer(Modifier.height(10.dp))
            CommunityEmojiPanel(
                onEmojiClick = onEmoji,
                modifier = Modifier.trackCommunityEmojiPanelBounds(emojiDismissState)
            )
        }
        PreviewPanel(stringResource(R.string.composer_preview)) {
            TextReelPreview(state.text)
        }
        PublishButton(state.isLoading, onSubmit)
    }
}

@Composable
private fun ImagePostForm(
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
    val template = quataTheme()
    ComposerPanel(stringResource(R.string.composer_image), highlighted = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComposerActionButton(stringResource(R.string.composer_pick_image), Icons.Filled.PhotoLibrary, onPickImage, Modifier.weight(1f))
            ComposerActionButton(stringResource(R.string.composer_take_photo), Icons.Filled.PhotoCamera, onTakePhoto, Modifier.weight(1f))
        }
        if (state.imageUri != null) {
            Spacer(Modifier.height(12.dp))
            ComposerActionButton(stringResource(R.string.video_editor_crop), Icons.Filled.Edit, onEditImage)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onLocationPositioned(coordinates.positionInRoot().y.roundToInt())
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactIcon(Icons.Filled.LocationOn, contentDescription = null, tint = template.colors.accent)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.composer_location),
                        color = template.colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = state.locationLabel ?: stringResource(R.string.composer_no_location),
                        color = template.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                LocationEditButton(
                    isEditing = isLocationEditorOpen,
                    highlighted = highlightLocationEditor,
                    onClick = { onLocationEditorOpenChange(!isLocationEditorOpen) }
                )
            }
            if (isLocationEditorOpen) {
                Spacer(Modifier.height(12.dp))
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
            val shouldEmphasizeLocation = highlightLocationEditor && state.locationLabel.isNullOrBlank()
            Text(
                text = stringResource(
                    if (shouldEmphasizeLocation) R.string.composer_location_required else R.string.composer_location_helper
                ),
                color = if (shouldEmphasizeLocation) {
                    template.colors.accent
                } else {
                    template.colors.textSecondary
                },
                fontSize = 12.sp,
                fontWeight = if (shouldEmphasizeLocation) {
                    FontWeight.ExtraBold
                } else {
                    FontWeight.Medium
                },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    PreviewPanel(stringResource(R.string.composer_preview)) {
        if (state.imageUri != null) {
            ComposerFeedPreviewFrame(
                isVideo = false,
                description = "",
                locationLabel = state.locationLabel
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
                stringResource(R.string.composer_image_preview_body)
            )
        }
    }
    PublishButton(state.isLoading, onSubmit)
}

@Composable
private fun VideoPostForm(
    state: CreatePostUiState,
    onDescriptionChange: (String) -> Unit,
    onPickVideo: () -> Unit,
    onRecordVideo: () -> Unit,
    onEditVideo: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()
    ComposerPanel(stringResource(R.string.composer_video), highlighted = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComposerActionButton(stringResource(R.string.composer_pick_video), Icons.Filled.VideoLibrary, onPickVideo, Modifier.weight(1f))
            ComposerActionButton(stringResource(R.string.composer_record_video), Icons.Filled.Videocam, onRecordVideo, Modifier.weight(1f))
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
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
    PreviewPanel(stringResource(R.string.composer_preview)) {
        if (state.videoUri != null) {
            ComposerFeedPreviewFrame(
                isVideo = true,
                description = state.text,
                locationLabel = null
            ) {
                ComposerPreviewVideoPlayer(videoUri = state.videoUri)
            }
        } else {
            EmptyPreview(
                title = stringResource(R.string.composer_video_preview_empty),
                tag = stringResource(R.string.composer_video_preview_tag),
                body = state.text.ifBlank { stringResource(R.string.composer_video_preview_body) }
            )
        }
    }
    PublishButton(state.isLoading, onSubmit)
}

@Composable
private fun ComposerFeedPreviewFrame(
    isVideo: Boolean,
    description: String,
    locationLabel: String?,
    media: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    ) {
        media()
        ComposerPreviewScrims()
        ComposerPreviewTopChips(
            isVideo = isVideo,
            locationLabel = locationLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 14.dp)
        )
        ComposerPreviewActions(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 18.dp)
        )
        ComposerPreviewAuthor(
            description = description,
            locationLabel = locationLabel,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 76.dp, bottom = if (isVideo) 78.dp else 18.dp)
        )
    }
}

@Composable
private fun ComposerPreviewVideoPlayer(videoUri: String) {
    val context = LocalContext.current
    val template = quataTheme()
    val playerBackground = template.colors.surfaceAlt.toArgb()
    var isPlaying by rememberSaveable(videoUri) { mutableStateOf(false) }
    var positionMs by remember(videoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }
    var hasRenderedFirstFrame by remember(videoUri) { mutableStateOf(false) }
    val posterFrame by produceState<Bitmap?>(initialValue = null, videoUri) {
        value = withContext(Dispatchers.IO) {
            context.loadComposerVideoPosterFrame(Uri.parse(videoUri))
        }
    }
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            volume = 1f
            prepare()
            seekTo(0L)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            delay(250L)
        }
    }

    DisposableEffect(player) {
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

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
            isPlaying = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(template.colors.surfaceAlt)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(playerBackground)
                    setShutterBackgroundColor(playerBackground)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.useController = false
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                view.setBackgroundColor(playerBackground)
                view.setShutterBackgroundColor(playerBackground)
                if (view.player !== player) {
                    view.player = player
                }
            }
        )
        posterFrame?.takeUnless { hasRenderedFirstFrame }?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
                player.seekTo(0L)
                positionMs = 0L
                player.play()
                isPlaying = true
            },
            onSeek = { targetMs ->
                player.seekTo(targetMs)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.34f),
                    0.28f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
    )
}

@Composable
private fun ComposerPreviewTopChips(
    isVideo: Boolean,
    locationLabel: String?,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        locationLabel?.takeIf { it.isNotBlank() }?.let { label ->
            ComposerPreviewChip(text = stringResource(R.string.feed_location_chip, label))
        }
        ComposerPreviewChip(text = stringResource(R.string.feed_rank_chip, 3, 0), highlighted = true)
        ComposerPreviewChip(text = stringResource(R.string.common_live), highlighted = true)
        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, template.colors.live, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(R.string.feed_mute),
                    tint = template.colors.live,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
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
private fun ComposerPreviewActions(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ComposerPreviewActionButton(Icons.Filled.FavoriteBorder, stringResource(R.string.feed_like), "0")
        ComposerPreviewActionButton(Icons.Filled.ChatBubble, stringResource(R.string.feed_comments), "0")
        ComposerPreviewActionButton(Icons.Filled.Share, stringResource(R.string.feed_share))
        ComposerPreviewActionButton(Icons.Filled.Flag, stringResource(R.string.feed_report))
    }
}

@Composable
private fun ComposerPreviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    count: String? = null
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CompactIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            count?.let {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ComposerPreviewAuthor(
    description: String,
    locationLabel: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        description.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
            Spacer(Modifier.height(9.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Q\u00FCata",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = locationLabel?.takeIf { it.isNotBlank() } ?: "Feed",
                    color = QuataOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatComposerVideoTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Context.loadComposerVideoPosterFrame(uri: Uri): Bitmap? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setComposerVideoSource(this, uri)
            retriever.getScaledComposerVideoFrameAtTime(
                timeUs = 0L,
                option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                maxDimension = ComposerPreviewPosterMaxDimension
            )
        }
    }.getOrNull()

private fun MediaMetadataRetriever.setComposerVideoSource(context: Context, uri: Uri) {
    if (uri.scheme == "content" || uri.scheme == "file") {
        setDataSource(context, uri)
    } else {
        setDataSource(uri.toString(), emptyMap())
    }
}

private fun MediaMetadataRetriever.getScaledComposerVideoFrameAtTime(
    timeUs: Long,
    option: Int,
    maxDimension: Int
): Bitmap? {
    val targetSize = scaledComposerVideoFrameSize(maxDimension)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
        runCatching {
            getScaledFrameAtTime(timeUs, option, targetSize.first, targetSize.second)
        }.getOrNull()?.let { return it }
    }
    return getFrameAtTime(timeUs, option)?.scaleComposerVideoPoster(maxDimension)
}

private fun MediaMetadataRetriever.scaledComposerVideoFrameSize(maxDimension: Int): Pair<Int, Int>? {
    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
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
    val template = quataTheme()
    val panelColor = if (highlighted) template.colors.surfaceRaised else template.colors.surface
    val contentColor = template.colors.textPrimary
    val borderColor = template.colors.divider
    Surface(
        color = panelColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), color = contentColor.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
    Spacer(Modifier.height(18.dp))
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .compactButtonMinSize(),
        shape = RoundedCornerShape(9.dp),
        contentPadding = CompactButtonContentPadding
    ) {
        CompactIcon(icon, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LocationEditButton(
    isEditing: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val template = quataTheme()
    val backgroundColor by animateColorAsState(
        targetValue = if (highlighted) template.colors.accent.copy(alpha = 0.22f) else Color.Transparent
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) template.colors.accent else template.colors.divider
    )
    Surface(
        color = backgroundColor,
        contentColor = if (highlighted) template.colors.accent else template.colors.textPrimary,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .height(40.dp)
            .compactButtonMinSize()
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIcon(
                if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = null,
                tint = if (highlighted) template.colors.accent else template.colors.textPrimary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(if (isEditing) R.string.composer_save_location else R.string.composer_edit_location),
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun TextReelPreview(text: String) {
    val seedText = remember(text) { text.cleanTextCanvasSeedBody() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textCanvasBrush(seedText))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.ifBlank { stringResource(R.string.composer_text_preview_empty) },
            color = Color.White,
            fontSize = if (text.isBlank()) 20.sp else 34.sp,
            lineHeight = if (text.isBlank()) 28.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPreview(title: String, tag: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF101827))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Surface(color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))) {
            Text(tag, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(body, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PublishButton(isLoading: Boolean, onSubmit: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isLoading) {
        progress = 0f
        while (isLoading) {
            delay(180L)
            progress = (progress + 0.018f).coerceAtMost(0.92f)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .compactButtonMinSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (isLoading) Color(0xFFFFB45E) else QuataOrange)
            .clickable(enabled = !isLoading, onClick = onSubmit),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .align(Alignment.CenterStart)
                    .background(Color(0xFFE86F12))
            )
        }
        Text(
            if (isLoading) stringResource(R.string.composer_publishing) else stringResource(R.string.nav_publish),
            color = Color.Black,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
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

@Suppress("MissingPermission")
private fun Context.lastKnownLocation(): Location? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getProviders(true)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Location.toLocationLabel(): String = "%.5f, %.5f".format(latitude, longitude)

private suspend fun Context.resolvePlaceName(location: Location): String? = withContext(Dispatchers.IO) {
    runCatching {
        val geocoder = Geocoder(this@resolvePlaceName, Locale.getDefault())
        @Suppress("DEPRECATION")
        val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            .orEmpty()
            .firstOrNull()
            ?: return@runCatching null

        listOf(
            address.subLocality,
            address.premises,
            address.featureName,
            address.locality,
            address.subAdminArea,
            address.adminArea
        ).firstOrNull { candidate ->
            !candidate.isNullOrBlank() && !candidate.matches(Regex("""[-\d.,\s]+"""))
        }?.trim()
    }.getOrNull()
}

private fun Context.createMediaUri(prefix: String, mimeType: String, collection: Uri): Uri? {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$prefix$timestamp")
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
    }
    return contentResolver.insert(collection, values)
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

private const val QuataEditedVideoFilePrefix = "quata-edited-video-"
private const val ComposerPreviewPosterMaxDimension = 720
