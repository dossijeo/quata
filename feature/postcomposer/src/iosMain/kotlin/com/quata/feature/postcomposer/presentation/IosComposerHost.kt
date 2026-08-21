package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.accessibility.CriticalControlsAccessibilityCatalog
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.LocationService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.VideoThumbnailService
import com.quata.feature.postcomposer.domain.PostComposerRepository
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

class IosComposerHostDependencies(
    val repository: PostComposerRepository,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
    val videoThumbnails: VideoThumbnailService,
    val location: LocationService,
    val permissions: PermissionService,
    val languageTag: String?,
    val onClose: () -> Unit,
    val initialImageReference: String? = null,
    val initialLocationLabel: String? = null,
    val videoEditorNativeDriver: IosPostVideoEditorNativeDriver = UnsupportedIosPostVideoEditorNativeDriver,
)

fun createIosComposerHostDependencies(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    location: LocationService,
    permissions: PermissionService,
    languageTag: String?,
    onClose: () -> Unit,
): IosComposerHostDependencies = IosComposerHostDependencies(repository, filePicker, cameraCapture, videoThumbnails, location, permissions, languageTag, onClose)

fun createIosComposerHostDependenciesWithVideoEditor(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    location: LocationService,
    permissions: PermissionService,
    languageTag: String?,
    onClose: () -> Unit,
    videoEditorNativeDriver: IosPostVideoEditorNativeDriver,
): IosComposerHostDependencies = IosComposerHostDependencies(
    repository,
    filePicker,
    cameraCapture,
    videoThumbnails,
    location,
    permissions,
    languageTag,
    onClose,
    videoEditorNativeDriver = videoEditorNativeDriver,
)

fun createIosComposerHostDependenciesWithInitialDraft(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    location: LocationService,
    permissions: PermissionService,
    languageTag: String?,
    onClose: () -> Unit,
    initialImageReference: String?,
    initialLocationLabel: String?,
): IosComposerHostDependencies = IosComposerHostDependencies(
    repository,
    filePicker,
    cameraCapture,
    videoThumbnails,
    location,
    permissions,
    languageTag,
    onClose,
    initialImageReference,
    initialLocationLabel,
)

fun createIosComposerHostDependenciesWithInitialDraftAndVideoEditor(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    location: LocationService,
    permissions: PermissionService,
    languageTag: String?,
    onClose: () -> Unit,
    initialImageReference: String?,
    initialLocationLabel: String?,
    videoEditorNativeDriver: IosPostVideoEditorNativeDriver,
): IosComposerHostDependencies = IosComposerHostDependencies(
    repository,
    filePicker,
    cameraCapture,
    videoThumbnails,
    location,
    permissions,
    languageTag,
    onClose,
    initialImageReference,
    initialLocationLabel,
    videoEditorNativeDriver,
)

fun QuataComposerViewController(dependencies: IosComposerHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme { IosPostComposerHost(dependencies) }
}

private suspend fun PlatformResult<List<PlatformFile>>.dispatchIosComposerMediaResult(
    viewModel: CreatePostViewModel,
    copy: CreatePostRootCopy,
    onSuccess: suspend (PlatformFile) -> Unit,
) {
    when (this) {
        is PlatformResult.Success -> {
            val file = value.firstOrNull { it.reference.isNotBlank() }
            if (file != null) onSuccess(file) else viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaSelectionFailed))
        }
        is PlatformResult.Failure -> viewModel.onEvent(
            CreatePostUiEvent.MediaSelectionFailed(reason.iosComposerMediaFailureMessage(copy)),
        )
        PlatformResult.Unsupported -> viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaUnsupported))
        PlatformResult.Cancelled -> viewModel.onEvent(CreatePostUiEvent.ClearMediaError)
    }
}

