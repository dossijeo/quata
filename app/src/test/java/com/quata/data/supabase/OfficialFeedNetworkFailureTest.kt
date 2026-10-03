package com.quata.data.supabase

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
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

class OfficialFeedNetworkFailureTest {
    @Test
    fun olderPageNetworkFailurePropagatesAndExplicitRetryUsesTheSameCursor() = runBlocking {
        val requests = mutableListOf<Request>()
        val requestCount = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(requests) { requests += request }
            if (requestCount.incrementAndGet() == 1) throw IOException("synthetic_official_network_failure")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("[]".toResponseBody())
                .build()
        }.build()
        val api = SupabaseCommunityApi(
            SupabaseHttpClient(
                config = SupabaseConfig(projectUrl = "https://example.test", anonKey = "public-key"),
                okHttp = http,
            ),
        )
        val cursorSortAt = "2026-10-02T09:00:00Z"
        val cursorCreatedAt = "2026-10-02T08:00:00Z"
        val cursorId = "00000000-0000-4000-8000-000000000105"

        val first = runCatching {
            api.getOfficialFeedPage(
                limit = 25,
                beforeSortAt = cursorSortAt,
                beforeCreatedAt = cursorCreatedAt,
                beforeId = cursorId,
                cacheMode = SupabaseCacheMode.NETWORK_ONLY,
            )
        }
        assertTrue(first.exceptionOrNull() is IOException)

        assertTrue(
            api.getOfficialFeedPage(
                limit = 25,
                beforeSortAt = cursorSortAt,
                beforeCreatedAt = cursorCreatedAt,
                beforeId = cursorId,
                cacheMode = SupabaseCacheMode.NETWORK_ONLY,
            ).isEmpty(),
        )

        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("/rest/v1/rpc/quata_official_feed_page", request.url.encodedPath)
            assertEquals("25", request.url.queryParameter("p_limit"))
            assertEquals(cursorSortAt, request.url.queryParameter("p_before_sort_at"))
            assertEquals(cursorCreatedAt, request.url.queryParameter("p_before_created_at"))
            assertEquals(cursorId, request.url.queryParameter("p_before_id"))
            assertNull(request.header("Authorization"))
        }
    }
}
