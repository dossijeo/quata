package com.quata.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PrimaryNavigationTest {
    @Test
    fun hasTheAuditedFiveDestinationOrder() {
        assertEquals(
            listOf("neighborhoods", "conversations", "official", "feed", "profile"),
            primaryNavigationDestinations.map(PrimaryNavigationDestination::route),
        )
    }
}
