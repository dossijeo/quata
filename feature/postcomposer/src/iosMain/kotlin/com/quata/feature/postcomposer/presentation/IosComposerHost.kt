package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.accessibility.CriticalControlsAccessibilityCatalog
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.VideoThumbnailService
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the shared Composer presentation.
 *
 * The UIKit launcher injects the existing gallery and still-photo adapters. Video recording,
 * previews use only the local temporary files produced by those adapters. Edit/export and remote
 * publication are deliberately not synthesized here: the repository reports its current
 * capability error and the form keeps that error visible.
 */
class IosComposerHostDependencies(
    val repository: PostComposerRepository,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
    val videoThumbnails: VideoThumbnailService,
    val languageTag: String?,
    val onClose: () -> Unit,
)

/** Swift-friendly dependency factory; it does not create a repository or a platform service. */
fun createIosComposerHostDependencies(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    videoThumbnails: VideoThumbnailService,
    languageTag: String?,
    onClose: () -> Unit,
): IosComposerHostDependencies = IosComposerHostDependencies(repository, filePicker, cameraCapture, videoThumbnails, languageTag, onClose)

/**
 * Explicit iOS publication boundary until the authenticated PostgREST/storage write flow has
 * RLS and end-to-end evidence. Keeping it as a repository means the shared ViewModel returns a
 * visible error instead of UIKit inventing a post ID or treating a local draft as published.
 */
fun iosComposerPublicationUnavailableRepository(): PostComposerRepository = IosComposerPublicationUnavailableRepository

private object IosComposerPublicationUnavailableRepository : PostComposerRepository {
    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = Result.failure(
        IllegalStateException("ios_composer_publication_not_implemented"),
    )
}

/** Stable Swift-exported UIViewController factory for shared Composer forms/previews. */
fun QuataComposerViewController(dependencies: IosComposerHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme { IosPostComposerHost(dependencies) }
}

@Composable
private fun IosPostComposerHost(dependencies: IosComposerHostDependencies) {
    val viewModel = remember(dependencies.repository) { CreatePostViewModel(dependencies.repository) }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(PostComposerType.Text) }
    var imagePreviewFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoPreview by remember { mutableStateOf<IosComposerVideoPreview?>(null) }
    var videoThumbnailFile by remember { mutableStateOf<PlatformFile?>(null) }
    val accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(dependencies.languageTag)
    val copy = accessibility.composer
    fun releaseVideoThumbnail() {
        iosComposerThumbnailToRelease(videoThumbnailFile)?.let(::releaseIosComposerVideoThumbnail)
        videoThumbnailFile = null
        videoPreview = null
    }
    DisposableEffect(viewModel) {
        onDispose {
            iosComposerThumbnailToRelease(videoThumbnailFile)?.let(::releaseIosComposerVideoThumbnail)
            viewModel.close()
        }
    }

    ComposerScreenLayoutContent(
        title = copy.title,
        scrollState = rememberScrollState(),
        form = {
            ComposerTypePickerContent(
                isLandscapeLayout = false,
                strings = ComposerTypePickerStrings(copy.textType, copy.imageType, copy.videoType),
                selectedType = type,
                accessibility = accessibility,
                onText = { type = PostComposerType.Text },
                onImage = { type = PostComposerType.Image },
                onVideo = { type = PostComposerType.Video },
            )
            when (type) {
                PostComposerType.Text -> IosTextComposerForm(
                    state = state,
                    onTextChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                    onPublish = { viewModel.submit(PostComposerType.Text) },
                    accessibility = accessibility,
                )
                PostComposerType.Image -> IosImageComposerForm(
                    state = state,
                    onGallery = {
                        scope.launch {
                            dependencies.filePicker.pick(
                                FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                            ).composerSelectedFileOrNull()?.let { file ->
                                imagePreviewFile = file
                                viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                            }
                        }
                    },
                    onCamera = {
                        scope.launch {
                            dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))
                                .composerCapturedFileOrNull()?.let { file ->
                                    imagePreviewFile = file
                                    viewModel.onEvent(CreatePostUiEvent.ImageSelected(file.reference))
                                }
                        }
                    },
                    onPublish = { viewModel.submit(PostComposerType.Image) },
                    previewFile = imagePreviewFile,
                    accessibility = accessibility,
                )
                PostComposerType.Video -> IosVideoComposerForm(
                    state = state,
                    onGallery = {
                        scope.launch {
                            dependencies.filePicker.pick(
                                FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                            ).composerSelectedFileOrNull()?.let { file ->
                                releaseVideoThumbnail()
                                viewModel.onEvent(CreatePostUiEvent.VideoSelected(file.reference))
                                videoPreview = IosComposerVideoPreview.Generating
                                videoPreview = dependencies.videoThumbnails.createThumbnail(file).toIosComposerVideoPreview().also { result ->
                                    videoThumbnailFile = (result as? IosComposerVideoPreview.Thumbnail)?.file
                                }
                            }
                        }
                    },
                    onPublish = { viewModel.submit(PostComposerType.Video) },
                    preview = videoPreview,
                    accessibility = accessibility,
                )
            }
            ComposerBackButtonContent(
                label = copy.backToFeed,
                onBack = {
                    releaseVideoThumbnail()
                    viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                    dependencies.onClose()
                },
                accessibility = accessibility,
            )
        },
        feedback = { ComposerSubmissionFeedbackContent(state.error, state.successMessage) },
    )
}

