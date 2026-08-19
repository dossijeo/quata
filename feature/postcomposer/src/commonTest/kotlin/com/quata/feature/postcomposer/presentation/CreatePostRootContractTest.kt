package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.core.common.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

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
    fun postPublishEvidenceAnchorsStayCommon() {
        assertEquals("composer-text-input", ComposerTextInputTestTag)
        assertEquals("composer-publish", ComposerPublishButtonTestTag)
        assertEquals("composer-destination-selector", ComposerDestinationSelectorTestTag)
        assertEquals("composer-destination-selected", ComposerDestinationSelectedTestTag)
        assertEquals("composer-location-section", ComposerLocationSectionTestTag)
        assertEquals("composer-location-value", ComposerLocationValueTestTag)
        assertEquals("composer-location-edit", ComposerLocationEditTestTag)
        assertEquals("composer-location-input", ComposerLocationInputTestTag)
        assertEquals("composer-type-text", "composer-type-${PostComposerType.Text.name.lowercase()}")
        assertEquals("composer-feedback-error", ComposerFeedbackErrorTestTag)
        assertEquals("composer-feedback-retry", ComposerFeedbackRetryTestTag)
        assertEquals("composer-feedback-success", ComposerFeedbackSuccessTestTag)
    }

    @Test
    fun localeCatalogAndCounterRetainProductContract() {
        assertEquals("Create post", createPostRootCopyForLanguageTag("en-US").title)
        assertEquals("Crear publicación", createPostRootCopyForLanguageTag("es-ES").title)
        assertEquals("Créer une publication", createPostRootCopyForLanguageTag("fr-FR").title)
        assertEquals("42/500", createPostRootCopyForLanguageTag("en").characters(42))
        assertEquals("POSTEAR TEXTO", SpanishCreatePostRootCopy.textType)
        assertEquals("POSTEAR FOTO/IMAGEN", SpanishCreatePostRootCopy.imageType)
        assertEquals("POSTEAR VÍDEO", SpanishCreatePostRootCopy.videoType)
        assertEquals("POST TEXT", EnglishCreatePostRootCopy.textType)
        assertEquals("PUBLIER TEXTE", FrenchCreatePostRootCopy.textType)
        assertEquals(CreatePostMessages("Post created", "Could not publish"), EnglishCreatePostRootCopy.viewModelMessages())
        assertEquals(CreatePostMessages("Publicación creada", "No se pudo publicar"), SpanishCreatePostRootCopy.viewModelMessages())
        assertEquals(CreatePostMessages("Publication créée", "Impossible de publier"), FrenchCreatePostRootCopy.viewModelMessages())
        assertEquals("Retry", EnglishCreatePostRootCopy.retry)
        assertEquals("Reintentar", SpanishCreatePostRootCopy.retry)
        assertEquals("Réessayer", FrenchCreatePostRootCopy.retry)
    }

    @Test
    fun viewModelFeedbackUsesTheSameLocalizedCopyAsTheCommonRoot() = runTest {
        val successViewModel = CreatePostViewModel(
            repository = object : PostComposerRepository {
                override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft) = Result.success<String?>("post-1")
            },
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
            messages = EnglishCreatePostRootCopy.viewModelMessages(),
        )
        successViewModel.submit(PostComposerType.Text)
        advanceUntilIdle()
        assertEquals("Post created", successViewModel.uiState.value.successMessage)

        val failureViewModel = CreatePostViewModel(
            repository = object : PostComposerRepository {
                override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft) = Result.failure<String?>(IllegalStateException())
            },
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
            messages = FrenchCreatePostRootCopy.viewModelMessages(),
        )
        failureViewModel.submit(PostComposerType.Text)
        advanceUntilIdle()
        assertEquals("Impossible de publier", failureViewModel.uiState.value.error)
        successViewModel.close()
        failureViewModel.close()
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
    fun clearDraftPreservesLoadedDestinationSelection() = runTest {
        val viewModel = CreatePostViewModel(object : PostComposerRepository {
            override suspend fun loadDestinations() = Result.success(
                listOf(PostComposerDestination("wall-1", "Centro", isDefault = true)),
            )
            override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft) = Result.success<String?>(null)
        }, AppDispatchers(default = StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        viewModel.onEvent(CreatePostUiEvent.TextChanged("hola"))
        viewModel.onEvent(CreatePostUiEvent.ClearDraft)
        assertEquals("", viewModel.uiState.value.text)
        assertEquals("wall-1", viewModel.uiState.value.selectedDestinationWallId)
        assertEquals("Centro", viewModel.uiState.value.selectedDestination?.label)
        viewModel.close()
    }

    @Test
    fun publicationDispatchesExactlyOneEffectiveAuthOrSubmitCallback() {
        var authCalls = 0
        var submitCalls = 0
        dispatchCreatePostPublish(false, { submitCalls++ }, { authCalls++ })
        assertEquals(1, authCalls)
        assertEquals(0, submitCalls)
        dispatchCreatePostPublish(true, { submitCalls++ }, { authCalls++ })
        assertEquals(1, authCalls)
        assertEquals(1, submitCalls)
    }

    @Test
    fun backCancelsOnlyWhenLoadingThenAlwaysResetsAndNavigates() {
        val calls = mutableListOf<String>()
        dispatchCreatePostBack(true, { calls += "cancel" }, { calls += "reset" }, { calls += "back" })
        assertEquals(listOf("cancel", "reset", "back"), calls)
        calls.clear()
        dispatchCreatePostBack(false, { calls += "cancel" }, { calls += "reset" }, { calls += "back" })
        assertEquals(listOf("reset", "back"), calls)
    }

    @Test
    fun sharedEventsBuildTheSameCompleteVideoDraftUsedByEveryHost() = runTest {
        var published = com.quata.feature.postcomposer.domain.PostComposerDraft(PostComposerType.Text)
        val viewModel = CreatePostViewModel(object : PostComposerRepository {
            override suspend fun loadDestinations() = Result.success(
                listOf(
                    PostComposerDestination("wall-1", "Centro", isDefault = true),
                    PostComposerDestination("wall-2", "Bata"),
                ),
            )
            override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft): Result<String?> {
                published = draft
                return Result.success("post-1")
            }
        }, AppDispatchers(default = StandardTestDispatcher(testScheduler)))

        advanceUntilIdle()
        viewModel.onEvent(CreatePostUiEvent.DestinationSelected("wall-2"))
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
        assertEquals("wall-2", published.destinationWallId)
        assertEquals("Bata", published.destinationLabel)
    }

    @Test
    fun failedSubmitKeepsDraftAndRetryReusesTheSameType() = runTest {
        val calls = mutableListOf<PostComposerType>()
        var failNext = true
        val viewModel = CreatePostViewModel(object : PostComposerRepository {
            override suspend fun createPost(draft: com.quata.feature.postcomposer.domain.PostComposerDraft): Result<String?> {
                calls += draft.type
                return if (failNext) {
                    failNext = false
                    Result.failure(IllegalStateException("network down"))
                } else {
                    Result.success("post-2")
                }
            }
        }, AppDispatchers(default = StandardTestDispatcher(testScheduler)))

        viewModel.onEvent(CreatePostUiEvent.VideoSelected("file:///video.mp4"))
        viewModel.onEvent(CreatePostUiEvent.TextChanged("clip"))
        viewModel.submit(PostComposerType.Video)
        advanceUntilIdle()

        assertEquals("network down", viewModel.uiState.value.error)
        assertEquals(PostComposerType.Video, viewModel.uiState.value.lastFailedSubmitType)
        assertEquals("file:///video.mp4", viewModel.uiState.value.videoUri)

        viewModel.onEvent(CreatePostUiEvent.RetrySubmit)
        advanceUntilIdle()

        assertEquals(listOf(PostComposerType.Video, PostComposerType.Video), calls)
        assertEquals("Publicación creada", viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.lastFailedSubmitType)
        assertNull(viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.createdPostId)
        viewModel.close()
    }
}
