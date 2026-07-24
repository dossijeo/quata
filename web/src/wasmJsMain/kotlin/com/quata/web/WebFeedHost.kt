package com.quata.web

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.feature.feed.presentation.FeedStatusContent
import com.quata.feature.feed.presentation.FeedUiEvent
import com.quata.feature.feed.presentation.FeedViewModel
import com.quata.feature.feed.presentation.TextOnlyReelContent

/**
 * Minimal browser Feed host. Posts use the shared text reel while image/video rendering and all
 * mutation controls remain platform work; the source is polling, never a Realtime claim.
 */
@Composable
fun WebFeedHost(
    repository: WebFeedRepository,
    navigationMessage: String,
    onOpenChats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { FeedViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }

    when {
        state.isLoading && state.posts.isEmpty() -> FeedStatusContent(
            message = "Cargando publicaciones…",
            retryLabel = "Reintentar",
            onRetry = { viewModel.onEvent(FeedUiEvent.Refresh) },
            modifier = modifier,
        )

        state.error != null && state.posts.isEmpty() -> FeedStatusContent(
            message = state.error ?: "No se pudo cargar el feed.",
            retryLabel = "Reintentar",
            onRetry = { viewModel.onEvent(FeedUiEvent.Refresh) },
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "web-feed-status") {
                WebFeedStatusBanner(
                    message = navigationMessage,
                    error = state.error,
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) },
                    onOpenChats = onOpenChats,
                )
            }
            items(state.posts, key = Post::id) { post ->
                WebFeedPost(post)
            }
            if (state.posts.isEmpty()) {
                item(key = "web-feed-empty") {
                    WebFeedEmptyState()
                }
            }
            if (state.hasMoreOlderPosts && state.posts.isNotEmpty()) {
                item(key = "web-feed-older") {
                    Button(
                        enabled = !state.isLoadingOlder,
                        onClick = { viewModel.onEvent(FeedUiEvent.LoadOlderPage) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(if (state.isLoadingOlder) "Cargando…" else "Cargar anteriores")
                    }
                }
            }
            item(key = "web-feed-bottom-space") { Spacer(Modifier.height(84.dp)) }
        }
    }
}

@Composable
private fun WebFeedStatusBanner(
    message: String,
    error: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChats: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !isRefreshing, onClick = onRefresh) {
                Text(if (isRefreshing) "Actualizando…" else "Actualizar")
            }
            Button(onClick = onOpenChats) { Text("Conversaciones") }
        }
    }
}

@Composable
private fun WebFeedPost(post: Post) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = post.author.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = post.createdAt,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        TextOnlyReelContent(
            stableId = post.id,
            displayText = post.text.ifBlank { "Publicación sin texto" },
            seedText = post.id,
            patternId = null,
            readMoreText = "Leer más",
            readerDismissButton = { buttonModifier, onDismiss ->
                Button(onClick = onDismiss, modifier = buttonModifier) { Text("Cerrar") }
            },
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )
        if (post.imageUrl != null || post.videoUrl != null) {
            Text(
                text = "El contenido multimedia se mostrará próximamente en Quata Web.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun WebFeedEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aún no hay publicaciones disponibles.")
    }
}
