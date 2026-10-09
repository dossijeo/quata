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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.communityEmojiSections
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class CreatePostStep { TypePicker, Text, Image, Video }
const val CreatePostCommonRootTestTag = "create-post-common-root"
const val ComposerPickImageTestTag = "composer-media.pick-image"
const val ComposerCaptureImageTestTag = "composer-media.capture-image"
const val ComposerEditImageTestTag = "composer-media.edit-image"
const val ComposerPickVideoTestTag = "composer-media.pick-video"
const val ComposerCaptureVideoTestTag = "composer-media.capture-video"
const val ComposerEditVideoTestTag = "composer-media.edit-video"
const val ComposerSelectedImagePreviewTestTag = "composer-media.selected-image-preview"
const val ComposerSelectedVideoPreviewTestTag = "composer-media.selected-video-preview"
const val ComposerMediaErrorTestTag = "composer-media.error"

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
    val retry: String,
    val back: String,
    val publicationCreated: String,
    val publicationFailed: String,
    val draftDiscardFailed: String,
    val mediaSelectionFailed: String,
    val mediaUnsupported: String,
    val mediaPermissionDenied: String,
    val author: String = "Qüata",
    val feed: String = "Feed",
    val destination: String = "Destino",
    val destinationHelper: String = "Elige dónde se publicará.",
    val destinationLoading: String = "Cargando destinos…",
    val destinationEmpty: String = "No hay destinos disponibles para publicar.",
    val destinationLoadFailed: String = "No se pudieron cargar los destinos.",
    val destinationRetry: String = "Reintentar destinos",
    val destinationRequired: String = "Elige un destino antes de publicar.",
)

val SpanishCreatePostRootCopy = CreatePostRootCopy(
    title = "Crear publicación", textTitle = "Publicación de texto", imageTitle = "Publicación de imagen",
    videoTitle = "Publicación de vídeo", textType = "POSTEAR TEXTO", imageType = "POSTEAR FOTO/IMAGEN", videoType = "POSTEAR VÍDEO",
    content = "Tu publicación", textPlaceholder = "Escribe algo…", characters = { "$it/500" },
    emoji = "Emojis", textBackground = "Fondo y patrón", preview = "Vista previa",
    previewEmpty = "Tu texto aparecerá aquí", readMore = "Leer más", close = "Cerrar", image = "Imagen",
    pickImage = "Elegir imagen", takePhoto = "Tomar foto", editImage = "Editar imagen",
    selectedImage = "Imagen seleccionada", imagePreviewEmpty = "Selecciona o toma una imagen para previsualizarla.",
    location = "Ubicación", noLocation = "Sin ubicación", locationHelper = "Añade o corrige la ubicación de la imagen.",
    locationPlaceholder = "Barrio, ciudad o lugar", edit = "Editar", video = "Vídeo", pickVideo = "Elegir vídeo",
    recordVideo = "Grabar vídeo", editVideo = "Editar vídeo", noFile = "Ningún archivo seleccionado",
    description = "Descripción", descriptionPlaceholder = "Añade un título o descripción…",
    videoPreviewEmpty = "Selecciona o graba un vídeo para previsualizarlo.", publish = "Publicar",
    publishing = "Publicando…", retry = "Reintentar", back = "Volver al feed",
    publicationCreated = "Publicación creada", publicationFailed = "No se pudo publicar",
    draftDiscardFailed = "No se pudo descartar el borrador. Inténtalo de nuevo.",
    mediaSelectionFailed = "No se pudo cargar el archivo. Inténtalo de nuevo.",
    mediaUnsupported = "Esta fuente de medios no está disponible en este dispositivo.",
    mediaPermissionDenied = "Permiso denegado. Activa el acceso a cámara, micrófono o galería para continuar.",
)

