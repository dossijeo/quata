package com.quata.feature.auth.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared password-recovery state machine. */
class ForgotPasswordAndroidViewModel(repository: AuthRepository) : ViewModel() {
    private val delegate = ForgotPasswordViewModel(repository)
    val uiState: StateFlow<ForgotPasswordUiState> = delegate.uiState
    val effects: SharedFlow<ForgotPasswordEffect> = delegate.effects
    fun onEvent(event: ForgotPasswordUiEvent) = delegate.onEvent(event)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ForgotPasswordAndroidViewModel(repository) as T
        }
    }
}
