package com.quata.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseApiKeyInterceptorTest {
    @Test
    fun explicitFunctionKeySurvivesTheProductionInterceptor() {
        assertEquals(
            "registration-public-key",
            explicitSupabaseApiKeyOrFallback("registration-public-key", "supabase-publishable"),
        )
    }

    @Test
    fun ordinaryRequestsStillReceiveTheSupabasePublishableKey() {
        assertEquals(
            "supabase-publishable",
            explicitSupabaseApiKeyOrFallback(null, "supabase-publishable"),
        )
        assertEquals(
            "supabase-publishable",
            explicitSupabaseApiKeyOrFallback("", "supabase-publishable"),
        )
    }
}
