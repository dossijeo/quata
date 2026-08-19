@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.quata.core.media.withQuataMediaMetadataRetriever
import com.quata.core.ui.components.normalizedQuataVideoRotation
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.roundToInt

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
    evidenceImageUri: String? = null,
    evidenceLocationLabel: String? = null,
    evidencePickerSource: String? = null,
    evidencePickerOutcome: String? = null,
    evidencePickerPath: String? = null,
    viewModel: CreatePostAndroidViewModel = viewModel(
        factory = CreatePostAndroidViewModel.factory(
            repository,
            createPostRootCopyForLanguageTag(Locale.getDefault().toLanguageTag()),
            initialEvidenceImageUri = evidenceImageUri,
            initialEvidenceLocationLabel = evidenceLocationLabel,
        ),
    ),
) {
    val context = LocalContext.current
    val rootCopy = remember { createPostRootCopyForLanguageTag(Locale.getDefault().toLanguageTag()) }
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
    val evidencePicker = remember(evidencePickerSource, evidencePickerOutcome, evidencePickerPath) {
        AndroidPostComposerPickerEvidence.from(evidencePickerSource, evidencePickerOutcome, evidencePickerPath)
    }

    fun clearOwnedMedia() {
        listOfNotNull(preparedImageTempUri, editedImageTempUri).distinct().forEach(context::deleteComposerOwnedImage)
        listOfNotNull(preparedVideoTempUri, editedVideoTempUri).distinct().forEach(context::deleteComposerOwnedVideo)
        preparedImageTempUri = null
        editedImageTempUri = null
        preparedVideoTempUri = null
        editedVideoTempUri = null
    }

    fun resolveLocation(deliver: (String, Double?, Double?) -> Unit = { label, latitude, longitude ->
        viewModel.onEvent(CreatePostUiEvent.LocationResolved(label, latitude, longitude))
    }) {
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
            deliver(label, location.latitude, location.longitude)
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
        val evidenceSource = if (target == CaptureTarget.Photo) AndroidPostComposerPickerEvidence.Source.CameraImage else AndroidPostComposerPickerEvidence.Source.CameraVideo
        if (target == CaptureTarget.Photo && evidencePicker?.handle(evidenceSource) { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } == true) {
            return
        }
        if (target == CaptureTarget.Video && evidencePicker?.handle(evidenceSource) { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } == true) {
            return
        }
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
        evidencePicker?.let { picker ->
            Box(Modifier.size(1.dp).testTag("composer-picker-evidence-ready.${picker.source.testValue}.${picker.outcome}"))
        }
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
            copy = rootCopy,
            initialStep = if (evidenceImageUri != null) CreatePostStep.Image else null,
            slots = CreatePostPlatformSlots(
                pickImage = {
                    if (evidencePicker?.handle(AndroidPostComposerPickerEvidence.Source.GalleryImage) {
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(it))
                        } != true) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                captureImage = { capture(CaptureTarget.Photo) },
                editImage = { state.imageUri?.let(Uri::parse)?.let { imageEditorUri = it } },
                pickVideo = {
                    if (evidencePicker?.handle(AndroidPostComposerPickerEvidence.Source.GalleryVideo) {
                            viewModel.onEvent(CreatePostUiEvent.VideoSelected(it))
                        } != true) {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                },
                captureVideo = { capture(CaptureTarget.Video) },
                editVideo = { state.videoUri?.let(Uri::parse)?.let { videoEditorUri = it } },
                imagePreview = { uri, modifier -> AsyncImage(uri, context.getString(R.string.composer_selected_image), modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                videoPreview = { uri, contain, modifier -> AndroidComposerVideoPreview(uri, contain, modifier) },
                requestLocation = { resolved -> resolveLocation(resolved) },
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
        title = stringResource(R.string.composer_cancel_upload_title),
        message = stringResource(R.string.composer_cancel_upload_body),
        confirmLabel = stringResource(R.string.composer_cancel_upload_confirm),
        dismissLabel = stringResource(R.string.composer_cancel_upload_keep),
        onConfirm = { viewModel.cancelSubmit(); cancelDialog = false },
        onDismiss = { cancelDialog = false },
    )
}

@Composable
private fun AndroidComposerVideoPreview(uri: String, useContainLayout: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by rememberSaveable(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var hasRenderedFirstFrame by remember(uri) { mutableStateOf(false) }
    var isPlayerRequested by rememberSaveable(uri) { mutableStateOf(false) }
    var shouldAutoPlay by rememberSaveable(uri) { mutableStateOf(false) }
    val posterFrame by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { context.loadComposerVideoPosterFrame(Uri.parse(uri)) }
    }
    val playbackRotation by produceState(0, uri) {
        value = withContext(Dispatchers.IO) { context.readComposerVideoRotation(Uri.parse(uri)) ?: 0 }
    }
    val player = remember(uri, isPlayerRequested) {
        if (!isPlayerRequested) return@remember null
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = shouldAutoPlay; volume = 1f; prepare(); seekTo(0L)
        }
    }
    LaunchedEffect(player) {
        val active = player ?: return@LaunchedEffect
        while (true) {
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration.takeIf { it > 0L } ?: durationMs
            isPlaying = active.isPlaying
            delay(250L)
        }
    }
    LaunchedEffect(player, shouldAutoPlay) {
        val active = player ?: return@LaunchedEffect
        if (shouldAutoPlay) active.play() else active.pause()
        isPlaying = shouldAutoPlay
    }
    DisposableEffect(player) {
        if (player == null) onDispose { } else {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() { hasRenderedFirstFrame = true }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        durationMs = player.duration.takeIf { it > 0L } ?: durationMs
                        if (player.currentPosition <= 0L && !hasRenderedFirstFrame) runCatching { player.seekTo(0L) }
                    }
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener); player.release() }
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val active = player ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                shouldAutoPlay = false; active.pause(); isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun togglePlayback() {
        val active = player
        if (active == null) { shouldAutoPlay = true; isPlayerRequested = true; isPlaying = true; return }
        if (active.isPlaying) { shouldAutoPlay = false; active.pause(); isPlaying = false }
        else { if (active.playbackState == Player.STATE_ENDED) active.seekTo(0L); shouldAutoPlay = true; active.play(); isPlaying = true }
    }
    Box(modifier.fillMaxSize().background(Color.Transparent)) {
        val previewLayout = composerVideoPreviewLayout(useContainLayout)
        val resizeMode = if (previewLayout == ComposerVideoPreviewLayout.Contain) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        val posterScale = if (previewLayout == ComposerVideoPreviewLayout.Contain) ContentScale.Fit else ContentScale.Crop
        player?.let { active ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext).inflate(R.layout.quata_feed_player_texture, null, false) as PlayerView).apply {
                        this.player = active; useController = false; this.resizeMode = resizeMode
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { view ->
                    view.useController = false; view.resizeMode = resizeMode
                    if (view.player !== active) view.player = active
                    view.findQuataTextureView()?.applyQuataVideoPlaybackTransform(playbackRotation)
                },
                onRelease = { it.player = null },
            )
        }
        posterFrame?.takeUnless { hasRenderedFirstFrame }?.let { frame ->
            Image(frame.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = posterScale)
        }
        Box(Modifier.fillMaxSize().clickable { togglePlayback() })
        ComposerVideoPreviewControlsContent(
            isPlaying = isPlaying, positionMs = positionMs, durationMs = durationMs,
            playContentDescription = stringResource(R.string.feed_play), pauseContentDescription = stringResource(R.string.feed_pause),
            replayContentDescription = stringResource(R.string.video_editor_previous), onPlayPause = ::togglePlayback,
            onReplay = {
                positionMs = 0L; shouldAutoPlay = true
                if (player == null) isPlayerRequested = true else { player.seekTo(0L); player.play() }
                isPlaying = true
            },
            onSeek = { target -> player?.seekTo(target); positionMs = target },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 78.dp, bottom = 8.dp),
        )
    }
}

