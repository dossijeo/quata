package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.parseSosShortcode
import com.quata.feature.chat.presentation.chat.ChatText

/** Strings are injected by the launcher so the same locale produces the same UI on every target. */
data class ConversationsScreenStrings(
    val title: String,
    val searchPlaceholder: String,
    val favorites: String,
    val newChat: String,
    val newChatTitle: String,
    val empty: String?,
    val undo: String,
    val emergencyTitle: String,
    val sosLabel: String,
    val sosLocationUpdate: String,
    val sosLocationUnavailable: String,
    val photo: String,
    val video: String,
    val document: String,
    val voiceNote: String,
    val file: String,
    val time: (Long) -> String,
    val oneMinute: String,
    val minutes: (Long) -> String,
    val hours: (Long) -> String,
    val days: (Long) -> String,
    val oneWeek: String,
    val weeks: (Long) -> String,
    val oneMonth: String,
    val months: (Long) -> String,
    val oneYear: String,
    val years: (Long) -> String,
    val loadCandidatesError: String,
    val openConversationError: String,
    val loadConversationsError: String,
    val restoreConversationError: String,
    val deleteConversationError: String,
    val picker: ConversationCandidatePickerStrings,
)

/** Portable catalogue for launchers that do not have Android XML resources. */
fun defaultConversationsStrings(language: String = "en"): ConversationsScreenStrings {
    val spanish = language.startsWith("es", true)
    val french = language.startsWith("fr", true)
    fun pick(en: String, es: String, fr: String) = if (spanish) es else if (french) fr else en
    return ConversationsScreenStrings(
        pick("Chats", "Chats", "Chats"), pick("Search conversation...", "Buscar conversación...", "Chercher une conversation..."), pick("Favorite messages", "Mensajes favoritos", "Messages favoris"), pick("New chat", "Nuevo chat", "Nouveau chat"), pick("New chat", "Nuevo chat", "Nouveau chat"), pick("No conversations available.", "No hay conversaciones disponibles.", "Aucune conversation disponible."), pick("Undo", "Deshacer", "Annuler"), "🚨 SOS", "SOS", pick("SOS location update", "Actualizacion de ubicacion SOS", "Mise a jour de position SOS"), pick("📍 Location unavailable", "📍 Ubicación no disponible", "📍 Position indisponible"),
        pick("🖼️ Photo", "🖼️ Foto", "🖼️ Photo"), pick("🎥 Video", "🎥 Vídeo", "🎥 Vidéo"), pick("📄 Document", "📄 Documento", "📄 Document"), pick("🎤 Voice note", "🎤 Nota de voz", "🎤 Note vocale"), pick("📎 File", "📎 Archivo", "📎 Fichier"),
        { n -> pick("$n sec ago", "hace $n s", "il y a $n s") }, pick("1 min ago", "hace 1 min", "il y a 1 min"), { n -> pick("$n min ago", "hace $n min", "il y a $n min") }, { n -> pick("$n hr ago", "hace $n h", "il y a $n h") }, { n -> pick("$n days ago", "hace $n días", "il y a $n j") }, pick("1 week ago", "hace 1 semana", "il y a 1 semaine"), { n -> pick("$n weeks ago", "hace $n semanas", "il y a $n semaines") }, pick("1 month ago", "hace 1 mes", "il y a 1 mois"), { n -> pick("$n months ago", "hace $n meses", "il y a $n mois") }, pick("1 year ago", "hace 1 año", "il y a 1 an"), { n -> pick("$n years ago", "hace $n años", "il y a $n ans") },
        pick("Could not load the user list", "No se pudo cargar la lista de usuarios", "Impossible de charger la liste des utilisateurs"),
        pick("Could not open the conversation", "No se pudo abrir la conversación", "Impossible d’ouvrir la conversation"),
        pick("Could not load conversations", "No se pudieron cargar las conversaciones", "Impossible de charger les conversations"),
        pick("Could not restore the conversation", "No se pudo restaurar la conversación", "Impossible de restaurer la conversation"),
        pick("Could not delete the conversation", "No se pudo eliminar la conversación", "Impossible de supprimer la conversation"),
        ConversationCandidatePickerStrings(pick("Search by name, district, or phone...", "Buscar por nombre, barrio o teléfono...", "Chercher par nom, quartier ou téléphone..."), pick("No users found for this search.", "No hay usuarios para esta búsqueda.", "Aucun utilisateur trouvé."), pick("Cancel", "Cancelar", "Annuler"), pick("Your contacts", "Tus contactos", "Tes contacts"), pick("People you follow", "Los que sigues", "Personnes suivies"), pick("People following you", "Los que te siguen", "Abonnés"), pick("Recent", "Recientes", "Récents"), pick("Other districts", "Otros barrios", "Autres quartiers"), pick("No district", "Sin barrio", "Sans quartier"), pick("Invite to Quata", "Invitar a Qüata", "Inviter sur Qüata"), pick("Allow contacts access to see who you can invite.", "Permite acceso a contactos para ver a quién puedes invitar.", "Autorise l'accès aux contacts."), pick("Allow", "Permitir", "Autoriser"), pick("Invite", "Invitar", "Inviter"), pick("No one selected", "Nadie seleccionado", "Personne sélectionnée")),
    )
}

