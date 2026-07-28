package com.quata.web

import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import com.quata.core.accessibility.EnglishCriticalControlsAccessibility
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.feature.postcomposer.presentation.ComposerBackButtonContent
import com.quata.feature.postcomposer.presentation.ComposerTypePickerContent
import com.quata.feature.postcomposer.presentation.ComposerTypePickerStrings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposerCriticalControlSemanticsTest {
    @Test
    fun pickerPublishesSelectedDisabledAndTraversalSemantics() {
        runComposeUiTest {
            setContent {
                QuataTheme {
                    ComposerTypePickerContent(
                        isLandscapeLayout = false,
                        strings = ComposerTypePickerStrings("Text", "Image", "Video"),
                        selectedType = PostComposerType.Image,
                        accessibility = EnglishCriticalControlsAccessibility,
                        enabled = false,
                        onText = {},
                        onImage = {},
                        onVideo = {},
                    )
                }
            }

            onNodeWithTag("composer-type-picker").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true),
            )
            onNodeWithTag("composer-type-text")
                .assertIsNotSelected()
                .assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
            onNodeWithTag("composer-type-image")
                .assertIsSelected()
                .assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
            onNodeWithTag("composer-type-video")
                .assertIsNotSelected()
                .assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        }
    }

    @Test
    fun pickerKeyboardFocusFollowsTextImageVideoOrder() {
        runComposeUiTest {
            setContent {
                QuataTheme {
                    var selectedType by remember { mutableStateOf(PostComposerType.Text) }
                    Column {
                        ComposerTypePickerContent(
                            isLandscapeLayout = true,
                            strings = ComposerTypePickerStrings("Text", "Image", "Video"),
                            selectedType = selectedType,
                            accessibility = EnglishCriticalControlsAccessibility,
                            onText = { selectedType = PostComposerType.Text },
                            onImage = { selectedType = PostComposerType.Image },
                            onVideo = { selectedType = PostComposerType.Video },
                        )
                        ComposerBackButtonContent(
                            label = "Back to feed",
                            onBack = {},
                            accessibility = EnglishCriticalControlsAccessibility,
                        )
                    }
                }
            }

            onNodeWithTag("composer-type-text")
                .performSemanticsAction(SemanticsActions.RequestFocus)
            onNodeWithTag("composer-type-text").assertIsFocused()

            onNodeWithTag("composer-type-text").performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag("composer-type-image").assertIsFocused()

            onNodeWithTag("composer-type-image").performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag("composer-type-video").assertIsFocused()

            listOf("composer-type-text", "composer-type-image", "composer-type-video").forEach { typeTag ->
                onNodeWithTag(typeTag).performClick()
                onNodeWithTag("composer-back")
                    .assertContentDescriptionEquals("Back")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            }
        }
    }
}
