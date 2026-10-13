package com.quata.data.supabase

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAuthRefreshHttpBoundaryTest {
    @Test
    fun androidRefreshUsesTheExactSupabaseHttpBoundaryWithoutExposingCredentials() = runBlocking {
        val storage = InMemorySessionStorage(expiredSession())
        val manager = SessionManager(storage)
        var observed: Request? = null
        val transport = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                observed = request
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{
                          "access_token":"new-access-token",
                          "refresh_token":"new-refresh-token",
                          "expires_at":4102444800
                        }""".trimIndent().toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            })
            .build()
        val client = SupabaseHttpClient(
            config = SupabaseConfig(
                projectUrl = "https://auth-boundary.invalid",
                anonKey = "sb_publishable_boundary",
            ),
            okHttp = transport,
            sessionManager = manager,
        )

        val refreshed = client.ensureFreshSession(force = true)
        val request = requireNotNull(observed)
        val body = request.body!!.let { requestBody ->
            okio.Buffer().use { buffer -> requestBody.writeTo(buffer); buffer.readUtf8() }
        }

        assertEquals("POST", request.method)
        assertEquals("https", request.url.scheme)
        assertEquals("auth-boundary.invalid", request.url.host)
        assertEquals("/auth/v1/token", request.url.encodedPath)
        assertEquals("refresh_token", request.url.queryParameter("grant_type"))
        assertEquals("sb_publishable_boundary", request.header("apikey"))
        assertEquals("Bearer sb_publishable_boundary", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("application/json", request.header("Content-Type"))
        assertEquals("{\"refresh_token\":\"owned-refresh-token\"}", body)
        assertFalse(body.contains("old-access-token"))
        assertEquals("new-access-token", refreshed?.accessToken)
        assertEquals("new-refresh-token", refreshed?.refreshToken)
        assertSame(refreshed, manager.currentSession())
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

    private class InMemorySessionStorage(initial: AuthSession) : SessionStorage {
        private var value: AuthSession? = initial

        override fun saveSession(session: AuthSession) {
            value = session
        }

        override fun getSession(): AuthSession? = value

        override fun clear() {
            value = null
        }
    }
}
