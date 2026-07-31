package com.quata.web

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebProfileGatewayContractTest {
    @Test
    fun own_profile_patch_uses_the_direct_android_compatible_contract() = runTest {
        val transport = RecordingTransport()
        val gateway = WebProfileRemoteGateway(transport)

        gateway.saveProfile("profile-1", mapOf("display_name" to "Ada", "forbidden" to "drop"))

        assertEquals(listOf("PATCH community_profiles"), transport.operations)
        assertEquals(mapOf("id" to "eq.profile-1"), transport.lastQuery)
        val body = Json.parseToJsonElement(transport.lastBody!!).jsonObject
        assertEquals("Ada", body.getValue("display_name").jsonPrimitive.content)
        assertTrue("forbidden" !in body)
    }

    @Test
    fun sos_replace_sends_delete_then_ordered_post_rows() = runTest {
        val transport = RecordingTransport()
        val gateway = WebProfileRemoteGateway(transport)

        gateway.saveEmergencyContacts("profile-1", listOf("peer-b", "peer-a", "peer-b"))

        assertEquals(listOf("DELETE community_emergency_contacts", "POST community_emergency_contacts"), transport.operations)
        val rows = Json.parseToJsonElement(transport.lastBody!!).jsonArray
        assertEquals(listOf("peer-b", "peer-a"), rows.map { it.jsonObject.getValue("contact_profile_id").jsonPrimitive.content })
        assertEquals(listOf("1", "2"), rows.map { it.jsonObject.getValue("position").jsonPrimitive.content })
    }

    @Test
    fun missing_session_fails_before_any_mutation() = runTest {
        val transport = RecordingTransport(sessionProfileId = null)
        val gateway = WebProfileRemoteGateway(transport)

        assertFailsWith<IllegalStateException> {
            gateway.saveProfile("profile-1", mapOf("display_name" to "Ada"))
        }
        assertTrue(transport.operations.isEmpty())
    }

    private class RecordingTransport(
        private val sessionProfileId: String? = "profile-1",
    ) : WebProfileTransport {
        val operations = mutableListOf<String>()
        var lastQuery: Map<String, String>? = null
        var lastBody: String? = null
        override suspend fun get(table: String, query: Map<String, String>, limit: Int) =
            WebPostgrestResult.Success("[]", 200)
        override suspend fun patch(table: String, query: Map<String, String>, body: String) = success("PATCH", table, query, body)
        override suspend fun post(table: String, body: String) = success("POST", table, emptyMap(), body)
        override suspend fun delete(table: String, query: Map<String, String>) = success("DELETE", table, query, null)
        override suspend fun updateRecoverySecret(question: String, answer: String) = Result.success(Unit)
        override suspend fun sessionProfileId(): String? = sessionProfileId
        private fun success(method: String, table: String, query: Map<String, String>, body: String?): WebPostgrestResult {
            operations += "$method $table"
            lastQuery = query
            if (body != null) lastBody = body
            return WebPostgrestResult.Success("[]", 200)
        }
    }
}
