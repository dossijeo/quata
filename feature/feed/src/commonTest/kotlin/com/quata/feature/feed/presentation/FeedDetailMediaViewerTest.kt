package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.ui.components.QuataFullscreenMediaOverlayCloseTestTag
import com.quata.core.ui.components.QuataFullscreenMediaOverlayRootTestTag
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FeedDetailMediaViewerTest {
    @Test
    fun focusedFeedMediaOpensTheSharedViewerAndReturnsToTheSameDetail() = runComposeUiTest {
        val post = Post(
            id = "feed-media-detail",
            author = User("feed-media-author", "feed-media@example.invalid", "Feed Media"),
            text = "[MEDIA_TITULO:Imagen focal] Feed media detail",
            imageUrl = "fixture://feed-media.png",
            createdAt = "2026-09-20T00:00:00Z",
        )
        val holder = MediaStateHolder(post)
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = mediaRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(
                        media = { mediaPost, active, _, _, _, _ ->
                            Text("media-${mediaPost.id}-${if (active) "active" else "paused"}")
                        },
                    ),
                    focusedPostId = post.id,
                    onBackFromFocusedPost = {},
                    isLandscape = false,
                )
            }
        }

        onNodeWithTag(FeedPostDetailChromeTestTag).assertIsDisplayed()
        onNodeWithContentDescription("$FeedPostMediaOpenTestTagPrefix.${post.id}")
            .assertHasClickAction()
            .performClick()

        onNodeWithTag(QuataFullscreenMediaOverlayRootTestTag).assertIsDisplayed()
        onNodeWithTag(QuataFullscreenMediaOverlayCloseTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuataFullscreenMediaOverlayRootTestTag)
                .fetchSemanticsNodes()
                .isEmpty()
        }

        onNodeWithTag(FeedPostDetailChromeTestTag).assertIsDisplayed()
        onNodeWithContentDescription("$FeedPostMediaOpenTestTagPrefix.${post.id}")
            .assertHasClickAction()
    }
}

private class MediaStateHolder(post: Post) : FeedStateHolder {
    override val uiState = MutableStateFlow(FeedUiState(isLoading = false, posts = listOf(post)))
    override fun onEvent(event: FeedUiEvent) = Unit
}

private fun mediaRepository(post: Post) = ReadOnlyFeedRepository(object : FeedReadRepository {
    override fun observeFeed() = flowOf(Result.success(listOf(post)))
    override suspend fun getFeed() = Result.success(listOf(post))
    override suspend fun refreshFeed() = Result.success(listOf(post))
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(post.takeIf { it.id == postId })
})