private fun Context.loadComposerVideoPosterFrame(uri: Uri): Bitmap? = runCatching {
    withComposerMetadataRetriever { retriever ->
        retriever.setComposerVideoSource(this, uri)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.normalizedComposerVideoRotation() ?: 0
        if (uri.lastPathSegment?.startsWith("quata-edited-video-") == true && (rotation == 90 || rotation == 270)) return@withComposerMetadataRetriever null
        retriever.getScaledComposerVideoFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, ComposerPreviewPosterMaxDimension)
    }
}.getOrNull()

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

private fun Context.prepareComposerVideoSource(sourceUri: Uri): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseComposerVideoSourceDirectly(sourceUri)) return sourceUri
    val outputFile = File(cacheDir, "quata-prepared-video-${System.currentTimeMillis()}.mp4")
    return runCatching { remuxComposerVideoForEditor(sourceUri, outputFile); Uri.fromFile(outputFile) }
        .onFailure { Log.w("QuataComposerVideo", "Could not prepare video source source=$sourceUri", it); outputFile.delete() }.getOrNull()
}

private fun Context.canUseComposerVideoSourceDirectly(uri: Uri): Boolean = runCatching {
    withComposerMetadataRetriever { retriever ->
        retriever.setComposerVideoSource(this, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@withComposerMetadataRetriever false
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@withComposerMetadataRetriever false
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return@withComposerMetadataRetriever false
        isValidComposerVideoMetadata(width, height, durationMs)
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
            if (isVideo) rotationHint = composerVideoRotationHint(rotationHint, format.composerRotationOrNull())
            val muxerFormat = composerMuxerTrackFormat(format, mimeType, isVideo) ?: continue
            muxedTracks[trackIndex] = ComposerVideoRemuxTrack(
                muxedTrackIndex = muxer.addTrack(muxerFormat), isVideo = isVideo,
                fallbackSampleDurationUs = format.composerFallbackSampleDurationUs(mimeType, isVideo),
                forceSyntheticVideoTimestamps = format.composerVideoTimestampsNeedRepair(isVideo),
            )
            hasVideoTrack = hasVideoTrack || isVideo
            maxInputSize = maxOf(maxInputSize, format.composerMaxInputSizeOrNull() ?: ComposerVideoRemuxDefaultBufferSize)
        }
        check(hasVideoTrack) { "No video track available" }
        check(muxedTracks.isNotEmpty()) { "No audio or video tracks available" }
        rotationHint?.normalizedComposerVideoRotation()?.takeIf { it == 90 || it == 180 || it == 270 }?.let(muxer::setOrientationHint)
        muxedTracks.keys.forEach(extractor::selectTrack)
        muxer.start(); muxerStarted = true
        val buffer = ByteBuffer.allocateDirect(maxInputSize.coerceAtLeast(ComposerVideoRemuxDefaultBufferSize))
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            val muxedTrack = muxedTracks[trackIndex]
            if (muxedTrack == null) { extractor.advance(); continue }
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs < 0L) break
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            buffer.position(0); buffer.limit(sampleSize)
            bufferInfo.set(0, sampleSize, muxedTrack.presentationTimeUs(sampleTimeUs), extractor.sampleFlags.toMediaCodecBufferFlags())
            muxer.writeSampleData(muxedTrack.muxedTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
        muxer.stop(); completed = true
    } finally {
        extractor.release()
        runCatching { if (!completed && muxerStarted) muxer?.stop() }
        runCatching { muxer?.release() }
        if (!completed) runCatching { outputFile.delete() }
    }
}

