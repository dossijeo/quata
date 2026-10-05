package com.quata.feature.official.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialFeedCursor
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialRemoteRankingInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun remoteSecondPageFailsClosedRetriesAndOpensExactTarget() {
        val repository = AndroidOfficialRemoteRankingRepository()
        val model = OfficialFeedViewModel(repository)
        try {
            compose.setContent {
                QuataTheme {
                    OfficialFeedScreenHost(
                        padding = PaddingValues(),
                        repository = repository,
                        stateHolder = model,
                        slots = androidOfficialRemoteRankingSlots(),
                        currentUserId = null,
                        focusedPostId = null,
                        strings = OfficialFeedScreenStrings(),
                        onFocusedPostHandled = {},
                        onFocusedPostChanged = {},
                        onAuthRequired = {},
                        onOpenUserProfile = {},
                        onCreateOfficialPost = {},
                        modifier = Modifier,
                    )
                }
            }

            compose.waitUntil(10_000) { model.uiState.value.posts.size == 50 }
            compose.onNodeWithText(AndroidOfficialInitialMarker).assertIsDisplayed()
            compose.onAllNodesWithContentDescription("LIVE")[0].performClick()
            compose.waitUntil(10_000) { model.uiState.value.rankingError != null }
            compose.onNodeWithTag(OfficialRankingErrorTestTag).assertIsDisplayed()
            compose.onAllNodesWithTag("live.ranking.open.$AndroidOfficialRemoteTargetId").assertCountEquals(0)

            compose.onNodeWithTag(OfficialRankingRetryTestTag).performClick()
            compose.waitUntil(10_000) { model.uiState.value.rankingPosts?.size == 101 }
            compose.runOnIdle {
                assertEquals(50, model.uiState.value.posts.size)
                assertFalse(model.uiState.value.posts.any { it.id == AndroidOfficialRemoteTargetId })
                assertEquals(3, repository.olderPageCalls)
            }
            compose.onNodeWithTag("live.ranking.open.$AndroidOfficialRemoteTargetId").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { model.uiState.value.posts.any { it.id == AndroidOfficialRemoteTargetId } }
            compose.onNodeWithText(AndroidOfficialRemoteTargetTitle).assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(model.uiState.value.posts.any { it.id == AndroidOfficialRemoteTargetId })
            }
        } finally {
            model.close()
        }
    }
}

private class AndroidOfficialRemoteRankingRepository : OfficialRepository {
    private val allPosts = (0..100).map(::androidOfficialRankingPost)
    private val first = allPosts.subList(0, 50)
    private val second = allPosts.subList(50, 100)
    private val final = allPosts.subList(100, 101)
    private val observed = MutableStateFlow(Result.success(first))
    private var failOnce = true
    var olderPageCalls = 0
        private set

    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = observed
    override suspend fun getOfficialFeed() = Result.success(first)
    override suspend fun refreshOfficialFeed() = Result.success(first)
    override suspend fun loadOlderOfficialFeedPage(cursor: OfficialFeedCursor, limit: Int): Result<List<OfficialPostItem>> {
        olderPageCalls += 1
        check(limit == 50)
        return when (cursor.postId) {
            first.last().id -> if (failOnce) {
                failOnce = false
                Result.failure(IllegalStateException("forced_official_ranking_page_failure"))
            } else Result.success(second)
            second.last().id -> Result.success(final)
            else -> Result.success(emptyList())
        }
    }
    override suspend fun getOfficialPost(postId: String) = Result.success(allPosts.firstOrNull { it.id == postId })
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = unsupported()
    override suspend fun deletePost(postId: String): Result<Unit> = unsupported()
    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = unsupported()
    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = unsupported()
    override suspend fun reportComment(commentId: String): Result<Unit> = unsupported()
    private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException("fixture_mutation_not_supported"))
}

private fun androidOfficialRankingPost(index: Int): OfficialPostItem {
    val target = index == 50
    val id = if (target) AndroidOfficialRemoteTargetId else "android-official-ranking-$index"
    val title = when {
        target -> AndroidOfficialRemoteTargetTitle
        index == 0 -> AndroidOfficialInitialMarker
        else -> "Android Official ranking post $index"
    }
    return OfficialPostItem(
        id = id,
        author = User("android-official-author-$index", "android-official-$index@example.invalid", "Official Author $index"),
        title = title,
        summary = "Summary $index",
        contentHtml = "<p>Body $index</p>",
        contentPlain = "Body $index",
        createdAt = "2026-09-${(30 - index / 24).toString().padStart(2, '0')}T${(23 - index % 24).toString().padStart(2, '0')}:00:00Z",
        likesCount = if (target) 10_000 else 100 - index,
    )
}

private fun androidOfficialRemoteRankingSlots() = OfficialFeedScreenPlatformSlots(
    avatar = { _, _ -> },
    media = { _, _, _ -> },
    article = { _, _ -> },
    mediaViewer = { _, _ -> },
    openUrl = {},
    share = { PlatformResult.Unsupported },
    message = {},
    showComposeMessage = false,
    canCreateOfficialPost = false,
    rankingAvatar = {},
)

private const val AndroidOfficialRemoteTargetId = "android-official-ranking-remote-target"
private const val AndroidOfficialRemoteTargetTitle = "Android Official remote ranking target loaded exactly"
private const val AndroidOfficialInitialMarker = "Android Official initial pager remains intact"
