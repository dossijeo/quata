package com.quata.web

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

data class WebComposerMediaSlots(
    val pickImage: suspend () -> String?,
    val captureImage: suspend () -> String?,
    val pickVideo: suspend () -> String?,
    val captureVideo: suspend () -> String?,
    val imagePreview: @Composable (String, Modifier) -> Unit,
    val videoPreview: @Composable (String, Modifier) -> Unit,
    val export: (@Composable ColumnScope.(String, PostComposerType) -> Unit)? = null,
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
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { CreatePostViewModel(repository) }
    val scope = rememberCoroutineScope()
    CreatePostRoot(
        viewModel = viewModel,
        accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(browserCapabilityLanguageTag()),
        isLandscapeLayout = isLandscapeLayout,
        onBack = onBack,
        onAuthRequired = onAuthRequired,
        onPostCreated = onPostCreated,
        slots = CreatePostPlatformSlots(
            pickImage = { scope.launch { mediaSlots.pickImage()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } } },
            captureImage = { scope.launch { mediaSlots.captureImage()?.let { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) } } },
            pickVideo = { scope.launch { mediaSlots.pickVideo()?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } } },
            captureVideo = { scope.launch { mediaSlots.captureVideo()?.let { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) } } },
            imagePreview = mediaSlots.imagePreview,
            videoPreview = mediaSlots.videoPreview,
            mediaExport = mediaSlots.export,
        ),
        modifier = modifier,
    )
}
