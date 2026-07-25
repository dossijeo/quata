package com.quata.feature.official.presentation

import androidx.compose.foundation.clickable
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

/**
 * Platform-owned parts of the compact Official browser. The feed state and detail panel remain
 * shared, while hosts inject media/avatar rendering and navigation without platform imports.
 */
class OfficialBrowserHostSlots(
    val avatar: @Composable (OfficialPostItem, Modifier) -> Unit = { post, modifier ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = modifier.size(42.dp).clip(MaterialTheme.shapes.extraLarge),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(post.author.displayName.firstOrNull()?.uppercase() ?: "O")
            }
        }
    },
    val media: (@Composable (OfficialPostItem, Modifier) -> Unit)? = null,
    val actionRail: @Composable (OfficialPostItem, Boolean, Modifier) -> Unit = { _, _, _ -> },
    val article: @Composable (OfficialPostItem, Modifier) -> Unit = { post, modifier ->
        Text(post.contentPlain.ifBlank { post.summary }, modifier = modifier)
    },
    /** Optional platform-owned resource affordance rendered in the shared detail panel. */
    val resource: (@Composable (OfficialPostItem, Modifier) -> Unit)? = null,
    /** Optional platform-owned navigation affordance rendered in the shared detail panel. */
    val navigation: (@Composable (OfficialPostItem, Modifier) -> Unit)? = null,
    val onOpenAuthor: (String) -> Unit = {},
    val onOpenMedia: (String) -> Unit = {},
    val onOpenPost: (String) -> Unit = {},
)

/** Host-neutral read-only Official viewport; launch/navigation callbacks remain platform-owned. */
@Composable
fun OfficialBrowserHostContent(
    repository: OfficialRepository,
    officialPostId: String?,
    navigationMessage: String,
    modifier: Modifier = Modifier,
    slots: OfficialBrowserHostSlots = OfficialBrowserHostSlots(),
) {
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
                items(state.posts, key = OfficialPostItem::id) { post ->
                    OfficialBrowserPostCard(post, slots) {
                        slots.onOpenPost(post.id)
                        selectedPost = post
                    }
                }
                if (state.hasMoreOlderPosts) item { Button({ viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage) }, enabled = !state.isLoadingOlder, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(if (state.isLoadingOlder) "Cargando…" else "Cargar anteriores") } }
            }
        }
        selectedPost?.let { post ->
            OfficialPostDetailPanelContent(
                title = post.title,
                closeLabel = "Cerrar",
                link = post.linkUrl,
                onDismiss = { selectedPost = null },
                articleContent = { articleModifier -> slots.article(post, articleModifier) },
                author = { authorModifier ->
                    OfficialBrowserAuthorHeader(post, slots, authorModifier)
                },
                media = slots.media?.let { renderMedia ->
                    { mediaModifier ->
                        Box(mediaModifier.clickable { slots.onOpenMedia(post.id) }) {
                            renderMedia(post, Modifier.fillMaxSize())
                        }
                    }
                },
                resourceContent = slots.resource?.let { resource ->
                    { resourceModifier -> resource(post, resourceModifier) }
                },
                navigationContent = slots.navigation?.let { navigation ->
                    { navigationModifier -> navigation(post, navigationModifier) }
                },
            )
        }
    }
}
@Composable
private fun OfficialBrowserPostCard(
    post: OfficialPostItem,
    slots: OfficialBrowserHostSlots,
    onReadMore: () -> Unit,
) = OfficialPostPreviewFrameContent(Modifier.padding(horizontal = 14.dp)) { frame ->
    OfficialPostCardContent(
        post = post,
        typeLabel = post.type.remoteValue.uppercase(),
        readMoreLabel = post.readMoreLabel.ifBlank { "Leer más" },
        isLandscape = false,
        author = { authorModifier ->
            OfficialBrowserAuthorHeader(post, slots, authorModifier)
        },
        media = slots.media?.let { renderMedia ->
            { mediaModifier ->
                Box(mediaModifier.clickable { slots.onOpenMedia(post.id) }) {
                    renderMedia(post, Modifier.fillMaxSize())
                }
            }
        },
        actionRail = { isLandscape, actionModifier -> slots.actionRail(post, isLandscape, actionModifier) },
        onReadMore = onReadMore,
        modifier = frame,
    )
}

@Composable
private fun OfficialBrowserAuthorHeader(
    post: OfficialPostItem,
    slots: OfficialBrowserHostSlots,
    modifier: Modifier,
) {
    OfficialAuthorHeaderContent(
        displayName = post.author.displayName,
        neighborhood = post.author.neighborhood,
        fallbackNeighborhood = "Cuenta oficial",
        avatar = { slots.avatar(post, Modifier) },
        modifier = modifier.clickable { slots.onOpenAuthor(post.author.id) },
    )
}
@Composable private fun OfficialBrowserFailure(message: String, onRetry: () -> Unit, modifier: Modifier) = Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.error); Button(onRetry) { Text("Reintentar") } } }
