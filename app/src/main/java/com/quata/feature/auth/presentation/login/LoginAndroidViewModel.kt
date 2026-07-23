package com.quata.feature.auth.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared login state machine. */
class LoginAndroidViewModel(repository: AuthRepository) : ViewModel() {
    private val delegate = LoginViewModel(repository)
    val uiState: StateFlow<LoginUiState> = delegate.uiState
    val effects: SharedFlow<LoginEffect> = delegate.effects
    fun onEvent(event: LoginUiEvent) = delegate.onEvent(event)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginAndroidViewModel(repository) as T
        }
    }
}
