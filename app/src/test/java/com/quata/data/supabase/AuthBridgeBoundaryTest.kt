package com.quata.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthBridgeBoundaryTest {
    @Test
    fun validLegacyBridgeResponseHasNoShadowIssues() {
        val issues = AuthBridgeResponseContract.loginIssues(validResponse())

        assertTrue(issues.isEmpty())
    }

    @Test
    fun verifierIsInertWhenShadowFlagIsDisabled() {
        val reported = mutableListOf<AuthBridgeContractIssue>()
        val malformed = validResponse().copy(
            profile = validResponse().profile.copy(id = ""),
            session = validResponse().session.copy(access_token = "")
        )

        AuthBridgeShadowVerifier(enabled = false, report = reported::add).observeLogin(malformed)

        assertTrue(reported.isEmpty())
    }

    @Test
    fun enabledVerifierReportsOnlyNonSensitiveContractCodes() {
        val reported = mutableListOf<AuthBridgeContractIssue>()
        val malformed = validResponse().copy(
            profile = validResponse().profile.copy(auth_user_id = "different-auth-user"),
            session = validResponse().session.copy(refresh_token = "", expires_at = 0)
        )

        AuthBridgeShadowVerifier(enabled = true, report = reported::add).observeLogin(malformed)

        assertEquals(
            setOf(
                AuthBridgeContractIssue.AuthUserIdMismatch,
                AuthBridgeContractIssue.MissingRefreshToken,
                AuthBridgeContractIssue.InvalidExpiry
            ),
            reported.toSet()
        )
    }

    private fun validResponse() = SupabaseAuthBridgeResponse(
        profile = SupabaseAuthBridgeProfile(id = "profile-1", auth_user_id = "auth-1"),
        session = SupabaseAuthSession(access_token = "access", refresh_token = "refresh", expires_at = 1_900_000_000L),
        user = SupabaseAuthUser(id = "auth-1")
    )
}
