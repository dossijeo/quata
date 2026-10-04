package com.quata.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleOAuthContractTest {
    @Test
    fun `authorization request uses PKCE S256`() {
        val request = buildGoogleOAuthRequest(
            supabaseUrl = "https://project.supabase.co/",
            redirectUri = "quata://oauth/callback",
            randomBytes = { size -> ByteArray(size) { it.toByte() } },
        )

        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0-Pw",
            request.codeVerifier,
        )
        assertTrue(request.authorizationUrl.startsWith("https://project.supabase.co/auth/v1/authorize?"))
        assertTrue(request.authorizationUrl.contains("provider=google"))
        assertTrue(request.authorizationUrl.contains("code_challenge=wsNdZaf3VpLTsEDmR5gPk2C6xYVWxKb0xcaG3O6kX10"))
        assertTrue(request.authorizationUrl.contains("code_challenge_method=s256"))
        assertTrue(request.authorizationUrl.contains("redirect_to=quata%3A%2F%2Foauth%2Fcallback"))
    }

    @Test
    fun `callback requires exact redirect and one authorization code`() {
        val request = GoogleOAuthRequest("unused", "quata://oauth/callback", "verifier")
        assertEquals(
            "authorization-code",
            parseGoogleOAuthCallback(
                "quata://oauth/callback?code=authorization-code",
                request,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            parseGoogleOAuthCallback("quata://other/callback?code=stolen", request)
        }
        assertFailsWith<IllegalStateException> {
            parseGoogleOAuthCallback("quata://oauth/callback?code=first&code=second", request)
        }
        val denied = assertFailsWith<IllegalStateException> {
            parseGoogleOAuthCallback("quata://oauth/callback#error=access_denied&error_description=Cancelled", request)
        }
        assertEquals("google_oauth_access_denied", denied.message)
    }

    @Test
    fun `identity link request uses the authenticated Supabase endpoint`() {
        val request = buildGoogleIdentityLinkRequest(
            supabaseUrl = "https://project.supabase.co",
            redirectUri = "quata://oauth/callback",
            randomBytes = { size -> ByteArray(size) { it.toByte() } },
        )

        assertTrue(request.authorizationUrl.startsWith("https://project.supabase.co/auth/v1/user/identities/authorize?"))
        assertTrue(request.authorizationUrl.contains("provider=google"))
        assertTrue(request.authorizationUrl.contains("code_challenge_method=s256"))
        assertTrue(request.authorizationUrl.endsWith("&skip_http_redirect=true"))
    }

    @Test
    fun `federated profile must match authenticated user`() {
        val tokens = parseGoogleOAuthTokenSet(
            """{"access_token":"access","refresh_token":"refresh","expires_at":2000000000}""",
        )
        val accepted = tokens.acceptFederatedProfile(
            """{"profile":{"id":"profile-1","auth_user_id":"auth-1","display_name":"Gabriel","is_official":true},"user":{"id":"auth-1","email":"g@example.invalid"},"web_session":{"token":"web-token"}}""",
        )
        assertEquals("profile-1", accepted.session.userId)
        assertEquals("auth-1", accepted.session.authUserId)
        assertTrue(accepted.session.isOfficial)
        assertEquals("web-token", accepted.webSessionToken)
        assertFailsWith<IllegalArgumentException> {
            tokens.acceptFederatedProfile(
                """{"profile":{"id":"profile-1","auth_user_id":"auth-2"},"user":{"id":"auth-1"}}""",
            )
        }
    }

    @Test
    fun `linked identity can only refresh the same auth user`() {
        val current = com.quata.core.model.AuthSession(
            token = "old-access",
            userId = "profile-1",
            authUserId = "auth-1",
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAt = 1,
            email = "existing@example.invalid",
            displayName = "Existing",
        )
        val linked = current.acceptLinkedGoogleSession(
            """{"access_token":"new-access","refresh_token":"new-refresh","expires_at":2000000000,"user":{"id":"auth-1"}}""",
        )
        assertEquals("profile-1", linked.userId)
        assertEquals("new-access", linked.accessToken)
        assertFailsWith<IllegalArgumentException> {
            current.acceptLinkedGoogleSession(
                """{"access_token":"attacker","refresh_token":"refresh","expires_at":2000000000,"user":{"id":"auth-2"}}""",
            )
        }
    }
}
