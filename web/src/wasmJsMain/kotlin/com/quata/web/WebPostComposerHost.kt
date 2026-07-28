package com.quata.web

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.quata.core.accessibility.CriticalControlsAccessibilityCatalog
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.presentation.ComposerMediaPostFormContent
import com.quata.feature.postcomposer.presentation.ComposerMediaSourceFormContent
import com.quata.feature.postcomposer.presentation.ComposerPublishButtonContent
import com.quata.feature.postcomposer.presentation.ComposerScreenLayoutContent
import com.quata.feature.postcomposer.presentation.ComposerSubmissionFeedbackContent
import com.quata.feature.postcomposer.presentation.ComposerTextCanvasContent
import com.quata.feature.postcomposer.presentation.ComposerTextPostFormContent
import com.quata.feature.postcomposer.presentation.ComposerTypePickerContent
import com.quata.feature.postcomposer.presentation.ComposerTypePickerStrings
import com.quata.feature.postcomposer.presentation.CreatePostUiEvent
import com.quata.feature.postcomposer.presentation.CreatePostViewModel

/**
 * Browser composition boundary for the portable composer. Media acquisition, camera, gallery and
 * export remain injected slots; this host deliberately owns no browser/backend media pipeline.
 */
data class WebComposerMediaSlots(
    val imageGallery: @Composable (Modifier, (String?) -> Unit) -> Unit,
    val imageCamera: @Composable (Modifier, (String?) -> Unit) -> Unit,
    val videoGallery: @Composable (Modifier, (String?) -> Unit) -> Unit,
    val videoCamera: @Composable (Modifier, (String?) -> Unit) -> Unit,
    val preview: @Composable (uri: String?, isVideo: Boolean, modifier: Modifier) -> Unit,
    val export: @Composable ColumnScope.(uri: String?, isVideo: Boolean) -> Unit = { _, _ -> },
)

@Composable
fun WebPostComposerHost(
    repository: PostComposerRepository,
    mediaSlots: WebComposerMediaSlots,
    isLandscapeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { CreatePostViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    var type by remember { mutableStateOf(PostComposerType.Text) }
    val accessibility = CriticalControlsAccessibilityCatalog.forLanguageTag(browserCapabilityLanguageTag())
    val copy = accessibility.composer
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    ComposerScreenLayoutContent(
        title = copy.title,
        scrollState = rememberScrollState(),
        form = {
            ComposerTypePickerContent(
                isLandscapeLayout = isLandscapeLayout,
                strings = ComposerTypePickerStrings(copy.textType, copy.imageType, copy.videoType),
                selectedType = type,
                accessibility = accessibility,
                onText = { type = PostComposerType.Text },
                onImage = { type = PostComposerType.Image },
                onVideo = { type = PostComposerType.Video },
            )
            when (type) {
                PostComposerType.Text -> ComposerTextPostFormContent(
                    isLandscapeLayout = isLandscapeLayout,
                    textValue = TextFieldValue(state.text),
                    contentTitle = copy.contentTitle,
                    placeholder = copy.placeholder,
                    wordCountText = copy.characters(state.text.length),
                    minLines = 6,
                    onTextChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it.text)) },
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
                    publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, { viewModel.submit(type) }, accessibility = accessibility) },
                )
                PostComposerType.Image, PostComposerType.Video -> {
                    val isVideo = type == PostComposerType.Video
                    val uri = if (isVideo) state.videoUri else state.imageUri
                    ComposerMediaPostFormContent(
                        isLandscapeLayout = isLandscapeLayout,
                        mediaSource = {
                            ComposerMediaSourceFormContent(
                                title = if (isVideo) copy.videoType else copy.imageType,
                                isLandscapeLayout = isLandscapeLayout,
                                primarySourceAction = { slotModifier ->
                                    if (isVideo) mediaSlots.videoGallery(slotModifier) { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) }
                                    else mediaSlots.imageGallery(slotModifier) { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) }
                                },
                                secondarySourceAction = { slotModifier ->
                                    if (isVideo) mediaSlots.videoCamera(slotModifier) { viewModel.onEvent(CreatePostUiEvent.VideoSelected(it)) }
                                    else mediaSlots.imageCamera(slotModifier) { viewModel.onEvent(CreatePostUiEvent.ImageSelected(it)) }
                                },
                                afterEdit = { mediaSlots.export(this, uri, isVideo) },
                            )
                        },
                        preview = { mediaSlots.preview(uri, isVideo, Modifier.fillMaxWidth()) },
                        publish = { ComposerPublishButtonContent(state.isLoading, copy.publish, copy.publishing, { viewModel.submit(type) }, accessibility = accessibility) },
                    )
                }
            }
        },
        feedback = { ComposerSubmissionFeedbackContent(state.error, state.successMessage) },
        modifier = modifier,
    )
}