val EnglishCreatePostRootCopy = SpanishCreatePostRootCopy.copy(
    title = "Create post", textTitle = "Text post", imageTitle = "Image post", videoTitle = "Video post",
    textType = "POST TEXT", imageType = "POST PHOTO/IMAGE", videoType = "POST VIDEO", content = "Your post",
    textPlaceholder = "Write something…", characters = { "$it/500" }, emoji = "Emoji",
    textBackground = "Background and pattern", preview = "Preview", previewEmpty = "Your text will appear here",
    readMore = "Read more", close = "Close", pickImage = "Choose image", takePhoto = "Take photo",
    editImage = "Edit image", selectedImage = "Selected image", imagePreviewEmpty = "Choose or take an image to preview it.",
    location = "Location", noLocation = "No location", locationHelper = "Add or correct the image location.",
    locationPlaceholder = "Neighborhood, city or place", edit = "Edit", pickVideo = "Choose video", recordVideo = "Record video",
    editVideo = "Edit video", noFile = "No file selected", description = "Description",
    descriptionPlaceholder = "Add a title or description…", videoPreviewEmpty = "Choose or record a video to preview it.",
    publish = "Publish", publishing = "Publishing…", retry = "Retry", back = "Back to feed",
    publicationCreated = "Post created", publicationFailed = "Could not publish", feed = "Feed",
    draftDiscardFailed = "The draft could not be discarded. Try again.",
    mediaSelectionFailed = "The file could not be loaded. Try again.",
    mediaUnsupported = "This media source is not available on this device.",
    mediaPermissionDenied = "Permission denied. Enable camera, microphone or gallery access to continue.",
    destination = "Destination",
    destinationHelper = "Choose where this will be published.",
    destinationLoading = "Loading destinations…",
    destinationEmpty = "No publishing destinations are available.",
    destinationLoadFailed = "Destinations could not be loaded.",
    destinationRetry = "Retry destinations",
    destinationRequired = "Choose a destination before publishing.",
)

val FrenchCreatePostRootCopy = SpanishCreatePostRootCopy.copy(
    title = "Créer une publication", textTitle = "Publication texte", imageTitle = "Publication image", videoTitle = "Publication vidéo",
    textType = "PUBLIER TEXTE", imageType = "PUBLIER PHOTO/IMAGE", videoType = "PUBLIER VIDÉO", content = "Votre publication",
    textPlaceholder = "Écrivez quelque chose…", characters = { "$it/500" }, textBackground = "Fond et motif",
    preview = "Aperçu", previewEmpty = "Votre texte apparaîtra ici", readMore = "Lire la suite", close = "Fermer",
    pickImage = "Choisir une image", takePhoto = "Prendre une photo", editImage = "Modifier l'image",
    selectedImage = "Image sélectionnée", imagePreviewEmpty = "Choisissez une image pour l'aperçu.",
    location = "Lieu", noLocation = "Sans lieu", locationHelper = "Ajoutez ou corrigez le lieu de l'image.",
    locationPlaceholder = "Quartier, ville ou lieu", edit = "Modifier", pickVideo = "Choisir une vidéo",
    recordVideo = "Enregistrer une vidéo", editVideo = "Modifier la vidéo", noFile = "Aucun fichier sélectionné",
    description = "Description", descriptionPlaceholder = "Ajoutez un titre ou une description…",
    videoPreviewEmpty = "Choisissez ou enregistrez une vidéo pour l'aperçu.", publish = "Publier",
    publishing = "Publication…", retry = "Réessayer", back = "Retour au fil",
    publicationCreated = "Publication créée", publicationFailed = "Impossible de publier", feed = "Fil",
    draftDiscardFailed = "Impossible de supprimer le brouillon. Réessayez.",
    mediaSelectionFailed = "Impossible de charger le fichier. Réessayez.",
    mediaUnsupported = "Cette source média n'est pas disponible sur cet appareil.",
    mediaPermissionDenied = "Autorisation refusée. Activez l'accès caméra, micro ou galerie pour continuer.",
    destination = "Destination",
    destinationHelper = "Choisissez où publier.",
    destinationLoading = "Chargement des destinations…",
    destinationEmpty = "Aucune destination de publication disponible.",
    destinationLoadFailed = "Impossible de charger les destinations.",
    destinationRetry = "Réessayer les destinations",
    destinationRequired = "Choisissez une destination avant de publier.",
)

fun createPostRootCopyForLanguageTag(languageTag: String?): CreatePostRootCopy = when {
    languageTag?.lowercase()?.startsWith("fr") == true -> FrenchCreatePostRootCopy
    languageTag?.lowercase()?.startsWith("en") == true -> EnglishCreatePostRootCopy
    else -> SpanishCreatePostRootCopy
}

fun CreatePostRootCopy.viewModelMessages(): CreatePostMessages =
    CreatePostMessages(created = publicationCreated, failed = publicationFailed, destinationRequired = destinationRequired)

