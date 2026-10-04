package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FeedAuthenticationContinuationTest {
    @Test
    fun anonymousLikeResumesExactlyOnceAfterAuthentication() = runComposeUiTest {
        val post = continuationPost()
        val holder = ContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = continuationRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(media = { _, active, _, _, _, _ -> if (active) Text("media") }),
                    onAuthenticationContinuationRequired = { coordinator.request(it) },
                    authenticationContinuationCoordinator = coordinator,
                )
            }
        }

        onNodeWithTag("feed.action.like.${post.id}").performClick()
        runOnIdle {
            assertTrue(holder.events.none { it == FeedUiEvent.ToggleLike(post.id) })
            assertEquals(AuthenticationContinuationKind.FeedTogglePostLike, coordinator.pending.value?.intent?.kind)
            assertEquals(post.id, coordinator.pending.value?.intent?.targetId)
            assertEquals(true, coordinator.pending.value?.intent?.desiredState)
        }

        runOnIdle {
            holder.state.value = holder.state.value.copy(currentUser = continuationUser("actor"))
        }
        waitUntil(timeoutMillis = 5_000) { holder.events.count { it == FeedUiEvent.ToggleLike(post.id) } == 1 }
        runOnIdle {
            holder.state.value = holder.state.value.copy(isRefreshing = true)
        }
        waitForIdle()

        runOnIdle {
            assertEquals(1, holder.events.count { it == FeedUiEvent.ToggleLike(post.id) })
            assertNull(coordinator.pending.value)
        }
    }

    @Test
    fun authenticatedAccountThatAlreadyLikedDoesNotUndoAnonymousLikeIntent() = runComposeUiTest {
        val post = continuationPost()
        val holder = ContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = continuationRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(media = { _, active, _, _, _, _ -> if (active) Text("media") }),
                    onAuthenticationContinuationRequired = { coordinator.request(it) },
                    authenticationContinuationCoordinator = coordinator,
                )
            }
        }

        onNodeWithTag("feed.action.like.${post.id}").performClick()
        runOnIdle {
            holder.state.value = holder.state.value.copy(
                currentUser = continuationUser("actor"),
                posts = listOf(post.copy(isLikedByCurrentUser = true)),
            )
        }
        waitUntil(timeoutMillis = 5_000) { coordinator.pending.value == null }

        runOnIdle {
            assertTrue(holder.events.none { it == FeedUiEvent.ToggleLike(post.id) })
            assertNull(coordinator.pending.value)
        }
    }

    @Test
    fun reportSuccessIsShownOnlyAfterTheStateHolderConfirmsTransportSuccess() = runComposeUiTest {
        val post = continuationPostWithComment()
        val holder = ContinuationStateHolder(post)
        val messages = mutableListOf<String>()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = continuationRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(
                        media = { _, active, _, _, _, _ -> if (active) Text("media") },
                        message = messages::add,
                    ),
                )
            }
        }

        runOnIdle { assertTrue(messages.isEmpty()) }
        runOnIdle {
            holder.state.value = holder.state.value.copy(
                confirmedCommentReportIds = setOf(post.comments.single().id),
            )
        }
        waitUntil(timeoutMillis = 5_000) { messages.size == 1 }

        runOnIdle {
            assertEquals(FeedScreenStrings().reportSuccess, messages.single())
            assertEquals(
                1,
                holder.events.count { it == FeedUiEvent.ConfirmedCommentReportConsumed(post.comments.single().id) },
            )
        }
    }

    @Test
    fun dismissedAuthenticationCannotMutateOnALaterLogin() = runComposeUiTest {
        val post = continuationPost()
        val holder = ContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = continuationRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(media = { _, active, _, _, _, _ -> if (active) Text("media") }),
                    onAuthenticationContinuationRequired = { coordinator.request(it) },
                    authenticationContinuationCoordinator = coordinator,
                )
            }
        }

        onNodeWithTag("feed.action.like.${post.id}").performClick()
        runOnIdle {
            coordinator.clearAll()
            holder.state.value = holder.state.value.copy(currentUser = continuationUser("later-actor"))
        }
        waitForIdle()

        runOnIdle {
            assertTrue(holder.events.none { it == FeedUiEvent.ToggleLike(post.id) })
            assertNull(coordinator.pending.value)
        }
    }

    @Test
    fun everyFeedContinuationClaimsTheExactActionOnce() = runComposeUiTest {
        val post = continuationPostWithComment()
        val holder = ContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        var composerOpenCount = 0
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = continuationRepository(post),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(media = { _, active, _, _, _, _ -> if (active) Text("media") }),
                    onAuthenticationContinuationRequired = { coordinator.request(it) },
                    authenticationContinuationCoordinator = coordinator,
                    onCreatePost = { composerOpenCount += 1 },
                )
            }
        }

        fun authenticateAndWait(predicate: () -> Boolean) {
            runOnIdle { holder.state.value = holder.state.value.copy(currentUser = continuationUser("actor")) }
            waitUntil(timeoutMillis = 5_000, condition = predicate)
            runOnIdle { holder.state.value = holder.state.value.copy(currentUser = null) }
            waitForIdle()
        }

        runOnIdle {
            coordinator.request(feedAuthenticationContinuation(AuthenticationContinuationKind.FeedReportPost, post.id))
        }
        authenticateAndWait { holder.events.count { it == FeedUiEvent.ReportPost(post.id) } == 1 }

        runOnIdle {
            coordinator.request(feedAuthenticationContinuation(AuthenticationContinuationKind.FeedOpenComposer))
        }
        authenticateAndWait { composerOpenCount == 1 }

        runOnIdle {
            coordinator.request(
                feedAuthenticationContinuation(
                    kind = AuthenticationContinuationKind.FeedAddComment,
                    targetId = post.id,
                    relatedId = post.comments.single().id,
                    text = "exact retained draft",
                ),
            )
        }
        authenticateAndWait {
            holder.events.filterIsInstance<FeedUiEvent.AddComment>().any { event ->
                event.postId == post.id &&
                    event.comment.message == "exact retained draft" &&
                    event.comment.replyToCommentId == post.comments.single().id
            }
        }

        runOnIdle {
            coordinator.request(
                feedAuthenticationContinuation(
                    kind = AuthenticationContinuationKind.FeedReportComment,
                    targetId = post.comments.single().id,
                    relatedId = post.id,
                ),
            )
        }
        authenticateAndWait {
            holder.events.count { it == FeedUiEvent.ReportComment(post.comments.single().id) } == 1
        }

        runOnIdle {
            assertEquals(1, holder.events.count { it == FeedUiEvent.ReportPost(post.id) })
            assertEquals(1, composerOpenCount)
            assertEquals(1, holder.events.filterIsInstance<FeedUiEvent.AddComment>().size)
            assertEquals(1, holder.events.count { it == FeedUiEvent.ReportComment(post.comments.single().id) })
            assertNull(coordinator.pending.value)
        }
    }
}

private class ContinuationStateHolder(post: Post) : FeedStateHolder {
    val state = MutableStateFlow(FeedUiState(isLoading = false, posts = listOf(post)))
    val events = mutableListOf<FeedUiEvent>()
    override val uiState = state
    override fun onEvent(event: FeedUiEvent) {
        events += event
    }
}

private fun continuationPost() = Post(
    id = "auth-continuation-post",
    author = continuationUser("author"),
    text = "Continuation witness",
    createdAt = "2026-09-30T00:00:00Z",
)

private fun continuationPostWithComment() = continuationPost().copy(
    comments = listOf(
        PostComment(
            id = "auth-continuation-comment",
            authorName = "peer",
            message = "reply target",
            timestamp = "2026-09-30T00:00:01Z",
            authorId = "peer",
        ),
    ),
)

private fun continuationUser(id: String) = User(id, "$id@example.invalid", id)

private fun continuationRepository(post: Post) = ReadOnlyFeedRepository(object : FeedReadRepository {
    override fun observeFeed() = flowOf(Result.success(listOf(post)))
    override suspend fun getFeed() = Result.success(listOf(post))
    override suspend fun refreshFeed() = Result.success(listOf(post))
    override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(post.takeIf { it.id == postId })
})
