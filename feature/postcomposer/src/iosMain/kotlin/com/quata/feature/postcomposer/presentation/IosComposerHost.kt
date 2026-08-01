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
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.VideoThumbnailService
import com.quata.feature.postcomposer.domain.PostComposerRepository
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

class IosComposerHostDependencies(
    val repository: PostComposerRepository,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
    val videoThumbnails: VideoThumbnailService,
    val languageTag: String?,
    val onClose: () -> Unit,
)

fun createIosComposerHostDependencies(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    languageTag: String?,
    onClose: () -> Unit,
): IosComposerHostDependencies = IosComposerHostDependencies(repository, filePicker, cameraCapture, videoThumbnails, languageTag, onClose)

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
        CreatePostViewModel(dependencies.repository, messages = copy.viewModelMessages())
    }
    val scope = rememberCoroutineScope()
    var imageFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoThumbnail by remember { mutableStateOf<PlatformFile?>(null) }

    fun releaseVideoThumbnail() {
        iosComposerThumbnailToRelease(videoThumbnail)?.let(::releaseIosComposerVideoThumbnail)
        videoThumbnail = null
    }
    fun selectVideo(source: FilePickerSource) {
        scope.launch {
            dependencies.filePicker.pick(FilePickerRequest(listOf("video/*"), source = source))
                .composerSelectedFileOrNull()?.let { file ->
                    releaseVideoThumbnail()
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
        slots = CreatePostPlatformSlots(
            pickImage = {
                scope.launch {
                    dependencies.filePicker.pick(FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery))
                        .composerSelectedFileOrNull()?.let { file ->
                            imageFile = file
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                        }
                }
            },
            captureImage = {
                scope.launch {
                    dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))
                        .composerCapturedFileOrNull()?.let { file ->
                            imageFile = file
                            viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                        }
                }
            },
            editImage = null,
            pickVideo = { selectVideo(FilePickerSource.Gallery) },
            captureVideo = null,
            editVideo = null,
            imagePreview = { _, modifier -> imageFile?.let { IosComposerLocalImagePreview(it, modifier) } },
            videoPreview = { _, _, modifier -> videoThumbnail?.let { IosComposerLocalImagePreview(it, modifier) } },
        ),
    )
}
