package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NeighborhoodsViewModel(
    private val repository: NeighborhoodRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NeighborhoodsUiState())
    val uiState: StateFlow<NeighborhoodsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCommunities().collect { communities ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    communities = communities,
                    error = null
                )
            }
        }
    }

    fun openChat(neighborhood: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOpeningChat = true, error = null)
            repository.openNeighborhoodChat(neighborhood)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(isOpeningChat = false)
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        error = error.message ?: "No se pudo abrir el chat"
                    )
                }
        }
    }

    companion object {
        fun factory(repository: NeighborhoodRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NeighborhoodsViewModel(repository) as T
        }
    }
}
