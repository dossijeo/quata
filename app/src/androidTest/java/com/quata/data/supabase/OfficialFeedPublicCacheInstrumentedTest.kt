package com.quata.data.supabase

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialFeedPublicCacheInstrumentedTest {
    @Test
    fun publicRpcStartsOfflineFromCacheAndRefreshesAfterOfficialPostsInvalidation() = runBlocking {
        val cache = SupabaseResponseCacheStore(ApplicationProvider.getApplicationContext())
        cache.clearAll()
        val requests = mutableListOf<Request>()
        val failNetwork = AtomicBoolean(false)
        val responseGeneration = AtomicInteger(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(requests) { requests += request }
            if (failNetwork.get()) error("network_must_not_run_for_fresh_offline_cache")
            val generation = responseGeneration.get()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""[{"id":"00000000-0000-4000-8000-00000000000$generation","created_at":"2026-10-02T00:00:0${generation}Z"}]""".toResponseBody())
                .build()
        }.build()
        val storage = InMemorySessionStorage().apply {
            saveSession(
                AuthSession(
                    token = "header.payload.signature",
                    userId = "actor",
                    email = "actor@example.invalid",
                    displayName = "Actor",
                    accessToken = "header.payload.signature",
                    refreshToken = "refresh-token",
                    expiresAt = Long.MAX_VALUE,
                ),
            )
        }
        val client = SupabaseHttpClient(
            config = SupabaseConfig(projectUrl = "https://example.test", anonKey = "public-key"),
            okHttp = http,
            cacheStore = cache,
            sessionManager = SessionManager(storage),
        )
        val api = SupabaseCommunityApi(client)

        try {
            assertEquals(
                "00000000-0000-4000-8000-000000000001",
                api.getOfficialFeedPage(limit = 50).single().id,
            )
            assertEquals(1, requests.size)

            failNetwork.set(true)
            assertEquals(
                "00000000-0000-4000-8000-000000000001",
                api.observeOfficialFeedPage(limit = 50).first().single().id,
            )
            assertEquals(1, requests.size)

            failNetwork.set(false)
            responseGeneration.set(2)
            val firstEmission = CompletableDeferred<Unit>()
            val observed = async {
                api.observeOfficialFeedPage(limit = 50)
                    .onEach { firstEmission.complete(Unit) }
                    .take(2)
                    .toList()
            }
            firstEmission.await()
            client.invalidateTables("official_posts")

            assertEquals(
                listOf(
                    "00000000-0000-4000-8000-000000000001",
                    "00000000-0000-4000-8000-000000000002",
                ),
                observed.await().map { page -> page.single().id },
            )
            assertEquals(2, requests.size)
            requests.forEach { request ->
                assertEquals("/rest/v1/rpc/quata_official_feed_page", request.url.encodedPath)
                assertNull(request.header("Authorization"))
                assertEquals("public-key", request.header("apikey"))
                assertTrue(request.header("x-quata-official-language").orEmpty().isNotBlank())
            }
        } finally {
            cache.clearAll()
        }
    }

    @Test
    fun invalidationDuringPublicRpcDiscardsTheOlderResponse() = runBlocking {
        val cache = SupabaseResponseCacheStore(ApplicationProvider.getApplicationContext())
        cache.clearAll()
        val firstRequestStarted = CountDownLatch(1)
        val releaseFirstResponse = CountDownLatch(1)
        val requestCount = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val current = requestCount.incrementAndGet()
            if (current == 1) {
                firstRequestStarted.countDown()
                check(releaseFirstResponse.await(10, TimeUnit.SECONDS)) { "stale_response_release_timed_out" }
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""[{"id":"00000000-0000-4000-8000-00000000000$current","created_at":"2026-10-02T00:00:0${current}Z"}]""".toResponseBody())
                .build()
        }.build()
        val client = SupabaseHttpClient(
            config = SupabaseConfig(projectUrl = "https://example.test", anonKey = "public-key"),
            okHttp = http,
            cacheStore = cache,
        )
        val api = SupabaseCommunityApi(client)

        try {
            val read = async(Dispatchers.IO) { api.getOfficialFeedPage(limit = 50) }
            assertTrue(firstRequestStarted.await(10, TimeUnit.SECONDS))
            client.invalidateTables("official_posts")
            releaseFirstResponse.countDown()

            assertEquals("00000000-0000-4000-8000-000000000002", read.await().single().id)
            assertEquals(2, requestCount.get())
            assertEquals(
                "00000000-0000-4000-8000-000000000002",
                api.observeOfficialFeedPage(limit = 50).first().single().id,
            )
            assertEquals(2, requestCount.get())
        } finally {
            releaseFirstResponse.countDown()
            cache.clearAll()
        }
    }
}

private class InMemorySessionStorage : SessionStorage {
    private var session: AuthSession? = null
    override fun saveSession(session: AuthSession) { this.session = session }
    override fun getSession(): AuthSession? = session
    override fun clear() { session = null }
}