internal class ComposerVideoRemuxTrack(
    val muxedTrackIndex: Int,
    private val isVideo: Boolean,
    private val fallbackSampleDurationUs: Long,
    private val forceSyntheticVideoTimestamps: Boolean,
) {
    private var lastSourceTimeUs: Long? = null
    private var lastPresentationTimeUs = 0L
    private var nextVideoPresentationTimeUs = 0L
    fun presentationTimeUs(sourceTimeUs: Long): Long {
        if (isVideo && forceSyntheticVideoTimestamps) return nextVideoPresentationTimeUs.also { nextVideoPresentationTimeUs += fallbackSampleDurationUs }
        val previous = lastSourceTimeUs
        val presentation = if (previous == null) 0L else lastPresentationTimeUs + ((sourceTimeUs - previous).takeIf { it in 1L..ComposerVideoRemuxMaxTrustedSampleDeltaUs } ?: fallbackSampleDurationUs)
        lastSourceTimeUs = sourceTimeUs; lastPresentationTimeUs = presentation
        return presentation
    }
}

private fun Int.toMediaCodecBufferFlags(): Int {
    var result = 0
    if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0) result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
    if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) result = result or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    return result
}

private fun MediaFormat.composerFallbackSampleDurationUs(mimeType: String, isVideo: Boolean): Long {
    if (isVideo) {
        val frameRate = composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it in 12..120 } ?: ComposerVideoRemuxFallbackFrameRate
        return (1_000_000L / frameRate).coerceAtLeast(1L)
    }
    if (mimeType.contains("amr", true) || mimeType == "audio/3gpp") return 20_000L
    val sampleRate = composerIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 }
    return sampleRate?.let { (1024L * 1_000_000L / it).coerceAtLeast(1L) } ?: 23_000L
}

