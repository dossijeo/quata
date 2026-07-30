package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialFeedScreenStringsTest {
    @Test fun selectsCompleteSpanishEnglishAndFrenchCatalogs() {
        assertCatalogEquals(OfficialFeedScreenStrings(), defaultOfficialFeedScreenStrings("es-ES"))
        assertCatalogEquals(OfficialFeedScreenStrings(), defaultOfficialFeedScreenStrings("de-DE"))
        assertCatalogEquals(
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
        assertCatalogEquals(
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

    @Test fun commentCatalogNeverFallsBackToSpanishForEnglishOrFrench() {
        val english = defaultOfficialFeedScreenStrings("en-US")
        assertEquals("Write a comment…", english.commentPlaceholder)
        assertEquals("Send comment", english.commentSend)
        assertEquals("Report", english.commentReport)
        assertEquals("Reply", english.commentReply)
        assertEquals("Replying to Ana", english.commentReplyingTo("Ana"))
        assertEquals("Cancel reply", english.commentCancelReply)
        assertEquals("You", english.commentsYou)
        assertEquals("↳ Reply to Ana", english.commentReplyTo("Ana"))
        assertEquals("Show emojis", english.showEmojis)
        assertEquals("Fang translator", english.translatorContentDescription)
        assertEquals(
            listOf("Recent", "Frequent", "Gestures", "People", "Animals and nature", "Food and drink", "Objects and symbols", "Flags"),
            with(english.emojiLabels) { listOf(recent, frequent, gestures, people, animalsNature, foodDrink, objectsSymbols, flags) },
        )

        val french = defaultOfficialFeedScreenStrings("fr-FR")
        assertEquals("Écris un commentaire…", french.commentPlaceholder)
        assertEquals("Envoyer le commentaire", french.commentSend)
        assertEquals("Signaler", french.commentReport)
        assertEquals("Répondre", french.commentReply)
        assertEquals("Réponse à Ana", french.commentReplyingTo("Ana"))
        assertEquals("Annuler la réponse", french.commentCancelReply)
        assertEquals("Toi", french.commentsYou)
        assertEquals("↳ Réponse à Ana", french.commentReplyTo("Ana"))
        assertEquals("Afficher les emojis", french.showEmojis)
        assertEquals("Traducteur Fang", french.translatorContentDescription)
        assertEquals(
            listOf("Récents", "Fréquents", "Gestes", "Personnes", "Animaux et nature", "Cuisine et boissons", "Objets et symboles", "Drapeaux"),
            with(french.emojiLabels) { listOf(recent, frequent, gestures, people, animalsNature, foodDrink, objectsSymbols, flags) },
        )
    }

    private fun assertCatalogEquals(expected: OfficialFeedScreenStrings, actual: OfficialFeedScreenStrings) {
        assertEquals(
            listOf(
                expected.empty, expected.create, expected.retry, expected.loadingError, expected.like,
                expected.comments, expected.share, expected.rank, expected.live, expected.delete,
                expected.close, expected.profile, expected.readMore, expected.refresh,
                expected.readMoreMoreInformation, expected.readMoreContinueReading, expected.readMoreDetails,
                expected.typeAnnouncement, expected.typeNews, expected.typeEvent, expected.typeUrgent,
                expected.officialAccountFallback, expected.deleteTitle, expected.deleteMessage,
                expected.confirm, expected.cancel, expected.deleted, expected.reportSent, expected.reportFailed,
                expected.shareUnavailable, expected.shareFailed,
            ),
            listOf(
                actual.empty, actual.create, actual.retry, actual.loadingError, actual.like,
                actual.comments, actual.share, actual.rank, actual.live, actual.delete,
                actual.close, actual.profile, actual.readMore, actual.refresh,
                actual.readMoreMoreInformation, actual.readMoreContinueReading, actual.readMoreDetails,
                actual.typeAnnouncement, actual.typeNews, actual.typeEvent, actual.typeUrgent,
                actual.officialAccountFallback, actual.deleteTitle, actual.deleteMessage,
                actual.confirm, actual.cancel, actual.deleted, actual.reportSent, actual.reportFailed,
                actual.shareUnavailable, actual.shareFailed,
            ),
        )
    }
}
