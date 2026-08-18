package com.quata.feature.postcomposer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared post-composer presentation logic. */
class CreatePostAndroidViewModel(
    repository: PostComposerRepository,
    copy: CreatePostRootCopy,
    initialEvidenceImageUri: String? = null,
    initialEvidenceLocationLabel: String? = null,
) : ViewModel() {
    private val delegate = CreatePostViewModel(repository, messages = copy.viewModelMessages())
    internal val commonViewModel: CreatePostViewModel get() = delegate
    val uiState: StateFlow<CreatePostUiState> = delegate.uiState
    fun onEvent(event: CreatePostUiEvent) = delegate.onEvent(event)
    fun submit(type: PostComposerType) = delegate.submit(type)
    fun cancelSubmit() = delegate.cancelSubmit()

    init {
        if (initialEvidenceLocationLabel != null) {
            delegate.onEvent(CreatePostUiEvent.LocationLabelChanged(initialEvidenceLocationLabel))
        }
        if (initialEvidenceImageUri != null) {
            delegate.onEvent(CreatePostUiEvent.ImageSelected(initialEvidenceImageUri))
        }
    }

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(
            repository: PostComposerRepository,
            copy: CreatePostRootCopy,
            initialEvidenceImageUri: String? = null,
            initialEvidenceLocationLabel: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CreatePostAndroidViewModel(
                repository,
                copy,
                initialEvidenceImageUri,
                initialEvidenceLocationLabel,
            ) as T
        }
    }
}
