package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.platform.MediaFileExportAction
import com.quata.core.platform.MediaFileExportDescriptor
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.QuataFullscreenMediaOverlayCloseTestTag
import com.quata.core.ui.components.QuataFullscreenMediaOverlayRootTestTag
import com.quata.core.ui.components.QuataMediaExportDownloadTestTag
import com.quata.core.ui.components.QuataMediaExportFailureTestTag
import com.quata.core.ui.components.QuataMediaExportRetryTestTag
import com.quata.core.ui.components.QuataMediaExportShareTestTag
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val exports = mutableListOf<Pair<MediaFileExportDescriptor, MediaFileExportAction>>()
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
                        exportMediaFile = { descriptor, action ->
                            exports += descriptor to action
                            when (exports.size) {
                                1 -> PlatformResult.Failure("fixture_transport_failed")
                                3 -> PlatformResult.Cancelled
                                else -> PlatformResult.Success(Unit)
                            }
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
        onNodeWithTag(QuataMediaExportDownloadTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { exports.size == 1 }
        onNodeWithTag(QuataMediaExportFailureTestTag).assertIsDisplayed()
        onNodeWithTag(QuataMediaExportRetryTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { exports.size == 2 }
        onNodeWithTag(QuataMediaExportShareTestTag).performClick()
        waitUntil(timeoutMillis = 5_000) { exports.size == 3 }
        runOnIdle {
            assertEquals(listOf(MediaFileExportAction.Download, MediaFileExportAction.Download, MediaFileExportAction.Share), exports.map { it.second })
            assertEquals(1, exports.map { it.first }.distinct().size)
            assertEquals("fixture://feed-media.png", exports.first().first.reference)
            assertEquals("image/png", exports.first().first.mimeType)
            assertEquals("Imagen focal.png", exports.first().first.displayName)
        }
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

    @Test
    fun feedMediaFileExportDescriptorKeepsTheConcreteImageAndVideoTypes() {
        val author = User("feed-export-author", "feed-export@example.invalid", "Feed Export")
        val image = Post(
            id = "feed-export-image",
            author = author,
            text = "[MEDIA_TITULO:Imagen focal] imagen",
            imageUrl = "fixture://feed-media.png",
            createdAt = "2026-09-20T00:00:00Z",
        )
        val video = Post(
            id = "feed-export-video",
            author = author,
            text = "[MEDIA_TITULO:Vídeo focal] vídeo",
            videoUrl = "fixture://feed-video.mp4",
            createdAt = "2026-09-20T00:00:00Z",
        )

        assertEquals("image/png", feedMediaFileExportDescriptor(image, "Image", "Video")?.mimeType)
        assertEquals("Imagen focal.png", feedMediaFileExportDescriptor(image, "Image", "Video")?.displayName)
        assertEquals("video/mp4", feedMediaFileExportDescriptor(video, "Image", "Video")?.mimeType)
        assertEquals("Vídeo focal.mp4", feedMediaFileExportDescriptor(video, "Image", "Video")?.displayName)
    }

    @Test
    fun focusedFeedVideoUsesAnExplicitFullscreenActionAndPreservesPlaybackPosition() = runComposeUiTest {
        val post = Post(
            id = "feed-video-detail",
            author = User("feed-video-author", "feed-video@example.invalid", "Feed Video"),
            text = "[MEDIA_TITULO:Vídeo focal] Feed video detail",
            videoUrl = "fixture://feed-video.mp4",
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
                        media = { mediaPost, active, initialPositionMs, onPositionChanged, _, _ ->
                            val state = if (active) "active" else "paused"
                            Column {
                                Text(
                                    "media-${mediaPost.id}-$state-$initialPositionMs",
                                    Modifier.testTag("media-slot-$state"),
                                )
                                Button(
                                    onClick = { onPositionChanged(initialPositionMs + 1_000L) },
                                    modifier = Modifier.testTag("media-slot-$state-advance"),
                                ) {
                                    Text("advance")
                                }
                            }
                        },
                    ),
                    focusedPostId = post.id,
                    onBackFromFocusedPost = {},
                    isLandscape = false,
                )
            }
        }

        onNodeWithTag("media-slot-active").assertTextContains("-0", substring = true)
        onNodeWithTag("media-slot-active-advance").performClick()

        onNodeWithContentDescription("$FeedPostVideoFullscreenOpenTestTagPrefix.${post.id}")
            .assertHasClickAction()
            .performClick()

        onNodeWithTag(QuataFullscreenMediaOverlayRootTestTag).assertIsDisplayed()
        onNodeWithTag("media-slot-paused").assertTextContains("-1000", substring = true)
        onNodeWithTag("media-slot-active").assertTextContains("-1000", substring = true)
        onNodeWithTag("media-slot-active-advance").performClick()

        onNodeWithTag(QuataFullscreenMediaOverlayCloseTestTag).performClick()
        assertTrue(onAllNodesWithTag(QuataFullscreenMediaOverlayRootTestTag).fetchSemanticsNodes().isEmpty())

        onNodeWithTag(FeedPostDetailChromeTestTag).assertIsDisplayed()
        onNodeWithTag("media-slot-active").assertTextContains("-2000", substring = true)
        onNodeWithContentDescription("$FeedPostVideoFullscreenOpenTestTagPrefix.${post.id}")
            .assertHasClickAction()
    }

    @Test
    fun feedVideoRestoresAndPersistsTheActorScopedPositionThroughTheSharedHost() {
        val post = Post(
            id = "feed-video-durable",
            author = User("feed-video-author", "feed-video@example.invalid", "Feed Video"),
            text = "Feed video durable position",
            videoUrl = "fixture://feed-video-durable.mp4",
            createdAt = "2026-10-07T00:00:00Z",
        )
        val actorId = "feed-video-actor"
        val mediaId = feedVideoPositionMediaId(post.id, checkNotNull(post.videoUrl))
        val preferences = MediaMemoryPreferenceStore()
        val store = FeedVideoPositionStore(preferences)
        runTest { store.persist(actorId, mapOf(mediaId to 12_345L)) }

        runComposeUiTest {
            setContent {
                QuataTheme {
                    FeedScreenHost(
                        padding = PaddingValues(),
                        repository = mediaRepository(post),
                        stateHolder = MediaStateHolder(post),
                        slots = FeedScreenPlatformSlots(
                            media = { _, active, initialPositionMs, onPositionChanged, _, _ ->
                                if (initialPositionMs == 0L) {
                                    LaunchedEffect(Unit) { onPositionChanged(0L) }
                                }
                                if (active) {
                                    Column {
                                        Text("position-$initialPositionMs", Modifier.testTag("durable-video-position"))
                                        Button(
                                            onClick = { onPositionChanged(23_456L) },
                                            modifier = Modifier.testTag("durable-video-advance"),
                                        ) { Text("advance") }
                                    }
                                }
                            },
                        ),
                        currentUserId = actorId,
                        videoPositionStore = store,
                        focusedPostId = post.id,
                        onBackFromFocusedPost = {},
                        isLandscape = false,
                    )
                }
            }

            onNodeWithTag("durable-video-position").assertTextContains("12345", substring = true)
            onNodeWithTag("durable-video-advance").performClick()
            mainClock.advanceTimeBy(1_000L)
            waitForIdle()
        }

        runTest { assertEquals(23_456L, store.restore(actorId)[mediaId]) }
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
    override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(post.takeIf { it.id == postId })
})

private class MediaMemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}