data class CreatePostPlatformSlots(
    val pickImage: () -> Unit,
    val captureImage: () -> Unit,
    val editImage: (() -> Unit)?,
    val pickVideo: () -> Unit,
    val captureVideo: (() -> Unit)?,
    val editVideo: (() -> Unit)?,
    val imagePreview: @Composable (String, Modifier) -> Unit,
    val videoPreview: @Composable (String, Boolean, Modifier) -> Unit,
    val mediaExport: (@Composable ColumnScope.(String, PostComposerType) -> Unit)? = null,
    val requestLocation: (((String, Double?, Double?) -> Unit) -> Unit)? = null,
    val clearOwnedMedia: (() -> Unit)? = null,
)

@Composable
fun CreatePostRoot(
    viewModel: CreatePostViewModel,
    slots: CreatePostPlatformSlots,
    accessibility: CriticalControlsAccessibilityCopy,
    isLandscapeLayout: Boolean,
    canPublish: Boolean = true,
    onAuthRequired: () -> Unit,
    canPublishNow: (() -> Boolean)? = null,
    onAuthenticationContinuationRequired: ((PostComposerAuthenticationContinuation) -> Unit)? = null,
    authenticationContinuationCoordinator: PostComposerAuthenticationContinuationCoordinator? = null,
    onPostCreated: (String?) -> Unit,
    onBack: () -> Unit,
    resetToken: Int = 0,
    cancelUploadToken: Int = 0,
    copy: CreatePostRootCopy = SpanishCreatePostRootCopy,
    initialStep: CreatePostStep? = null,
    durableDraftStore: PostComposerDraftStore? = null,
    draftActorProfileId: String? = null,
    durableMediaReferenceAvailable: suspend (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var step by rememberSaveable(initialStep) { mutableStateOf(initialStep ?: CreatePostStep.TypePicker) }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.text)) }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    var locationOpen by rememberSaveable { mutableStateOf(false) }
    var lastResetToken by rememberSaveable { mutableStateOf(0) }
    var lastCancelUploadToken by rememberSaveable { mutableStateOf(0) }
    var durableDraftReady by remember(durableDraftStore, draftActorProfileId) {
        mutableStateOf(durableDraftStore == null || draftActorProfileId == null)
    }
    var durableDraftActorLease by remember(durableDraftStore, draftActorProfileId) {
        mutableStateOf<PostComposerDraftActorLease?>(null)
    }
    var durablePersistedSnapshot by remember(durableDraftStore, draftActorProfileId) {
        mutableStateOf<PostComposerDraftSnapshot?>(null)
    }
    var durableActorResolution by remember(durableDraftStore) {
        mutableStateOf(PostComposerDraftActorResolution())
    }
    var pendingDraftClearRequest by remember(durableDraftStore) {
        mutableStateOf<PostComposerDraftClearRequest?>(null)
    }
    val currentDraftActorProfileId by rememberUpdatedState(draftActorProfileId)
    val currentDraftActorLease by rememberUpdatedState(durableDraftActorLease)
    val scope = rememberCoroutineScope()
    val emojiDismissState = rememberCommunityEmojiPanelDismissState { emojiOpen = false }

    LaunchedEffect(durableDraftStore, draftActorProfileId) {
        val store = durableDraftStore ?: return@LaunchedEffect
        pendingDraftClearRequest = null
        durableDraftReady = false
        val actor = draftActorProfileId
        val resetForActorChange = shouldResetDraftForActorTransition(
            wasResolved = durableActorResolution.wasResolved,
            previousActorProfileId = durableActorResolution.actorProfileId,
            nextActorProfileId = actor,
            hasAuthenticationContinuation = initialStep != null,
        )
        if (resetForActorChange) {
            viewModel.onEvent(CreatePostUiEvent.ClearDraft)
            step = CreatePostStep.TypePicker
            textValue = TextFieldValue("")
            emojiOpen = false
            locationOpen = false
        }
        val baseline = viewModel.snapshot(step)
        val baselineMutationRevision = viewModel.draftMutationRevision()
        if (actor == null) {
            // Authentication can be restored after the first composition. An unresolved actor is
            // not evidence of logout and must never rotate away an existing actor-bound draft.
            // Session owners retire the stored actor explicitly when logout is authoritative.
            durableDraftActorLease = null
            durablePersistedSnapshot = baseline
            durableDraftReady = false
            return@LaunchedEffect
        }
        durableActorResolution = durableActorResolution.afterObservation(actor)
        val lease = store.activateActor(actor)
        durableDraftActorLease = lease
        val restoration = if (lease != null && initialStep == null && !resetForActorChange) {
            store.restore(lease.actorProfileId, durableMediaReferenceAvailable)
        } else {
            null
        }
        val appliedRestoration = if (
            restoration != null &&
            store.isCurrent(restoration) &&
            viewModel.draftMutationRevision() == baselineMutationRevision
        ) {
            val restored = restoration.snapshot
            viewModel.restore(restored)
            step = restored.step
            textValue = TextFieldValue(restored.text)
            restored
        } else {
            null
        }
        durablePersistedSnapshot = appliedRestoration ?: baseline
        durableDraftReady = true
    }
    val durableSnapshot = viewModel.snapshot(step)
    LaunchedEffect(durableDraftStore, durableDraftActorLease, durableDraftReady, durableSnapshot, durablePersistedSnapshot) {
        val store = durableDraftStore ?: return@LaunchedEffect
        val lease = durableDraftActorLease ?: return@LaunchedEffect
        if (shouldPersistPostComposerDraft(durableDraftReady, durableSnapshot, durablePersistedSnapshot)) {
            if (store.save(lease, durableSnapshot)) durablePersistedSnapshot = durableSnapshot
        }
    }

    fun select(next: CreatePostStep) {
        slots.clearOwnedMedia?.invoke()
        viewModel.onEvent(CreatePostUiEvent.ClearDraft)
        textValue = TextFieldValue("")
        emojiOpen = false
        locationOpen = false
        step = next
    }

    suspend fun completeDraftClear(request: PostComposerDraftClearRequest) {
        durableDraftReady = false
        val store = durableDraftStore
        val clearAttempt = if (store == null) {
            PostComposerDraftClearAttempt.NotRequired
        } else {
            attemptPostComposerDraftClear(request.actorProfileId, request.actorLease, store::clear)
        }
        if (!isPostComposerDraftClearRequestCurrent(
                request.actorProfileId,
                request.actorLease,
                currentDraftActorProfileId,
                currentDraftActorLease,
            )
        ) {
            pendingDraftClearRequest = null
            return
        }
        if (clearAttempt is PostComposerDraftClearAttempt.Failed) {
            pendingDraftClearRequest = request
            return
        }
        val clearedLease = (clearAttempt as? PostComposerDraftClearAttempt.Cleared)?.lease
        durableDraftActorLease = clearedLease
        durablePersistedSnapshot = null
        pendingDraftClearRequest = null
        when (val action = request.action) {
            is PostComposerDraftClearAction.Reset -> {
                slots.clearOwnedMedia?.invoke()
                viewModel.onEvent(CreatePostUiEvent.ClearDraft)
                step = CreatePostStep.TypePicker
                textValue = TextFieldValue("")
                emojiOpen = false
                locationOpen = false
                lastResetToken = action.token
                durableDraftReady = true
            }
            is PostComposerDraftClearAction.PublishSuccess -> {
                focusManager.clearFocus(force = true)
                slots.clearOwnedMedia?.invoke()
                step = CreatePostStep.TypePicker
                textValue = TextFieldValue("")
                emojiOpen = false
                locationOpen = false
                onPostCreated(action.createdPostId)
                viewModel.onEvent(CreatePostUiEvent.ClearDraft)
            }
            PostComposerDraftClearAction.Discard -> {
                dispatchCreatePostBack(
                    state.isLoading,
                    viewModel::cancelSubmit,
                    { select(CreatePostStep.TypePicker) },
                    onBack,
                )
            }
        }
    }

    fun requestDraftClear(action: PostComposerDraftClearAction) {
        val request = PostComposerDraftClearRequest(
            actorProfileId = currentDraftActorProfileId,
            actorLease = currentDraftActorLease,
            action = action,
        )
        durableDraftReady = false
        scope.launch { completeDraftClear(request) }
    }

    LaunchedEffect(resetToken) {
        if (resetToken > 0 && resetToken != lastResetToken) {
            completeDraftClear(
                PostComposerDraftClearRequest(
                    currentDraftActorProfileId,
                    currentDraftActorLease,
                    PostComposerDraftClearAction.Reset(resetToken),
                ),
            )
        }
    }
    LaunchedEffect(cancelUploadToken) {
        if (cancelUploadToken > 0 && cancelUploadToken != lastCancelUploadToken) {
            viewModel.cancelSubmit()
            lastCancelUploadToken = cancelUploadToken
        }
    }
    LaunchedEffect(state.imageUri) {
        val selectedImage = state.imageUri ?: return@LaunchedEffect
        if (state.locationOrigin == null) {
            slots.requestLocation?.invoke { label, latitude, longitude ->
                if (viewModel.uiState.value.imageUri == selectedImage) {
                    viewModel.onEvent(
                        CreatePostUiEvent.LocationResolved(
                            label = label,
                            latitude = latitude,
                            longitude = longitude,
                            origin = CreatePostLocationOrigin.Device,
                            imageUri = selectedImage,
                        ),
                    )
                }
            }
        }
    }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) {
            completeDraftClear(
                PostComposerDraftClearRequest(
                    currentDraftActorProfileId,
                    currentDraftActorLease,
                    PostComposerDraftClearAction.PublishSuccess(state.createdPostId),
                ),
            )
        }
    }
    LaunchedEffect(state.authenticationRequiredSubmitType) {
        val type = state.authenticationRequiredSubmitType ?: return@LaunchedEffect
        viewModel.onEvent(CreatePostUiEvent.AuthenticationContinuationHandled)
        val coordinator = authenticationContinuationCoordinator
        val callback = onAuthenticationContinuationRequired
        if (coordinator != null && callback != null) {
            callback(coordinator.request(viewModel.snapshot(step), type))
        } else {
            onAuthRequired()
        }
    }
    fun publish(type: PostComposerType) {
        if (canPublishNow?.invoke() ?: canPublish) {
            viewModel.submit(type)
        } else {
            val coordinator = authenticationContinuationCoordinator
            val callback = onAuthenticationContinuationRequired
            if (coordinator != null && callback != null) {
                callback(coordinator.request(viewModel.snapshot(step), type))
            } else {
                onAuthRequired()
            }
        }
    }
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
            if (step != CreatePostStep.TypePicker) {
                ComposerDestinationSelectorContent(
                    title = copy.destination,
                    helper = copy.destinationHelper,
                    destinations = state.destinations,
                    selectedDestination = state.selectedDestination,
                    loading = state.destinationsLoading,
                    errorMessage = state.destinationsError?.takeIf { it.isNotBlank() }?.let { copy.destinationLoadFailed },
                    emptyMessage = copy.destinationEmpty,
                    loadingMessage = copy.destinationLoading,
                    retryLabel = copy.destinationRetry,
                    onRetry = { viewModel.onEvent(CreatePostUiEvent.ReloadDestinations) },
                    onDestinationSelected = { viewModel.onEvent(CreatePostUiEvent.DestinationSelected(it)) },
                )
            }
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
                        val limited = it.text.take(CreatePostTextLimit)
                        textValue = TextFieldValue(limited, TextRange(it.selection.end.coerceAtMost(limited.length)))
                        viewModel.onEvent(CreatePostUiEvent.TextChanged(limited))
                    },
                    trailingInputAction = {
                        ComposerActionButtonContent(
                            copy.emoji,
                            { Icon(Icons.Filled.InsertEmoticon, null) },
                            { emojiOpen = !emojiOpen },
                            Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState),
                        )
                    },
                    emojiPanel = {
                        if (emojiOpen) {
                            CommunityEmojiPanelContent(
                                sections = communityEmojiSections(),
                                onEmojiClick = { emoji ->
                                    textValue = textValue.insertComposerText(emoji).let { inserted ->
                                        val limited = inserted.text.take(CreatePostTextLimit)
                                        TextFieldValue(limited, TextRange(inserted.selection.end.coerceAtMost(limited.length)))
                                    }
                                    viewModel.onEvent(CreatePostUiEvent.TextChanged(textValue.text))
                                },
                                modifier = Modifier.trackCommunityEmojiPanelBounds(emojiDismissState),
                            )
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
                                strings = ComposerTextPostPreviewStrings(
                                    copy.previewEmpty,
                                    copy.readMore,
                                    copy.author,
                                    state.selectedDestination?.label ?: copy.feed,
                                ),
                                actionLabels = defaultComposerPreviewActionLabels(),
                                readerDismissButton = { m, dismiss -> Button(onClick = dismiss, modifier = m) { Icon(Icons.Filled.Close, copy.close) } },
                            )
                        })
                    },
                    publish = {
                        ComposerPublishButtonContent(
                            state.isLoading,
                            copy.publish,
                            copy.publishing,
                            { publish(PostComposerType.Text) },
                            accessibility = accessibility,
                        )
                    },
                    modifier = Modifier.dismissCommunityEmojiPanelOnOutsideTap(emojiOpen, emojiDismissState),
                )
                CreatePostStep.Image -> CommonImageComposerForm(state, slots, copy, accessibility, isLandscapeLayout, locationOpen, { locationOpen = it }, {
                    viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(it))
                }) { publish(PostComposerType.Image) }
                CreatePostStep.Video -> CommonVideoComposerForm(state, slots, copy, accessibility, isLandscapeLayout, {
                    viewModel.onEvent(CreatePostUiEvent.TextChanged(it))
                }) { publish(PostComposerType.Video) }
            }
            if (step != CreatePostStep.TypePicker) {
                ComposerSubmissionFeedbackContent(
                    errorMessage = if (pendingDraftClearRequest != null) copy.draftDiscardFailed else state.error,
                    successMessage = state.successMessage,
                    retryLabel = copy.retry,
                    onRetry = pendingDraftClearRequest?.let { request ->
                        { scope.launch { completeDraftClear(request) } }
                    } ?: state.lastFailedSubmitType?.let { type -> { viewModel.submit(type) } },
                )
                ComposerBackButtonContent(copy.back, {
                    requestDraftClear(PostComposerDraftClearAction.Discard)
                }, accessibility = accessibility)
            }
        },
        feedback = {},
        modifier = modifier.fillMaxSize().testTag(CreatePostCommonRootTestTag),
    )
}

