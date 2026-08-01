package com.quata.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.accessibility.EnglishCriticalControlsAccessibility
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.presentation.CreatePostPlatformSlots
import com.quata.feature.postcomposer.presentation.CreatePostRoot
import com.quata.feature.postcomposer.presentation.CreatePostViewModel
import com.quata.feature.postcomposer.presentation.EnglishCreatePostRootCopy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CreatePostRootInteractionTest {
    @Test
    fun rootMountExercisesEmojiTriggerAndResetCleanup() = runComposeUiTest {
        var resetToken by mutableStateOf(0)
        var cleanupCalls = 0
        val repository = object : PostComposerRepository {
            override suspend fun createPost(draft: PostComposerDraft) = Result.success<String?>("post")
        }
        setContent {
            QuataTheme {
                val viewModel = remember { CreatePostViewModel(repository) }
                CreatePostRoot(
                    viewModel = viewModel,
                    accessibility = EnglishCriticalControlsAccessibility,
                    isLandscapeLayout = false,
                    canPublish = false,
                    onAuthRequired = {},
                    onPostCreated = {},
                    onBack = {},
                    resetToken = resetToken,
                    copy = EnglishCreatePostRootCopy,
                    slots = CreatePostPlatformSlots(
                        pickImage = {}, captureImage = {}, editImage = null,
                        pickVideo = {}, captureVideo = null, editVideo = null,
                        imagePreview = { _, _ -> }, videoPreview = { _, _, _ -> },
                        clearOwnedMedia = { cleanupCalls++ },
                    ),
                )
            }
        }

        onNodeWithTag("create-post-common-root").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag))
        onNodeWithTag("composer-type-text").performClick()
        onNodeWithText("Emoji").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        runOnIdle { resetToken = 1 }
        onNodeWithTag("composer-type-picker").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag))
        runOnIdle { assertEquals(2, cleanupCalls) }
    }
}
