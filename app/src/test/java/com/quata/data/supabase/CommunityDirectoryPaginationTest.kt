package com.quata.data.supabase

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityDirectoryPaginationTest {
    @Test
    fun completeDirectoryReadsEveryProfileAndWallBeyondTransportLimits() = runBlocking {
        val profileIds = (1..1_205).map { "profile-${it.toString().padStart(4, '0')}" }
        val wallIds = (1..601).map { "wall-${it.toString().padStart(4, '0')}" }
        val requests = mutableListOf<Request>()
        val api = api(profileIds, wallIds, requests)

        assertEquals(profileIds, api.getCompleteDirectoryProfiles().map(CommunityProfile::id))
        assertEquals(wallIds, api.getCompleteActiveWallsStats().map(CommunityWallStats::id))

        assertEquals(listOf(null, "gt.profile-0500", "gt.profile-1000"), requests
            .filter { it.url.encodedPath.endsWith("/community_profiles") }
            .map { it.url.queryParameter("id") })
        assertEquals(listOf(null, "gt.wall-0250", "gt.wall-0500"), requests
            .filter { it.url.encodedPath.endsWith("/community_walls_stats") }
            .map { it.url.queryParameter("id") })
        requests.forEach { request -> assertEquals("id.asc", request.url.queryParameter("order")) }
    }

    private fun api(
        profileIds: List<String>,
        wallIds: List<String>,
        requests: MutableList<Request>,
    ): SupabaseCommunityApi {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val source = when {
                request.url.encodedPath.endsWith("/community_profiles") -> profileIds
                request.url.encodedPath.endsWith("/community_walls_stats") -> wallIds
                else -> error("unexpected_directory_path_${request.url.encodedPath}")
            }
            val after = request.url.queryParameter("id")?.removePrefix("gt.")
            val limit = request.url.queryParameter("limit")?.toInt() ?: error("directory_limit_missing")
            val page = source.asSequence()
                .filter { after == null || it > after }
                .take(limit)
                .joinToString(prefix = "[", postfix = "]") { id -> "{\"id\":\"$id\"}" }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(page.toResponseBody())
                .build()
        }.build()
        return SupabaseCommunityApi(
            SupabaseHttpClient(
                SupabaseConfig(projectUrl = "https://example.test", anonKey = "synthetic"),
                okHttp = http,
            ),
        )
    }
}
