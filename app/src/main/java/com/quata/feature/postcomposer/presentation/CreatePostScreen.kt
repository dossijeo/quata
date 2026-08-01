@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.media.copyImageToFileNormalizingOrientation
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
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
    onBack: () -> Unit,
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
    var preparedImageTempUri by remember { mutableStateOf<Uri?>(null) }
    var editedImageTempUri by remember { mutableStateOf<Uri?>(null) }
    var preparedVideoTempUri by remember { mutableStateOf<Uri?>(null) }
    var editedVideoTempUri by remember { mutableStateOf<Uri?>(null) }

    fun clearOwnedMedia() {
        listOfNotNull(preparedImageTempUri, editedImageTempUri).distinct().forEach(context::deleteComposerOwnedImage)
        listOfNotNull(preparedVideoTempUri, editedVideoTempUri).distinct().forEach(context::deleteComposerOwnedVideo)
        preparedImageTempUri = null
        editedImageTempUri = null
        preparedVideoTempUri = null
        editedVideoTempUri = null
    }

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
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { context.prepareComposerImageSource(uri) } ?: return@launch
            preparedImageTempUri?.let(context::deleteComposerOwnedImage)
            editedImageTempUri?.let(context::deleteComposerOwnedImage)
            preparedImageTempUri = prepared
            editedImageTempUri = null
            viewModel.onEvent(CreatePostUiEvent.ImageSelected(prepared.toString()))
            val exif = withContext(Dispatchers.IO) { context.exifLocationFromUri(prepared) }
            if (exif != null) {
                val label = withContext(Dispatchers.IO) { context.locationLabel(exif) }
                viewModel.onEvent(CreatePostUiEvent.LocationResolved(label, exif.latitude, exif.longitude))
            } else resolveLocation()
            imageEditorUri = prepared
        }
    }
    fun selectVideo(uri: Uri?) {
        uri ?: return
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { context.prepareComposerVideoSource(uri) } ?: return@launch
            preparedVideoTempUri?.let(context::deleteComposerOwnedVideo)
            editedVideoTempUri?.let(context::deleteComposerOwnedVideo)
            preparedVideoTempUri = prepared.takeIf { it.scheme == "file" }
            editedVideoTempUri = null
            viewModel.onEvent(CreatePostUiEvent.VideoSelected(prepared.toString()))
            videoEditorUri = prepared
        }
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

    LaunchedEffect(state.isLoading) { onUploadStateChange(state.isLoading) }
    LaunchedEffect(imageEditorUri, videoEditorUri) { onVideoEditorVisibilityChange(imageEditorUri != null || videoEditorUri != null) }
    DisposableEffect(Unit) { onDispose { onUploadStateChange(false); onVideoEditorVisibilityChange(false); clearOwnedMedia() } }
    BackHandler(state.isLoading) { cancelDialog = true }

    QuataScreen(padding) {
        CreatePostRoot(
            viewModel = viewModel.commonViewModel,
            accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(Locale.getDefault().toLanguageTag()),
            isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape,
            canPublish = canPublish,
            onAuthRequired = onAuthRequired,
            onPostCreated = onPostCreated,
            onBack = onBack,
            resetToken = resetToken,
            cancelUploadToken = cancelUploadToken,
            copy = createPostRootCopyForLanguageTag(Locale.getDefault().toLanguageTag()),
            slots = CreatePostPlatformSlots(
                pickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                captureImage = { capture(CaptureTarget.Photo) },
                editImage = { state.imageUri?.let(Uri::parse)?.let { imageEditorUri = it } },
                pickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                captureVideo = { capture(CaptureTarget.Video) },
                editVideo = { state.videoUri?.let(Uri::parse)?.let { videoEditorUri = it } },
                imagePreview = { uri, modifier -> AsyncImage(uri, context.getString(R.string.composer_selected_image), modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                videoPreview = { uri, modifier -> AndroidComposerVideoPreview(uri, modifier) },
                clearOwnedMedia = ::clearOwnedMedia,
            ),
        )
    }

    imageEditorUri?.let { uri ->
        QuataImageEditorDialog(
            imageUri = uri,
            onDismiss = { imageEditorUri = null },
            onEdited = { edited ->
                editedImageTempUri?.let(context::deleteComposerOwnedImage)
                if (preparedImageTempUri != edited) preparedImageTempUri?.let(context::deleteComposerOwnedImage)
                preparedImageTempUri = null
                editedImageTempUri = edited
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
                editedVideoTempUri?.let(context::deleteComposerOwnedVideo)
                if (preparedVideoTempUri != edited) preparedVideoTempUri?.let(context::deleteComposerOwnedVideo)
                preparedVideoTempUri = null
                editedVideoTempUri = edited
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
    var rotation by remember(uri) { mutableStateOf(0) }
    LaunchedEffect(uri) { rotation = withContext(Dispatchers.IO) { context.readComposerVideoRotation(Uri.parse(uri)) } }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
        update = { view ->
            view.player = player
            view.post { view.findQuataTextureView()?.applyQuataVideoPlaybackTransform(rotation) }
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun android.content.Context.locationLabel(location: Location): String = runCatching {
    Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
        ?.firstOrNull()?.let { listOfNotNull(it.subLocality, it.locality, it.adminArea).distinct().joinToString(", ") }
        ?.takeIf(String::isNotBlank)
}.getOrNull() ?: "${location.latitude}, ${location.longitude}"

private fun android.content.Context.exifLocationFromUri(uri: Uri): Location? = runCatching {
    contentResolver.openInputStream(uri)?.use { input ->
        val coordinates = ExifInterface(input).latLong ?: return null
        Location("exif").apply { latitude = coordinates[0]; longitude = coordinates[1] }
    }
}.getOrNull()

private fun android.content.Context.prepareComposerImageSource(source: Uri): Uri? {
    val output = File(cacheDir, "quata-prepared-image-${System.currentTimeMillis()}.jpg")
    return runCatching { copyImageToFileNormalizingOrientation(source, output) }
        .onFailure { Log.w("QuataComposerImage", "Could not prepare $source", it); output.delete() }.getOrNull()
}

private fun android.content.Context.prepareComposerVideoSource(source: Uri): Uri? {
    val output = File(cacheDir, "quata-prepared-video-${System.currentTimeMillis()}.mp4")
    return runCatching {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(this, source, null)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            readComposerVideoRotation(source).takeIf { it != 0 }?.let(muxer::setOrientationHint)
            val tracks = mutableMapOf<Int, Int>()
            var bufferSize = 1024 * 1024
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    tracks[index] = muxer.addTrack(format)
                    extractor.selectTrack(index)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(tracks.keys.any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true })
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (extractor.sampleTrackIndex >= 0) {
                val sourceTrack = extractor.sampleTrackIndex
                val targetTrack = tracks[sourceTrack]
                if (targetTrack == null) { extractor.advance(); continue }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime.coerceAtLeast(0), extractor.sampleFlags)
                muxer.writeSampleData(targetTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally { extractor.release(); runCatching { muxer?.release() } }
        Uri.fromFile(output)
    }.onFailure { Log.w("QuataComposerVideo", "Could not prepare $source", it); output.delete() }.getOrNull()
}

private fun android.content.Context.readComposerVideoRotation(uri: Uri): Int = runCatching {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(this, uri)
        ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 360 + 360) % 360
    }
}.getOrDefault(0)

private fun android.content.Context.deleteComposerOwnedImage(uri: Uri) = deleteComposerOwned(uri, "quata-prepared-image-", "quata-edited-image-")
private fun android.content.Context.deleteComposerOwnedVideo(uri: Uri) = deleteComposerOwned(uri, "quata-prepared-video-", "quata-edited-video-")
private fun android.content.Context.deleteComposerOwned(uri: Uri, vararg prefixes: String) {
    if (uri.scheme != "file") return
    runCatching {
        val file = File(uri.path.orEmpty()).canonicalFile
        if (file.parentFile == cacheDir.canonicalFile && prefixes.any(file.name::startsWith)) file.delete()
    }
}
