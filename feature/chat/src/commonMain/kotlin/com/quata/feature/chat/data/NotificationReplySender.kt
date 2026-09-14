package com.quata.feature.chat.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class NotificationReplyOutcome { Sent, Failed, Rejected }

/** Background text replies use the same authenticated Chat RPC, without creating a UI repository. */
class NotificationReplySender(
    private val transport: ChatPostgrestTransport,
    private val authenticatedUser: ChatAuthenticatedUserProvider,
) {
    suspend fun send(
        conversationId: String,
        recipientProfileId: String,
        text: String,
        clientMessageId: String,
    ): NotificationReplyOutcome {
        val threadId = conversationId.removePrefix("sb:").toLongOrNull()
        if (!conversationId.startsWith("sb:") || threadId == null || threadId <= 0 ||
            recipientProfileId.isBlank() || text.isBlank() || clientMessageId.isBlank() ||
            clientMessageId.length > 128 || clientMessageId != clientMessageId.trim()
        ) return NotificationReplyOutcome.Rejected
        // Freeze the actor and idempotency key in the body, including across session refresh.
        val body = buildJsonObject {
            put("p_actor_profile_id", recipientProfileId)
            put("p_thread_id", threadId)
            put("p_message", text.trim())
            put("p_file_ids", JsonArray(emptyList()))
            put("p_reply_to_message_id", JsonNull)
            put("p_client_message_id", clientMessageId)
        }.toString()
        repeat(3) { attempt ->
            try {
                if (authenticatedUser.currentUserId() != recipientProfileId) {
                    return NotificationReplyOutcome.Rejected
                }
                when (val response = transport.post("quata_chat_send_message", body)) {
                    is ChatPostgrestResponse.Failure -> throw response.cause
                    is ChatPostgrestResponse.Success -> {
                        val result = Json.parseToJsonElement(response.body).jsonObject
                        if (result["result"]?.jsonPrimitive?.booleanOrNull == true &&
                            result["message_id"]?.jsonPrimitive?.longOrNull?.let { it > 0 } == true &&
                            result["thread_id"]?.jsonPrimitive?.longOrNull == threadId
                        ) return NotificationReplyOutcome.Sent
                    }
                }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                // Failures stay private; the OS host receives a bounded outcome, never raw RPC text.
            }
            if (attempt < 2) delay(2_000L)
        }
        return NotificationReplyOutcome.Failed
    }
}
