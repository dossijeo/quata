package com.quata.feature.neighborhoods.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NeighborhoodsViewModel(
    private val repository: NeighborhoodRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : NeighborhoodsScreenModel {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(NeighborhoodsUiState())
    override val uiState: StateFlow<NeighborhoodsUiState> = _uiState.asStateFlow()
    private var communitiesJob: Job? = null
    private var profileJob: Job? = null
    private val profileBackStack = mutableListOf<String>()
    private val pendingProfileCommentCounts = mutableMapOf<String, Int>()

    override fun startObservingCommunities() {
        if (communitiesJob?.isActive == true) return
        communitiesJob = scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.observeCommunities()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudieron cargar las comunidades"
                    )
                }
                .collect { communities ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        communities = communities,
                        error = null
                    )
                }
        }
    }

    override fun stopObservingCommunities() {
        communitiesJob?.cancel()
        communitiesJob = null
    }

    override fun openChat(neighborhood: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingChatNeighborhood != null) return
        _uiState.value = _uiState.value.copy(
            isOpeningChat = true,
            openingChatNeighborhood = neighborhood,
            error = null,
            chatErrorNeighborhood = null,
        )
        scope.launch {
            repository.openNeighborhoodChat(neighborhood)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null,
                        chatErrorNeighborhood = null,
                    )
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null,
                        error = error.message ?: "No se pudo abrir el chat",
                        chatErrorNeighborhood = neighborhood,
                    )
                }
        }
    }

    override fun toggleFollowUser(userId: String) {
        if (_uiState.value.followingUserId == userId) return
        val before = _uiState.value
        val optimisticProfile = before.selectedProfile?.optimisticallyToggleFollow(userId)
        val optimisticCommunities = before.communities.map { community ->
            community.copy(users = community.users.map { user -> user.optimisticallyToggleFollow(userId) })
        }
        scope.launch {
            _uiState.value = before.copy(
                followingUserId = userId,
                selectedProfile = optimisticProfile,
                communities = optimisticCommunities,
                error = null,
            )
            repository.toggleFollowUser(userId)
                .onSuccess { result ->
                    val currentState = _uiState.value
                    val enrichedResult = currentState.withKnownCurrentUser(result)
                    val selectedProfile = currentState.selectedProfile?.withFollowResult(enrichedResult)
                    if (selectedProfile != null) {
                        repository.cacheUserProfile(selectedProfile)
                    }
                    _uiState.value = currentState.copy(
                        followingUserId = null,
                        selectedProfile = selectedProfile ?: currentState.selectedProfile,
                        communities = currentState.communities.withFollowResult(enrichedResult),
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = before.copy(
                        followingUserId = null,
                        error = error.message ?: "No se pudo actualizar el seguimiento"
                    )
                }
        }
    }

    override fun openPrivateChat(userId: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingPrivateChatUserId != null) return
        _uiState.value = _uiState.value.copy(openingPrivateChatUserId = userId, error = null)
        scope.launch {
            repository.openPrivateChat(userId)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(openingPrivateChatUserId = null)
                    withContext(dispatchers.main) {
                        onOpened(conversationId)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        openingPrivateChatUserId = null,
                        error = error.message ?: "No se pudo abrir PRIVI"
                    )
                }
        }
    }

    override fun openUserProfile(userId: String) = openUserProfile(userId, addCurrentToBackStack = true)

    private fun openUserProfile(userId: String, addCurrentToBackStack: Boolean) {
        val currentProfileId = _uiState.value.selectedProfile?.user?.id
        if (addCurrentToBackStack && currentProfileId != null && currentProfileId != userId && profileBackStack.lastOrNull() != currentProfileId) {
            profileBackStack += currentProfileId
        }
        profileJob?.cancel()
        scope.launch {
            val currentUserIsAdmin = repository.isCurrentUserAdmin()
            val freshCachedProfile = repository.getCachedUserProfile(userId, PROFILE_CACHE_FRESH_MILLIS)
            val cachedProfile = freshCachedProfile ?: repository.getCachedUserProfile(userId)
            if (cachedProfile != null) {
                _uiState.value = _uiState.value.copy(
                    selectedProfile = cachedProfile,
                    openingProfileUserId = null,
                    refreshingProfileUserId = if (freshCachedProfile == null) userId else null,
                    currentUserIsAdmin = currentUserIsAdmin,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    openingProfileUserId = userId,
                    refreshingProfileUserId = null,
                    currentUserIsAdmin = currentUserIsAdmin,
                    error = null
                )
            }
            profileJob = scope.launch {
                repository.observeUserProfile(userId)
                    .collect { result ->
                        result
                            .onSuccess { profile ->
                                val currentState = _uiState.value
                                val shouldUpdateVisibleProfile =
                                    currentState.selectedProfile?.user?.id == userId ||
                                        currentState.openingProfileUserId == userId ||
                                        currentState.refreshingProfileUserId == userId
                                _uiState.value = currentState.copy(
                                    openingProfileUserId = if (currentState.openingProfileUserId == userId) null else currentState.openingProfileUserId,
                                    refreshingProfileUserId = if (currentState.refreshingProfileUserId == userId) null else currentState.refreshingProfileUserId,
                                    selectedProfile = if (shouldUpdateVisibleProfile) profile else currentState.selectedProfile,
                                    error = null
                                )
                            }
                            .onFailure { error ->
                                val currentState = _uiState.value
                                _uiState.value = currentState.copy(
                                    openingProfileUserId = if (currentState.openingProfileUserId == userId) null else currentState.openingProfileUserId,
                                    refreshingProfileUserId = if (currentState.refreshingProfileUserId == userId) null else currentState.refreshingProfileUserId,
                                    error = error.message ?: "No se pudo abrir el perfil"
                                )
                            }
                    }
                }
        }
    }

    /** Returns true only when the global overlay should be dismissed by its platform host. */
    fun closeUserProfile(): Boolean {
        val previousProfileId = profileBackStack.removeLastOrNull()
        if (previousProfileId != null) {
            openUserProfile(previousProfileId, addCurrentToBackStack = false)
            return false
        }
        clearUserProfile()
        return true
    }

    fun clearUserProfile() {
        profileJob?.cancel()
        profileJob = null
        profileBackStack.clear()
        _uiState.value = _uiState.value.copy(
            openingProfileUserId = null,
            refreshingProfileUserId = null,
            selectedProfile = null
        )
    }

    fun reportProfilePost(postId: String) {
        scope.launch {
            val profileUserId = _uiState.value.selectedProfile?.user?.id
            repository.reportPost(postId)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo reportar")
                }
            if (profileUserId != null) {
                refreshSelectedProfile(profileUserId)
            }
        }
    }

    fun addProfileComment(postId: String, comment: PostComment) {
        val before = _uiState.value.selectedProfile ?: return
        val optimistic = before.copy(posts = before.posts.map { post ->
            if (post.id == postId && post.comments.none { it.id == comment.id }) {
                post.copy(comments = post.comments + comment)
            } else {
                post
            }
        })
        pendingProfileCommentCounts[postId] = (pendingProfileCommentCounts[postId] ?: 0) + 1
        _uiState.value = _uiState.value.copy(
            selectedProfile = optimistic,
            commentingPostId = postId,
            error = null,
        )
        scope.launch {
            repository.addProfileComment(postId, comment)
                .onSuccess { persisted ->
                    val current = _uiState.value.selectedProfile
                    completeProfileComment(
                        postId = postId,
                        selectedProfile = if (persisted == null || current == null) current else current.copy(
                            posts = current.posts.map { if (it.id == postId) mergePersistedProfilePost(persisted, it) else it },
                        ),
                        error = null,
                    )
                }
                .onFailure { error ->
                    val current = _uiState.value.selectedProfile
                    completeProfileComment(
                        postId = postId,
                        selectedProfile = current?.copy(
                            posts = current.posts.map { post ->
                                if (post.id == postId) post.copy(comments = post.comments.filterNot { it.id == comment.id }) else post
                            },
                        ) ?: before,
                        error = error.message ?: "No se pudo publicar el comentario",
                    )
                }
        }
    }

    private fun completeProfileComment(
        postId: String,
        selectedProfile: CommunityUserProfile?,
        error: String?,
    ) {
        val remaining = (pendingProfileCommentCounts[postId] ?: 1) - 1
        if (remaining > 0) {
            pendingProfileCommentCounts[postId] = remaining
        } else {
            pendingProfileCommentCounts.remove(postId)
        }
        _uiState.value = _uiState.value.copy(
            selectedProfile = selectedProfile,
            commentingPostId = pendingProfileCommentCounts.keys.firstOrNull(),
            error = error,
        )
    }

    private fun mergePersistedProfilePost(persisted: Post, current: Post): Post {
        val persistedCommentIds = persisted.comments.mapTo(mutableSetOf()) { it.id }
        val pendingComments = current.comments.filterNot { it.id in persistedCommentIds }
        return persisted.copy(comments = persisted.comments + pendingComments)
    }

    fun toggleProfilePostLike(postId: String) {
        if (_uiState.value.likingPostId != null) return
        val before = _uiState.value.selectedProfile ?: return
        val beforePost = before.posts.firstOrNull { it.id == postId } ?: return
        val nextLiked = !beforePost.isLikedByCurrentUser
        val optimistic = before.copy(posts = before.posts.map { post ->
            if (post.id == postId) {
                post.copy(
                    isLikedByCurrentUser = nextLiked,
                    likesCount = (post.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0),
                )
            } else post
        })
        _uiState.value = _uiState.value.copy(
            selectedProfile = optimistic,
            likingPostId = postId,
            error = null,
        )
        scope.launch {
            repository.toggleProfilePostLike(postId)
                .onSuccess { persisted ->
                    val current = _uiState.value.selectedProfile
                    val resolved = if (persisted == null || current == null) current else current.copy(
                        posts = current.posts.map { if (it.id == postId) persisted else it },
                    )
                    resolved?.let { repository.cacheUserProfile(it) }
                    _uiState.value = _uiState.value.copy(
                        selectedProfile = resolved,
                        likingPostId = null,
                        error = null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        selectedProfile = before,
                        likingPostId = null,
                        error = error.message ?: "No se pudo actualizar el me gusta",
                    )
                }
        }
    }

    fun reportProfile(userId: String) {
        if (_uiState.value.profileSafetyUpdatingUserId != null) return
        scope.launch {
            _uiState.value = _uiState.value.copy(profileSafetyUpdatingUserId = userId, error = null)
            repository.reportProfile(userId)
                .onSuccess { _uiState.value = _uiState.value.copy(profileSafetyUpdatingUserId = null) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        profileSafetyUpdatingUserId = null,
                        error = error.message ?: "No se pudo reportar el perfil",
                    )
                }
        }
    }

    fun setProfileBlocked(userId: String, blocked: Boolean) {
        if (_uiState.value.profileSafetyUpdatingUserId != null) return
        val before = _uiState.value.selectedProfile ?: return
        _uiState.value = _uiState.value.copy(
            selectedProfile = before.copy(isBlockedByCurrentUser = blocked),
            profileSafetyUpdatingUserId = userId,
            error = null,
        )
        scope.launch {
            repository.setProfileBlocked(userId, blocked)
                .onSuccess { persisted ->
                    val current = _uiState.value.selectedProfile
                    _uiState.value = _uiState.value.copy(
                        selectedProfile = current?.copy(isBlockedByCurrentUser = persisted),
                        profileSafetyUpdatingUserId = null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        selectedProfile = before,
                        profileSafetyUpdatingUserId = null,
                        error = error.message ?: "No se pudo actualizar el bloqueo",
                    )
                }
        }
    }

    fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) {
        if (_uiState.value.roleUpdatingUserId != null) return
        scope.launch {
            _uiState.value = _uiState.value.copy(roleUpdatingUserId = userId, error = null)
            repository.setUserRoles(userId, isAdmin, isOfficial)
                .onSuccess { updatedUser ->
                    val current = _uiState.value
                    val selectedProfile = current.selectedProfile?.let { profile ->
                        if (profile.user.id == userId) {
                            profile.copy(user = profile.user.copy(isAdmin = updatedUser.isAdmin, isOfficial = updatedUser.isOfficial))
                        } else {
                            profile.copy(
                                followers = profile.followers.map { if (it.id == userId) it.copy(isAdmin = updatedUser.isAdmin, isOfficial = updatedUser.isOfficial) else it },
                                following = profile.following.map { if (it.id == userId) it.copy(isAdmin = updatedUser.isAdmin, isOfficial = updatedUser.isOfficial) else it }
                            )
                        }
                    }
                    selectedProfile?.let { repository.cacheUserProfile(it) }
                    _uiState.value = current.copy(
                        roleUpdatingUserId = null,
                        selectedProfile = selectedProfile ?: current.selectedProfile,
                        communities = current.communities.map { community ->
                            community.copy(
                                users = community.users.map { user ->
                                    if (user.id == userId) user.copy(isAdmin = updatedUser.isAdmin, isOfficial = updatedUser.isOfficial) else user
                                }
                            )
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        roleUpdatingUserId = null,
                        error = error.message ?: "No se pudieron actualizar los permisos"
                    )
                }
        }
    }

    private suspend fun refreshSelectedProfile(userId: String) {
        val current = _uiState.value.selectedProfile ?: return
        if (current.user.id != userId) return
        repository.getUserProfile(userId)
            .onSuccess { profile ->
                repository.cacheUserProfile(profile)
                _uiState.value = _uiState.value.copy(selectedProfile = profile)
            }
    }

    private fun CommunityUserProfile.withFollowResult(result: FollowUserResult): CommunityUserProfile {
        val targetUserId = result.userId
        val currentUserId = result.currentUser.id
        val targetUser = sequenceOf(user)
            .plus(followers.asSequence())
            .plus(following.asSequence())
            .firstOrNull { it.id == targetUserId }
        val wasFollowingTarget = following.any { it.id == targetUserId }
        val updatedUser = user
            .withFollowResult(result, updateFollowerCount = user.id == targetUserId)
            .let { updated ->
                if (user.id == currentUserId) {
                    updated.withFollowingCountResult(
                        wasFollowing = wasFollowingTarget,
                        isFollowing = result.isFollowing
                    )
                } else {
                    updated
                }
            }
        return copy(
            user = updatedUser,
            followers = if (user.id == targetUserId) {
                followers.withCurrentFollower(result)
            } else {
                followers.map { it.withFollowResult(result) }
            },
            following = if (user.id == currentUserId) {
                following.withTargetFollowing(result, targetUser)
            } else {
                following.map { it.withFollowResult(result) }
            }
        )
    }

    private fun NeighborhoodsUiState.withKnownCurrentUser(result: FollowUserResult): FollowUserResult =
        findKnownUser(result.currentUser.id)?.let { result.copy(currentUser = it) } ?: result

    private fun NeighborhoodsUiState.findKnownUser(userId: String): NeighborhoodUser? {
        selectedProfile?.let { profile ->
            sequenceOf(profile.user)
                .plus(profile.followers.asSequence())
                .plus(profile.following.asSequence())
                .firstOrNull { it.id == userId }
                ?.let { return it }
        }
        return communities
            .asSequence()
            .flatMap { it.users.asSequence() }
            .firstOrNull { it.id == userId }
    }

    private fun List<NeighborhoodUser>.withCurrentFollower(result: FollowUserResult): List<NeighborhoodUser> {
        val updated = map { it.withFollowResult(result) }
        if (!result.isFollowing) return updated.filterNot { it.id == result.currentUser.id }
        if (updated.any { it.id == result.currentUser.id }) return updated
        return listOf(result.currentUser) + updated
    }

    private fun List<NeighborhoodUser>.withTargetFollowing(
        result: FollowUserResult,
        targetUser: NeighborhoodUser?
    ): List<NeighborhoodUser> {
        val updated = map { it.withFollowResult(result) }
        if (!result.isFollowing) return updated.filterNot { it.id == result.userId }
        if (updated.any { it.id == result.userId }) return updated
        return targetUser?.withFollowResult(result)?.let { listOf(it) + updated } ?: updated
    }

    private fun List<NeighborhoodCommunity>.withFollowResult(result: FollowUserResult): List<NeighborhoodCommunity> =
        map { community ->
            community.copy(users = community.users.map { it.withFollowResult(result) })
        }

    private fun NeighborhoodUser.withFollowResult(
        result: FollowUserResult,
        updateFollowerCount: Boolean = false
    ): NeighborhoodUser {
        if (id != result.userId) return this
        val followerDelta = when {
            !updateFollowerCount || isFollowing == result.isFollowing -> 0
            result.isFollowing -> 1
            else -> -1
        }
        return copy(
            isFollowing = result.isFollowing,
            followersCount = (followersCount + followerDelta).coerceAtLeast(0)
        )
    }

    private fun NeighborhoodUser.withFollowingCountResult(
        wasFollowing: Boolean,
        isFollowing: Boolean
    ): NeighborhoodUser {
        val followingDelta = when {
            wasFollowing == isFollowing -> 0
            isFollowing -> 1
            else -> -1
        }
        return copy(followingCount = (followingCount + followingDelta).coerceAtLeast(0))
    }

    private fun CommunityUserProfile.optimisticallyToggleFollow(targetUserId: String): CommunityUserProfile = copy(
        user = user.optimisticallyToggleFollow(targetUserId),
        followers = followers.map { it.optimisticallyToggleFollow(targetUserId) },
        following = following.map { it.optimisticallyToggleFollow(targetUserId) },
    )

    private fun NeighborhoodUser.optimisticallyToggleFollow(targetUserId: String): NeighborhoodUser {
        if (id != targetUserId) return this
        val next = !isFollowing
        return copy(
            isFollowing = next,
            followersCount = (followersCount + if (next) 1 else -1).coerceAtLeast(0),
        )
    }

    companion object {
        private const val PROFILE_CACHE_FRESH_MILLIS = 5 * 60_000L

    }

    override fun close() {
        communitiesJob?.cancel()
        profileJob?.cancel()
        scope.coroutineContext.cancel()
    }
}
