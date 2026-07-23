package com.quata.feature.profile.presentation

import com.quata.feature.profile.domain.EmergencyContactCandidate

/** Shared ordering and search policy for the SOS contact selector. */
fun filterEmergencyContactCandidates(
    candidates: List<EmergencyContactCandidate>,
    selectedIds: Set<String>,
    query: String
): List<EmergencyContactCandidate> = candidates
    .asSequence()
    .filter { candidate ->
        query.isBlank() ||
            candidate.displayName.contains(query, ignoreCase = true) ||
            candidate.email.contains(query, ignoreCase = true) ||
            candidate.neighborhood.contains(query, ignoreCase = true) ||
            candidate.phone.contains(query, ignoreCase = true)
    }
    .sortedWith(
        compareByDescending<EmergencyContactCandidate> { it.id in selectedIds }
            .thenBy { it.displayName.lowercase() }
    )
    .toList()
