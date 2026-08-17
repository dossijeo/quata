package com.quata.feature.neighborhoods.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NeighborhoodsViewModelTest {
    @Test
    fun `nested public profiles return through the common stack`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)

        model.openUserProfile("a")
        advanceUntilIdle()
        model.openUserProfile("b")
        advanceUntilIdle()

        assertFalse(model.closeUserProfile())
        advanceUntilIdle()
        assertEquals("a", model.uiState.value.selectedProfile?.user?.id)
        assertTrue(model.closeUserProfile())
        assertEquals(null, model.uiState.value.selectedProfile)
        model.close()
    }

    @Test
    fun `follow is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.followResult = CompletableDeferred()

        model.toggleFollowUser("a")
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.user?.isFollowing == true)

        repository.followResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertFalse(model.uiState.value.selectedProfile?.user?.isFollowing == true)
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `follow success updates selected profile counters and follower list`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        advanceUntilIdle()

        val profile = model.uiState.value.selectedProfile
        assertTrue(profile?.user?.isFollowing == true)
        assertEquals(1, profile?.user?.followersCount)
        assertEquals(listOf("me"), profile?.followers?.map { it.id })
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `unfollow success restores selected profile counters and follower list`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.profileOverride = profile(
            "a",
            user = user("a").copy(isFollowing = true, followersCount = 1),
            followers = listOf(user("me")),
        )
        repository.followResult = CompletableDeferred(Result.success(FollowUserResult("a", false, user("me"))))
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        advanceUntilIdle()

        val profile = model.uiState.value.selectedProfile
        assertFalse(profile?.user?.isFollowing == true)
        assertEquals(0, profile?.user?.followersCount)
        assertTrue(profile?.followers.orEmpty().isEmpty())
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `comment is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.commentResult = CompletableDeferred()
        val comment = PostComment("c", "You", "hello", "Now")

        model.addProfileComment("post-a", comment)
        runCurrent()
        assertEquals(listOf(comment), model.uiState.value.selectedProfile?.posts?.single()?.comments)

        repository.commentResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertTrue(model.uiState.value.selectedProfile?.posts?.single()?.comments.orEmpty().isEmpty())
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `consecutive profile comments keep optimistic state until all requests finish`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        val firstResult = CompletableDeferred<Result<Post?>>()
        val secondResult = CompletableDeferred<Result<Post?>>()
        repository.commentResults += firstResult
        repository.commentResults += secondResult
        val first = PostComment("c1", "You", "first", "Now")
        val second = PostComment("c2", "You", "second", "Now")

        model.addProfileComment("post-a", first)
        model.addProfileComment("post-a", second)
        runCurrent()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals("post-a", model.uiState.value.commentingPostId)

        firstResult.complete(Result.success(Post("post-a", User("a", "", "a"), "post", comments = listOf(first), createdAt = "now")))
        advanceUntilIdle()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals("post-a", model.uiState.value.commentingPostId)

        secondResult.complete(Result.success(Post("post-a", User("a", "", "a"), "post", comments = listOf(first, second), createdAt = "now")))
        advanceUntilIdle()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals(null, model.uiState.value.commentingPostId)
        model.close()
    }

    @Test
    fun `profile post like is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.likeResult = CompletableDeferred()

        model.toggleProfilePostLike("post-a")
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.posts?.single()?.isLikedByCurrentUser == true)
        assertEquals(1, model.uiState.value.selectedProfile?.posts?.single()?.likesCount)
        assertEquals("post-a", model.uiState.value.likingPostId)

        repository.likeResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertFalse(model.uiState.value.selectedProfile?.posts?.single()?.isLikedByCurrentUser == true)
        assertEquals(0, model.uiState.value.selectedProfile?.posts?.single()?.likesCount)
        assertEquals(null, model.uiState.value.likingPostId)
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    private fun kotlinx.coroutines.test.TestScope.model(repository: FakeNeighborhoodRepository): NeighborhoodsViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return NeighborhoodsViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
    }
}

private class FakeNeighborhoodRepository : NeighborhoodRepository {
    var followResult = CompletableDeferred(Result.success(FollowUserResult("a", true, user("me"))))
    var commentResult = CompletableDeferred<Result<Post?>>(Result.success(null))
    val commentResults = mutableListOf<CompletableDeferred<Result<Post?>>>()
    var likeResult = CompletableDeferred<Result<Post?>>(Result.success(null))
    var profileOverride: CommunityUserProfile? = null

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flowOf(emptyList())
    override suspend fun openNeighborhoodChat(neighborhood: String) = Result.success("community")
    override suspend fun toggleFollowUser(userId: String) = followResult.await()
    override suspend fun toggleProfilePostLike(postId: String) = likeResult.await()
    override suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?> {
        val queued = commentResults.firstOrNull()
        if (queued != null) {
            commentResults.removeAt(0)
            return queued.await()
        }
        return commentResult.await()
    }
    override suspend fun reportPost(postId: String) = Result.success(Unit)
    override suspend fun reportProfile(userId: String) = Result.success(Unit)
    override suspend fun setProfileBlocked(userId: String, blocked: Boolean) = Result.success(blocked)
    override suspend fun openPrivateChat(userId: String) = Result.success("private")
    override suspend fun isCurrentUserAdmin() = false
    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) =
        Result.success(user(userId).copy(isAdmin = isAdmin, isOfficial = isOfficial))
    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?) = null
    override suspend fun cacheUserProfile(profile: CommunityUserProfile) = Unit
    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow { emit(getUserProfile(userId)) }
    override suspend fun getUserProfile(userId: String) = Result.success(profileOverride ?: profile(userId))

}

private fun user(id: String) = NeighborhoodUser(id, id, "", "Barrio")

private fun profile(
    id: String,
    user: NeighborhoodUser = user(id),
    followers: List<NeighborhoodUser> = emptyList(),
    following: List<NeighborhoodUser> = emptyList(),
): CommunityUserProfile = CommunityUserProfile(
    user = user,
    posts = listOf(Post("post-$id", User(id, "", id), "post", createdAt = "now")),
    followers = followers,
    following = following,
)
