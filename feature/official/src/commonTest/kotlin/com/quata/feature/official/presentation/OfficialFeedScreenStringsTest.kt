package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialFeedScreenStringsTest {
    @Test fun selectsCompleteSpanishEnglishAndFrenchCatalogs() {
        assertEquals(OfficialFeedScreenStrings(), defaultOfficialFeedScreenStrings("es-ES"))
        assertEquals(OfficialFeedScreenStrings(), defaultOfficialFeedScreenStrings("de-DE"))
        assertEquals(
            OfficialFeedScreenStrings(
                empty = "No official notices are available.", create = "Create notice", retry = "Retry",
                loadingError = "Could not load official notices.", like = "Like", comments = "Comments",
                share = "Share", rank = "Ranking", live = "LIVE", delete = "Delete", close = "Close",
                profile = "Profile", readMore = "Read more", refresh = "Refresh",
                readMoreMoreInformation = "More information", readMoreContinueReading = "Continue reading",
                readMoreDetails = "Details", typeAnnouncement = "Announcement", typeNews = "News",
                typeEvent = "Event", typeUrgent = "Urgent", officialAccountFallback = "Official account",
                deleteTitle = "Delete notice", deleteMessage = "This action cannot be undone.",
                confirm = "Confirm", cancel = "Cancel", deleted = "Notice deleted",
                reportSent = "Report sent for review", reportFailed = "Could not send report",
                shareUnavailable = "This notice cannot be shared on this device.", shareFailed = "Could not share notice",
            ),
            defaultOfficialFeedScreenStrings("en-US"),
        )
        assertEquals(
            OfficialFeedScreenStrings(
                empty = "Aucun communiqu\u00e9 officiel disponible.", create = "Cr\u00e9er un communiqu\u00e9", retry = "R\u00e9essayer",
                loadingError = "Impossible de charger les communiqu\u00e9s officiels.", like = "J'aime", comments = "Commentaires",
                share = "Partager", rank = "Classement", live = "DIRECT", delete = "Supprimer", close = "Fermer",
                profile = "Profil", readMore = "Lire plus", refresh = "Actualiser",
                readMoreMoreInformation = "Plus d'informations", readMoreContinueReading = "Continuer la lecture",
                readMoreDetails = "D\u00e9tails", typeAnnouncement = "Communiqu\u00e9", typeNews = "Actualit\u00e9s",
                typeEvent = "\u00c9v\u00e9nement", typeUrgent = "Urgent", officialAccountFallback = "Compte officiel",
                deleteTitle = "Supprimer le communiqu\u00e9", deleteMessage = "Cette action est irr\u00e9versible.",
                confirm = "Confirmer", cancel = "Annuler", deleted = "Communiqu\u00e9 supprim\u00e9",
                reportSent = "Signalement envoy\u00e9 pour examen", reportFailed = "Impossible d'envoyer le signalement",
                shareUnavailable = "Ce communiqu\u00e9 ne peut pas \u00eatre partag\u00e9 sur cet appareil.", shareFailed = "Impossible de partager le communiqu\u00e9",
            ),
            defaultOfficialFeedScreenStrings("fr-FR"),
        )
    }
}
