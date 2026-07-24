package com.quata.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.presentation.OfficialAuthorHeaderContent
import com.quata.feature.official.presentation.OfficialEmptyContent
import com.quata.feature.official.presentation.OfficialFeedUiEvent
import com.quata.feature.official.presentation.OfficialFeedViewModel
import com.quata.feature.official.presentation.OfficialLoadingContent
import com.quata.feature.official.presentation.OfficialPostCardContent
import com.quata.feature.official.presentation.OfficialPostDetailPanelContent
import com.quata.feature.official.presentation.OfficialPostPreviewFrameContent
import com.quata.feature.official.presentation.OfficialStatusStrings

/** Read-only Web host for the shared Official feed presentation. */
@Composable
fun WebOfficialHost(
    repository: WebOfficialRepository,
    officialPostId: String?,
    navigationMessage: String,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { OfficialFeedViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    var selectedPost by remember { mutableStateOf<OfficialPostItem?>(null) }
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(officialPostId) {
        officialPostId?.let { viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(it)) }
    }
    LaunchedEffect(officialPostId, state.posts) {
        if (officialPostId != null) selectedPost = state.posts.firstOrNull { it.id == officialPostId }
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading && state.posts.isEmpty() -> OfficialLoadingContent(
                canPublish = false,
                strings = OfficialStatusStrings(empty = "Cargando comunicados…", create = ""),
                onCreate = {},
                modifier = Modifier.fillMaxSize(),
            )
            state.error != null && state.posts.isEmpty() -> WebOfficialFailure(
                message = state.error ?: "No se pudieron cargar los comunicados oficiales.",
                onRetry = { viewModel.onEvent(OfficialFeedUiEvent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            )
            state.posts.isEmpty() -> OfficialEmptyContent(
                canPublish = false,
                strings = OfficialStatusStrings(empty = "No hay comunicados oficiales disponibles.", create = ""),
                onCreate = {},
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "web-official-status") {
                    WebOfficialStatus(navigationMessage, state.error, state.isRefreshing) {
                        viewModel.onEvent(OfficialFeedUiEvent.Refresh)
                    }
                }
                items(state.posts, key = OfficialPostItem::id) { post ->
                    WebOfficialPostCard(post = post, onReadMore = { selectedPost = post })
                }
                if (state.hasMoreOlderPosts) {
                    item(key = "web-official-older") {
                        Button(
                            enabled = !state.isLoadingOlder,
                            onClick = { viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) { Text(if (state.isLoadingOlder) "Cargando…" else "Cargar anteriores") }
                    }
                }
            }
        }
        selectedPost?.let { post ->
            OfficialPostDetailPanelContent(
                title = post.title,
                closeLabel = "Cerrar",
                link = post.linkUrl,
                onDismiss = { selectedPost = null },
                articleContent = { articleModifier ->
                    Text(post.contentPlain.ifBlank { post.summary }, modifier = articleModifier)
                },
            )
        }
    }
}

@Composable
private fun WebOfficialPostCard(post: OfficialPostItem, onReadMore: () -> Unit) {
    OfficialPostPreviewFrameContent(modifier = Modifier.padding(horizontal = 14.dp)) { frameModifier ->
        OfficialPostCardContent(
            post = post,
            typeLabel = post.type.remoteValue.uppercase(),
            readMoreLabel = post.readMoreLabel.ifBlank { "Leer más" },
            isLandscape = false,
            author = { authorModifier ->
                OfficialAuthorHeaderContent(
                    displayName = post.author.displayName,
                    neighborhood = post.author.neighborhood,
                    fallbackNeighborhood = "Cuenta oficial",
                    avatar = {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.size(42.dp).clip(MaterialTheme.shapes.extraLarge),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(post.author.displayName.firstOrNull()?.uppercase() ?: "O")
                            }
                        }
                    },
                    modifier = authorModifier,
                )
            },
            media = null,
            actionRail = { _, _ -> },
            onReadMore = onReadMore,
            modifier = frameModifier,
        )
    }
}

@Composable
private fun WebOfficialStatus(message: String, error: String?, refreshing: Boolean, onRefresh: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !refreshing, onClick = onRefresh) {
                Text(if (refreshing) "Actualizando…" else "Actualizar")
            }
        }
    }
}

@Composable
private fun WebOfficialFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}
