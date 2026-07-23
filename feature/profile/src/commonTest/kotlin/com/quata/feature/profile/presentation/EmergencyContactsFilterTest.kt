package com.quata.feature.profile.presentation

import com.quata.feature.profile.domain.EmergencyContactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals

class EmergencyContactsFilterTest {
    private val candidates = listOf(
        EmergencyContactCandidate("1", "Zoe", "zoe@quata.app", "Centro", "111"),
        EmergencyContactCandidate("2", "Ana", "ana@quata.app", "Norte", "222"),
        EmergencyContactCandidate("3", "Beto", "beto@quata.app", "Sur", "333")
    )

    @Test
    fun selectedContactsAreFirstAndEachGroupIsAlphabetical() {
        val result = filterEmergencyContactCandidates(candidates, setOf("1", "3"), "")

        assertEquals(listOf("3", "1", "2"), result.map { it.id })
    }

    @Test
    fun matchesEverySearchableContactField() {
        assertEquals(listOf("2"), filterEmergencyContactCandidates(candidates, emptySet(), "norte").map { it.id })
        assertEquals(listOf("3"), filterEmergencyContactCandidates(candidates, emptySet(), "333").map { it.id })
    }
}
