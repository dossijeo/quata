package com.quata.data.supabase

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSignedObjectUrlTest {
    @Test
    fun signingUsesAuthenticatedExactObjectEndpointAndFiveMinuteLifetime() = runBlocking {
        val requests = mutableListOf<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            chain.request().also(requests::add).let { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"signedURL":"/storage/v1/object/sign/chat-attachments/actor/message/file.pdf?token=header.payload.signature"}"""
                            .toResponseBody(),
                    )
                    .build()
            }
        }.build()
        val session = AuthSession(
            token = "access-token",
            userId = "actor",
            email = "actor@example.test",
            displayName = "Actor",
            authUserId = "actor",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Long.MAX_VALUE,
        )
        val client = SupabaseHttpClient(
            config = SupabaseConfig(
                projectUrl = "https://project-ref.supabase.co",
                anonKey = "publishable-key",
            ),
            okHttp = http,
            sessionManager = SessionManager(InMemorySessionStorage(session)),
        )

        val signed = client.createSignedObjectUrl(
            bucket = "chat-attachments",
            path = "actor/message/file.pdf",
            expiresInSeconds = 300,
        )

        assertEquals(
            "https://project-ref.supabase.co/storage/v1/object/sign/chat-attachments/actor/message/file.pdf?token=header.payload.signature",
            signed,
        )
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "/storage/v1/object/sign/chat-attachments/actor/message/file.pdf",
            request.url.encodedPath,
        )
        assertEquals("publishable-key", request.header("apikey"))
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals("""{"expiresIn":300}""", request.body.readUtf8())
    }

    @Test
    fun missingObjectPathFailsBeforeNetwork() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls += 1
            error("network_must_not_run:${chain.request().url}")
        }.build()
        val client = SupabaseHttpClient(
            config = SupabaseConfig("https://project-ref.supabase.co", "publishable-key"),
            okHttp = http,
        )

        val error = runCatching {
            client.createSignedObjectUrl("", "chat-attachments", 300)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("storage_sign_path_missing", error?.message)
        assertEquals(0, calls)
    }

    private class InMemorySessionStorage(initial: AuthSession?) : SessionStorage {
        private var session = initial

        override fun saveSession(session: AuthSession) {
            this.session = session
        }

        override fun getSession(): AuthSession? = session

        override fun clear() {
            session = null
        }
    }
}

private fun okhttp3.RequestBody?.readUtf8(): String {
    val buffer = okio.Buffer()
    this?.writeTo(buffer)
    return buffer.readUtf8()
}
