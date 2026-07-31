package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy
import com.quata.feature.postcomposer.domain.PostComposerType

enum class CreatePostStep { TypePicker, Text, Image, Video }
const val CreatePostCommonRootTestTag = "create-post-common-root"

fun createPostStepFor(type: PostComposerType): CreatePostStep = when (type) {
    PostComposerType.Text -> CreatePostStep.Text
    PostComposerType.Image -> CreatePostStep.Image
    PostComposerType.Video -> CreatePostStep.Video
}

data class CreatePostRootCopy(
    val title: String,
    val textTitle: String,
    val imageTitle: String,
    val videoTitle: String,
    val textType: String,
    val imageType: String,
    val videoType: String,
    val content: String,
    val textPlaceholder: String,
    val characters: (Int) -> String,
    val emoji: String,
    val textBackground: String,
    val preview: String,
    val previewEmpty: String,
    val readMore: String,
    val close: String,
    val image: String,
    val pickImage: String,
    val takePhoto: String,
    val editImage: String,
    val selectedImage: String,
    val imagePreviewEmpty: String,
    val location: String,
    val noLocation: String,
    val locationHelper: String,
    val locationPlaceholder: String,
    val edit: String,
    val video: String,
    val pickVideo: String,
    val recordVideo: String,
    val editVideo: String,
    val noFile: String,
    val description: String,
    val descriptionPlaceholder: String,
    val videoPreviewEmpty: String,
    val publish: String,
    val publishing: String,
    val back: String,
    val author: String = "Qüata",
    val feed: String = "Feed",
)

val SpanishCreatePostRootCopy = CreatePostRootCopy(
    title = "Crear publicación", textTitle = "Publicación de texto", imageTitle = "Publicación de imagen",
    videoTitle = "Publicación de vídeo", textType = "Texto", imageType = "Imagen", videoType = "Vídeo",
    content = "Tu publicación", textPlaceholder = "Escribe algo…", characters = { "$it caracteres" },
    emoji = "Emojis", textBackground = "Fondo y patrón", preview = "Vista previa",
    previewEmpty = "Tu texto aparecerá aquí", readMore = "Leer más", close = "Cerrar", image = "Imagen",
    pickImage = "Elegir imagen", takePhoto = "Tomar foto", editImage = "Editar imagen",
    selectedImage = "Imagen seleccionada", imagePreviewEmpty = "Selecciona o toma una imagen para previsualizarla.",
    location = "Ubicación", noLocation = "Sin ubicación", locationHelper = "Añade o corrige la ubicación de la imagen.",
    locationPlaceholder = "Barrio, ciudad o lugar", edit = "Editar", video = "Vídeo", pickVideo = "Elegir vídeo",
    recordVideo = "Grabar vídeo", editVideo = "Editar vídeo", noFile = "Ningún archivo seleccionado",
    description = "Descripción", descriptionPlaceholder = "Añade un título o descripción…",
    videoPreviewEmpty = "Selecciona o graba un vídeo para previsualizarlo.", publish = "Publicar",
    publishing = "Publicando…", back = "Volver al feed",
)

data class CreatePostPlatformSlots(
    val pickImage: () -> Unit,
    val captureImage: () -> Unit,
    val editImage: (() -> Unit)? = null,
    val pickVideo: () -> Unit,
    val captureVideo: () -> Unit,
    val editVideo: (() -> Unit)? = null,
    val imagePreview: @Composable (String, Modifier) -> Unit,
    val videoPreview: @Composable (String, Modifier) -> Unit,
    val mediaExport: (@Composable ColumnScope.(String, PostComposerType) -> Unit)? = null,
    val requestLocation: (((String, Double?, Double?) -> Unit) -> Unit)? = null,
)

