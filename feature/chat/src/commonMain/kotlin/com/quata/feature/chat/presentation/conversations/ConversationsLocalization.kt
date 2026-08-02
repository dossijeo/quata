package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.parseSosShortcode

/** Complete conversation copy catalogue shared by Android, Web and iOS. */
data class ConversationsLocaleCatalog(
    val host: ConversationsHostStrings,
    val invitation: ConversationInvitationStrings,
    val preview: ConversationPreviewStrings,
    val relativeTime: ConversationRelativeTimeStrings,
)

data class ConversationInvitationStrings(
    val message: String, val shareTarget: String, val shareTitle: String,
    val sheetTitle: (String) -> String, val copyMessage: String, val chooseAppFor: (String) -> String,
)

data class ConversationPreviewStrings(
    val photo: String, val video: String, val document: String, val voiceNote: String, val file: String,
    val sosLocationUpdate: String, val sosLocationUnavailable: String, val sosLocationApproximate: (String) -> String,
)

data class ConversationRelativeTimeStrings(
    val seconds: (Long) -> String, val oneMinute: String, val minutes: (Long) -> String,
    val hours: (Long) -> String, val days: (Long) -> String, val oneWeek: String,
    val weeks: (Long) -> String, val oneMonth: String, val months: (Long) -> String,
    val oneYear: String, val years: (Long) -> String,
)

fun conversationsLocaleCatalogForLanguage(languageTag: String?): ConversationsLocaleCatalog = when (languageTag.language()) {
    "es" -> catalog(
        title = "Chats", search = "Buscar conversaci?n...", favorites = "Mensajes favoritos", newChat = "Nuevo chat", undo = "Deshacer", group = "A?adir participantes", create = "Crear grupo",
        candidates = ConversationCandidatePickerStrings("Buscar por nombre, barrio o telefono...", "No hay usuarios para esta busqueda.", "Cancelar", "Tus contactos", "Los que sigues", "Los que te siguen", "Conversaciones recientes", "Otros barrios", "Sin barrio", "Invitar a Q?ata", "Permite el acceso a tus contactos para ver a qui?n puedes invitar.", "Permitir", "Invitar", "Selecciona conversaciones"),
        selection = { "$it participantes" }, preview = ConversationPreviewStrings("??? Foto", "?? V?deo", "?? Documento", "?? Nota de voz", "?? Archivo", "Actualizacion de ubicacion SOS", "?? Ubicaci?n no disponible") { "Ubicacion aproximada: $it" },
        relative = relative({ "hace $it s" }, "hace 1 min", { "hace $it min" }, { "hace $it h" }, { "hace $it d" }, "hace 1 semana", { "hace $it semanas" }, "hace 1 mes", { "hace $it meses" }, "hace 1 a?o", { "hace $it a?os" }),
        invitation = ConversationInvitationStrings("Me gustar?a hablar contigo en Q?ata. Desc?rgala aqu?: https://play.google.com/store/apps/details?id=com.quata", "Compartir", "Q?ata", { "Invitar a $it con" }, "Copiar texto", { "Elige una aplicaci?n para invitar a $it" }),
    )
    "fr" -> catalog(
        title = "Chats", search = "Chercher une conversation...", favorites = "Messages favoris", newChat = "Nouveau chat", undo = "Annuler", group = "Ajouter des participants", create = "Cr?er le groupe",
        candidates = ConversationCandidatePickerStrings("Chercher par nom, quartier ou telephone...", "Aucun utilisateur pour cette recherche.", "Annuler", "Tes contacts", "Les personnes que tu suis", "Les personnes qui te suivent", "Conversations r?centes", "Autres quartiers", "Sans quartier", "Inviter sur Q?ata", "Autorise l'acc?s ? tes contacts pour voir qui tu peux inviter.", "Autoriser", "Inviter", "Selectionne des conversations"),
        selection = { "$it participants" }, preview = ConversationPreviewStrings("??? Photo", "?? Vid?o", "?? Document", "?? Note vocale", "?? Fichier", "Mise a jour de position SOS", "?? Position indisponible") { "Position approximative : $it" },
        relative = relative({ "il y a $it s" }, "il y a 1 min", { "il y a $it min" }, { "il y a $it h" }, { "il y a $it j" }, "il y a 1 semaine", { "il y a $it semaines" }, "il y a 1 mois", { "il y a $it mois" }, "il y a 1 an", { "il y a $it ans" }),
        invitation = ConversationInvitationStrings("J'aimerais discuter avec toi sur Q?ata. T?l?charge l'application ici : https://play.google.com/store/apps/details?id=com.quata", "Partager", "Q?ata", { "Inviter $it avec" }, "Copier le texte", { "Choisissez une application pour inviter $it" }),
    )
    else -> catalog(
        title = "Chats", search = "Search conversation...", favorites = "Favorite messages", newChat = "New chat", undo = "Undo", group = "Add participants", create = "Create group",
        candidates = ConversationCandidatePickerStrings("Search by name, district, or phone...", "No users found for this search.", "Cancel", "Your contacts", "People you follow", "People following you", "Recent conversations", "Other districts", "No district", "Invite to Q?ata", "Allow contact access to see who you can invite.", "Allow", "Invite", "Select conversations"),
        selection = { "$it participants" }, preview = ConversationPreviewStrings("??? Photo", "?? Video", "?? Document", "?? Voice note", "?? File", "SOS location update", "?? Location unavailable") { "Location (approximate): $it" },
        relative = relative({ "$it sec ago" }, "1 min ago", { "$it min ago" }, { "$it h ago" }, { "$it d ago" }, "1 week ago", { "$it weeks ago" }, "1 month ago", { "$it months ago" }, "1 year ago", { "$it years ago" }),
        invitation = ConversationInvitationStrings("I'd like to talk with you on Q?ata. Download it here: https://play.google.com/store/apps/details?id=com.quata", "Share", "Q?ata", { "Invite $it with" }, "Copy text", { "Choose an app to invite $it" }),
    )
}

