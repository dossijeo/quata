package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OfficialAuthenticationContinuationTest {
    @Test
    fun anonymousLikeRetainsDesiredStateAndResumesExactlyOnce() = runComposeUiTest {
        val post = continuationOfficialPost()
        val holder = OfficialContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        setContent { OfficialContinuationFixture(holder, coordinator) }

        onNodeWithTag("official.action.like.${post.id}").performClick()
        runOnIdle {
            assertTrue(holder.events.none { it == OfficialFeedUiEvent.ToggleLike(post.id) })
            assertEquals(AuthenticationContinuationKind.OfficialTogglePostLike, coordinator.pending.value?.intent?.kind)
            assertEquals(post.id, coordinator.pending.value?.intent?.targetId)
            assertEquals(true, coordinator.pending.value?.intent?.desiredState)
            holder.state.value = holder.state.value.copy(currentUser = continuationOfficialUser("actor"))
        }
        waitUntil(timeoutMillis = 5_000) {
            holder.events.count { it == OfficialFeedUiEvent.ToggleLike(post.id) } == 1
        }
        runOnIdle { holder.state.value = holder.state.value.copy(isRefreshing = true) }
        waitForIdle()

        runOnIdle {
            assertEquals(1, holder.events.count { it == OfficialFeedUiEvent.ToggleLike(post.id) })
            assertNull(coordinator.pending.value)
        }
    }

    @Test
    fun officialCommentContinuationsPreserveExactDraftReplyAndReportTargets() = runComposeUiTest {
        val post = continuationOfficialPost()
        val holder = OfficialContinuationStateHolder(post)
        val coordinator = AuthenticationContinuationCoordinator()
        setContent { OfficialContinuationFixture(holder, coordinator) }

        fun authenticateAndWait(predicate: () -> Boolean) {
            runOnIdle { holder.state.value = holder.state.value.copy(currentUser = continuationOfficialUser("actor")) }
            waitUntil(timeoutMillis = 5_000, condition = predicate)
            runOnIdle { holder.state.value = holder.state.value.copy(currentUser = null) }
            waitForIdle()
        }

        runOnIdle {
            coordinator.request(
                officialAuthenticationContinuation(
                    kind = AuthenticationContinuationKind.OfficialAddComment,
                    targetId = post.id,
                    relatedId = post.comments.single().id,
                    text = "exact official draft",
                ),
            )
        }
        authenticateAndWait {
            holder.events.filterIsInstance<OfficialFeedUiEvent.AddComment>().any { event ->
                event.postId == post.id &&
                    event.comment.message == "exact official draft" &&
                    event.comment.replyToCommentId == post.comments.single().id
            }
        }

        runOnIdle {
            coordinator.request(
                officialAuthenticationContinuation(
                    kind = AuthenticationContinuationKind.OfficialReportComment,
                    targetId = post.comments.single().id,
                    relatedId = post.id,
                ),
            )
        }
        authenticateAndWait {
            holder.events.count { it == OfficialFeedUiEvent.ReportComment(post.comments.single().id) } == 1
        }

        runOnIdle {
            assertEquals(1, holder.events.filterIsInstance<OfficialFeedUiEvent.AddComment>().size)
            assertEquals(1, holder.events.count { it == OfficialFeedUiEvent.ReportComment(post.comments.single().id) })
            assertNull(coordinator.pending.value)
        }
    }
}

@androidx.compose.runtime.Composable
private fun OfficialContinuationFixture(
    holder: OfficialContinuationStateHolder,
    coordinator: AuthenticationContinuationCoordinator,
) {
    QuataTheme {
        OfficialFeedScreenHost(
            padding = PaddingValues(),
            repository = continuationOfficialRepository(holder.post),
            stateHolder = holder,
            slots = OfficialFeedScreenPlatformSlots(
                avatar = { _, _ -> },
                media = { _, _, _ -> },
                article = { _, _ -> },
                mediaViewer = { _, _, _, _ -> },
                openUrl = {},
                share = { PlatformResult.Unsupported },
                message = {},
                showComposeMessage = false,
                canCreateOfficialPost = false,
                rankingAvatar = {},
            ),
            currentUserId = null,
            focusedPostId = null,
            strings = OfficialFeedScreenStrings(),
            onFocusedPostHandled = {},
            onAuthRequired = {},
            onAuthenticationContinuationRequired = { coordinator.request(it) },
            authenticationContinuationCoordinator = coordinator,
            onOpenUserProfile = {},
            onCreateOfficialPost = {},
            modifier = Modifier,
        )
    }
}

private class OfficialContinuationStateHolder(val post: OfficialPostItem) : OfficialFeedStateHolder {
    val state = MutableStateFlow(OfficialFeedUiState(isLoading = false, posts = listOf(post)))
    val events = mutableListOf<OfficialFeedUiEvent>()
    override val uiState = state
    override fun onEvent(event: OfficialFeedUiEvent) {
        events += event
    }
    override fun refreshCurrentUser() = Unit
}

private fun continuationOfficialPost() = OfficialPostItem(
    id = "official-auth-continuation-post",
    author = continuationOfficialUser("official-author").copy(isOfficial = true),
    title = "Official continuation",
    summary = "Continuation witness",
    contentHtml = "<p>Continuation witness</p>",
    contentPlain = "Continuation witness",
    type = OfficialPostType.Announcement,
    createdAt = "2026-09-30T00:00:00Z",
    comments = listOf(
        PostComment(
            id = "official-auth-continuation-comment",
            authorName = "peer",
            message = "official reply target",
            timestamp = "2026-09-30T00:00:01Z",
            authorId = "peer",
        ),
    ),
)

private fun continuationOfficialUser(id: String) = User(id, "$id@example.invalid", id)

private fun continuationOfficialRepository(post: OfficialPostItem) = object : OfficialRepository {
    override fun observeOfficialFeed() = flowOf(Result.success(listOf(post)))
    override suspend fun getOfficialFeed() = Result.success(listOf(post))
    override suspend fun refreshOfficialFeed() = Result.success(listOf(post))
    override suspend fun loadOlderOfficialFeedPage(cursor: com.quata.feature.official.domain.OfficialFeedCursor, limit: Int) = Result.success(emptyList<OfficialPostItem>())
    override suspend fun getOfficialPost(postId: String) = Result.success(post.takeIf { it.id == postId })
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun createPost(draft: OfficialPostDraft) = Result.success<OfficialPostItem?>(null)
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}
