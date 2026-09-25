package com.quata.data.supabase

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRegistrationApiKeyTest {
    private val request = QuataRegistrationRequest(
        challenge_token = "turnstile-token",
        client_instance_id = "android-registration-client",
        idempotency_key = "0123456789abcdef0123456789abcdef",
        country_code = "34",
        phone_local = "600000000",
        password = "LongPassword7",
        display_name = "Test",
        neighborhood = "Centro",
        secret_question = "barrio",
        secret_answer = "answer",
    )

    @Test
    fun registrationUsesItsDedicatedPublicKeyWithoutChangingTheAuthBearer() = runBlocking {
        val requests = mutableListOf<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            chain.request().also(requests::add).let { captured ->
                Response.Builder().request(captured).protocol(Protocol.HTTP_1_1)
                    .code(202).message("Accepted")
                    .body("""{"version":1,"status":"accepted"}""".toResponseBody())
                    .build()
            }
        }.build()
        val api = SupabaseCommunityApi(
            client = SupabaseHttpClient(
                SupabaseConfig(projectUrl = "https://example.test", anonKey = "supabase-publishable"),
                okHttp = http,
            ),
            registrationApiKey = "registration-public-key",
        )

        api.requestRegistration(request)

        assertEquals(1, requests.size)
        assertEquals("registration-public-key", requests.single().header("apikey"))
        assertEquals("Bearer supabase-publishable", requests.single().header("Authorization"))
        assertEquals("/functions/v1/quata-register", requests.single().url.encodedPath)
    }

    @Test
    fun missingRegistrationKeyFailsBeforeAnyNetworkRequest() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls += 1
            error("network_must_not_run:${chain.request().url}")
        }.build()
        val api = SupabaseCommunityApi(
            client = SupabaseHttpClient(
                SupabaseConfig(projectUrl = "https://example.test", anonKey = "supabase-publishable"),
                okHttp = http,
            ),
            registrationApiKey = "",
        )

        val failure = runCatching { api.requestRegistration(request) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("registration_api_key_missing", failure?.message)
        assertEquals(0, calls)
    }
}