fun conversationsHostStringsForLanguage(languageTag: String?): ConversationsHostStrings = conversationsLocaleCatalogForLanguage(languageTag).host

private fun String?.language(): String? = this?.substringBefore('-')?.substringBefore('_')?.lowercase()

private fun relative(seconds: (Long) -> String, oneMinute: String, minutes: (Long) -> String, hours: (Long) -> String, days: (Long) -> String, oneWeek: String, weeks: (Long) -> String, oneMonth: String, months: (Long) -> String, oneYear: String, years: (Long) -> String) =
    ConversationRelativeTimeStrings(seconds, oneMinute, minutes, hours, days, oneWeek, weeks, oneMonth, months, oneYear, years)

private fun catalog(title: String, search: String, favorites: String, newChat: String, undo: String, group: String, create: String, candidates: ConversationCandidatePickerStrings, selection: (Int) -> String, preview: ConversationPreviewStrings, relative: ConversationRelativeTimeStrings, invitation: ConversationInvitationStrings): ConversationsLocaleCatalog {
    val host = ConversationsHostStrings(title, search, favorites, newChat, undo, candidates, ::conversationTitle, { localizedChatPreview(it, preview) }, { conversation, now -> conversation.updatedAtMillis?.let { localizedRelativeConversationTime((now - it).coerceAtLeast(0L), relative) } ?: conversation.updatedAt }, newChat, group, create, selection)
    return ConversationsLocaleCatalog(host, invitation, preview, relative)
}

private fun conversationTitle(conversation: Conversation): String = when {
    conversation.isEmergency -> "?? SOS"
    !conversation.communityName.isNullOrBlank() -> conversation.communityName.orEmpty()
    conversation.isGroup && conversation.participantNames.isNotEmpty() && conversation.title == "Chat ${conversation.id.substringAfterLast(':')}" -> conversation.participantNames.joinToString(", ")
    conversation.title.isNotBlank() -> conversation.title
    conversation.isGroup && conversation.participantNames.isNotEmpty() -> conversation.participantNames.joinToString(", ")
    else -> ""
}

/** Exact Android preview token semantics, now usable by every launcher. */
fun localizedChatPreview(raw: String, strings: ConversationPreviewStrings): String {
    raw.parseSosShortcode()?.let { message -> return when {
        message.kind == SosShortcodeKind.LocationUpdate -> strings.sosLocationUpdate
        !message.hasLocation -> strings.sosLocationUnavailable
        else -> strings.sosLocationApproximate(message.mapsUrl.orEmpty())
    } }
    return when (raw.trim()) {
        "[QUATA_ATTACHMENT:photo]" -> strings.photo; "[QUATA_ATTACHMENT:video]" -> strings.video
        "[QUATA_ATTACHMENT:document]" -> strings.document
        "[QUATA_ATTACHMENT:voice_note]", "[QUATA_NOTIFICATION:chat_voice_note]" -> strings.voiceNote
        "[QUATA_ATTACHMENT:file]", "[QUATA_NOTIFICATION:chat_attachment]" -> strings.file
        else -> raw
    }
}

/** Exact Android relative-time thresholds (seconds through years). */
fun localizedRelativeConversationTime(ageMillis: Long, strings: ConversationRelativeTimeStrings): String {
    val seconds = (ageMillis.coerceAtLeast(0L) / 1_000L).coerceAtLeast(1L); val minutes = seconds / 60L
    return when {
        seconds < 60L -> strings.seconds(seconds); minutes < 2L -> strings.oneMinute; minutes < 60L -> strings.minutes(minutes)
        minutes < 1_440L -> strings.hours(minutes / 60L); minutes < 10_080L -> strings.days(minutes / 1_440L)
        minutes < 20_160L -> strings.oneWeek; minutes < 44_640L -> strings.weeks(minutes / 10_080L)
        minutes < 89_280L -> strings.oneMonth; minutes < 525_600L -> strings.months(minutes / 44_640L)
        minutes < 1_051_200L -> strings.oneYear; else -> strings.years(minutes / 525_600L)
    }
}
