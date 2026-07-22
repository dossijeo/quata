package com.quata.feature.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDiscoveryTest {
    @Test
    fun normalizesFormattingWithoutChangingPhoneDigits() {
        assertEquals("34612345678", normalizeContactPhoneKey("+34 612-345-678"))
    }

    @Test
    fun removesInvalidAndDuplicateCandidatesBeforeBatching() {
        val batches = prepareContactDiscoveryBatches(
            listOf("+34 612 345 678", "34612345678", "123", "")
        )

        assertEquals(listOf(listOf("34612345678")), batches)
    }

    @Test
    fun splitsLargeContactDirectoriesIntoBoundedBatches() {
        val phones = (0 until 1_201).map { index -> "34${index.toString().padStart(9, '0')}" }
        val batches = prepareContactDiscoveryBatches(phones)

        assertEquals(listOf(500, 500, 201), batches.map(List<String>::size))
        assertTrue(batches.flatten().all { it.length in 6..20 })
    }
}
