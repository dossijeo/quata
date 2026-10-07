package com.quata.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseLogoutScopeTest {
    private val configuration = SupabaseConfig(
        projectUrl = "https://example.supabase.co/",
        anonKey = "synthetic",
    )

    @Test
    fun `local logout URL uses explicit local scope`() {
        assertEquals(
            "https://example.supabase.co/auth/v1/logout?scope=local",
            configuration.authLogoutUrl,
        )
    }

    @Test
    fun `global logout uses the endpoint that retires every device`() {
        assertEquals(
            "https://example.supabase.co/functions/v1/quata-auth-global-logout",
            configuration.globalLogoutUrl,
        )
    }
}
