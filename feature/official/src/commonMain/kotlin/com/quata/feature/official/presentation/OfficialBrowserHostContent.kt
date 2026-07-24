package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository

/** Host-neutral read-only Official viewport; launch/navigation callbacks remain platform-owned. */
@Composable
fun OfficialBrowserHostContent(repository: OfficialRepository, officialPostId: String?, navigationMessage: String, modifier: Modifier = Modifier) {
    val viewModel = remember(repository) { OfficialFeedViewModel(repository) }; val state by viewModel.uiState.collectAsState(); var selectedPost by remember { mutableStateOf<OfficialPostItem?>(null) }
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(officialPostId) { officialPostId?.let { viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(it)) } }
    LaunchedEffect(officialPostId, state.posts) { if (officialPostId != null) selectedPost = state.posts.firstOrNull { it.id == officialPostId } }
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading && state.posts.isEmpty() -> OfficialLoadingContent(false, OfficialStatusStrings("Cargando comunicados…", ""), {}, Modifier.fillMaxSize())
            state.error != null && state.posts.isEmpty() -> OfficialBrowserFailure(state.error ?: "No se pudieron cargar los comunicados oficiales.", { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }, Modifier.fillMaxSize())
            state.posts.isEmpty() -> OfficialEmptyContent(false, OfficialStatusStrings("No hay comunicados oficiales disponibles.", ""), {}, Modifier.fillMaxSize())
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Surface(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(navigationMessage); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Button({ viewModel.onEvent(OfficialFeedUiEvent.Refresh) }, enabled = !state.isRefreshing) { Text(if (state.isRefreshing) "Actualizando…" else "Actualizar") } } } }
                items(state.posts, key = OfficialPostItem::id) { post -> OfficialBrowserPostCard(post) { selectedPost = post } }
                if (state.hasMoreOlderPosts) item { Button({ viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage) }, enabled = !state.isLoadingOlder, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(if (state.isLoadingOlder) "Cargando…" else "Cargar anteriores") } }
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
@Composable private fun OfficialBrowserPostCard(post: OfficialPostItem, onReadMore: () -> Unit) = OfficialPostPreviewFrameContent(Modifier.padding(horizontal = 14.dp)) { frame -> OfficialPostCardContent(post, post.type.remoteValue.uppercase(), post.readMoreLabel.ifBlank { "Leer más" }, false, author = { author -> OfficialAuthorHeaderContent(post.author.displayName, post.author.neighborhood, "Cuenta oficial", avatar = { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.size(42.dp).clip(MaterialTheme.shapes.extraLarge)) { Box(contentAlignment = Alignment.Center) { Text(post.author.displayName.firstOrNull()?.uppercase() ?: "O") } } }, modifier = author) }, media = null, actionRail = { _, _ -> }, onReadMore = onReadMore, modifier = frame) }
@Composable private fun OfficialBrowserFailure(message: String, onRetry: () -> Unit, modifier: Modifier) = Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.error); Button(onRetry) { Text("Reintentar") } } }