@Composable
fun CreatePostRoot(
    viewModel: CreatePostViewModel,
    slots: CreatePostPlatformSlots,
    accessibility: CriticalControlsAccessibilityCopy,
    isLandscapeLayout: Boolean,
    canPublish: Boolean = true,
    onAuthRequired: () -> Unit,
    onPostCreated: (String?) -> Unit,
    onBack: () -> Unit,
    copy: CreatePostRootCopy = SpanishCreatePostRootCopy,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var step by rememberSaveable { mutableStateOf(CreatePostStep.TypePicker) }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.text)) }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    var locationOpen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) {
            onPostCreated(state.createdPostId)
            viewModel.onEvent(CreatePostUiEvent.ClearMessage)
        }
    }
    fun select(next: CreatePostStep) {
        viewModel.onEvent(CreatePostUiEvent.ClearDraft)
        textValue = TextFieldValue("")
        emojiOpen = false
        locationOpen = false
        step = next
    }
    fun publish(type: PostComposerType) { if (canPublish) viewModel.submit(type) else onAuthRequired() }
    val title = when (step) {
        CreatePostStep.TypePicker -> copy.title
        CreatePostStep.Text -> copy.textTitle
        CreatePostStep.Image -> copy.imageTitle
        CreatePostStep.Video -> copy.videoTitle
    }

    ComposerScreenLayoutContent(
        title = title,
        scrollState = rememberScrollState(),
        form = {
            when (step) {
                CreatePostStep.TypePicker -> ComposerTypePickerContent(
                    isLandscapeLayout = isLandscapeLayout,
                    strings = ComposerTypePickerStrings(copy.textType, copy.imageType, copy.videoType),
                    selectedType = null,
                    accessibility = accessibility,
                    onText = { select(createPostStepFor(PostComposerType.Text)) },
                    onImage = { select(createPostStepFor(PostComposerType.Image)) },
                    onVideo = { select(createPostStepFor(PostComposerType.Video)) },
                )
                CreatePostStep.Text -> ComposerTextPostFormContent(
                    isLandscapeLayout = isLandscapeLayout,
                    textValue = textValue,
                    contentTitle = copy.content,
                    placeholder = copy.textPlaceholder,
                    wordCountText = copy.characters(state.text.length),
                    minLines = if (isLandscapeLayout) 4 else 5,
                    onTextChange = {
                        textValue = it
                        viewModel.onEvent(CreatePostUiEvent.TextChanged(it.text))
                    },
                    trailingInputAction = {
                        ComposerActionButtonContent(copy.emoji, { Icon(Icons.Filled.InsertEmoticon, null) }, { emojiOpen = !emojiOpen })
                    },
                    emojiPanel = {
                        if (emojiOpen) {
                            listOf("😀", "😂", "😍", "🥳", "❤️", "👍").chunked(3).forEach { emojis ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    emojis.forEach { emoji ->
                                        Button(onClick = {
                                            textValue = textValue.insertComposerText(emoji)
                                            viewModel.onEvent(CreatePostUiEvent.TextChanged(textValue.text))
                                        }, modifier = Modifier.weight(1f)) { Text(emoji) }
                                        Spacer(Modifier.width(4.dp))
                                    }
                                }
                            }
                        }
                    },
                    preview = {
                        TextPatternSelectorContent(state.textPatternId, copy.textBackground) {
                            viewModel.onEvent(CreatePostUiEvent.TextPatternSelected(it))
                        }
                        Spacer(Modifier.height(10.dp))
                        ComposerSectionPanelContent(copy.preview, content = {
                            ComposerTextPostPreviewContent(
                                text = state.text, patternId = state.textPatternId, compact = isLandscapeLayout,
                                strings = ComposerTextPostPreviewStrings(copy.previewEmpty, copy.readMore, copy.author, copy.feed),
                                actionLabels = defaultComposerPreviewActionLabels(),
                                readerDismissButton = { m, dismiss -> Button(onClick = dismiss, modifier = m) { Icon(Icons.Filled.Close, copy.close) } },
                            )
                        })
                    },
                    publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, { publish(PostComposerType.Text) }, accessibility = accessibility) },
                )
                CreatePostStep.Image -> CommonImageComposerForm(state, slots, copy, accessibility, isLandscapeLayout, locationOpen, { locationOpen = it }, {
                    viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(it))
                }) { publish(PostComposerType.Image) }
                CreatePostStep.Video -> CommonVideoComposerForm(state, slots, copy, accessibility, isLandscapeLayout, {
                    viewModel.onEvent(CreatePostUiEvent.TextChanged(it))
                }) { publish(PostComposerType.Video) }
            }
            if (step != CreatePostStep.TypePicker) {
                ComposerBackButtonContent(copy.back, {
                    if (state.isLoading) viewModel.cancelSubmit()
                    select(CreatePostStep.TypePicker)
                    onBack()
                }, accessibility = accessibility)
            }
        },
        feedback = { ComposerSubmissionFeedbackContent(state.error, state.successMessage) },
        modifier = modifier.fillMaxSize().testTag(CreatePostCommonRootTestTag),
    )
}

