package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

fun QuataComposerViewController(dependencies: IosComposerHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme { IosPostComposerHost(dependencies) }
}

private fun PlatformResult<List<PlatformFile>>.composerSelectedFileOrNull(): PlatformFile? =
    (this as? PlatformResult.Success)?.value?.firstOrNull()

private fun PlatformResult<PlatformFile>.composerCapturedFileOrNull(): PlatformFile? =
    (this as? PlatformResult.Success)?.value

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
    var videoFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoThumbnail by remember { mutableStateOf<PlatformFile?>(null) }

    fun releaseVideoThumbnail() {
        iosComposerThumbnailToRelease(videoThumbnail)?.let(::releaseIosComposerVideoThumbnail)
        videoThumbnail = null
    }
    fun selectVideo(source: FilePickerSource) {
        scope.launch {
            filePicker.pick(FilePickerRequest(listOf("video/*"), source = source))
                .composerSelectedFileOrNull()?.let { file ->
                    releaseVideoThumbnail()
                    videoFile = file
                    viewModel.onEvent(CreatePostUiEvent.VideoSelected(file.reference))
                    videoThumbnail = (dependencies.videoThumbnails.createThumbnail(file).toIosComposerVideoPreview() as? IosComposerVideoPreview.Thumbnail)?.file
                }
        }
    }
    DisposableEffect(Unit) { onDispose(::releaseVideoThumbnail) }

    CreatePostRoot(
        viewModel = viewModel,
        accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(dependencies.languageTag),
        isLandscapeLayout = false,
        onAuthRequired = dependencies.onClose,
        onBack = dependencies.onClose,
        onPostCreated = { dependencies.onClose() },
        copy = copy,
        initialStep = if (dependencies.initialImageReference != null) CreatePostStep.Image else null,
        slots = CreatePostPlatformSlots(
            pickImage = {
                scope.launch {
                    filePicker.pick(FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery))
                        .composerSelectedFileOrNull()?.let { file ->
                            imageFile = file
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                        }
                }
            },
            captureImage = {
                scope.launch {
                    val result = iosPostComposerEvidenceCameraCapturePhoto()
                        ?: dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))
                    result.composerCapturedFileOrNull()?.let { file ->
                        imageFile = file
                        viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                    }
                }
            },
            editImage = if (iosPostComposerImageEditorEvidenceOptedIn()) {{
                imageFile?.let { current ->
                    val edited = iosPostComposerImageEditorEvidenceEditedFile(current)
                    imageFile = edited
                    viewModel.onEvent(CreatePostUiEvent.ImageSelected(edited.reference))
                }
            }} else null,
            pickVideo = { selectVideo(FilePickerSource.Gallery) },
            captureVideo = { selectVideo(FilePickerSource.Camera) },
            editVideo = if (iosPostComposerVideoEditorEvidenceOptedIn()) {{
                videoFile?.let { current ->
                    val edited = iosPostComposerVideoEditorEvidenceEditedFile(current)
                    videoFile = edited
                    viewModel.onEvent(CreatePostUiEvent.VideoSelected(edited.reference))
                }
            }} else null,
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
        else -> null
    }
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

private fun iosPostComposerImageEditorEvidenceOptedIn(): Boolean =
    NSProcessInfo.processInfo.environment
        .iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_FIXTURE_OPT_IN") ==
        "I_ACCEPT_IOS_POST_COMPOSER_IMAGE_EDITOR_FIXTURE"

private fun iosPostComposerImageEditorEvidenceEditedFile(current: PlatformFile): PlatformFile {
    val environment = NSProcessInfo.processInfo.environment
    val overridePath = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_PATH")
    val reference = overridePath
        ?.takeIf(String::isNotBlank)
        ?.let { if (it.startsWith("file://")) it else NSURL.fileURLWithPath(it).absoluteString ?: it }
        ?: "${current.reference}#quata-edited-image"
    return PlatformFile(
        reference = reference,
        displayName = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_NAME")
            ?: "post-image-editor-fixture.png",
        mimeType = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_IMAGE_EDITOR_MIME")
            ?: current.mimeType,
    )
}

private fun iosPostComposerVideoEditorEvidenceOptedIn(): Boolean =
    NSProcessInfo.processInfo.environment
        .iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_FIXTURE_OPT_IN") ==
        "I_ACCEPT_IOS_POST_COMPOSER_VIDEO_EDITOR_FIXTURE"

private fun iosPostComposerVideoEditorEvidenceEditedFile(current: PlatformFile): PlatformFile {
    val environment = NSProcessInfo.processInfo.environment
    val overridePath = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_PATH")
    val reference = overridePath
        ?.takeIf(String::isNotBlank)
        ?.let { if (it.startsWith("file://")) it else NSURL.fileURLWithPath(it).absoluteString ?: it }
        ?: "${current.reference}#quata-edited-video"
    return PlatformFile(
        reference = reference,
        displayName = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_NAME")
            ?: "post-video-editor-fixture.mp4",
        mimeType = environment.iosPostComposerFixtureValue("QUATA_IOS_POST_COMPOSER_VIDEO_EDITOR_MIME")
            ?: current.mimeType,
    )
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