internal fun composerVideoTimestampsNeedRepair(frameRate: Int?, durationUs: Long?): Boolean {
    if (frameRate != null && frameRate in 1..240) return true
    val duration = durationUs?.takeIf { it > 0L } ?: return true
    val expectedMin = frameRate?.let { 1_000_000L / it } ?: 1L
    return duration < expectedMin
}

internal enum class ComposerVideoPreviewLayout { Contain, Crop }
internal fun composerVideoPreviewLayout(useContainLayout: Boolean) = if (useContainLayout) ComposerVideoPreviewLayout.Contain else ComposerVideoPreviewLayout.Crop
internal fun isValidComposerVideoMetadata(width: Int, height: Int, durationMs: Long) = width > 0 && height > 0 && durationMs > 0L
internal fun composerVideoRotationHint(metadataRotation: Int?, formatRotation: Int?): Int? = metadataRotation ?: formatRotation

private fun MediaFormat.composerVideoTimestampsNeedRepair(isVideo: Boolean): Boolean = isVideo && composerVideoTimestampsNeedRepair(
    composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE), composerLongOrNull(MediaFormat.KEY_DURATION),
)

private fun composerMuxerTrackFormat(format: MediaFormat, mimeType: String, isVideo: Boolean): MediaFormat? = runCatching {
    val target = if (isVideo) {
        MediaFormat.createVideoFormat(mimeType, format.composerIntegerOrNull(MediaFormat.KEY_WIDTH) ?: return@runCatching null, format.composerIntegerOrNull(MediaFormat.KEY_HEIGHT) ?: return@runCatching null)
    } else {
        MediaFormat.createAudioFormat(mimeType, format.composerIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return@runCatching null, format.composerIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: return@runCatching null)
    }
    format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_MAX_INPUT_SIZE)
    format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_BIT_RATE)
    format.composerCopyLongKeyTo(target, MediaFormat.KEY_DURATION)
    if (isVideo) format.composerIntegerOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it in 1..240 }?.let { target.setInteger(MediaFormat.KEY_FRAME_RATE, it) }
    else {
        format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_AAC_PROFILE); format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_CHANNEL_MASK); format.composerCopyIntegerKeyTo(target, MediaFormat.KEY_PCM_ENCODING)
    }
    format.composerCopyStringKeyTo(target, MediaFormat.KEY_LANGUAGE)
    for (index in 0..3) format.composerCopyByteBufferKeyTo(target, "csd-$index")
    target
}.getOrNull()