internal fun shouldResetDraftForActorTransition(
    wasResolved: Boolean,
    previousActorProfileId: String?,
    nextActorProfileId: String?,
    hasAuthenticationContinuation: Boolean,
): Boolean {
    if (!wasResolved || previousActorProfileId == nextActorProfileId) return false
    val completingAuthenticationContinuation =
        previousActorProfileId == null && nextActorProfileId != null && hasAuthenticationContinuation
    return !completingAuthenticationContinuation
}

internal data class PostComposerDraftActorResolution(
    val wasResolved: Boolean = false,
    val actorProfileId: String? = null,
) {
    fun afterObservation(nextActorProfileId: String?): PostComposerDraftActorResolution =
        if (nextActorProfileId == null) this else PostComposerDraftActorResolution(true, nextActorProfileId)
}

private sealed interface PostComposerDraftClearAction {
    data class Reset(val token: Int) : PostComposerDraftClearAction
    data class PublishSuccess(val createdPostId: String?) : PostComposerDraftClearAction
    data object Discard : PostComposerDraftClearAction
}

private data class PostComposerDraftClearRequest(
    val actorProfileId: String?,
    val actorLease: PostComposerDraftActorLease?,
    val action: PostComposerDraftClearAction,
)

internal sealed interface PostComposerDraftClearAttempt {
    data object NotRequired : PostComposerDraftClearAttempt
    data class Cleared(val lease: PostComposerDraftActorLease) : PostComposerDraftClearAttempt
    data object Failed : PostComposerDraftClearAttempt
}

