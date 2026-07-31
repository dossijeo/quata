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
    fun sos_replace_serializes_exact_valid_json_for_zero_one_two_and_five_rows() = runTest {
        listOf(0, 1, 2, 5).forEach { count ->
            val transport = RecordingTransport()
            val gateway = WebProfileRemoteGateway(transport)
            val ids = (1..count).map { "peer-$it" }

            gateway.saveEmergencyContacts("profile-1", ids)

            val expectedOperations = listOf("DELETE community_emergency_contacts") +
                if (count == 0) emptyList() else listOf("POST community_emergency_contacts")
            assertEquals(expectedOperations, transport.operations, "row count $count")
            if (count == 0) {
                assertEquals(null, transport.lastBody)
            } else {
                val expected = ids.mapIndexed { index, id ->
                    "{\"profile_id\":\"profile-1\",\"contact_profile_id\":\"$id\",\"position\":${index + 1}}"
                }.joinToString(separator = ",", prefix = "[", postfix = "]")
                assertEquals(expected, transport.lastBody, "row count $count")
                val rows = Json.parseToJsonElement(requireNotNull(transport.lastBody)).jsonArray
                assertEquals(ids, rows.map { it.jsonObject.getValue("contact_profile_id").jsonPrimitive.content })
                assertEquals((1..count).map(Int::toString), rows.map { it.jsonObject.getValue("position").jsonPrimitive.content })
            }
        }
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
