package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        mediaUnavailable = "El contenido multimedia aún no está disponible en Quata Web.",
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
            modifier = modifier,
        )
    } else {
        FeedPostDetailHostContent(
            repository = repository,
            postId = sharedPostId,
            navigationMessage = navigationMessage,
            strings = strings,
            onBackToFeed = onBackToFeed,
            modifier = modifier,
        )
    }
}
