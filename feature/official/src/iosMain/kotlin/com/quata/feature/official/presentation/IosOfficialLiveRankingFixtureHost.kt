package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.feature.official.domain.OfficialFeedCursor
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController

/** Opt-in iOS XCTest fixture that keeps the product Official host and replaces only its repository. */
fun QuataIosOfficialLiveRankingFixtureViewController(
    failFirstOlderPage: Boolean,
): UIViewController = QuataOfficialViewController(
    IosOfficialHostDependencies(
        repository = IosOfficialLiveRankingFixtureRepository(failFirstOlderPage),
        profileOpeningState = IosMemberProfileOpeningState(),
    ),
)

private class IosOfficialLiveRankingFixtureRepository(
    failFirstOlderPage: Boolean,
) : OfficialRepository {
    private val allPosts = (0..100).map(::officialRankingFixturePost)
    private val firstPage = allPosts.subList(0, 50)
    private val secondPage = allPosts.subList(50, 100)
    private val finalPage = allPosts.subList(100, 101)
    private val observed = MutableStateFlow(Result.success(firstPage))
    private var shouldFailOlderPage = failFirstOlderPage

    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = observed
    override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(firstPage)
    override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(firstPage)

    override suspend fun loadOlderOfficialFeedPage(
        cursor: OfficialFeedCursor,
        limit: Int,
    ): Result<List<OfficialPostItem>> {
        check(limit == 50) { "official_ranking_fixture_unexpected_limit" }
        return when (cursor.postId) {
            firstPage.last().id -> {
                if (shouldFailOlderPage) {
                    shouldFailOlderPage = false
                    Result.failure(IllegalStateException("official_ranking_fixture_forced_page_failure"))
                } else {
                    Result.success(secondPage)
                }
            }
            secondPage.last().id -> Result.success(finalPage)
            finalPage.last().id -> Result.success(emptyList())
            else -> Result.failure(IllegalStateException("official_ranking_fixture_unexpected_cursor"))
        }
    }

    override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> =
        Result.success(allPosts.firstOrNull { it.id == postId })
    override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = unsupported()
    override suspend fun deletePost(postId: String): Result<Unit> = unsupported()
    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = unsupported()
    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = unsupported()
    override suspend fun reportComment(commentId: String): Result<Unit> = unsupported()

    private fun <T> unsupported(): Result<T> =
        Result.failure(UnsupportedOperationException("official_ranking_fixture_mutation_not_supported"))
}

private fun officialRankingFixturePost(index: Int): OfficialPostItem {
    val isRemoteTarget = index == 50
    val id = if (isRemoteTarget) OfficialLiveRankingRemoteTargetId else "official-ranking-fixture-$index"
    val day = 30 - (index / 24)
    val hour = 23 - (index % 24)
    val timestamp = "2026-09-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z"
    val title = when {
        isRemoteTarget -> OfficialLiveRankingRemoteTargetTitle
        index == 0 -> OfficialLiveRankingInitialMarkerTitle
        else -> "Official ranking fixture $index"
    }
    return OfficialPostItem(
        id = id,
        author = User(
            id = "official-ranking-author-$index",
            email = "official-ranking-$index@example.invalid",
            displayName = if (isRemoteTarget) "Remote Official Ranking Author" else "Official Ranking Author $index",
        ),
        title = title,
        summary = "Summary for $title",
        contentHtml = "<p>Body for $title</p>",
        contentPlain = "Body for $title",
        createdAt = timestamp,
        likesCount = if (isRemoteTarget) 10_000 else 100 - index,
    )
}

const val OfficialLiveRankingRemoteTargetId = "official-ranking-remote-target"
const val OfficialLiveRankingRemoteTargetTitle = "Official remote ranking target loaded exactly"
const val OfficialLiveRankingInitialMarkerTitle = "Official initial pager remains intact"
