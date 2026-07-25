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
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the shared Composer presentation.
 *
 * The UIKit launcher injects the existing gallery and still-photo adapters. Video recording,
 * previews, edit/export and remote publication are deliberately not synthesized here: the
 * repository reports its current capability error and the form keeps that error visible.
 */
class IosComposerHostDependencies(
    val repository: PostComposerRepository,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
    val onClose: () -> Unit,
)

/** Swift-friendly dependency factory; it does not create a repository or a platform service. */
fun createIosComposerHostDependencies(
    repository: PostComposerRepository,
    filePicker: FilePickerService,
    cameraCapture: CameraCaptureService,
    onClose: () -> Unit,
): IosComposerHostDependencies = IosComposerHostDependencies(repository, filePicker, cameraCapture, onClose)

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
    DisposableEffect(viewModel) { onDispose(viewModel::close) }

    ComposerScreenLayoutContent(
        title = "Crear publicación",
        scrollState = rememberScrollState(),
        form = {
            ComposerTypePickerContent(
                isLandscapeLayout = false,
                strings = ComposerTypePickerStrings("Texto", "Imagen", "Vídeo"),
                onText = { type = PostComposerType.Text },
                onImage = { type = PostComposerType.Image },
                onVideo = { type = PostComposerType.Video },
            )
            when (type) {
                PostComposerType.Text -> IosTextComposerForm(
                    state = state,
                    onTextChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                    onPublish = { viewModel.submit(PostComposerType.Text) },
                    onClose = dependencies.onClose,
                )
                PostComposerType.Image -> IosImageComposerForm(
                    state = state,
                    onGallery = {
                        scope.launch {
                            dependencies.filePicker.pick(
                                FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                            ).firstReferenceOrNull()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) }
                        }
                    },
                    onCamera = {
                        scope.launch {
                            dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))
                                .referenceOrNull()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) }
                        }
                    },
                    onPublish = { viewModel.submit(PostComposerType.Image) },
                )
                PostComposerType.Video -> IosVideoComposerForm(
                    state = state,
                    onGallery = {
                        scope.launch {
                            dependencies.filePicker.pick(
                                FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                            ).firstReferenceOrNull()?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) }
                        }
                    },
                    onPublish = { viewModel.submit(PostComposerType.Video) },
                )
            }
        },
        feedback = { ComposerSubmissionFeedbackContent(state.error, state.successMessage) },
    )
}

@Composable
private fun ColumnScope.IosTextComposerForm(
    state: CreatePostUiState,
    onTextChange: (String) -> Unit,
    onPublish: () -> Unit,
    onClose: () -> Unit,
) {
    ComposerTextPostFormContent(
        isLandscapeLayout = false,
        textValue = TextFieldValue(state.text),
        contentTitle = "Tu publicación",
        placeholder = "Escribe algo…",
        wordCountText = "${state.text.length} caracteres",
        minLines = 6,
        onTextChange = { onTextChange(it.text) },
        trailingInputAction = {},
        emojiPanel = {},
        preview = {
            ComposerTextCanvasContent(
                text = state.text,
                patternId = state.textPatternId,
                compact = true,
                emptyText = "Vista previa",
                readMoreText = "Leer más",
                readerDismissButton = { _, dismiss -> Button(onClick = dismiss) { Text("Cerrar") } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        publish = {
            ComposerPublishButtonContent(state.isLoading, "Publicar", "Publicando…", onPublish)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Volver al feed") }
        },
    )
}

@Composable
private fun ColumnScope.IosImageComposerForm(
    state: CreatePostUiState,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onPublish: () -> Unit,
) {
    ComposerMediaPostFormContent(
        isLandscapeLayout = false,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = "Imagen",
                isLandscapeLayout = false,
                primarySourceAction = { modifier -> Button(onClick = onGallery, modifier = modifier) { Text("Elegir imagen") } },
                secondarySourceAction = { modifier -> Button(onClick = onCamera, modifier = modifier) { Text("Tomar foto") } },
            )
        },
        preview = {
            ComposerEmptyPreviewContent(
                title = "Vista previa de imagen",
                tag = "iOS",
                body = state.imageUri?.let { "Imagen seleccionada: $it" }
                    ?: "El renderizado y la edición de bitmap aún no están disponibles en iOS.",
            )
        },
        publish = { ComposerPublishButtonContent(state.isLoading, "Publicar", "Publicando…", onPublish) },
    )
}

@Composable
private fun ColumnScope.IosVideoComposerForm(
    state: CreatePostUiState,
    onGallery: () -> Unit,
    onPublish: () -> Unit,
) {
    ComposerMediaPostFormContent(
        isLandscapeLayout = false,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = "Vídeo",
                isLandscapeLayout = false,
                primarySourceAction = { modifier -> Button(onClick = onGallery, modifier = modifier) { Text("Elegir vídeo") } },
                secondarySourceAction = { modifier -> Button(onClick = {}, enabled = false, modifier = modifier) { Text("Grabar vídeo no disponible") } },
            )
        },
        preview = {
            ComposerEmptyPreviewContent(
                title = "Vista previa de vídeo",
                tag = "iOS",
                body = state.videoUri?.let { "Vídeo seleccionado: $it" }
                    ?: "La reproducción, edición y exportación de vídeo aún no están disponibles en iOS.",
            )
        },
        publish = { ComposerPublishButtonContent(state.isLoading, "Publicar", "Publicando…", onPublish) },
    )
}

private fun PlatformResult<List<PlatformFile>>.firstReferenceOrNull(): String? = when (this) {
    is PlatformResult.Success -> value.firstOrNull()?.reference
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}

private fun PlatformResult<PlatformFile>.referenceOrNull(): String? = when (this) {
    is PlatformResult.Success -> value.reference
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}