internal suspend fun attemptPostComposerDraftClear(
    actorProfileId: String?,
    actorLease: PostComposerDraftActorLease?,
    clear: suspend (PostComposerDraftActorLease) -> PostComposerDraftActorLease?,
): PostComposerDraftClearAttempt {
    if (actorProfileId == null) return PostComposerDraftClearAttempt.NotRequired
    if (actorLease?.actorProfileId != actorProfileId) return PostComposerDraftClearAttempt.Failed
    return try {
        clear(actorLease)?.let(PostComposerDraftClearAttempt::Cleared)
            ?: PostComposerDraftClearAttempt.Failed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        PostComposerDraftClearAttempt.Failed
    }
}

internal fun isPostComposerDraftClearRequestCurrent(
    requestedActorProfileId: String?,
    requestedActorLease: PostComposerDraftActorLease?,
    currentActorProfileId: String?,
    currentActorLease: PostComposerDraftActorLease?,
): Boolean = requestedActorProfileId == currentActorProfileId && requestedActorLease == currentActorLease

internal fun shouldPersistPostComposerDraft(
    ready: Boolean,
    snapshot: PostComposerDraftSnapshot,
    lastPersistedSnapshot: PostComposerDraftSnapshot?,
): Boolean = ready && snapshot != lastPersistedSnapshot

