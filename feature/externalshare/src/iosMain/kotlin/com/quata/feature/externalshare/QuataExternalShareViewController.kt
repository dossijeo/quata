package com.quata.feature.externalshare

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerStrings
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** iOS launcher input; payload parsing, file access, previewing and destination navigation stay host-owned. */
class IosExternalShareHostDependencies(
    val payload: ExternalSharePayload,
    val repository: ChatRepository,
    val viewModel: ShareToQuataViewModel,
    val documentOpener: DocumentOpenService,
    val onDismiss: () -> Unit,
    val onOpenConversation: (String) -> Unit = {},
    val clipboardService: ClipboardService = IosExternalShareClipboardService(),
)

/** UIKit/Compose host for the common external-share state and destination selection flow. */
fun QuataExternalShareViewController(dependencies: IosExternalShareHostDependencies): UIViewController =
    ComposeUIViewController {
        val attachmentScope = rememberCoroutineScope()
        QuataTheme {
            Surface(Modifier.fillMaxSize()) {
                ExternalShareDestinationHostContent(
                    payload = dependencies.payload,
                    repository = dependencies.repository,
                    clipboardService = dependencies.clipboardService,
                    strings = iosExternalShareDestinationStrings(),
                    onDismiss = dependencies.onDismiss,
                    onSent = { conversationId ->
                        conversationId?.let(dependencies.onOpenConversation)
                        dependencies.onDismiss()
                    },
                    panelHost = { content ->
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            content(Modifier.fillMaxSize().padding(16.dp), maxWidth > maxHeight)
                        }
                    },
                    candidateAvatar = { candidate, modifier ->
                        QuataAvatarFallback(candidate.displayName, candidate.profileId, modifier)
                    },
                    inviteAvatar = { contact, modifier ->
                        QuataAvatarFallback(contact.displayName, contact.id, modifier)
                    },
                    attachmentContent = { attachment, modifier, onOpen ->
                        ExternalShareAttachmentRowContent(attachment, "Abrir adjunto", modifier, onOpen)
                    },
                    onOpenAttachment = { attachment ->
                        attachmentScope.launch {
                            dependencies.documentOpener.open(
                                PlatformFile(
                                    reference = attachment.uri,
                                    displayName = attachment.name,
                                    mimeType = attachment.mimeType,
                                ),
                            )
                        }
                    },
                    viewModelFactory = { _, _ -> dependencies.viewModel },
                )
            }
        }
    }

private fun iosExternalShareDestinationStrings() = ExternalShareDestinationStrings(
    title = "Compartir en Quata",
    sending = "Enviando...",
    close = "Cerrar",
    payloadTextLabel = "Contenido compartido",
    attachmentsLabel = { count -> if (count == 1) "1 adjunto" else "$count adjuntos" },
    picker = ConversationCandidatePickerStrings(
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
        invitePermission = "Los contactos no estan disponibles en iOS.",
        inviteAllow = "Permitir",
        inviteAction = "Invitar",
        noneSelected = "Selecciona al menos un destinatario",
    ),
    sendContentDescription = "Enviar",
)

private class IosExternalShareClipboardService : ClipboardService {
    override suspend fun readText(): String? = null
    override suspend fun writeText(text: String) = Unit
}
