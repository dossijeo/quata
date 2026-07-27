package com.quata.data.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseAuthBridgeBoundaryInstrumentedTest {
    @Test
    fun recoveryRequestCarriesOnlyBridgeInputs() {
        val payload = Json.encodeToString(
            SupabaseAuthBridgeRequest(
                action = "recovery_question",
                country_code = "34",
                phone = "600000000"
            )
        )

        assertTrue(payload.contains("\"action\":\"recovery_question\""))
        assertFalse(payload.contains("pass_plain"))
        assertFalse(payload.contains("pass_hash"))
    }
}
