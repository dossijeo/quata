package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared communities presentation logic. */
class NeighborhoodsAndroidViewModel(repository: NeighborhoodRepository) : ViewModel() {
    private val delegate = NeighborhoodsViewModel(repository)
    val uiState: StateFlow<NeighborhoodsUiState> = delegate.uiState
    fun startObservingCommunities() = delegate.startObservingCommunities()
    fun stopObservingCommunities() = delegate.stopObservingCommunities()
    fun openChat(neighborhood: String, onOpened: (String) -> Unit) = delegate.openChat(neighborhood, onOpened)
    fun toggleFollowUser(userId: String) = delegate.toggleFollowUser(userId)
    fun openPrivateChat(userId: String, onOpened: (String) -> Unit) = delegate.openPrivateChat(userId, onOpened)
    fun openUserProfile(userId: String) = delegate.openUserProfile(userId)
    fun closeUserProfile() = delegate.closeUserProfile()
    fun reportProfilePost(postId: String) = delegate.reportProfilePost(postId)
    fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) = delegate.setUserRoles(userId, isAdmin, isOfficial)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: NeighborhoodRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NeighborhoodsAndroidViewModel(repository) as T
        }
    }
}
