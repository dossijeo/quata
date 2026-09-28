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

    @Test
    fun dedicatedPublicKeySuppressesAnyImplicitBearer() {
        assertEquals(
            SupabaseAuthHeaders("registration-public-key", null),
            resolveSupabaseAuthHeaders(
                explicitApiKey = "registration-public-key",
                explicitAuthorization = null,
                sessionBearer = "stale-session",
                fallbackApiKey = "sb_publishable_example",
            ),
        )
    }

    @Test
    fun opaquePublishableKeyDoesNotBecomeAnAuthorizationBearer() {
        assertEquals(
            SupabaseAuthHeaders("sb_publishable_example", null),
            resolveSupabaseAuthHeaders(null, null, null, "sb_publishable_example"),
        )
    }

    @Test
    fun explicitAndSessionBearersRemainAuthenticated() {
        assertEquals(
            SupabaseAuthHeaders("sb_publishable_example", "Bearer explicit-user"),
            resolveSupabaseAuthHeaders(
                explicitApiKey = null,
                explicitAuthorization = "Bearer explicit-user",
                sessionBearer = "different-session",
                fallbackApiKey = "sb_publishable_example",
            ),
        )
        assertEquals(
            SupabaseAuthHeaders("sb_publishable_example", "Bearer current-user"),
            resolveSupabaseAuthHeaders(null, null, "current-user", "sb_publishable_example"),
        )
    }

    @Test
    fun legacyAnonJwtKeepsBearerCompatibility() {
        assertEquals(
            SupabaseAuthHeaders("header.payload.signature", "Bearer header.payload.signature"),
            resolveSupabaseAuthHeaders(null, null, null, "header.payload.signature"),
        )
    }
}