private suspend fun PlatformResult<PlatformFile>.dispatchIosComposerMediaResult(
    viewModel: CreatePostViewModel,
    copy: CreatePostRootCopy,
    onSuccess: suspend (PlatformFile) -> Unit,
) {
    when (this) {
        is PlatformResult.Success -> {
            if (value.reference.isNotBlank()) onSuccess(value) else viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaSelectionFailed))
        }
        is PlatformResult.Failure -> viewModel.onEvent(
            CreatePostUiEvent.MediaSelectionFailed(reason.iosComposerMediaFailureMessage(copy)),
        )
        PlatformResult.Unsupported -> viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaUnsupported))
        PlatformResult.Cancelled -> viewModel.onEvent(CreatePostUiEvent.ClearMediaError)
    }
}

private fun String?.iosComposerMediaFailureMessage(copy: CreatePostRootCopy): String =
    when (this) {
        CreatePostMediaPermissionDeniedReason -> copy.mediaPermissionDenied
        null, "" -> copy.mediaSelectionFailed
        else -> this
    }

/** Thin UIKit wrapper around the one common composer root. */
@Composable
private fun IosPostComposerHost(dependencies: IosComposerHostDependencies) {
    val copy = createPostRootCopyForLanguageTag(dependencies.languageTag)
    val viewModel = remember(dependencies.repository, copy) {
        CreatePostViewModel(dependencies.repository, messages = copy.viewModelMessages()).also { model ->
            dependencies.initialLocationLabel?.takeIf(String::isNotBlank)
                ?.let { model.onEvent(CreatePostUiEvent.LocationLabelChanged(it)) }
            dependencies.initialImageReference?.takeIf(String::isNotBlank)
                ?.let { model.onEvent(CreatePostUiEvent.ImageSelected(it)) }
        }
    }
    val scope = rememberCoroutineScope()
    val filePicker = remember(dependencies.filePicker) {
        IosPostComposerEvidenceFilePicker.wrapIfRequested(dependencies.filePicker)
    }
    var imageFile by remember {
        mutableStateOf(
            dependencies.initialImageReference?.takeIf(String::isNotBlank)
                ?.let { PlatformFile(it, "post-publish-evidence-image.png", "image/png") },
        )
    }
    var imageEditorFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoEditorFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoThumbnail by remember { mutableStateOf<PlatformFile?>(null) }
    var isLandscapeLayout by remember { mutableStateOf(false) }

    fun releaseVideoThumbnail() {
        iosComposerThumbnailToRelease(videoThumbnail)?.let(::releaseIosComposerVideoThumbnail)
        videoThumbnail = null
    }
    fun selectVideo(source: FilePickerSource) {
        scope.launch {
            if (source == FilePickerSource.Camera &&
                !iosPostComposerEvidenceShouldBypassNativePermission(source) &&
                !dependencies.permissions.ensureCreatePostMediaPermissions(CreatePostMediaPermissionRequest.CameraVideo)
            ) {
                viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaPermissionDenied))
                return@launch
            }
            filePicker.pick(FilePickerRequest(listOf("video/*"), source = source)).dispatchIosComposerMediaResult(viewModel, copy) { file ->
                    releaseVideoThumbnail()
                    videoFile = file
                    viewModel.onEvent(CreatePostUiEvent.VideoSelected(file.reference))
                    videoThumbnail = (dependencies.videoThumbnails.createThumbnail(file).toIosComposerVideoPreview() as? IosComposerVideoPreview.Thumbnail)?.file
            }
        }
    }
    DisposableEffect(Unit) { onDispose(::releaseVideoThumbnail) }

    BoxWithConstraints {
        val currentIsLandscapeLayout = maxWidth > maxHeight
        SideEffect { isLandscapeLayout = currentIsLandscapeLayout }
        CreatePostRoot(
            viewModel = viewModel,
            accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(dependencies.languageTag),
            isLandscapeLayout = currentIsLandscapeLayout,
            onAuthRequired = dependencies.onClose,
            onBack = dependencies.onClose,
            onPostCreated = { dependencies.onClose() },
            copy = copy,
            initialStep = if (dependencies.initialImageReference != null) CreatePostStep.Image else null,
            slots = CreatePostPlatformSlots(
            pickImage = {
                scope.launch {
                    filePicker.pick(FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery)).dispatchIosComposerMediaResult(viewModel, copy) { file ->
                            imageFile = file
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                    }
                }
            },
            captureImage = {
                scope.launch {
                    val evidenceResult = iosPostComposerEvidenceCameraCapturePhoto()
                    if (evidenceResult != null) {
                        evidenceResult.dispatchIosComposerMediaResult(viewModel, copy) { file ->
                            imageFile = file
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                        }
                        return@launch
                    }
                    if (!dependencies.permissions.ensureCreatePostMediaPermissions(CreatePostMediaPermissionRequest.CameraImage)) {
                        viewModel.onEvent(CreatePostUiEvent.MediaSelectionFailed(copy.mediaPermissionDenied))
                        return@launch
                    }
                    val result = dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))
                    result.dispatchIosComposerMediaResult(viewModel, copy) { file ->
                        imageFile = file
                        viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                    }
                }
            },
            editImage = ({ imageFile?.let { imageEditorFile = it } }),
            pickVideo = { selectVideo(FilePickerSource.Gallery) },
            captureVideo = { selectVideo(FilePickerSource.Camera) },
            editVideo = ({ videoFile?.let { videoEditorFile = it } }),
            imagePreview = { _, modifier -> imageFile?.let { IosComposerLocalImagePreview(it, modifier) } },
            videoPreview = { _, _, modifier -> videoThumbnail?.let { IosComposerLocalImagePreview(it, modifier) } },
            requestLocation = { resolved ->
                scope.launch {
                    if (dependencies.permissions.status(PlatformPermission.Location) != PermissionStatus.Granted &&
                        dependencies.permissions.request(PlatformPermission.Location) != PermissionStatus.Granted
                    ) return@launch
                    val location = (dependencies.location.currentLocation() as? PlatformResult.Success)?.value
                        ?: return@launch
                    resolved(
                        iosComposerCoordinateLabel(location.latitude, location.longitude),
                        location.latitude,
                        location.longitude,
                    )
                }
            },
            ),
        )
    }
    imageEditorFile?.let { current ->
        IosPostImageEditor(
            source = current,
            onDismiss = { imageEditorFile = null },
            onEdited = { edited ->
                imageEditorFile = null
                imageFile = edited
                viewModel.onEvent(CreatePostUiEvent.ImageSelected(edited.reference))
            },
        )
    }
    videoEditorFile?.let { current ->
        IosPostVideoEditor(
            source = current,
            nativeDriver = dependencies.videoEditorNativeDriver,
            isLandscapeLayout = isLandscapeLayout,
            onDismiss = { videoEditorFile = null },
            onEdited = { edited ->
                videoEditorFile = null
                releaseVideoThumbnail()
                videoFile = edited
                viewModel.onEvent(CreatePostUiEvent.VideoSelected(edited.reference))
                scope.launch {
                    videoThumbnail = (dependencies.videoThumbnails.createThumbnail(edited).toIosComposerVideoPreview() as? IosComposerVideoPreview.Thumbnail)?.file
                }
            },
        )
    }
}

