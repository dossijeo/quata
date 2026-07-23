package com.quata.feature.postcomposer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared post-composer presentation logic. */
class CreatePostAndroidViewModel(repository: PostComposerRepository) : ViewModel() {
    private val delegate = CreatePostViewModel(repository)
    val uiState: StateFlow<CreatePostUiState> = delegate.uiState
    fun onEvent(event: CreatePostUiEvent) = delegate.onEvent(event)
    fun submit(type: PostComposerType) = delegate.submit(type)
    fun cancelSubmit() = delegate.cancelSubmit()

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: PostComposerRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CreatePostAndroidViewModel(repository) as T
        }
    }
}
