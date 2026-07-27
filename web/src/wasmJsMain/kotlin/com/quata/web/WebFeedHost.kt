package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.quata.core.capability.FeatureCapabilityTextCatalog
import com.quata.feature.feed.presentation.FeedBrowserHostContent
import com.quata.feature.feed.presentation.FeedBrowserHostStrings
import com.quata.feature.feed.presentation.FeedPostDetailHostContent

/** Browser adapter: route callbacks and repository construction remain in the Web launcher. */
@Composable
fun WebFeedHost(
    repository: WebFeedRepository,
    navigationMessage: String,
    onOpenChats: () -> Unit,
    sharedPostId: String? = null,
    onBackToFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sharedPostId) {
        setWebFeedDetailMarker(sharedPostId)
    }
    val strings = FeedBrowserHostStrings(
        loading = "Cargando publicaciones…",
        retry = "Reintentar",
        loadFailure = "No se pudo cargar el feed.",
        refresh = "Actualizar",
        refreshing = "Actualizando…",
        conversations = "Conversaciones",
        loadingOlder = "Cargando…",
        loadOlder = "Cargar anteriores",
        noText = "Publicación sin texto",
        readMore = "Leer más",
        close = "Cerrar",
        empty = "Aún no hay publicaciones disponibles.",
        mediaUnavailable = FeatureCapabilityTextCatalog
            .forLanguageTag(browserCapabilityLanguageTag())
            .mediaUnavailable(),
        backToFeed = "Volver a publicaciones",
        detailLoading = "Cargando publicación…",
        detailUnavailable = "Esta publicación ya no está disponible.",
    )
    if (sharedPostId == null) {
        FeedBrowserHostContent(
            repository = repository,
            navigationMessage = navigationMessage,
            strings = strings,
            onOpenChats = onOpenChats,
            mediaContent = { post -> BrowserFeedMediaContent(post, strings.mediaUnavailable) },
            modifier = modifier,
        )
    } else {
        FeedPostDetailHostContent(
            repository = repository,
            postId = sharedPostId,
            navigationMessage = navigationMessage,
            strings = strings,
            onBackToFeed = onBackToFeed,
            mediaContent = { post -> BrowserFeedMediaContent(post, strings.mediaUnavailable) },
            modifier = modifier,
        )
    }
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}