const val CreatePostTextLimit = 500

fun dispatchCreatePostPublish(canPublish: Boolean, submit: () -> Unit, onAuthRequired: () -> Unit) {
    if (canPublish) submit() else onAuthRequired()
}

fun dispatchCreatePostBack(isLoading: Boolean, cancel: () -> Unit, reset: () -> Unit, onBack: () -> Unit) {
    if (isLoading) cancel()
    reset()
    onBack()
}

@Composable
private fun ColumnScope.CommonImageComposerForm(state: CreatePostUiState, slots: CreatePostPlatformSlots, copy: CreatePostRootCopy, accessibility: CriticalControlsAccessibilityCopy, landscape: Boolean, locationOpen: Boolean, onLocationOpen: (Boolean) -> Unit, onLocationChange: (String) -> Unit, publish: () -> Unit) {
    ComposerMediaPostFormContent(
        isLandscapeLayout = landscape,
        mediaSource = {
            ComposerMediaSourceFormContent(
                title = copy.image, isLandscapeLayout = landscape,
                primarySourceAction = { m -> ComposerActionButtonContent(copy.pickImage, { Icon(Icons.Filled.PhotoLibrary, null) }, slots.pickImage, m.testTag(ComposerPickImageTestTag)) },
                secondarySourceAction = { m -> ComposerActionButtonContent(copy.takePhoto, { Icon(Icons.Filled.PhotoCamera, null) }, slots.captureImage, m.testTag(ComposerCaptureImageTestTag)) },
                errorMessage = state.mediaError,
                editAction = state.imageUri?.let { slots.editImage }?.let { edit -> { m: Modifier -> ComposerActionButtonContent(copy.editImage, { Icon(Icons.Filled.Edit, null) }, edit, m.testTag(ComposerEditImageTestTag)) } },
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
                        isVideo = false, description = "", subtitle = state.locationLabel ?: state.selectedDestination?.label ?: copy.feed,
                        topChips = state.locationLabel?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
                        actionLabels = defaultComposerPreviewActionLabels(), authorName = copy.author,
                        compact = landscape, backgroundSeed = uri,
                        media = { slots.imagePreview(uri, Modifier.fillMaxSize()) },
                        modifier = Modifier
                            .testTag(ComposerSelectedImagePreviewTestTag)
                            .semantics { contentDescription = ComposerSelectedImagePreviewTestTag },
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
                primarySourceAction = { m -> ComposerActionButtonContent(copy.pickVideo, { Icon(Icons.Filled.VideoLibrary, null) }, slots.pickVideo, m.testTag(ComposerPickVideoTestTag)) },
                secondarySourceAction = slots.captureVideo?.let { capture -> { m -> ComposerActionButtonContent(copy.recordVideo, { Icon(Icons.Filled.Videocam, null) }, capture, m.testTag(ComposerCaptureVideoTestTag)) } },
                beforeEdit = { Text(state.videoUri?.substringAfterLast('/') ?: copy.noFile, maxLines = 1) },
                errorMessage = state.mediaError,
                editAction = state.videoUri?.let { slots.editVideo }?.let { edit -> { m: Modifier -> ComposerActionButtonContent(copy.editVideo, { Icon(Icons.Filled.Edit, null) }, edit, m.testTag(ComposerEditVideoTestTag)) } },
                afterEdit = { state.videoUri?.let { uri -> slots.mediaExport?.invoke(this, uri, PostComposerType.Video) } },
            )
        },
        controls = { ComposerDescriptionFormContent(state.text, copy.description, copy.descriptionPlaceholder, if (landscape) 2 else 3, onDescriptionChange) },
        preview = {
            ComposerSectionPanelContent(copy.preview, content = {
                state.videoUri?.let { uri ->
                    ComposerMediaPostPreviewContent(
                        isVideo = true, description = state.text, subtitle = state.selectedDestination?.label ?: copy.feed, topChips = emptyList(),
                        actionLabels = defaultComposerPreviewActionLabels(), authorName = copy.author,
                        compact = landscape, backgroundSeed = uri,
                        media = { slots.videoPreview(uri, landscape, Modifier.fillMaxSize()) },
                        modifier = Modifier
                            .testTag(ComposerSelectedVideoPreviewTestTag)
                            .semantics { contentDescription = ComposerSelectedVideoPreviewTestTag },
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
