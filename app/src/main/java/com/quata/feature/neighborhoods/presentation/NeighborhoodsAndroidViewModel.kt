package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared communities presentation logic. */
class NeighborhoodsAndroidViewModel(repository: NeighborhoodRepository) : ViewModel(), NeighborhoodsScreenModel {
    private val delegate = NeighborhoodsViewModel(repository)
    override val uiState: StateFlow<NeighborhoodsUiState> = delegate.uiState
    override fun startObservingCommunities() = delegate.startObservingCommunities()
    override fun stopObservingCommunities() = delegate.stopObservingCommunities()
    override fun openChat(neighborhood: String, onOpened: (String) -> Unit) = delegate.openChat(neighborhood, onOpened)
    override fun toggleFollowUser(userId: String) = delegate.toggleFollowUser(userId)
    override fun openPrivateChat(userId: String, onOpened: (String) -> Unit) = delegate.openPrivateChat(userId, onOpened)
    override fun openUserProfile(userId: String) = delegate.openUserProfile(userId)
    fun closeUserProfile() = delegate.closeUserProfile()
    fun reportProfilePost(postId: String) = delegate.reportProfilePost(postId)
    fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) = delegate.setUserRoles(userId, isAdmin, isOfficial)

    override fun close() = delegate.close()

    override fun onCleared() = close()

    companion object {
        fun factory(repository: NeighborhoodRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NeighborhoodsAndroidViewModel(repository) as T
        }
    }
}