@Composable
private fun ColumnScope.CommonImageComposerForm(state: CreatePostUiState, slots: CreatePostPlatformSlots, copy: CreatePostRootCopy, accessibility: CriticalControlsAccessibilityCopy, landscape: Boolean, locationOpen: Boolean, onLocationOpen: (Boolean) -> Unit, onLocationChange: (String) -> Unit, publish: () -> Unit) {
    ComposerMediaPostFormContent(
        isLandscapeLayout = landscape,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = copy.image, isLandscapeLayout = landscape,
                primarySourceAction = { m -> ComposerActionButtonContent(copy.pickImage, { Icon(Icons.Filled.PhotoLibrary, null) }, slots.pickImage, m) },
                secondarySourceAction = { m -> ComposerActionButtonContent(copy.takePhoto, { Icon(Icons.Filled.PhotoCamera, null) }, slots.captureImage, m) },
                editAction = state.imageUri?.let { slots.editImage }?.let { edit -> { m -> ComposerActionButtonContent(copy.editImage, { Icon(Icons.Filled.Edit, null) }, edit, m) } },
                afterEdit = { state.imageUri?.let { uri -> slots.mediaExport?.invoke(this, uri, PostComposerType.Image) } },
            )
        },
        controls = {
            ComposerLocationSectionContent(
                title = copy.location, locationText = state.locationLabel ?: copy.noLocation, helperText = copy.locationHelper,
                isHighlighted = false, leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                editAction = { m -> ComposerActionButtonContent(copy.edit, { Icon(Icons.Filled.Edit, null) }, { onLocationOpen(!locationOpen) }, m) },
                editor = if (locationOpen) {{
                    ComposerLocationTextEditorContent(state.locationLabel.orEmpty(), copy.locationPlaceholder, onLocationChange)
                }} else null,
            )
        },
        preview = {
            ComposerSectionPanelContent(copy.preview, content = {
                state.imageUri?.let { uri ->
                    ComposerMediaPostPreviewContent(
                        isVideo = false, description = "", subtitle = state.locationLabel ?: copy.feed,
                        topChips = state.locationLabel?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
                        actionLabels = defaultComposerPreviewActionLabels(), authorName = copy.author,
                        compact = landscape, backgroundSeed = uri,
                        media = { slots.imagePreview(uri, Modifier.fillMaxSize()) },
                    )
                } ?: ComposerEmptyPreviewContent(copy.preview, copy.imageType, copy.imagePreviewEmpty)
            })
        },
        publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, publish, accessibility = accessibility) },
    )
}

@Composable
private fun ColumnScope.CommonVideoComposerForm(state: CreatePostUiState, slots: CreatePostPlatformSlots, copy: CreatePostRootCopy, accessibility: CriticalControlsAccessibilityCopy, landscape: Boolean, onDescriptionChange: (String) -> Unit, publish: () -> Unit) {
    ComposerMediaPostFormContent(
        isLandscapeLayout = landscape,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = copy.video, isLandscapeLayout = landscape,
                primarySourceAction = { m -> ComposerActionButtonContent(copy.pickVideo, { Icon(Icons.Filled.VideoLibrary, null) }, slots.pickVideo, m) },
                secondarySourceAction = { m -> ComposerActionButtonContent(copy.recordVideo, { Icon(Icons.Filled.Videocam, null) }, slots.captureVideo, m) },
                beforeEdit = { Text(state.videoUri?.substringAfterLast('/') ?: copy.noFile, maxLines = 1) },
                editAction = state.videoUri?.let { slots.editVideo }?.let { edit -> { m -> ComposerActionButtonContent(copy.editVideo, { Icon(Icons.Filled.Edit, null) }, edit, m) } },
                afterEdit = { state.videoUri?.let { uri -> slots.mediaExport?.invoke(this, uri, PostComposerType.Video) } },
            )
        },
        controls = { ComposerDescriptionFormContent(state.text, copy.description, copy.descriptionPlaceholder, if (landscape) 2 else 3, onDescriptionChange) },
        preview = {
            ComposerSectionPanelContent(copy.preview, content = {
                state.videoUri?.let { uri ->
                    ComposerMediaPostPreviewContent(
                        isVideo = true, description = state.text, subtitle = copy.feed, topChips = emptyList(),
                        actionLabels = defaultComposerPreviewActionLabels(), authorName = copy.author,
                        compact = landscape, backgroundSeed = uri,
                        media = { slots.videoPreview(uri, Modifier.fillMaxSize()) },
                    )
                } ?: ComposerEmptyPreviewContent(copy.preview, copy.videoType, copy.videoPreviewEmpty)
            })
        },
        publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, publish, accessibility = accessibility) },
    )
}

private fun TextFieldValue.insertComposerText(value: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    return copy(text = text.replaceRange(start, end, value), selection = TextRange(start + value.length))
}

private fun defaultComposerPreviewActionLabels() = ComposerPreviewActionLabels("Me gusta", "Comentar", "Compartir", "Reportar", "Rango", "Directo")
