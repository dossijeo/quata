package com.quata.web

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WebNeighborhoodsActorScopeTest {
    @Test
    fun hotActorChangesReplaceTheActorScopedModel() = runComposeUiTest {
        val composedActor = mutableStateOf<String?>("actor-a")
        val repository = ActorScopedNeighborhoodRepository()
        var currentModel: NeighborhoodsViewModel? = null

        setContent {
            val model = rememberWebNeighborhoodsViewModel(
                repository = repository,
                currentUserId = composedActor.value,
                initialProfileRoute = emptyList(),
                onProfileRouteChanged = {},
            )
            currentModel = model
        }

        waitForIdle()
        val actorAModel = requireNotNull(currentModel)

        runOnIdle {
            composedActor.value = "actor-b"
        }
        waitForIdle()
        val actorBModel = requireNotNull(currentModel)
        assertNotSame(actorAModel, actorBModel)

        runOnIdle {
            composedActor.value = null
        }
        waitForIdle()
        assertNotSame(actorBModel, currentModel)
    }

    @Test
    fun cachedAttachmentsAndLateWritesRemainBoundToTheirOriginalActor() = runTest {
        var now = 1L
        val actorACache = WebActorBoundCommunityProfileCache(actorId = "actor-a", nowMillis = { now++ })
        var activeCache = actorACache
        activeCache.put(actorProfile("actor-a"))

        assertEquals(listOf("actor-a.pdf"), activeCache.get(PeerProfileId, null)?.attachments?.map(ProfileAttachment::name))

        val releaseLateActorA = CompletableDeferred<Unit>()
        val lateActorAWrite = launch {
            releaseLateActorA.await()
            actorACache.put(actorProfile("actor-a-late"))
        }
        activeCache = WebActorBoundCommunityProfileCache(actorId = "actor-b", nowMillis = { now++ })
        releaseLateActorA.complete(Unit)
        lateActorAWrite.join()

        assertEquals(listOf("actor-a-late.pdf"), actorACache.get(PeerProfileId, null)?.attachments?.map(ProfileAttachment::name))
        assertNull(activeCache.get(PeerProfileId, null))

        activeCache = WebActorBoundCommunityProfileCache(actorId = null, nowMillis = { now++ })
        assertNull(activeCache.get(PeerProfileId, null))
    }

    @Test
    fun suspendedOldModelCallbackCachesOnlyThroughItsRetiredRepository() = runTest {
        val oldCallStarted = CompletableDeferred<Unit>()
        val releaseOldCall = CompletableDeferred<Unit>()
        val actorARepository = ActorScopedNeighborhoodRepository(
            profileActor = "actor-a-late",
            profileCallStarted = oldCallStarted,
            releaseProfileCall = releaseOldCall,
        )
        val background = UnconfinedTestDispatcher(testScheduler)
        val main = StandardTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(background, background, main)
        val actorAModel = NeighborhoodsViewModel(actorARepository, dispatchers)

        actorAModel.ensureProfilePostLikeState(PeerProfileId, "missing-post", desiredState = false)
        runCurrent()
        oldCallStarted.await()

        val actorBRepository = ActorScopedNeighborhoodRepository(profileActor = "actor-b")
        val anonymousRepository = ActorScopedNeighborhoodRepository(profileActor = null)
        val actorBModel = NeighborhoodsViewModel(actorBRepository, dispatchers)
        val anonymousModel = NeighborhoodsViewModel(anonymousRepository, dispatchers)

        releaseOldCall.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("actor-a-late.pdf"),
            actorARepository.cachedProfile?.attachments?.map(ProfileAttachment::name),
        )
        assertNull(actorBRepository.cachedProfile)
        assertNull(anonymousRepository.cachedProfile)

        actorAModel.close()
        actorBModel.close()
        anonymousModel.close()
    }
}

private class ActorScopedNeighborhoodRepository(
    private val profileActor: String? = null,
    private val profileCallStarted: CompletableDeferred<Unit>? = null,
    private val releaseProfileCall: CompletableDeferred<Unit>? = null,
) : NeighborhoodRepository {
    var cachedProfile: CommunityUserProfile? = null
        private set

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flowOf(emptyList())

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = Result.failure(NotImplementedError())

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = Result.failure(NotImplementedError())

    override suspend fun toggleProfilePostLike(postId: String): Result<Post?> = Result.failure(NotImplementedError())

    override suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?> = Result.failure(NotImplementedError())

    override suspend fun reportPost(postId: String): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun reportProfile(userId: String): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun setProfileBlocked(userId: String, blocked: Boolean): Result<Boolean> = Result.failure(NotImplementedError())

    override suspend fun openPrivateChat(userId: String): Result<String> = Result.failure(NotImplementedError())

    override suspend fun isCurrentUserAdmin(): Boolean = false

    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> =
        Result.failure(NotImplementedError())

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? = null

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        cachedProfile = profile
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> {
        return flowOf(Result.success(actorProfile(profileActor)))
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> {
        profileCallStarted?.complete(Unit)
        releaseProfileCall?.await()
        return Result.success(actorProfile(profileActor))
    }
}

private fun actorProfile(actorId: String?): CommunityUserProfile = CommunityUserProfile(
        user = NeighborhoodUser(
            id = PeerProfileId,
            displayName = "Peer",
            email = "peer@example.invalid",
            neighborhood = "Test",
        ),
        posts = emptyList(),
        attachments = actorId?.let { id ->
            listOf(
                ProfileAttachment(
                    id = "document-$id",
                    name = "$id.pdf",
                    uri = "https://example.invalid/$id.pdf",
                    mimeType = "application/pdf",
                    sentAtMillis = 1L,
                    senderName = "Peer",
                ),
            )
        }.orEmpty(),
    )

private const val PeerProfileId = "peer-profile"
