package com.quata.web

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.quata.core.accessibility.CriticalControlsAccessibilityCatalog
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.presentation.CreatePostPlatformSlots
import com.quata.feature.postcomposer.presentation.CreatePostRoot
import com.quata.feature.postcomposer.presentation.CreatePostUiEvent
import com.quata.feature.postcomposer.presentation.CreatePostViewModel
import com.quata.feature.postcomposer.presentation.createPostRootCopyForLanguageTag
import com.quata.feature.postcomposer.presentation.viewModelMessages
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WebComposerMediaSlots(
    val pickImage: suspend () -> String?,
    val captureImage: suspend () -> String?,
    val pickVideo: suspend () -> String?,
    val captureVideo: suspend () -> String?,
    val editImage: (suspend (String) -> String?)? = null,
    val editVideo: (suspend (String) -> String?)? = null,
    val imagePreview: @Composable (String, Modifier) -> Unit,
    val videoPreview: @Composable (String, Modifier) -> Unit,
    val export: (@Composable ColumnScope.(String, PostComposerType) -> Unit)? = null,
    val requestLocation: (((String, Double?, Double?) -> Unit) -> Unit)? = null,
)

/** Thin browser wrapper: all form/state/preview structure lives in common CreatePostRoot. */
@Composable
fun WebPostComposerHost(
    repository: PostComposerRepository,
    mediaSlots: WebComposerMediaSlots,
    isLandscapeLayout: Boolean,
    onBack: () -> Unit,
    onAuthRequired: () -> Unit,
    onPostCreated: (String?) -> Unit,
    canPublish: Boolean,
    modifier: Modifier = Modifier,
) {
    val copy = createPostRootCopyForLanguageTag(browserCapabilityLanguageTag())
    val viewModel = remember(repository, copy) { CreatePostViewModel(repository, messages = copy.viewModelMessages()) }
    val scope = rememberCoroutineScope()
    DisposableEffect(viewModel, canPublish, onAuthRequired) {
        val uninstall = installWebPostComposerE2eBridge(
            setText = { value -> viewModel.onEvent(CreatePostUiEvent.TextChanged(value)) },
            setImage = { value -> viewModel.onEvent(CreatePostUiEvent.ImageSelected(value.takeIf(String::isNotBlank))) },
            setVideo = { value -> viewModel.onEvent(CreatePostUiEvent.VideoSelected(value.takeIf(String::isNotBlank))) },
            setLocation = { value -> viewModel.onEvent(CreatePostUiEvent.LocationLabelChanged(value)) },
            submitText = { if (canPublish) viewModel.submit(PostComposerType.Text) else onAuthRequired() },
            submitImage = { if (canPublish) viewModel.submit(PostComposerType.Image) else onAuthRequired() },
            state = {
                val state = viewModel.uiState.value
                buildJsonObject {
                    put("textLength", state.text.length)
                    put("isLoading", state.isLoading)
                    put("hasImage", !state.imageUri.isNullOrBlank())
                    put("hasVideo", !state.videoUri.isNullOrBlank())
                    state.imageUri?.let { put("imageUri", it.take(220)) }
                    state.videoUri?.let { put("videoUri", it.take(220)) }
                    state.locationLabel?.let { put("locationLabel", it) }
                    put("destinationCount", state.destinations.size)
                    state.selectedDestinationWallId?.let { put("selectedDestinationWallId", it) }
                    state.selectedDestination?.label?.let { put("selectedDestinationLabel", it) }
                    put("hasError", state.error != null)
                    state.error?.let { put("error", it.take(160)) }
                    put("hasSuccess", state.successMessage != null)
                    put("hasCreatedPostId", !state.createdPostId.isNullOrBlank())
                }.toString()
            },
        )
        onDispose { uninstall() }
    }
    CreatePostRoot(
        viewModel = viewModel,
        accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(browserCapabilityLanguageTag()),
        isLandscapeLayout = isLandscapeLayout,
        onBack = onBack,
        onAuthRequired = onAuthRequired,
        onPostCreated = onPostCreated,
        canPublish = canPublish,
        copy = copy,
        slots = CreatePostPlatformSlots(
            pickImage = { scope.launch { mediaSlots.pickImage()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } } },
            captureImage = { scope.launch { mediaSlots.captureImage()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } } },
            editImage = mediaSlots.editImage?.let { edit -> { scope.launch { stateUri(viewModel, true)?.let { current -> edit(current)?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } } } } },
            pickVideo = { scope.launch { mediaSlots.pickVideo()?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } } },
            captureVideo = { scope.launch { mediaSlots.captureVideo()?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } } },
            editVideo = mediaSlots.editVideo?.let { edit -> { scope.launch { stateUri(viewModel, false)?.let { current -> edit(current)?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } } } } },
            imagePreview = mediaSlots.imagePreview,
            videoPreview = { uri, _, modifier -> mediaSlots.videoPreview(uri, modifier) },
            mediaExport = mediaSlots.export,
            requestLocation = mediaSlots.requestLocation,
        ),
        modifier = modifier,
    )
}

private fun stateUri(viewModel: CreatePostViewModel, image: Boolean): String? =
    if (image) viewModel.uiState.value.imageUri else viewModel.uiState.value.videoUri