fun ConversationsScreenStrings.chatText(value: ChatText): String = when (value) {
    ChatText.LoadCandidates -> loadCandidatesError
    ChatText.OpenConversation -> openConversationError
    ChatText.LoadConversations -> loadConversationsError
    ChatText.RestoreConversation -> restoreConversationError
    ChatText.DeleteConversation -> deleteConversationError
    else -> loadConversationsError
}

fun Conversation.conversationDisplayTitle(emergencyTitle: String): String = when {
    isEmergency -> emergencyTitle
    !communityName.isNullOrBlank() -> communityName.orEmpty()
    isGroup && participantNames.isNotEmpty() && title.isGeneratedChatTitle(id) -> participantNames.joinToString(", ")
    title.isNotBlank() -> title
    isGroup && participantNames.isNotEmpty() -> participantNames.joinToString(", ")
    else -> ""
}

private fun String.isGeneratedChatTitle(conversationId: String): Boolean =
    substringAfterLast(':', "").let { it.isNotBlank() && this == "Chat $it" }

fun ConversationsScreenStrings.localizePreview(raw: String): String {
    raw.parseSosShortcode()?.let { sos ->
        return when {
            sos.kind == SosShortcodeKind.LocationUpdate -> sosLocationUpdate
            !sos.hasLocation -> sosLocationUnavailable
            else -> sos.senderName.ifBlank { emergencyTitle }
        }
    }
    return when (raw.trim()) {
    "[QUATA_ATTACHMENT:photo]" -> photo
    "[QUATA_ATTACHMENT:video]" -> video
    "[QUATA_ATTACHMENT:document]" -> document
    "[QUATA_ATTACHMENT:voice_note]", "[QUATA_NOTIFICATION:chat_voice_note]" -> voiceNote
    "[QUATA_ATTACHMENT:file]", "[QUATA_NOTIFICATION:chat_attachment]" -> file
    else -> raw
    }
}

fun ConversationsScreenStrings.relativeTime(value: String, millis: Long?, nowMillis: Long): String {
    val timestamp = millis ?: parseConversationUpdatedAtMillis(value, nowMillis) ?: return value
    val seconds = ((nowMillis - timestamp).coerceAtLeast(0L) / 1000L)
    val minutesValue = seconds / 60L
    return when {
        seconds < 60L -> time(seconds.coerceAtLeast(1L))
        minutesValue < 2L -> oneMinute
        minutesValue < 60L -> minutes(minutesValue)
        minutesValue < 24L * 60L -> hours(minutesValue / 60L)
        minutesValue < 7L * 24L * 60L -> days(minutesValue / (24L * 60L))
        minutesValue < 14L * 24L * 60L -> oneWeek
        minutesValue < 31L * 24L * 60L -> weeks(minutesValue / (7L * 24L * 60L))
        minutesValue < 62L * 24L * 60L -> oneMonth
        minutesValue < 365L * 24L * 60L -> months(minutesValue / (31L * 24L * 60L))
        minutesValue < 2L * 365L * 24L * 60L -> oneYear
        else -> years(minutesValue / (365L * 24L * 60L))
    }
}

fun filterConversations(
    conversations: List<Conversation>, query: String, usersById: Map<String, com.quata.core.model.User>,
    messagesByConversation: Map<String, List<com.quata.core.model.Message>>, strings: ConversationsScreenStrings,
): List<Conversation> {
    val needle = query.trim()
    if (needle.isBlank()) return conversations
    return conversations.filter { conversation ->
        val preview = strings.localizePreview(messagesByConversation[conversation.id].orEmpty().lastOrNull()?.text ?: conversation.lastMessagePreview)
        val names = conversation.participantIds.mapNotNull(usersById::get).joinToString(" ") { it.displayName }
        listOf(conversation.conversationDisplayTitle(strings.emergencyTitle), conversation.title, conversation.participantNames.joinToString(" "), names, preview)
            .any { it.contains(needle, ignoreCase = true) }
    }
}
