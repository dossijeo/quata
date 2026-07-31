package com.quata.feature.neighborhoods.presentation

/** Portable directory copy. Unknown locales deliberately fall back to English, never a key name. */
fun defaultNeighborhoodsScreenStrings(languageTag: String?): NeighborhoodsScreenStrings {
    val language = languageTag.orEmpty().substringBefore('-').lowercase()
    return when (language) {
        "es" -> neighborhoodStrings(
            "Comunidades", "Buscar comunidad", "Cargando comunidades…", "1 miembro", "miembros",
            "1 mensaje", "mensajes", "Ver miembros", "Abrir conversación", "Actividad reciente",
            "Miembros de", "Directorio de la comunidad", "Volver", "Seguir", "Siguiendo", "Chat",
        )
        "fr" -> neighborhoodStrings(
            "Communautés", "Rechercher une communauté", "Chargement des communautés…", "1 membre", "membres",
            "1 message", "messages", "Voir les membres", "Ouvrir la conversation", "Activité récente",
            "Membres de", "Annuaire de la communauté", "Retour", "Suivre", "Suivi", "Discussion",
        )
        else -> neighborhoodStrings(
            "Communities", "Search communities", "Loading communities…", "1 member", "members",
            "1 message", "messages", "View members", "Open chat", "Recent activity",
            "Members of", "Community directory", "Back", "Follow", "Following", "Chat",
        )
    }
}

private fun neighborhoodStrings(
    title: String, search: String, loading: String, oneUser: String, users: String,
    oneMessage: String, messages: String, viewUsers: String, openChat: String, activity: String,
    membersOf: String, subtitle: String, back: String, follow: String, following: String, chat: String,
) = NeighborhoodsScreenStrings(
    list = NeighborhoodListStrings(title, search, loading, oneUser, { "$it $users" }, oneMessage, { "$it $messages" }, viewUsers, openChat, { activity }),
    members = NeighborhoodUsersStrings({ "$membersOf $it" }, subtitle, back, { if (it == 1) oneUser else "$it $users" }, NeighborhoodUserRowStrings(follow, following, chat)),
)
