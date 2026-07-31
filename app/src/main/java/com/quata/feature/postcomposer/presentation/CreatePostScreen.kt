@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.accessibility.CriticalControlsAccessibilityCatalog
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode
import com.quata.core.ui.components.QuataConfirmationDialogContent
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class CaptureTarget { Photo, Video }

/** Android now owns only platform acquisition/edit/render slots around the common root. */
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
    viewModel: CreatePostAndroidViewModel = viewModel(factory = CreatePostAndroidViewModel.factory(repository)),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var imageEditorUri by remember { mutableStateOf<Uri?>(null) }
    var videoEditorUri by remember { mutableStateOf<Uri?>(null) }
    var cameraMode by remember { mutableStateOf<QuataCameraMode?>(null) }
    var pendingCapture by remember { mutableStateOf<CaptureTarget?>(null) }
    var cancelDialog by remember { mutableStateOf(false) }

    fun resolveLocation() {
        scope.launch {
            if (permissionService.status(PlatformPermission.Location) != PermissionStatus.Granted &&
                permissionService.request(PlatformPermission.Location) != PermissionStatus.Granted
            ) return@launch
            val location = (locationService.currentLocation() as? PlatformResult.Success)?.value ?: return@launch
            val label = withContext(Dispatchers.IO) {
                runCatching {
                    Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()?.let { address ->
                            listOfNotNull(address.subLocality, address.locality, address.adminArea).distinct().joinToString(", ")
                        }?.takeIf(String::isNotBlank)
                }.getOrNull()
            } ?: "${location.latitude}, ${location.longitude}"
            viewModel.onEvent(CreatePostUiEvent.LocationResolved(
                label, location.latitude, location.longitude,
            ))
        }
    }
    fun selectImage(uri: Uri?) {
        uri ?: return
        viewModel.onEvent(CreatePostUiEvent.ImageSelected(uri.toString()))
        imageEditorUri = uri
        resolveLocation()
    }
    fun selectVideo(uri: Uri?) {
        uri ?: return
        viewModel.onEvent(CreatePostUiEvent.VideoSelected(uri.toString()))
        videoEditorUri = uri
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::selectImage)
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::selectVideo)
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) cameraMode = if (pendingCapture == CaptureTarget.Photo) QuataCameraMode.Photo else QuataCameraMode.Video
    }
    fun capture(target: CaptureTarget) {
        pendingCapture = target
        val required = buildList {
            add(Manifest.permission.CAMERA)
            if (target == CaptureTarget.Video) add(Manifest.permission.RECORD_AUDIO)
        }
        if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            cameraMode = if (target == CaptureTarget.Photo) QuataCameraMode.Photo else QuataCameraMode.Video
        } else permissions.launch(required.toTypedArray())
    }

    LaunchedEffect(resetToken) { if (resetToken > 0) viewModel.onEvent(CreatePostUiEvent.ClearDraft) }
    LaunchedEffect(cancelUploadToken) { if (cancelUploadToken > 0) viewModel.cancelSubmit() }
    LaunchedEffect(state.isLoading) { onUploadStateChange(state.isLoading) }
    LaunchedEffect(imageEditorUri, videoEditorUri) { onVideoEditorVisibilityChange(imageEditorUri != null || videoEditorUri != null) }
    DisposableEffect(Unit) { onDispose { onUploadStateChange(false); onVideoEditorVisibilityChange(false) } }
    BackHandler(state.isLoading) { cancelDialog = true }

    QuataScreen(padding) {
        CreatePostRoot(
            viewModel = viewModel.commonViewModel,
            accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(Locale.getDefault().toLanguageTag()),
            isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape,
            canPublish = canPublish,
            onAuthRequired = onAuthRequired,
            onPostCreated = onPostCreated,
            onBack = { viewModel.onEvent(CreatePostUiEvent.ClearDraft) },
            slots = CreatePostPlatformSlots(
                pickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                captureImage = { capture(CaptureTarget.Photo) },
                editImage = { state.imageUri?.let(Uri::parse)?.let { imageEditorUri = it } },
                pickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                captureVideo = { capture(CaptureTarget.Video) },
                editVideo = { state.videoUri?.let(Uri::parse)?.let { videoEditorUri = it } },
                imagePreview = { uri, modifier -> AsyncImage(uri, context.getString(R.string.composer_selected_image), modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                videoPreview = { uri, modifier -> AndroidComposerVideoPreview(uri, modifier) },
            ),
        )
    }

    imageEditorUri?.let { uri ->
        QuataImageEditorDialog(
            imageUri = uri,
            onDismiss = { imageEditorUri = null },
            onEdited = { edited ->
                viewModel.onEvent(CreatePostUiEvent.ImageSelected(edited.toString()))
                imageEditorUri = null
            },
        )
    }
    videoEditorUri?.let { uri ->
        QuataVideoEditorDialog(
            videoUri = uri,
            onDismiss = { videoEditorUri = null },
            onExported = { edited ->
                viewModel.onEvent(CreatePostUiEvent.VideoSelected(edited.toString()))
                videoEditorUri = null
            },
        )
    }
    cameraMode?.let { mode ->
        QuataCameraDialog(
            mode = mode,
            audioEnabled = mode == QuataCameraMode.Video,
            onDismiss = { cameraMode = null; pendingCapture = null },
            onPhotoCaptured = { uri, _, _ -> cameraMode = null; pendingCapture = null; selectImage(uri) },
            onVideoCaptured = { uri, _, _ -> cameraMode = null; pendingCapture = null; selectVideo(uri) },
        )
    }
    if (cancelDialog) QuataConfirmationDialogContent(
        title = context.getString(R.string.composer_cancel_upload_title),
        message = context.getString(R.string.composer_cancel_upload_body),
        confirmLabel = context.getString(R.string.composer_cancel_upload_confirm),
        dismissLabel = context.getString(R.string.composer_cancel_upload_keep),
        onConfirm = { viewModel.cancelSubmit(); cancelDialog = false },
        onDismiss = { cancelDialog = false },
    )
}

@Composable
private fun AndroidComposerVideoPreview(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(player, lifecycleOwner) { onDispose(player::release) }
    AndroidView(
        factory = { PlayerView(it).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
        update = { it.player = player },
        modifier = modifier.fillMaxSize(),
    )
}