private fun iosComposerCoordinateLabel(latitude: Double, longitude: Double): String = "$latitude, $longitude"

private const val PostComposerPickerFixtureOptIn = "I_ACCEPT_IOS_POST_COMPOSER_PICKER_FIXTURE"

private class IosPostComposerEvidenceFilePicker(
    private val delegate: FilePickerService,
) : FilePickerService {
    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = pick(
        FilePickerRequest(acceptedMimeTypes, allowMultiple, FilePickerSource.Documents),
    )

    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        iosPostComposerEvidencePickerOutcome(request.source)?.let { return it }
        iosPostComposerEvidencePickedFile(request.source)?.let { return PlatformResult.Success(listOf(it)) }
        return delegate.pick(request)
    }

    companion object {
        fun wrapIfRequested(delegate: FilePickerService): FilePickerService =
            if (iosPostComposerEvidenceFixtureOptedIn()) IosPostComposerEvidenceFilePicker(delegate) else delegate
    }
}

private fun iosPostComposerEvidenceCameraCapturePhoto(): PlatformResult<PlatformFile>? =
    iosPostComposerEvidencePickerOutcome(FilePickerSource.Camera)
        ?.let {
            when (it) {
                is PlatformResult.Success -> it.value.firstOrNull()?.let { file -> PlatformResult.Success(file) }
                    ?: PlatformResult.Failure("post_composer_camera_capture_empty")
                is PlatformResult.Failure -> it
                PlatformResult.Cancelled -> PlatformResult.Cancelled
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }
        }
        ?: iosPostComposerEvidencePickedFile(FilePickerSource.Camera)?.let { PlatformResult.Success(it) }

