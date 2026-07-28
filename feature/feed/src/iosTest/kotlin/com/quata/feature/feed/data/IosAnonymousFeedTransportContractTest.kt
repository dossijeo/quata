package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosAnonymousFeedTransportContractTest {
    @Test
    fun publicReadsUseOnlyPublishableKeyAndJsonAcceptHeaders() {
        val headers = iosFeedPublicHeaders("public-key")

        assertEquals("public-key", headers["apikey"])
        assertEquals("application/json", headers["Accept"])
        assertFalse(headers.keys.any { it?.toString()?.equals("Authorization", ignoreCase = true) == true })
    }
}
