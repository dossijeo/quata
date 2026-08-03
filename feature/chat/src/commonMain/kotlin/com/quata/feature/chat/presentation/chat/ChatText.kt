package com.quata.feature.chat.presentation.chat

import kotlin.random.Random

/** UI copy requested by shared chat presentation logic. Each platform resolves it locally. */
enum class ChatText {
    LoadConversations,
    LoadMessages,
    Send,
    You,
    Update,
    AddParticipant,
    AddParticipants,
    LoadCandidates,
    DeleteConversation,
    LeaveConversation,
    PromoteParticipant,
    DemoteParticipant,
    RemoveParticipant,
    BlockParticipant,
    UpdateFavorite,
    DeleteMessage,
    ReportSent,
    ReportMessage,
    Forward,
    RestoreConversation,
    OpenConversation
}

/** Platform-neutral chat error copy used by the Web and iOS Compose hosts. */
fun chatTextForLanguage(value: ChatText, languageTag: String?): String {
    val language = languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()
    return when (language) {
        "es" -> when (value) {
            ChatText.LoadConversations -> "No se pudieron cargar los chats."
            ChatText.LoadMessages -> "No se pudieron cargar los mensajes."
            ChatText.Send -> "No se pudo enviar el mensaje."
            ChatText.You -> "Tú"
            ChatText.Update -> "No se pudo actualizar el chat."
            ChatText.AddParticipant -> "No se pudo añadir al participante."
            ChatText.AddParticipants -> "No se pudieron añadir los participantes."
            ChatText.LoadCandidates -> "No se pudieron cargar los contactos."
            ChatText.DeleteConversation -> "No se pudo eliminar el chat."
            ChatText.LeaveConversation -> "No se pudo abandonar el chat."
            ChatText.PromoteParticipant -> "No se pudo nombrar administrador."
            ChatText.DemoteParticipant -> "No se pudo retirar el rol de administrador."
            ChatText.RemoveParticipant -> "No se pudo eliminar al participante."
            ChatText.BlockParticipant -> "No se pudo bloquear al participante."
            ChatText.UpdateFavorite -> "No se pudo actualizar el favorito."
            ChatText.DeleteMessage -> "No se pudo eliminar el mensaje."
            ChatText.ReportSent -> "Reporte enviado."
            ChatText.ReportMessage -> "No se pudo reportar el mensaje."
            ChatText.Forward -> "No se pudo reenviar el mensaje."
            ChatText.RestoreConversation -> "No se pudo restaurar el chat."
            ChatText.OpenConversation -> "No se pudo abrir el chat."
        }
        "fr" -> when (value) {
            ChatText.LoadConversations -> "Impossible de charger les discussions."
            ChatText.LoadMessages -> "Impossible de charger les messages."
            ChatText.Send -> "Impossible d’envoyer le message."
            ChatText.You -> "Vous"
            ChatText.Update -> "Impossible de mettre à jour la discussion."
            ChatText.AddParticipant -> "Impossible d’ajouter le participant."
            ChatText.AddParticipants -> "Impossible d’ajouter les participants."
            ChatText.LoadCandidates -> "Impossible de charger les contacts."
            ChatText.DeleteConversation -> "Impossible de supprimer la discussion."
            ChatText.LeaveConversation -> "Impossible de quitter la discussion."
            ChatText.PromoteParticipant -> "Impossible de nommer l’administrateur."
            ChatText.DemoteParticipant -> "Impossible de retirer le rôle d’administrateur."
            ChatText.RemoveParticipant -> "Impossible de retirer le participant."
            ChatText.BlockParticipant -> "Impossible de bloquer le participant."
            ChatText.UpdateFavorite -> "Impossible de mettre à jour le favori."
            ChatText.DeleteMessage -> "Impossible de supprimer le message."
            ChatText.ReportSent -> "Signalement envoyé."
            ChatText.ReportMessage -> "Impossible de signaler le message."
            ChatText.Forward -> "Impossible de transférer le message."
            ChatText.RestoreConversation -> "Impossible de restaurer la discussion."
            ChatText.OpenConversation -> "Impossible d’ouvrir la discussion."
        }
        else -> when (value) {
            ChatText.LoadConversations -> "Could not load chats."
            ChatText.LoadMessages -> "Could not load messages."
            ChatText.Send -> "Could not send the message."
            ChatText.You -> "You"
            ChatText.Update -> "Could not update the chat."
            ChatText.AddParticipant -> "Could not add the participant."
            ChatText.AddParticipants -> "Could not add the participants."
            ChatText.LoadCandidates -> "Could not load contacts."
            ChatText.DeleteConversation -> "Could not delete the chat."
            ChatText.LeaveConversation -> "Could not leave the chat."
            ChatText.PromoteParticipant -> "Could not make the participant an administrator."
            ChatText.DemoteParticipant -> "Could not remove the administrator role."
            ChatText.RemoveParticipant -> "Could not remove the participant."
            ChatText.BlockParticipant -> "Could not block the participant."
            ChatText.UpdateFavorite -> "Could not update the favorite."
            ChatText.DeleteMessage -> "Could not delete the message."
            ChatText.ReportSent -> "Report sent."
            ChatText.ReportMessage -> "Could not report the message."
            ChatText.Forward -> "Could not forward the message."
            ChatText.RestoreConversation -> "Could not restore the chat."
            ChatText.OpenConversation -> "Could not open the chat."
        }
    }
}

internal fun newClientMessageId(): String =
    "${currentEpochMillis()}-${Random.nextLong().toString(16)}"

internal fun String.stripMarkup(): String =
    replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

expect fun currentEpochMillis(): Long
