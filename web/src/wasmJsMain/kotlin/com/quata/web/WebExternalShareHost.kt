@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataFloatingPanelContent
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerStrings
import com.quata.feature.externalshare.ExternalShareAttachmentRowContent
import com.quata.feature.externalshare.ExternalShareDestinationHostContent
import com.quata.feature.externalshare.ExternalShareDestinationStrings
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
    val viewModel = remember(payload.id, repository) {
        ShareToQuataViewModel(
            repository = repository,
            payload = payload,
            text = { key -> if (key == ShareText.LoadCandidates) "No se pudieron cargar los destinatarios." else "No se pudo enviar." },
        )
    }
    val state by viewModel.uiState.collectAsState()
    val displayedCandidateIds = (state.recentCandidates.takeIf { state.candidateQuery.isBlank() }.orEmpty() + state.candidates)
        .map { it.profileId }
        .toSet()
    DisposableEffect(viewModel, displayedCandidateIds, state.selectedProfileIds, state.isSending) {
        val hasTarget = { target: String ->
            when {
                target.startsWith("external-share.candidate.action.") ->
                    target.removePrefix("external-share.candidate.action.") in displayedCandidateIds
                target == "external-share.confirm" -> state.selectedProfileIds.isNotEmpty() && !state.isSending
                else -> false
            }
        }
        val uninstall = installWebExternalShareE2eBridge(
            hasSemanticTarget = hasTarget,
            semanticClick = { target ->
                when {
                    target.startsWith("external-share.candidate.action.") && hasTarget(target) -> {
                        viewModel.toggle(target.removePrefix("external-share.candidate.action."))
                        true
                    }
                    target == "external-share.confirm" && hasTarget(target) -> {
                        viewModel.send()
                        true
                    }
                    else -> false
                }
            },
        )
        onDispose(uninstall)
    }
    ExternalShareDestinationHostContent(
        payload = payload,
        repository = repository,
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
        ).let { picker ->
            ExternalShareDestinationStrings(
                title = "Compartir en Quata",
                sending = "Enviando...",
                close = "Cerrar",
                payloadTextLabel = "Contenido compartido",
                attachmentsLabel = { count -> if (count == 1) "1 adjunto" else "$count adjuntos" },
                picker = picker,
                sendContentDescription = "Enviar",
            )
        },
        onDismiss = {
            // A user cancellation is explicit: remove the persisted payload and revoke its Blob URLs.
            scope.launch {
                store.discard(payload)
                onDismiss()
            }
        },
        onSent = { conversationId ->
            scope.launch {
                store.discard(payload).onSuccess { onFinished(conversationId) }
            }
        },
        panelHost = { dismissEnabled, content ->
            QuataFloatingPanelContent(onDismiss = {
                scope.launch {
                    store.discard(payload)
                    onDismiss()
                }
            }, modifier = modifier, dismissEnabled = dismissEnabled) { panelModifier, isLandscape ->
                content(panelModifier, isLandscape)
            }
        },
        candidateAvatar = { candidate, avatarModifier ->
            QuataAvatarFallback(candidate.displayName, candidate.profileId, avatarModifier)
        },
        inviteAvatar = { contact, avatarModifier ->
            QuataAvatarFallback(contact.displayName, contact.id, avatarModifier)
        },
        attachmentContent = { attachment, attachmentModifier, onOpen ->
            ExternalShareAttachmentRowContent(attachment, "Abrir adjunto", attachmentModifier, onOpen)
        },
        onOpenAttachment = { attachment -> browserOpenIncomingShareAttachment(attachment.uri) },
        viewModelFactory = { _, _ -> viewModel },
        modifier = modifier,
    )
}

private fun installWebExternalShareE2eBridge(
    hasSemanticTarget: (String) -> Boolean,
    semanticClick: (String) -> Boolean,
): () -> Unit = installExternalShareBridgeWhenAllowed(hasSemanticTarget, semanticClick)

@JsFun(
    """(hasSemanticTarget, semanticClick) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-external-share-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.external_share.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        hasSemanticTarget: (target) => hasSemanticTarget(String(target ?? '')) === true,
        semanticClick: (target) => semanticClick(String(target ?? '')) === true,
      });
      globalThis.__quataExternalShareE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-external-share-e2e', 'ready');
      return () => {
        if (globalThis.__quataExternalShareE2eProduct === bridge) delete globalThis.__quataExternalShareE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-external-share-e2e');
      };
    }""",
)
private external fun installExternalShareBridgeWhenAllowed(
    hasSemanticTarget: (String) -> Boolean,
    semanticClick: (String) -> Boolean,
): () -> Unit

private fun browserOpenIncomingShareAttachment(reference: String): Unit = js(
    """
    (() => {
      if (typeof reference === 'string' && reference.length > 0) {
        window.open(reference, '_blank', 'noopener,noreferrer');
      }
    })()
    """,
)
