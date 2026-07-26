package com.quata.feature.profile.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.profile.domain.ProfileViewerProfile
import com.quata.feature.profile.domain.ProfileViewerRepository
import com.quata.feature.profile.domain.ProfileViewerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileViewerUiState(
    val isLoading: Boolean = true,
    val profile: ProfileViewerProfile? = null,
    val unavailable: Boolean = false,
    val errorMessage: String? = null,
)

/** Lifecycle-owned read model for a selected member profile. */
class ProfileViewerViewModel(
    private val profileId: String,
    private val repository: ProfileViewerRepository,
    dispatchers: AppDispatchers = AppDispatchers(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(ProfileViewerUiState())
    val uiState: StateFlow<ProfileViewerUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repository.observeProfile(profileId).collect { result ->
                _uiState.update {
                    when (result) {
                        is ProfileViewerResult.Available -> ProfileViewerUiState(isLoading = false, profile = result.profile)
                        ProfileViewerResult.Unavailable -> ProfileViewerUiState(isLoading = false, unavailable = true)
                        is ProfileViewerResult.Failure -> ProfileViewerUiState(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun close() = scope.coroutineContext.cancel()
}
