package com.quata.feature.chat.presentation.conversations

import com.quata.feature.chat.domain.ChatConversationCandidate

/** Platform-neutral grouping used by every conversation destination picker. */
sealed class CandidateDisplayItem(val key: String) {
    data class SectionHeader(val title: String, val sectionKey: String) : CandidateDisplayItem("section:$sectionKey")
    data class NeighborhoodHeader(val title: String) : CandidateDisplayItem("neighborhood:$title")
    data class CandidateRow(val candidate: ChatConversationCandidate) : CandidateDisplayItem("candidate:${candidate.profileId}")
}

data class CandidateDisplayLabels(
    val contacts: String,
    val following: String,
    val followers: String,
    val recent: String,
    val otherNeighborhoods: String,
    val unknownNeighborhood: String
)

fun buildCandidateDisplayItems(
    candidates: List<ChatConversationCandidate>,
    actorNeighborhood: String,
    labels: CandidateDisplayLabels
): List<CandidateDisplayItem> {
    val items = mutableListOf<CandidateDisplayItem>()
    var lastSectionKey: String? = null
    var lastOtherNeighborhood: String? = null
    candidates.forEach { candidate ->
        if (candidate.sectionKey != lastSectionKey) {
            lastSectionKey = candidate.sectionKey
            lastOtherNeighborhood = null
            val title = when (candidate.sectionKey) {
                "recent" -> labels.recent
                "contacts" -> labels.contacts
                "following" -> labels.following
                "followers" -> labels.followers
                "neighborhood" -> actorNeighborhood.ifBlank {
                    candidate.neighborhood.ifBlank { labels.unknownNeighborhood }
                }
                else -> labels.otherNeighborhoods
            }
            items += CandidateDisplayItem.SectionHeader(title, candidate.sectionKey)
        }
        if (candidate.sectionKey == "other") {
            val neighborhood = candidate.neighborhoodGroup
                .ifBlank { candidate.neighborhood }
                .ifBlank { labels.unknownNeighborhood }
            if (neighborhood != lastOtherNeighborhood) {
                lastOtherNeighborhood = neighborhood
                items += CandidateDisplayItem.NeighborhoodHeader(neighborhood)
            }
        }
        items += CandidateDisplayItem.CandidateRow(candidate)
    }
    return items
}
