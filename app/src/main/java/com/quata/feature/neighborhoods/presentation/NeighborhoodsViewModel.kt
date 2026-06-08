package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NeighborhoodsViewModel(
    private val repository: NeighborhoodRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NeighborhoodsUiState())
    val uiState: StateFlow<NeighborhoodsUiState> = _uiState.asStateFlow()
    private var communitiesJob: Job? = null
    private var profileJob: Job? = null

    fun startObservingCommunities() {
        if (communitiesJob?.isActive == true) return
        communitiesJob = viewModelScope.launch {
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

    fun stopObservingCommunities() {
        communitiesJob?.cancel()
        communitiesJob = null
    }

    fun openChat(neighborhood: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingChatNeighborhood != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isOpeningChat = true,
                openingChatNeighborhood = neighborhood,
                error = null
            )
            repository.openNeighborhoodChat(neighborhood)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null
                    )
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null,
                        error = error.message ?: "No se pudo abrir el chat"
                    )
                }
        }
    }

    fun toggleFollowUser(userId: String) {
        if (_uiState.value.followingUserId == userId) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(followingUserId = userId, error = null)
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
                    _uiState.value = _uiState.value.copy(
                        followingUserId = null,
                        error = error.message ?: "No se pudo actualizar el seguimiento"
                    )
                }
        }
    }

    fun openPrivateChat(userId: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingPrivateChatUserId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(openingPrivateChatUserId = userId, error = null)
            repository.openPrivateChat(userId)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(openingPrivateChatUserId = null)
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        openingPrivateChatUserId = null,
                        error = error.message ?: "No se pudo abrir PRIVI"
                    )
                }
        }
    }

    fun openUserProfile(userId: String) {
        profileJob?.cancel()
        viewModelScope.launch {
            val freshCachedProfile = repository.getCachedUserProfile(userId, PROFILE_CACHE_FRESH_MILLIS)
            val cachedProfile = freshCachedProfile ?: repository.getCachedUserProfile(userId)
            if (cachedProfile != null) {
                _uiState.value = _uiState.value.copy(
                    selectedProfile = cachedProfile,
                    openingProfileUserId = null,
                    refreshingProfileUserId = if (freshCachedProfile == null) userId else null,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    openingProfileUserId = userId,
                    refreshingProfileUserId = null,
                    error = null
                )
            }
            profileJob = viewModelScope.launch {
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

    fun closeUserProfile() {
        profileJob?.cancel()
        profileJob = null
        _uiState.value = _uiState.value.copy(
            openingProfileUserId = null,
            refreshingProfileUserId = null,
            selectedProfile = null
        )
    }

    fun reportProfilePost(postId: String) {
        viewModelScope.launch {
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

    companion object {
        private const val PROFILE_CACHE_FRESH_MILLIS = 5 * 60_000L

        fun factory(repository: NeighborhoodRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NeighborhoodsViewModel(repository) as T
        }
    }
}
