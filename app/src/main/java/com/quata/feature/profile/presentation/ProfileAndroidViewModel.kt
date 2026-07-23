package com.quata.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.profile.domain.ProfileRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared profile presentation logic. */
class ProfileAndroidViewModel(repository: ProfileRepository) : ViewModel() {
    private val delegate = ProfileViewModel(repository)
    val uiState: StateFlow<ProfileUiState> = delegate.uiState
    fun onEvent(event: ProfileUiEvent) = delegate.onEvent(event)

    override fun onCleared() = delegate.close()

    class Factory(private val repository: ProfileRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileAndroidViewModel(repository) as T
    }
}
