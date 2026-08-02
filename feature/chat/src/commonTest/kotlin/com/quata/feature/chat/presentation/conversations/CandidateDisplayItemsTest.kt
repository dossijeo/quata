package com.quata.feature.chat.presentation.conversations

import com.quata.feature.chat.domain.ChatConversationCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateDisplayItemsTest {
    private val labels = CandidateDisplayLabels(
        contacts = "Contacts",
        following = "Following",
        followers = "Followers",
        recent = "Recent",
        otherNeighborhoods = "Other neighborhoods",
        unknownNeighborhood = "Unknown neighborhood",
    )

    @Test
    fun groupsCandidatesBySectionAndEmitsOtherNeighborhoodHeadersOnlyWhenTheyChange() {
        val result = buildCandidateDisplayItems(
            candidates = listOf(
                candidate(profileId = "recent", sectionKey = "recent"),
                candidate(profileId = "north-1", sectionKey = "other", neighborhood = "North"),
                candidate(profileId = "north-2", sectionKey = "other", neighborhood = "North"),
                candidate(profileId = "south", sectionKey = "other", neighborhood = "South"),
            ),
            actorNeighborhood = "Central",
            labels = labels,
        )

        assertEquals(
            listOf(
                CandidateDisplayItem.SectionHeader("Recent", "recent"),
                CandidateDisplayItem.CandidateRow(candidate("recent", "recent")),
                CandidateDisplayItem.SectionHeader("Other neighborhoods", "other"),
                CandidateDisplayItem.NeighborhoodHeader("North"),
                CandidateDisplayItem.CandidateRow(candidate("north-1", "other", "North")),
                CandidateDisplayItem.CandidateRow(candidate("north-2", "other", "North")),
                CandidateDisplayItem.NeighborhoodHeader("South"),
                CandidateDisplayItem.CandidateRow(candidate("south", "other", "South")),
            ),
            result,
        )
    }

    @Test
    fun usesActorNeighborhoodForNeighborhoodSectionAndCandidateFallbackWhenItIsMissing() {
        val actorNeighborhoodResult = buildCandidateDisplayItems(
            candidates = listOf(candidate(profileId = "a", sectionKey = "neighborhood", neighborhood = "Ignored")),
            actorNeighborhood = "Central",
            labels = labels,
        )
        val fallbackResult = buildCandidateDisplayItems(
            candidates = listOf(candidate(profileId = "b", sectionKey = "neighborhood", neighborhood = "Harbor")),
            actorNeighborhood = "",
            labels = labels,
        )

        assertEquals(CandidateDisplayItem.SectionHeader("Central", "neighborhood"), actorNeighborhoodResult.first())
        assertEquals(CandidateDisplayItem.SectionHeader("Harbor", "neighborhood"), fallbackResult.first())
    }

    @Test
    fun usesUnknownNeighborhoodForBlankOtherNeighborhoodGroups() {
        val result = buildCandidateDisplayItems(
            candidates = listOf(candidate(profileId = "a", sectionKey = "other")),
            actorNeighborhood = "Central",
            labels = labels,
        )

        assertEquals(
            listOf(
                CandidateDisplayItem.SectionHeader("Other neighborhoods", "other"),
                CandidateDisplayItem.NeighborhoodHeader("Unknown neighborhood"),
                CandidateDisplayItem.CandidateRow(candidate("a", "other")),
            ),
            result,
        )
    }

    @Test
    fun localizedPreviewKeepsAndroidAttachmentAndLegacyTokens() {
        val spanish = conversationsLocaleCatalogForLanguage("es").preview
        assertEquals("??? Foto", localizedChatPreview(" [QUATA_ATTACHMENT:photo] ", spanish))
        assertEquals("?? Nota de voz", localizedChatPreview("[QUATA_NOTIFICATION:chat_voice_note]", spanish))
        assertEquals("?? Archivo", localizedChatPreview("[QUATA_NOTIFICATION:chat_attachment]", spanish))
        assertEquals("plain text", localizedChatPreview("plain text", spanish))
    }

    @Test
    fun relativeConversationTimeMatchesAndroidAtEveryBoundary() {
        val es = conversationsLocaleCatalogForLanguage("es").relativeTime
        assertEquals("hace 1 s", localizedRelativeConversationTime(0L, es))
        assertEquals("hace 59 s", localizedRelativeConversationTime(59_999L, es))
        assertEquals("hace 1 min", localizedRelativeConversationTime(60_000L, es))
        assertEquals("hace 59 min", localizedRelativeConversationTime(59 * 60_000L, es))
        assertEquals("hace 1 h", localizedRelativeConversationTime(60 * 60_000L, es))
        assertEquals("hace 6 d", localizedRelativeConversationTime(6 * 24 * 60 * 60_000L, es))
        assertEquals("hace 1 semana", localizedRelativeConversationTime(7 * 24 * 60 * 60_000L, es))
        assertEquals("hace 4 semanas", localizedRelativeConversationTime(30 * 24 * 60 * 60_000L, es))
        assertEquals("hace 1 mes", localizedRelativeConversationTime(31 * 24 * 60 * 60_000L, es))
        assertEquals("hace 11 meses", localizedRelativeConversationTime(364 * 24 * 60 * 60_000L, es))
        assertEquals("hace 1 a?o", localizedRelativeConversationTime(365 * 24 * 60 * 60_000L, es))
        assertEquals("hace 2 a?os", localizedRelativeConversationTime(2 * 365 * 24 * 60 * 60_000L, es))
    }

    @Test
    fun catalogLocalizesCandidatesInvitationsAndSelectionForAllSupportedLanguages() {
        assertEquals("Tus contactos", conversationsLocaleCatalogForLanguage("es").host.candidates.contacts)
        assertEquals("Your contacts", conversationsLocaleCatalogForLanguage("en-US").host.candidates.contacts)
        assertEquals("Tes contacts", conversationsLocaleCatalogForLanguage("fr_FR").host.candidates.contacts)
        assertEquals("2 participantes", conversationsLocaleCatalogForLanguage("es").host.selectionSummary(2))
        assertEquals("2 participants", conversationsLocaleCatalogForLanguage("en").host.selectionSummary(2))
        assertEquals("2 participants", conversationsLocaleCatalogForLanguage("fr").host.selectionSummary(2))
        assertEquals("Partager", conversationsLocaleCatalogForLanguage("fr").invitation.shareTarget)
    }

    private fun candidate(
        profileId: String,
        sectionKey: String,
        neighborhood: String = "",
    ) = ChatConversationCandidate(
        profileId = profileId,
        displayName = profileId,
        neighborhood = neighborhood,
        phone = "",
        avatarUrl = null,
        sectionKey = sectionKey,
        neighborhoodGroup = "",
        existingConversationId = null,
    )
}
