package com.quata.core.session

import com.quata.core.data.toFoundationData
import com.quata.core.model.AuthSession
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSString
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.valueForHTTPHeaderField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(BetaInteropApi::class)
class IosSupabaseAuthSessionRefresherTest {
    @Test
    fun refreshTokenJsonEscapingIteratesTheTokenInsteadOfTheGrowingBuilder() {
        assertEquals(
            "\"refresh\\\\token\\\"with\\ncontrols\"",
            "refresh\\token\"with\ncontrols".toIosJsonString(),
        )
    }

    @Test
    fun refreshUsesTheExactSupabaseHttpBoundaryWithoutExposingTheAccessToken() = runBlocking {
        var captured: NSURLRequest? = null
        val refresher = IosSupabaseAuthSessionRefresher(
            configuration = IosSupabaseAuthRuntimeConfiguration(
                supabaseUrl = "https://auth-boundary.invalid/",
                supabasePublishableKey = "sb_publishable_boundary",
            ),
            executeRequest = { request ->
                captured = request
                """{
                  "access_token":"new-access-token",
                  "refresh_token":"new-refresh-token",
                  "expires_at":4102444800
                }""".trimIndent().encodeToByteArray().toFoundationData()
            },
            testBoundary = Unit,
        )

        val refreshed = refresher.refresh(expiredSession())
        val request = assertNotNull(captured)
        val body = assertNotNull(request.HTTPBody)
            .let { NSString.create(data = it, encoding = NSUTF8StringEncoding) }
            .toString()

        assertEquals("POST", request.HTTPMethod)
        assertEquals(
            "https://auth-boundary.invalid/auth/v1/token?grant_type=refresh_token",
            request.URL?.absoluteString,
        )
        assertEquals("sb_publishable_boundary", request.valueForHTTPHeaderField("apikey"))
        assertEquals("Bearer sb_publishable_boundary", request.valueForHTTPHeaderField("Authorization"))
        assertEquals("application/json", request.valueForHTTPHeaderField("Accept"))
        assertEquals("application/json", request.valueForHTTPHeaderField("Content-Type"))
        assertEquals("{\"refresh_token\":\"owned-refresh-token\"}", body)
        assertFalse(body.contains("old-access-token"))
        assertEquals("new-access-token", refreshed?.accessToken)
        assertEquals("new-refresh-token", refreshed?.refreshToken)
    }

    private fun expiredSession() = AuthSession(
        token = "old-access-token",
        accessToken = "old-access-token",
        refreshToken = "owned-refresh-token",
        expiresAt = 1L,
        userId = "profile-boundary",
        authUserId = "auth-boundary",
        email = "boundary@example.invalid",
        displayName = "Boundary",
    )
}
