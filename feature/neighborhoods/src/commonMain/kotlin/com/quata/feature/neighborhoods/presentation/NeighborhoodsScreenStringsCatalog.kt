@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quata.feature.neighborhoods.presentation

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Portable directory copy. Unknown locales deliberately fall back to English, never a key name. */
fun defaultNeighborhoodsScreenStrings(languageTag: String?): NeighborhoodsScreenStrings {
    val language = languageTag.orEmpty().substringBefore('-').lowercase()
    return when (language) {
        "es" -> neighborhoodStrings(
            language,
            "Comunidades", "Buscar comunidad", "Cargando comunidades…", "1 miembro", "miembros",
            "1 mensaje", "mensajes", "Ver miembros", "Abrir conversación", "Actividad reciente",
            "Miembros de", "Directorio de la comunidad", "Volver", "Seguir", "Siguiendo", "Chat",
        )
        "fr" -> neighborhoodStrings(
            language,
            "Communautés", "Rechercher une communauté", "Chargement des communautés…", "1 membre", "membres",
            "1 message", "messages", "Voir les membres", "Ouvrir la conversation", "Activité récente",
            "Membres de", "Annuaire de la communauté", "Retour", "Suivre", "Suivi", "Discussion",
        )
        else -> neighborhoodStrings(
            "en",
            "Communities", "Search communities", "Loading communities…", "1 member", "members",
            "1 message", "messages", "View members", "Open chat", "Recent activity",
            "Members of", "Community directory", "Back", "Follow", "Following", "Chat",
        )
    }
}

fun defaultCommunityProfileStrings(languageTag: String?): CommunityProfileStrings {
    val language = languageTag.orEmpty().substringBefore('-').lowercase()
    return when (language) {
        "es" -> communityProfileStrings("Publicaciones", "Seguidores", "Siguiendo", "Seguidores de", "Seguidos por", "Fotos y vídeos", "No hay publicaciones visibles", "Archivos adjuntos", "No hay archivos adjuntos", "Seguir", "Siguiendo", "Chat", "Reportar", "Bloquear", "Reportar perfil", "Bloquear perfil", "¿Reportar este perfil?", "¿Bloquear este perfil?", "Cancelar", "Permisos", "Administrador", "Oficial", "Volver", CommunityProfileRuntimeStrings("Cargando perfil…", "Reintentar", "Archivo", "Cargar vídeo", "No se pudo abrir el archivo", "Vista previa cancelada", "Formato no compatible", "Imagen", "Vídeo", "Audio", "Documento", "Reproducir audio", "Pausar audio"))
        "fr" -> communityProfileStrings("Publications", "Abonnés", "Abonnements", "Abonnés de", "Abonnements de", "Photos et vidéos", "Aucune publication visible", "Pièces jointes", "Aucune pièce jointe", "Suivre", "Suivi", "Discussion", "Signaler", "Bloquer", "Signaler le profil", "Bloquer le profil", "Signaler ce profil ?", "Bloquer ce profil ?", "Annuler", "Autorisations", "Administrateur", "Officiel", "Retour", CommunityProfileRuntimeStrings("Chargement du profil…", "Réessayer", "Fichier", "Charger la vidéo", "Impossible d’ouvrir le fichier", "Aperçu annulé", "Format non pris en charge", "Image", "Vidéo", "Audio", "Document", "Lire l’audio", "Mettre l’audio en pause"))
        else -> communityProfileStrings("Posts", "Followers", "Following", "Followers of", "Following of", "Photos and videos", "No visible posts", "Attachments", "No attachments", "Follow", "Following", "Chat", "Report", "Block", "Report profile", "Block profile", "Report this profile?", "Block this profile?", "Cancel", "Permissions", "Admin", "Official", "Back", CommunityProfileRuntimeStrings("Loading profile…", "Retry", "File", "Load video", "Could not open attachment", "Attachment preview cancelled", "Attachment format is not supported", "Image", "Video", "Audio", "Document", "Play audio", "Pause audio"))
    }
}

