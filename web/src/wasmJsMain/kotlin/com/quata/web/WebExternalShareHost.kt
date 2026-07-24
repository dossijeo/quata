package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataFloatingPanelContent
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerDialogContent
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerStrings
import com.quata.feature.chat.presentation.conversations.ConversationsUiState
import com.quata.feature.externalshare.ExternalSharePayload
import com.quata.feature.externalshare.ShareText
import com.quata.feature.externalshare.ShareToQuataViewModel
import kotlinx.coroutines.launch

/** Browser host for persisted Web Share Target payloads. The picker and sending state are shared. */
@Composable
fun WebExternalShareHost(
    repository: WebChatRepository,
    clipboardService: ClipboardService,
    store: WebIncomingShareStore,
    onFinished: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var payload by remember { mutableStateOf<ExternalSharePayload?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(store) {
        store.readOldest()
            .onSuccess { payload = it }
            .onFailure { loadError = "No se pudo leer el contenido compartido." }
        isLoading = false
    }
    val currentPayload = payload
    when {
        currentPayload != null -> WebExternalSharePicker(
            payload = currentPayload,
            repository = repository,
            clipboardService = clipboardService,
            store = store,
            onFinished = onFinished,
            onDismiss = onDismiss,
            modifier = modifier,
        )
        loadError != null -> Text(loadError.orEmpty(), modifier = modifier)
        isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> WebShareTargetErrorHost(onDismiss = onDismiss, modifier = modifier)
    }
}

@Composable
private fun WebExternalSharePicker(
    payload: ExternalSharePayload,
    repository: WebChatRepository,
    clipboardService: ClipboardService,
    store: WebIncomingShareStore,
    onFinished: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(payload.id) {
        ShareToQuataViewModel(
            repository = repository,
            payload = payload,
            text = { key -> if (key == ShareText.LoadCandidates) "No se pudieron cargar los destinatarios." else "No se pudo enviar." },
        )
    }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            store.discard(payload).onSuccess { onFinished(state.completedConversationId) }
        }
    }
    ConversationCandidatePickerDialogContent(
        state = ConversationsUiState(
            currentUser = state.currentUser,
            candidateQuery = state.candidateQuery,
            conversationCandidates = (state.recentCandidates.takeIf { state.candidateQuery.isBlank() }.orEmpty() + state.candidates)
                .distinctBy { it.profileId },
            isCandidateInitialLoading = state.isInitialLoading,
            isCandidatePageLoading = state.isPageLoading,
            candidateHasMore = state.hasMore,
            candidateNextOffset = state.nextOffset,
            candidateActorNeighborhood = state.actorNeighborhood,
            candidateError = state.error,
        ),
        clipboardService = clipboardService,
        strings = ConversationCandidatePickerStrings(
            searchPlaceholder = "Buscar personas",
            noResults = "No hay destinatarios disponibles.",
            cancel = "Cancelar",
            contacts = "Contactos",
            following = "Siguiendo",
            followers = "Seguidores",
            recent = "Conversaciones recientes",
            otherNeighborhoods = "Otros barrios",
            unknownNeighborhood = "Sin barrio",
            inviteTitle = "Invitar a Quata",
            invitePermission = "Los contactos no están disponibles en la web.",
            inviteAllow = "Permitir",
            inviteAction = "Invitar",
            noneSelected = "Selecciona al menos un destinatario",
        ),
        onSearchChange = viewModel::onQueryChanged,
        onLoadMore = viewModel::loadMore,
        onOpenCandidate = { viewModel.toggle(it.profileId) },
        onDismiss = {
            // A user cancellation is explicit: remove the persisted payload and revoke its Blob URLs.
            scope.launch {
                store.discard(payload)
                onDismiss()
            }
        },
        panelHost = { content ->
            QuataFloatingPanelContent(onDismiss = {
                scope.launch {
                    store.discard(payload)
                    onDismiss()
                }
            }, modifier = modifier) { panelModifier, isLandscape ->
                content(panelModifier, isLandscape)
            }
        },
        candidateAvatar = { candidate, avatarModifier ->
            QuataAvatarFallback(candidate.displayName, candidate.profileId, avatarModifier)
        },
        inviteAvatar = { contact, avatarModifier ->
            QuataAvatarFallback(contact.displayName, contact.id, avatarModifier)
        },
        title = "Compartir en Quata",
        actionIcon = Icons.Filled.ChatBubble,
        actionContentDescription = "Abrir conversación",
        selectedCandidateIds = state.selectedProfileIds,
        onToggleCandidate = { viewModel.toggle(it.profileId) },
        onConfirmSelection = viewModel::send,
        confirmEnabled = state.selectedProfileIds.isNotEmpty() && !state.isSending,
        selectionSummary = if (state.isSending) "Enviando…" else "Enviar a los destinatarios seleccionados",
        confirmIcon = Icons.AutoMirrored.Filled.Send,
        confirmContentDescription = "Enviar",
    )
}
