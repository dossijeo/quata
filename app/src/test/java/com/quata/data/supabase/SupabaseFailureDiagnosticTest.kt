package com.quata.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseFailureDiagnosticTest {
    @Test
    fun keepsOnlyBoundedServerErrorCodes() {
        assertEquals("invalid_password", safeSupabaseErrorCode("""{"error":"invalid_password"}"""))
        assertEquals("unclassified", safeSupabaseErrorCode("""{"message":"private detail"}"""))
        assertEquals("unclassified", safeSupabaseErrorCode("""{"error":"contains spaces"}"""))
        assertEquals("unclassified", safeSupabaseErrorCode("""{"error":"${"a".repeat(65)}"}"""))
    }
}