@Composable
private fun ColumnScope.IosTextComposerForm(
    state: CreatePostUiState,
    onTextChange: (String) -> Unit,
    onPublish: () -> Unit,
    accessibility: CriticalControlsAccessibilityCopy,
) {
    val copy = accessibility.composer
    ComposerTextPostFormContent(
        isLandscapeLayout = false,
        textValue = TextFieldValue(state.text),
        contentTitle = copy.contentTitle,
        placeholder = copy.placeholder,
        wordCountText = copy.characters(state.text.length),
        minLines = 6,
        onTextChange = { onTextChange(it.text) },
        trailingInputAction = {},
        emojiPanel = {},
        preview = {
            ComposerTextCanvasContent(
                text = state.text,
                patternId = state.textPatternId,
                compact = true,
                emptyText = copy.preview,
                readMoreText = copy.readMore,
                readerDismissButton = { _, dismiss -> Button(onClick = dismiss) { Text(copy.close) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        publish = {
            ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, onPublish, accessibility = accessibility)
        },
    )
}

@Composable
private fun ColumnScope.IosImageComposerForm(
    state: CreatePostUiState,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onPublish: () -> Unit,
    previewFile: PlatformFile?,
    accessibility: CriticalControlsAccessibilityCopy,
) {
    val copy = accessibility.composer
    ComposerMediaPostFormContent(
        isLandscapeLayout = false,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = copy.imageType,
                isLandscapeLayout = false,
                primarySourceAction = { modifier -> Button(onClick = onGallery, modifier = modifier) { Text(copy.chooseImage) } },
                secondarySourceAction = { modifier -> Button(onClick = onCamera, modifier = modifier) { Text(copy.takePhoto) } },
            )
        },
        preview = {
            if (previewFile != null) {
                IosComposerLocalImagePreview(previewFile)
            } else ComposerEmptyPreviewContent(
                title = copy.imagePreview,
                tag = "iOS",
                body = state.imageUri?.let(copy.selectedImage) ?: copy.imageUnavailable,
            )
        },
        publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, onPublish, accessibility = accessibility) },
    )
}

@Composable
private fun ColumnScope.IosVideoComposerForm(
    state: CreatePostUiState,
    onGallery: () -> Unit,
    onPublish: () -> Unit,
    preview: IosComposerVideoPreview?,
    accessibility: CriticalControlsAccessibilityCopy,
) {
    val copy = accessibility.composer
    ComposerMediaPostFormContent(
        isLandscapeLayout = false,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = copy.videoType,
                isLandscapeLayout = false,
                primarySourceAction = { modifier -> Button(onClick = onGallery, modifier = modifier) { Text(copy.chooseVideo) } },
                secondarySourceAction = { modifier -> Button(onClick = {}, enabled = false, modifier = modifier) { Text(copy.recordVideoUnavailable) } },
            )
        },
        preview = {
            when (preview) {
                is IosComposerVideoPreview.Thumbnail -> IosComposerLocalImagePreview(preview.file)
                IosComposerVideoPreview.Generating -> ComposerEmptyPreviewContent(
                    title = copy.videoPreview, tag = "iOS", body = "Generating local video thumbnail…",
                )
                is IosComposerVideoPreview.Unavailable -> ComposerEmptyPreviewContent(
                    title = copy.videoPreview, tag = "iOS", body = "Local video preview unavailable: ${preview.reason}",
                )
                null -> ComposerEmptyPreviewContent(
                    title = copy.videoPreview, tag = "iOS", body = state.videoUri?.let(copy.selectedVideo) ?: copy.videoUnavailable,
                )
            }
        },
        publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, onPublish, accessibility = accessibility) },
    )
}
