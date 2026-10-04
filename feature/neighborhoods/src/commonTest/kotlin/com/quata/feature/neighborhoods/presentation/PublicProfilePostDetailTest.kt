package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PublicProfilePostDetailTest {
    @Test
    fun opensExactPostAndReturnsToTheSameGalleryPage() = runComposeUiTest {
        val posts = listOf(
            Post("post-a", User("author-a", "", "Ada"), "A", createdAt = "2026-10-01"),
            Post("post-b", User("author-b", "", "Biko"), "B", createdAt = "2026-10-02"),
        )

        setContent {
            QuataTheme {
                val pagerState = rememberPagerState(pageCount = posts::size)
                val scope = rememberCoroutineScope()
                Column {
                    Button(
                        onClick = { scope.launch { pagerState.scrollToPage(1) } },
                        modifier = Modifier.testTag("profile-detail.select-b"),
                    ) { Text("Select B") }
                    ProfilePostsPagerContent(
                        posts = posts,
                        pagerState = pagerState,
                        onAddComment = { _, _ -> },
                        postPreview = { post, _, _, openDetail ->
                            Button(
                                onClick = { openDetail?.invoke() },
                                enabled = openDetail != null,
                                modifier = Modifier.testTag("profile-detail.open.${post.id}"),
                            ) { Text("Preview ${post.id}") }
                        },
                        detailChrome = { post, back ->
                            Column(Modifier.testTag("profile-detail.chrome.${post.id}")) {
                                Text("Detail ${post.id}")
                                Button(
                                    onClick = back,
                                    modifier = Modifier.testTag("profile-detail.back.${post.id}"),
                                ) { Text("Back") }
                            }
                        },
                        commentsDialog = { _, _, _ -> },
                    )
                }
            }
        }

        onNodeWithTag("profile-detail.select-b").performClick()
        waitUntil { onNodeWithTag("profile-detail.open.post-b").isDisplayed() }
        onNodeWithTag("profile-detail.open.post-b").performClick()

        onNodeWithTag("profile-detail.chrome.post-b").assertIsDisplayed()
        onNodeWithTag(PublicProfilePostDetailContentTestTagPrefix + "post-b").assertIsDisplayed()
        onNodeWithTag(PublicProfilePostDetailContentTestTagPrefix + "post-a").assertDoesNotExist()

        onNodeWithTag("profile-detail.back.post-b").performClick()
        onNodeWithTag("profile-detail.chrome.post-b").assertDoesNotExist()
        onNodeWithTag("profile-detail.open.post-b").assertIsDisplayed()
        onNodeWithTag("profile-detail.open.post-a").assertDoesNotExist()
    }

    @Test
    fun imagePreviewPrimarySurfaceOpensDetailInsteadOfMedia() = runComposeUiTest {
        var detailOpens = 0
        var mediaOpens = 0
        val post = Post(
            "post-with-image",
            User("author", "", "Ada"),
            "Image post",
            imageUrl = "https://example.invalid/post-with-image.png",
            createdAt = "2026-10-03",
        )

        setContent {
            QuataTheme {
                CommunityProfilePostPreviewContent(
                    post = post,
                    commentsCount = 0,
                    canParticipate = true,
                    isLikeUpdating = false,
                    onToggleLike = {},
                    onOpenComments = {},
                    onAuthRequired = {},
                    onOpenDetail = { detailOpens += 1 },
                    onOpenMedia = { mediaOpens += 1 },
                    onShare = {},
                    onReport = {},
                    media = { _, _ -> Box(Modifier.matchParentSize()) },
                )
            }
        }

        onNodeWithTag(PublicProfilePostOpenDetailTestTagPrefix + post.id).performClick()
        runOnIdle {
            assertEquals(1, detailOpens)
            assertEquals(0, mediaOpens)
        }
    }

    @Test
    fun unloadedVideoPrimarySurfaceOpensDetailInsteadOfLoadingInTheGallery() = runComposeUiTest {
        var detailOpens = 0
        var mediaOpens = 0
        val post = Post(
            "post-with-video",
            User("author", "", "Ada"),
            "Video post",
            videoUrl = "https://example.invalid/post-with-video.mp4",
            createdAt = "2026-10-04",
        )

        setContent {
            QuataTheme {
                CommunityProfilePostPreviewContent(
                    post = post,
                    commentsCount = 0,
                    canParticipate = true,
                    isLikeUpdating = false,
                    onToggleLike = {},
                    onOpenComments = {},
                    onAuthRequired = {},
                    onOpenDetail = { detailOpens += 1 },
                    onOpenMedia = { mediaOpens += 1 },
                    onShare = {},
                    onReport = {},
                    media = { _, _ -> Box(Modifier.matchParentSize()) },
                )
            }
        }

        onNodeWithTag(PublicProfilePostVideoStartTestTagPrefix + post.id).performClick()
        runOnIdle {
            assertEquals(1, detailOpens)
            assertEquals(0, mediaOpens)
        }
    }
}
