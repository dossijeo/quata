package com.quata.feature.auth.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared registration state machine. */
class RegisterAndroidViewModel(repository: AuthRepository) : ViewModel() {
    private val delegate = RegisterViewModel(repository)
    val uiState: StateFlow<RegisterUiState> = delegate.uiState
    val effects: SharedFlow<RegisterEffect> = delegate.effects
    fun onEvent(event: RegisterUiEvent) = delegate.onEvent(event)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RegisterAndroidViewModel(repository) as T
        }
    }
}
