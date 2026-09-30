package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerAuthenticationRequiredException
import com.quata.core.common.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostComposerAuthenticationContinuationTest {
    @Test
    fun replacementAndClaimAreOneShotWhileCancelRetainsEditableDraft() {
        val coordinator = PostComposerAuthenticationContinuationCoordinator()
        val first = coordinator.request(snapshot("first"), PostComposerType.Text)
        val replacement = coordinator.request(snapshot("replacement"), PostComposerType.Image)

        assertNull(coordinator.claim(first.requestId))
        assertEquals(replacement, coordinator.claim(replacement.requestId))
        assertNull(coordinator.claim(replacement.requestId))
        assertEquals("replacement", coordinator.retainedDraft.value?.text)

        coordinator.request(snapshot("cancelled"), PostComposerType.Video)
        coordinator.cancelAuthentication()
        assertNull(coordinator.pending.value)
        assertEquals("cancelled", coordinator.retainedDraft.value?.text)

        coordinator.clear()
        assertNull(coordinator.retainedDraft.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun typedAuthenticationRejectionPreservesDraftAndRequestsContinuation() = runTest {
        val viewModel = CreatePostViewModel(
            repository = object : PostComposerRepository {
                override suspend fun loadDestinations() = Result.success(
                    listOf(PostComposerDestination("wall", "Centro", isDefault = true)),
                )

                override suspend fun createPost(draft: PostComposerDraft) =
                    Result.failure<String?>(PostComposerAuthenticationRequiredException())
            },
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        viewModel.onEvent(CreatePostUiEvent.TextChanged("draft survives"))
        viewModel.submit(PostComposerType.Text)
        advanceUntilIdle()

        assertEquals(PostComposerType.Text, viewModel.uiState.value.authenticationRequiredSubmitType)
        assertEquals("draft survives", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.error)

        val restored = viewModel.snapshot(CreatePostStep.Text)
        viewModel.onEvent(CreatePostUiEvent.ClearDraft)
        viewModel.restore(restored)
        assertEquals("draft survives", viewModel.uiState.value.text)
        assertEquals("wall", viewModel.uiState.value.selectedDestinationWallId)
    }

    private fun snapshot(text: String) = PostComposerDraftSnapshot(
        step = CreatePostStep.Text,
        text = text,
        textPatternId = DEFAULT_TEXT_CANVAS_PATTERN_ID,
        imageUri = "file://image",
        videoUri = "file://video",
        locationLabel = "Madrid",
        latitude = 40.0,
        longitude = -3.0,
        locationOrigin = CreatePostLocationOrigin.Manual,
        selectedDestinationWallId = "wall",
    )
}
