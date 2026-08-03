package com.quata.feature.neighborhoods.presentation

/** Localized copy shared by non-Android hosts; Android continues to consume its resources. */
fun neighborhoodsScreenStringsForLanguage(languageTag: String?): NeighborhoodsScreenStrings =
    when (languageTag?.substringBefore('-')?.lowercase()) {
        "es" -> NeighborhoodsScreenStrings(
            list = NeighborhoodListStrings(
                title = "Abre una comunidad",
                searchPlaceholder = "Selecciona o busca un barrio para abrir su chat comunitario.",
                loading = "Cargando barrios…",
                oneUser = "1 usuario",
                users = { "$it usuarios" },
                oneMessage = "1 mensaje",
                messages = { "$it mensajes" },
                viewUsers = "Ver usuarios",
                openChat = "Abrir chat",
                timeLabel = { if (it == null) "Nuevo" else "Actividad reciente" },
            ),
            members = NeighborhoodUsersStrings(
                title = { "Usuarios · $it" },
                subtitle = "Comunidad creada por usuarios de QUATA",
                backContentDescription = "Volver",
                memberCount = { if (it == 1) "1 usuario" else "$it usuarios" },
                row = NeighborhoodUserRowStrings("Seguir", "Siguiendo", "Chat"),
            ),
        )
        "fr" -> NeighborhoodsScreenStrings(
            list = NeighborhoodListStrings(
                title = "Ouvre une communauté",
                searchPlaceholder = "Sélectionne ou cherche un quartier pour ouvrir son chat communautaire.",
                loading = "Chargement des quartiers…",
                oneUser = "1 utilisateur",
                users = { "$it utilisateurs" },
                oneMessage = "1 message",
                messages = { "$it messages" },
                viewUsers = "Voir les utilisateurs",
                openChat = "Ouvrir le chat",
                timeLabel = { if (it == null) "Nouveau" else "Activité récente" },
            ),
            members = NeighborhoodUsersStrings(
                title = { "Utilisateurs · $it" },
                subtitle = "Communauté créée par les utilisateurs de QUATA",
                backContentDescription = "Retour",
                memberCount = { if (it == 1) "1 utilisateur" else "$it utilisateurs" },
                row = NeighborhoodUserRowStrings("Suivre", "Suivi", "Chat"),
            ),
        )
        else -> NeighborhoodsScreenStrings(
            list = NeighborhoodListStrings(
                title = "Open a community",
                searchPlaceholder = "Select or search for a district to open its community chat.",
                loading = "Loading districts…",
                oneUser = "1 user",
                users = { "$it users" },
                oneMessage = "1 message",
                messages = { "$it messages" },
                viewUsers = "View users",
                openChat = "Open chat",
                timeLabel = { if (it == null) "New" else "Recent activity" },
            ),
            members = NeighborhoodUsersStrings(
                title = { "Users · $it" },
                subtitle = "Community created by QUATA users",
                backContentDescription = "Back",
                memberCount = { if (it == 1) "1 user" else "$it users" },
                row = NeighborhoodUserRowStrings("Follow", "Following", "Chat"),
            ),
        )
    }
