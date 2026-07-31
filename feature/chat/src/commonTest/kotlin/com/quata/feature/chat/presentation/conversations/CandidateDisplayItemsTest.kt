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
    fun relativeConversationLabelsCoverTheSameReadableRangesAsAndroid() {
        assertEquals("Ahora", spanishRelativeConversationTime(0L))
        assertEquals("2 min", spanishRelativeConversationTime(2 * 60_000L))
        assertEquals("3 h", spanishRelativeConversationTime(3 * 60 * 60_000L))
        assertEquals("4 d", spanishRelativeConversationTime(4 * 24 * 60 * 60_000L))
        assertEquals("2 sem", spanishRelativeConversationTime(2 * 7 * 24 * 60 * 60_000L))
        assertEquals("3 mes", spanishRelativeConversationTime(3 * 30 * 24 * 60 * 60_000L))
        assertEquals("2 a", spanishRelativeConversationTime(2 * 365 * 24 * 60 * 60_000L))
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