private fun iosPostComposerEvidencePickerOutcome(source: FilePickerSource): PlatformResult<List<PlatformFile>>? {
    val environment = NSProcessInfo.processInfo.environment
    if (!iosPostComposerEvidenceFixtureOptedIn(environment)) return null
    if (environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_SOURCE")?.lowercase() != source.iosPostComposerEvidenceSourceName()) {
        return null
    }
    return when (environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME")?.lowercase()) {
        "cancelled" -> PlatformResult.Cancelled
        "failure" -> PlatformResult.Failure(
            environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_REASON")
                ?: "post_composer_picker_e2e_failure",
        )
        "unsupported" -> PlatformResult.Unsupported
        "permission-denied" -> PlatformResult.Failure(CreatePostMediaPermissionDeniedReason)
        else -> null
    }
}

private fun iosPostComposerEvidenceShouldBypassNativePermission(source: FilePickerSource): Boolean {
    val environment = NSProcessInfo.processInfo.environment
    if (!iosPostComposerEvidenceFixtureOptedIn(environment)) return false
    if (environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_SOURCE")?.lowercase() != source.iosPostComposerEvidenceSourceName()) {
        return false
    }
    return environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_OUTCOME")?.isNotBlank() == true ||
        environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_PATH")?.isNotBlank() == true
}

private fun iosPostComposerEvidencePickedFile(source: FilePickerSource): PlatformFile? {
    val environment = NSProcessInfo.processInfo.environment
    if (!iosPostComposerEvidenceFixtureOptedIn(environment)) return null
    if (environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_SOURCE")?.lowercase() != source.iosPostComposerEvidenceSourceName()) {
        return null
    }
    val path = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_PATH")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val reference = if (path.startsWith("file://")) path else NSURL.fileURLWithPath(path).absoluteString ?: path
    val name = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_NAME")
        ?: path.substringAfterLast('/').ifBlank { "post-composer-picker-fixture" }
    val mimeType = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_MIME")
        ?: when (source) {
            FilePickerSource.Documents -> "application/octet-stream"
            FilePickerSource.Gallery,
            FilePickerSource.Camera -> "image/png"
        }
    return PlatformFile(reference = reference, displayName = name, mimeType = mimeType)
}

private fun FilePickerSource.iosPostComposerEvidenceSourceName(): String = when (this) {
    FilePickerSource.Documents -> "document"
    FilePickerSource.Gallery -> "gallery"
    FilePickerSource.Camera -> "camera"
}

private fun iosPostComposerEvidenceFixtureOptedIn(
    environment: Map<Any?, *> = NSProcessInfo.processInfo.environment,
): Boolean =
    environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_PICKER_FIXTURE_OPT_IN") == PostComposerPickerFixtureOptIn

private fun Map<Any?, *>.iosPostComposerFixtureValue(key: String): String? =
    this[key]?.toString()?.takeIf(String::isNotBlank)
