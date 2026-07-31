package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.core.common.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CreatePostRootContractTest {
    @Test
    fun oneRootOwnsAllThreeComposerTypes() {
        assertEquals("create-post-common-root", CreatePostCommonRootTestTag)
        assertEquals(CreatePostStep.Text, createPostStepFor(PostComposerType.Text))
        assertEquals(CreatePostStep.Image, createPostStepFor(PostComposerType.Image))
        assertEquals(CreatePostStep.Video, createPostStepFor(PostComposerType.Video))
    }

    @Test
    fun sharedEventsBuildTheSameCompleteVideoDraftUsedByEveryHost() = runTest {
        var published = com.quata.feature.postcomposer.domain.PostComposerDraft(PostComposerType.Text)
        val viewModel = CreatePostViewModel(object : PostComposerRepository {
            override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft): Result<String?> {
                published = draft
                return Result.success("post-1")
            }
        }, AppDispatchers(default = StandardTestDispatcher(testScheduler)))

        viewModel.onEvent(CreatePostUiEvent.TextChanged("Título"))
        viewModel.onEvent(CreatePostUiEvent.TextPatternSelected("midnight-blue"))
        viewModel.onEvent(CreatePostUiEvent.VideoSelected("file:///video.mp4"))
        viewModel.onEvent(CreatePostUiEvent.LocationResolved("Madrid", 40.4, -3.7))
        viewModel.submit(PostComposerType.Video)
        advanceUntilIdle()

        assertEquals(PostComposerType.Video, published.type)
        assertEquals("Título", published.text)
        assertEquals("file:///video.mp4", published.videoUri)
        assertEquals("Madrid", published.locationLabel)
    }
}
