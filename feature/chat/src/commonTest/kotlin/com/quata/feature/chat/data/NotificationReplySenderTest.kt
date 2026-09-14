package com.quata.feature.chat.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotificationReplySenderTest {
    private fun transport(block: suspend (String, String) -> ChatPostgrestResponse) =
        object : ChatPostgrestTransport {
            override suspend fun post(functionName: String, body: String) = block(functionName, body)
        }

    private val success = ChatPostgrestResponse.Success("""{"result":true,"message_id":12,"thread_id":7}""")

    @Test fun lostResponseRetainsActorTextAndIdempotencyKey() = runTest {
        val bodies = mutableListOf<String>()
        val sender = NotificationReplySender(transport { name, body ->
            assertEquals("quata_chat_send_message", name)
            bodies += body
            if (bodies.size == 1) ChatPostgrestResponse.Failure(Exception("response lost")) else success
        }, ChatAuthenticatedUserProvider { "actor-a" })
        assertEquals(NotificationReplyOutcome.Sent, sender.send("sb:7", "actor-a", " hello ", "reply-1"))
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        val body = Json.parseToJsonElement(bodies[0]).jsonObject
        assertEquals("actor-a", body["p_actor_profile_id"]?.jsonPrimitive?.content)
        assertEquals("hello", body["p_message"]?.jsonPrimitive?.content)
        assertEquals("reply-1", body["p_client_message_id"]?.jsonPrimitive?.content)
    }

    @Test fun actorSwitchStopsRetry() = runTest {
        var actor = "actor-a"
        var calls = 0
        val sender = NotificationReplySender(transport { _, _ ->
            calls++; actor = "actor-b"
            ChatPostgrestResponse.Failure(Exception("lost"))
        }, ChatAuthenticatedUserProvider { actor })
        assertEquals(NotificationReplyOutcome.Rejected, sender.send("sb:7", "actor-a", "text", "reply-1"))
        assertEquals(1, calls)
    }

    @Test fun unboundOrMalformedRepliesNeverReachTransport() = runTest {
        val sender = NotificationReplySender(transport { _, _ -> error("unexpected send") },
            ChatAuthenticatedUserProvider { "actor-a" })
        for ((conversation, actor, text, id) in listOf(
            listOf("sb:7", "", "text", "reply-1"),
            listOf("sb:7", "actor-b", "text", "reply-1"),
            listOf("7", "actor-a", "text", "reply-1"),
            listOf("sb:7", "actor-a", " ", "reply-1"),
            listOf("sb:7", "actor-a", "text", "x".repeat(129))
        )) assertEquals(NotificationReplyOutcome.Rejected, sender.send(conversation, actor, text, id))
    }

    @Test fun httpSuccessWithoutMatchingCommittedMessageIsNotSuccess() = runTest {
        for (body in listOf("{}", """{"result":false}""", """{"result":true,"message_id":12,"thread_id":8}""")) {
            var calls = 0
            val sender = NotificationReplySender(transport { _, _ ->
                calls++; ChatPostgrestResponse.Success(body)
            }, ChatAuthenticatedUserProvider { "actor-a" })
            assertEquals(NotificationReplyOutcome.Failed, sender.send("sb:7", "actor-a", "text", "reply-1"))
            assertEquals(3, calls)
        }
    }

    @Test fun cancellationIsNotRetried() = runTest {
        var calls = 0
        val sender = NotificationReplySender(transport { _, _ ->
            calls++; throw CancellationException()
        }, ChatAuthenticatedUserProvider { "actor-a" })
        assertFailsWith<CancellationException> { sender.send("sb:7", "actor-a", "text", "reply-1") }
        assertEquals(1, calls)
    }

    @Test fun requestTimeoutRetriesButOuterDeadlineCancels() = runTest {
        var calls = 0
        val sender = NotificationReplySender(transport { _, _ ->
            calls++
            withTimeout(5) { delay(10); success }
        }, ChatAuthenticatedUserProvider { "actor-a" })
        assertEquals(NotificationReplyOutcome.Failed, sender.send("sb:7", "actor-a", "text", "reply-1"))
        assertEquals(3, calls)
        calls = 0
        assertFailsWith<CancellationException> {
            withTimeout(3) { sender.send("sb:7", "actor-a", "text", "reply-2") }
        }
        assertEquals(1, calls)
    }
}
