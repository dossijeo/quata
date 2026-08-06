package com.quata.feature.chat.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRpcPayloadEnvelopeTest {
    @Test
    fun mergesRootAndUpdateVariantsWithoutDuplicatingRecords() {
        val payload = Json.parseToJsonElement(
            """
            {
              "threads":[{"id":1}],
              "messages":[{"id":10}],
              "profiles":[{"id":"author"}],
              "update":{
                "thread":{"thread_id":1},
                "message":{"id":11},
                "profiles":[{"id":"author"},{"id":"reader"}]
              }
            }
            """.trimIndent(),
        )

        val envelope = parseChatRpcPayloadEnvelope(payload)

        assertEquals(listOf(1L), envelope.threads.map { (it["id"] ?: it["thread_id"])!!.jsonPrimitive.long })
        assertEquals(listOf(10L, 11L), envelope.messages.map { it["id"]!!.jsonPrimitive.long })
        assertEquals(listOf("author", "reader"), envelope.profiles.map { it["id"].toString().trim('"') })
    }

    @Test
    fun ignoresNonObjectPayloads() {
        val envelope = parseChatRpcPayloadEnvelope(Json.parseToJsonElement("[]"))

        assertEquals(emptyList(), envelope.threads)
        assertEquals(emptyList(), envelope.messages)
        assertEquals(emptyList(), envelope.profiles)
    }

    @Test
    fun acceptsTheEmptyInboxEnvelopeUsedByTheWebNotificationFixture() {
        val envelope = parseChatRpcPayloadEnvelope(
            Json.parseToJsonElement("""{"threads":[],"messages":[],"profiles":[]}"""),
        )

        assertEquals(emptyList(), envelope.threads)
        assertEquals(emptyList(), envelope.messages)
        assertEquals(emptyList(), envelope.profiles)
    }

    @Test
    fun parsesForwardRpcSentAndErrorsInsteadOfTreatingThePayloadAsUnit() {
        val result = parseChatForwardResult(
            """
            {
              "result": true,
              "sent": {"42": 1001, "43": 1002},
              "errors": ["not participant in thread 44"]
            }
            """.trimIndent(),
            requestedCount = 3,
        )

        assertEquals(3, result.requestedCount)
        assertEquals(2, result.sentCount)
        assertEquals(1, result.errorCount)
        assertEquals(false, result.isComplete)
    }
}