fun defaultCommunityProfileCommentsDialogStrings(languageTag: String?): CommunityProfileCommentsDialogStrings =
    when (languageTag.orEmpty().substringBefore('-').lowercase()) {
        "es" -> CommunityProfileCommentsDialogStrings("Comentarios", "Cerrar", "Escribe un comentario", "Enviar")
        "fr" -> CommunityProfileCommentsDialogStrings("Commentaires", "Fermer", "Écrire un commentaire", "Envoyer")
        else -> CommunityProfileCommentsDialogStrings("Comments", "Close", "Write a comment", "Send")
    }

private fun communityProfileStrings(
    posts: String, followers: String, following: String, followersOf: String, followingOf: String,
    gallery: String, noPosts: String, attachments: String, noAttachments: String,
    follow: String, followingAction: String, chat: String, report: String, block: String,
    reportTitle: String, blockTitle: String, reportConfirm: String, blockConfirm: String,
    cancel: String, permissions: String, admin: String, official: String, back: String,
    runtime: CommunityProfileRuntimeStrings,
) = CommunityProfileStrings(
    posts, followers, following, { "$followersOf $it" }, { "$followingOf $it" }, gallery, noPosts,
    ProfileAttachmentsStrings(attachments, noAttachments), ProfileActionStrings(follow, followingAction, chat),
    ProfileModerationStrings(report, block), ProfileModerationConfirmationStrings(reportTitle, blockTitle, reportConfirm, blockConfirm, cancel, report, block),
    ProfileRoleStrings(permissions, admin, official), NeighborhoodUserRowStrings(follow, followingAction, chat), back, runtime,
)

private fun neighborhoodStrings(
    languageTag: String,
    title: String, search: String, loading: String, oneUser: String, users: String,
    oneMessage: String, messages: String, viewUsers: String, openChat: String, activity: String,
    membersOf: String, subtitle: String, back: String, follow: String, following: String, chat: String,
) = NeighborhoodsScreenStrings(
    list = NeighborhoodListStrings(title, search, loading, oneUser, { "$it $users" }, oneMessage, { "$it $messages" }, viewUsers, openChat, { timestamp -> neighborhoodTimeLabel(timestamp, kotlin.time.Clock.System.now().toEpochMilliseconds(), languageTag) ?: activity }, retry = when (languageTag) { "es" -> "Reintentar"; "fr" -> "Réessayer"; else -> "Retry" }),
    members = NeighborhoodUsersStrings({ "$membersOf $it" }, subtitle, back, { if (it == 1) oneUser else "$it $users" }, NeighborhoodUserRowStrings(follow, following, chat)),
)

internal fun neighborhoodTimeLabel(timestamp: Long?, nowMillis: Long, languageTag: String): String? {
    timestamp ?: return null
    val language = languageTag.substringBefore('-').lowercase().let { if (it in setOf("es", "fr")) it else "en" }
    val zone = TimeZone.currentSystemDefault()
    val messageDateTime = kotlin.time.Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    val today = kotlin.time.Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val days = (today.toEpochDays() - messageDateTime.date.toEpochDays()).toLong().coerceAtLeast(0L)
    if (days == 0L) {
        return messageDateTime.hour.toString().padStart(2, '0') + ":" + messageDateTime.minute.toString().padStart(2, '0')
    }
    fun unit(value: Long, es: String, en: String, fr: String) = when (language) { "es" -> "Hace $value $es"; "fr" -> "Il y a $value $fr"; else -> "$value $en ago" }
    return when {
        days == 1L -> when (language) { "es" -> "Ayer"; "fr" -> "Hier"; else -> "Yesterday" }
        days < 7 -> unit(days, "días", "days", "jours")
        days < 30 -> unit((days / 7).coerceAtLeast(1), if (days / 7 == 1L) "semana" else "semanas", if (days / 7 == 1L) "week" else "weeks", if (days / 7 == 1L) "semaine" else "semaines")
        days < 365 -> unit((days / 30).coerceAtLeast(1), if (days / 30 == 1L) "mes" else "meses", if (days / 30 == 1L) "month" else "months", if (days / 30 == 1L) "mois" else "mois")
        else -> unit((days / 365).coerceAtLeast(1), if (days / 365 == 1L) "año" else "años", if (days / 365 == 1L) "year" else "years", if (days / 365 == 1L) "an" else "ans")
    }
}
