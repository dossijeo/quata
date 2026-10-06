package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.core.model.PostComment
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared communities presentation logic. */
class NeighborhoodsAndroidViewModel(
    repository: NeighborhoodRepository,
    private val savedStateHandle: SavedStateHandle,
    initialActorId: String?,
) : ViewModel(), NeighborhoodsScreenModel {
    private var actorId = initialActorId
    private val initialRoute = if (savedStateHandle.get<String>(PROFILE_ROUTE_ACTOR_KEY) == initialActorId) {
        savedStateHandle.get<ArrayList<String>>(PROFILE_ROUTE_KEY).orEmpty()
    } else {
        savedStateHandle[PROFILE_ROUTE_ACTOR_KEY] = initialActorId
        savedStateHandle.remove<ArrayList<String>>(PROFILE_ROUTE_KEY)
        emptyList()
    }
    private val delegate = NeighborhoodsViewModel(
        repository = repository,
        initialProfileRoute = initialRoute,
        onProfileRouteChanged = ::persistProfileRoute,
    )
    override val uiState: StateFlow<NeighborhoodsUiState> = delegate.uiState
    override fun startObservingCommunities() = delegate.startObservingCommunities()
    override fun stopObservingCommunities() = delegate.stopObservingCommunities()
    override fun openChat(neighborhood: String, onOpened: (String) -> Unit) = delegate.openChat(neighborhood, onOpened)
    override fun toggleFollowUser(userId: String) = delegate.toggleFollowUser(userId)
    override fun ensureFollowUserState(userId: String, desiredState: Boolean) =
        delegate.ensureFollowUserState(userId, desiredState)
    override fun retryFollowUser(userId: String) = delegate.retryFollowUser(userId)
    override fun openPrivateChat(userId: String, onOpened: (String) -> Unit) = delegate.openPrivateChat(userId, onOpened)
    override fun cancelPrivateChatOpen() = delegate.cancelPrivateChatOpen()
    override fun openUserProfile(userId: String) = delegate.openUserProfile(userId)
    fun retryFailedUserProfile() = delegate.retryFailedUserProfile()
    fun dismissUserProfileLoadFailure() = delegate.dismissUserProfileLoadFailure()
    fun closeUserProfile() = delegate.closeUserProfile()
    fun clearUserProfile() = delegate.clearUserProfile()
    fun profileRouteSnapshot(): List<String> = delegate.profileRouteSnapshot()
    fun bindActor(nextActorId: String?) {
        if (actorId == nextActorId) return
        delegate.clearUserProfile()
        actorId = nextActorId
        savedStateHandle[PROFILE_ROUTE_ACTOR_KEY] = nextActorId
        savedStateHandle.remove<ArrayList<String>>(PROFILE_ROUTE_KEY)
    }
    fun reportProfilePost(postId: String) = delegate.reportProfilePost(postId)
    fun ensureProfilePostReported(profileId: String, postId: String) =
        delegate.ensureProfilePostReported(profileId, postId)
    fun toggleProfilePostLike(postId: String) = delegate.toggleProfilePostLike(postId)
    fun ensureProfilePostLikeState(profileId: String, postId: String, desiredState: Boolean) =
        delegate.ensureProfilePostLikeState(profileId, postId, desiredState)
    fun addProfileComment(postId: String, comment: PostComment) = delegate.addProfileComment(postId, comment)
    fun reportProfile(userId: String) = delegate.reportProfile(userId)
    fun setProfileBlocked(userId: String, blocked: Boolean) = delegate.setProfileBlocked(userId, blocked)
    fun retryProfileSafety() = delegate.retryProfileSafety()
    fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) = delegate.setUserRoles(userId, isAdmin, isOfficial)

    override fun close() = delegate.close()

    override fun onCleared() = close()

    private fun persistProfileRoute(route: List<String>) {
        if (route.isEmpty()) {
            savedStateHandle.remove<ArrayList<String>>(PROFILE_ROUTE_KEY)
        } else {
            savedStateHandle[PROFILE_ROUTE_KEY] = ArrayList(route)
        }
        savedStateHandle[PROFILE_ROUTE_ACTOR_KEY] = actorId
    }

    companion object {
        private const val PROFILE_ROUTE_KEY = "quata.profile.route.ids"
        private const val PROFILE_ROUTE_ACTOR_KEY = "quata.profile.route.actor"

        fun factory(repository: NeighborhoodRepository, actorId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                NeighborhoodsAndroidViewModel(repository, extras.createSavedStateHandle(), actorId) as T
        }
    }
}