private fun MediaFormat.composerMimeType(): String? = if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null
private fun MediaFormat.composerMaxInputSizeOrNull(): Int? = if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) runCatching { getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrNull() else null
private fun MediaFormat.composerIntegerOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
private fun MediaFormat.composerLongOrNull(key: String): Long? = if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
private fun MediaFormat.composerRotationOrNull(): Int? = composerIntegerOrNull(MediaFormat.KEY_ROTATION)?.normalizedComposerVideoRotation()?.takeIf { it == 90 || it == 180 || it == 270 }
private fun MediaFormat.composerCopyIntegerKeyTo(target: MediaFormat, key: String) { composerIntegerOrNull(key)?.let { target.setInteger(key, it) } }
private fun MediaFormat.composerCopyLongKeyTo(target: MediaFormat, key: String) { composerLongOrNull(key)?.let { target.setLong(key, it) } }
private fun MediaFormat.composerCopyStringKeyTo(target: MediaFormat, key: String) { if (containsKey(key)) runCatching { getString(key) }.getOrNull()?.let { target.setString(key, it) } }
private fun MediaFormat.composerCopyByteBufferKeyTo(target: MediaFormat, key: String) {
    if (!containsKey(key)) return
    val source = runCatching { getByteBuffer(key) }.getOrNull() ?: return
    val duplicate = source.duplicate(); val copy = ByteBuffer.allocate(duplicate.remaining()); copy.put(duplicate); copy.flip(); target.setByteBuffer(key, copy)
}

private fun Context.readComposerVideoRotation(uri: Uri): Int? = runCatching {
    withComposerMetadataRetriever { retriever -> retriever.setComposerVideoSource(this, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.normalizedComposerVideoRotation() }
}.getOrNull()

private inline fun <T> withComposerMetadataRetriever(block: (MediaMetadataRetriever) -> T): T =
    withQuataMediaMetadataRetriever(block)

private fun MediaMetadataRetriever.setComposerVideoSource(context: Context, uri: Uri) {
    when (uri.scheme) {
        "content" -> {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0L) setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length) else setDataSource(descriptor.fileDescriptor)
                return
            }
            setDataSource(context, uri)
        }
        "file" -> setDataSource(uri.path)
        else -> setDataSource(uri.toString(), emptyMap())
    }
}

private fun MediaMetadataRetriever.getScaledComposerVideoFrameAtTime(timeUs: Long, option: Int, maxDimension: Int): Bitmap? {
    val targetSize = scaledComposerVideoFrameSize(maxDimension)
    val rawWidth = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    val rawHeight = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.normalizedComposerVideoRotation() ?: 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
        runCatching { getScaledFrameAtTime(timeUs, option, targetSize.first, targetSize.second) }.getOrNull()
            ?.orientComposerVideoFrameIfNeeded(rawWidth, rawHeight, rotation)?.let { return it }
    }
    return getFrameAtTime(timeUs, option)?.scaleComposerVideoPoster(maxDimension)?.orientComposerVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
}

private fun Bitmap.orientComposerVideoFrameIfNeeded(rawWidth: Int, rawHeight: Int, rotationDegrees: Int): Bitmap {
    val rotation = rotationDegrees.normalizedComposerVideoRotation()
    if (rotation != 90 && rotation != 270 || rawWidth <= 0 || rawHeight <= 0 || width <= 0 || height <= 0) return this
    val expectedPortrait = rawHeight < rawWidth
    if (expectedPortrait == (height > width)) return this
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    if (rotated !== this) recycle()
    return rotated
}

