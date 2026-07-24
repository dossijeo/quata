package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedPostDetailUiState(
    val isLoading: Boolean = true,
    val post: Post? = null,
    val error: String? = null,
)

/**
 * Presentation state for a shared-post route. It loads the precise post directly instead of
 * depending on it having appeared in the current paged feed snapshot.
 */
class FeedPostDetailViewModel(
    private val postId: String,
    private val repository: FeedRepository,
    dispatchers: AppDispatchers = AppDispatchers(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(FeedPostDetailUiState())
    val uiState: StateFlow<FeedPostDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (postId.isBlank()) {
            _uiState.value = FeedPostDetailUiState(isLoading = false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        scope.launch {
            repository.refreshPost(postId)
                .onSuccess { post ->
                    _uiState.value = FeedPostDetailUiState(isLoading = false, post = post)
                }
                .onFailure { error ->
                    _uiState.value = FeedPostDetailUiState(
                        isLoading = false,
                        error = error.message,
                    )
                }
        }
    }

    fun close() {
        scope.cancel()
    }
}

/**
 * Platform-neutral detail surface for a post opened from a shared deep link. Media rendering
 * remains the existing text/reel fallback; browser routing and repository construction stay out
 * of this component.
 */
@Composable
fun FeedPostDetailHostContent(
    repository: FeedRepository,
    postId: String,
    navigationMessage: String,
    strings: FeedBrowserHostStrings,
    onBackToFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository, postId) { FeedPostDetailViewModel(postId, repository) }
    val state by viewModel.uiState.collectAsState()
    val post = state.post
    DisposableEffect(viewModel) { onDispose(viewModel::close) }

    Column(modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onBackToFeed) { Text(strings.backToFeed) }
                Text(navigationMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when {
            state.isLoading -> FeedStatusContent(
                message = strings.detailLoading,
                retryLabel = strings.retry,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )
            post != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = post.id) { FeedBrowserPostContent(post, strings) }
            }
            else -> FeedStatusContent(
                message = state.error ?: strings.detailUnavailable,
                retryLabel = strings.retry,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
