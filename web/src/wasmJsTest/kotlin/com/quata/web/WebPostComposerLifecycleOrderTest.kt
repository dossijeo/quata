package com.quata.web

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.PlatformFile
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Host-level Compose evidence for the ordering guarantee: replacement first detaches the old
 * preview and only then invokes the old local object's revocation capability.
 */
@OptIn(ExperimentalTestApi::class)
class WebPostComposerLifecycleOrderTest {
    @Test
    fun replacementDetachesPreviewBeforeRevokingItsLocalBlob() {
        val order = mutableListOf<String>()
        val first = owned("blob:https://quata.test/first", "first", order)
        val second = owned("blob:https://quata.test/second", "second", order)

        runComposeUiTest {
            setContent {
                QuataTheme {
                    WebPostComposerHost(
                        repository = NoopPostComposerRepository,
                        mediaSlots = WebComposerMediaSlots(
                            imageGallery = { modifier, onSelected ->
                                Button(onClick = { onSelected(first) }, modifier = modifier) { Text("Select first") }
                            },
                            imageCamera = { modifier, onSelected ->
                                Button(onClick = { onSelected(second) }, modifier = modifier) { Text("Select second") }
                            },
                            videoGallery = { _, _ -> },
                            videoCamera = { _, _ -> },
                            preview = { uri, _, _ ->
                                DisposableEffect(uri) {
                                    onDispose {
                                        if (uri != null) order += "detached:$uri"
                                    }
                                }
                            },
                        ),
                        isLandscapeLayout = false,
                    )
                }
            }

            onNodeWithTag("composer-type-image").performClick()
            onNodeWithText("Select first").performClick()
            onNodeWithText("Select second").performClick()

            val detachedFirst = order.indexOf("detached:${first.file.reference}")
            val releasedFirst = order.indexOf("released:first")
            assertTrue(detachedFirst >= 0, "the old preview must detach during replacement")
            assertTrue(releasedFirst > detachedFirst, "revocation must occur after the old preview detaches")
        }
    }

    private fun owned(reference: String, name: String, order: MutableList<String>): WebComposerMediaSelection =
        WebComposerMediaSelection.ownedLocal(PlatformFile(reference)) { order += "released:$name" }

    private object NoopPostComposerRepository : PostComposerRepository {
        override suspend fun createPost(draft: PostComposerDraft): Result<String?> = Result.failure(
            IllegalStateException("test-only"),
        )
    }
}
