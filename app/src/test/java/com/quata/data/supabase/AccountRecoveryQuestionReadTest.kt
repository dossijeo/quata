package com.quata.data.supabase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRecoveryQuestionReadTest {
    @Test
    fun accountReadsQuestionWithoutAnswerWhileDirectoryKeepsPublicProjection() = runBlocking {
        val requests = mutableListOf<Request>()
        val profileId = "11111111-1111-4111-8111-111111111111"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val fields = request.url.queryParameter("select").orEmpty().split(',')
            val body = if ("secret_question" in fields) {
                """[{"id":"$profileId","secret_question":"madre"}]"""
            } else {
                """[{"id":"$profileId"}]"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val api = SupabaseCommunityApi(SupabaseHttpClient(
            SupabaseConfig(projectUrl = "https://example.test", anonKey = "synthetic"),
            okHttp = http
        ))

        assertEquals("madre", api.getAccountProfile(profileId)?.secret_question)
        assertEquals("madre", api.observeAccountProfile(profileId).first()?.secret_question)
        assertNull(api.getProfiles(listOf(profileId)).single().secret_question)
        assertNull(api.observeProfiles(listOf(profileId)).first().single().secret_question)
        assertEquals(4, requests.size)
        requests.forEachIndexed { index, request ->
            assertEquals("/rest/v1/community_profiles", request.url.encodedPath)
            val fields = request.url.queryParameter("select").orEmpty().split(',')
            assertFalse("secret_answer" in fields)
            assertFalse("*" in fields)
            assertTrue("display_name" in fields)
            assertEquals(index < 2, "secret_question" in fields)
            assertEquals(if (index < 2) "eq.$profileId" else "in.($profileId)", request.url.queryParameter("id"))
            assertEquals(if (index < 2) "1" else "500", request.url.queryParameter("limit"))
        }
    }
}
