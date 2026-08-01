package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.core.common.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun localeCatalogAndCounterRetainProductContract() {
        assertEquals("Create post", createPostRootCopyForLanguageTag("en-US").title)
        assertEquals("Crear publicación", createPostRootCopyForLanguageTag("es-ES").title)
        assertEquals("Créer une publication", createPostRootCopyForLanguageTag("fr-FR").title)
        assertEquals("42/500", createPostRootCopyForLanguageTag("en").characters(42))
    }

    @Test
    fun clearDraftResetsMediaLocationAndText() {
        val viewModel = CreatePostViewModel(object : PostComposerRepository {
            override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft) = Result.success<String?>(null)
        })
        viewModel.onEvent(CreatePostUiEvent.TextChanged("x".repeat(600)))
        viewModel.onEvent(CreatePostUiEvent.ImageSelected("file:///image.jpg"))
        viewModel.onEvent(CreatePostUiEvent.LocationResolved("Madrid", 40.4, -3.7))
        assertEquals(500, viewModel.uiState.value.text.length)
        viewModel.onEvent(CreatePostUiEvent.ClearDraft)
        assertEquals("", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.imageUri)
        assertNull(viewModel.uiState.value.locationLabel)
        viewModel.close()
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
