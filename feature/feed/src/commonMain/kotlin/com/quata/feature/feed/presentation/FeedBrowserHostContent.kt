package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.feature.feed.domain.FeedRepository

data class FeedBrowserHostStrings(
    val loading: String,
    val retry: String,
    val loadFailure: String,
    val refresh: String,
    val refreshing: String,
    val conversations: String,
    val loadingOlder: String,
    val loadOlder: String,
    val noText: String,
    val readMore: String,
    val close: String,
    val empty: String,
    val mediaUnavailable: String,
    val backToFeed: String = "Back to feed",
    val detailLoading: String = "Loading post…",
    val detailUnavailable: String = "This post is no longer available.",
)

/**
 * Read-only browser-style Feed surface shared outside platform launchers.
 *
 * Repository construction and route changes remain platform concerns. Hosts inject media into a
 * slot, while the common fallback remains explicit for platforms without a compatible renderer.
 */
@Composable
fun FeedBrowserHostContent(
    repository: FeedRepository,
    navigationMessage: String,
    strings: FeedBrowserHostStrings,
    onOpenChats: () -> Unit,
    mediaContent: @Composable (Post) -> Unit = { post -> FeedBrowserMediaUnavailableContent(post, strings) },
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { FeedViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::close) }

    when {
        state.isLoading && state.posts.isEmpty() -> FeedStatusContent(
            message = strings.loading,
            retryLabel = strings.retry,
            onRetry = { viewModel.onEvent(FeedUiEvent.Refresh) },
            modifier = modifier,
        )
        state.error != null && state.posts.isEmpty() -> FeedStatusContent(
            message = state.error ?: strings.loadFailure,
            retryLabel = strings.retry,
            onRetry = { viewModel.onEvent(FeedUiEvent.Refresh) },
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "browser-feed-status") {
                FeedBrowserStatusContent(
                    message = navigationMessage,
                    error = state.error,
                    isRefreshing = state.isRefreshing,
                    strings = strings,
                    onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) },
                    onOpenChats = onOpenChats,
                )
            }
            items(state.posts, key = Post::id) { post -> FeedBrowserPostContent(post, strings, mediaContent) }
            if (state.posts.isEmpty()) {
                item(key = "browser-feed-empty") { FeedBrowserEmptyContent(strings.empty) }
            }
            if (state.hasMoreOlderPosts && state.posts.isNotEmpty()) {
                item(key = "browser-feed-older") {
                    Button(
                        enabled = !state.isLoadingOlder,
                        onClick = { viewModel.onEvent(FeedUiEvent.LoadOlderPage) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(if (state.isLoadingOlder) strings.loadingOlder else strings.loadOlder) }
                }
            }
            item(key = "browser-feed-bottom-space") { Spacer(Modifier.height(84.dp)) }
        }
    }
}

@Composable
private fun FeedBrowserStatusContent(
    message: String,
    error: String?,
    isRefreshing: Boolean,
    strings: FeedBrowserHostStrings,
    onRefresh: () -> Unit,
    onOpenChats: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !isRefreshing, onClick = onRefresh) {
                Text(if (isRefreshing) strings.refreshing else strings.refresh)
            }
            Button(onClick = onOpenChats) { Text(strings.conversations) }
        }
    }
}

@Composable
fun FeedBrowserPostContent(
    post: Post,
    strings: FeedBrowserHostStrings,
    mediaContent: @Composable (Post) -> Unit = { item -> FeedBrowserMediaUnavailableContent(item, strings) },
    author: @Composable (Post, Modifier) -> Unit = { item, modifier ->
        FeedPostMetadataContent(
            displayName = item.author.displayName,
            publishedAt = item.createdAt,
            modifier = modifier,
        )
    },
    navigation: @Composable BoxScope.(Post) -> Unit = {},
    actionRail: @Composable BoxScope.(Post) -> Unit = {},
) {
    val hasPlatformMedia = post.imageUrl != null || post.videoUrl != null
    FeedPostPreviewCardContent(
        author = { modifier -> author(post, modifier) },
        media = {
            if (hasPlatformMedia) {
                mediaContent(post)
            } else {
                TextOnlyReelContent(
                    stableId = post.id,
                    displayText = post.text.ifBlank { strings.noText },
                    seedText = post.id,
                    patternId = null,
                    readMoreText = strings.readMore,
                    readerDismissButton = { buttonModifier, onDismiss ->
                        Button(onClick = onDismiss, modifier = buttonModifier) { Text(strings.close) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        actionRail = { actionRail(post) },
        navigation = { navigation(post) },
        body = {
            if (hasPlatformMedia && post.text.isNotBlank()) {
                Text(post.text, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
internal fun FeedBrowserMediaUnavailableContent(post: Post, strings: FeedBrowserHostStrings) {
    if (post.imageUrl != null || post.videoUrl != null) {
        FeedMediaUnavailablePlaceholderContent(
            message = strings.mediaUnavailable,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun FeedMediaUnavailablePlaceholderContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = feedMediaUnavailableTextStyle(MaterialTheme.typography.bodySmall),
        modifier = modifier,
    )
}

internal fun feedMediaUnavailableTextStyle(base: TextStyle): TextStyle =
    base.copy(color = FeedMediaUnavailableContentColor)

@Composable
private fun FeedBrowserEmptyContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(message) }
}