private fun MediaMetadataRetriever.scaledComposerVideoFrameSize(maxDimension: Int): Pair<Int, Int>? {
    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.normalizedComposerVideoRotation() ?: 0
    val displayWidth = if (rotation == 90 || rotation == 270) height else width
    val displayHeight = if (rotation == 90 || rotation == 270) width else height
    val scale = maxDimension.toFloat() / maxOf(displayWidth, displayHeight).toFloat()
    val targetWidth = (displayWidth * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (displayHeight * scale).roundToInt().coerceAtLeast(1)
    return if (rotation == 90 || rotation == 270) targetHeight to targetWidth else targetWidth to targetHeight
}

private fun Int.normalizedComposerVideoRotation(): Int = normalizedQuataVideoRotation()

private data class AndroidPostComposerPickerEvidence(
    val source: Source,
    val outcome: String,
    val path: String?,
) {
    fun handle(expectedSource: Source, consume: (String) -> Unit): Boolean {
        if (source != expectedSource) return false
        if (outcome != "success") return true
        val reference = path
            ?.takeIf { it.isNotBlank() }
            ?.let { rawPath ->
                val file = File(rawPath)
                if (file.isFile && file.length() > 0L) Uri.fromFile(file).toString() else rawPath
            }
            ?: "evidence://${expectedSource.testValue}"
        consume(reference)
        return true
    }

    enum class Source {
        GalleryImage,
        CameraImage,
        GalleryVideo,
        CameraVideo,
    }

    companion object {
        fun from(source: String?, outcome: String?, path: String?): AndroidPostComposerPickerEvidence? {
            val resolvedSource = when (source?.lowercase(Locale.US)) {
                "gallery-image" -> Source.GalleryImage
                "camera-image" -> Source.CameraImage
                "gallery-video" -> Source.GalleryVideo
                "camera-video" -> Source.CameraVideo
                else -> return null
            }
            val resolvedOutcome = outcome?.lowercase(Locale.US)?.takeIf {
                it in setOf("success", "cancelled", "failure", "unsupported")
            } ?: "success"
            return AndroidPostComposerPickerEvidence(resolvedSource, resolvedOutcome, path)
        }
    }
}

private val AndroidPostComposerPickerEvidence.Source.testValue: String
    get() = when (this) {
        AndroidPostComposerPickerEvidence.Source.GalleryImage -> "gallery-image"
        AndroidPostComposerPickerEvidence.Source.CameraImage -> "camera-image"
        AndroidPostComposerPickerEvidence.Source.GalleryVideo -> "gallery-video"
        AndroidPostComposerPickerEvidence.Source.CameraVideo -> "camera-video"
    }

private fun Bitmap.scaleComposerVideoPoster(maxDimension: Int): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxDimension) return this
    val scale = maxDimension.toFloat() / largest.toFloat()
    val scaled = Bitmap.createScaledBitmap(this, (width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1), true)
    if (scaled !== this) recycle()
    return scaled
}

private const val ComposerPreviewPosterMaxDimension = 720
private const val ComposerVideoRemuxDefaultBufferSize = 1024 * 1024
private const val ComposerVideoRemuxFallbackFrameRate = 30
private const val ComposerVideoRemuxMaxTrustedSampleDeltaUs = 1_000_000L

private fun android.content.Context.deleteComposerOwnedImage(uri: Uri) = deleteComposerOwned(uri, "quata-prepared-image-", "quata-edited-image-")
private fun android.content.Context.deleteComposerOwnedVideo(uri: Uri) = deleteComposerOwned(uri, "quata-prepared-video-", "quata-edited-video-")
private fun android.content.Context.deleteComposerOwned(uri: Uri, vararg prefixes: String) {
    if (uri.scheme != "file") return
    runCatching {
        val file = File(uri.path.orEmpty()).canonicalFile
        if (file.parentFile == cacheDir.canonicalFile && prefixes.any(file.name::startsWith)) file.delete()
    }
}
