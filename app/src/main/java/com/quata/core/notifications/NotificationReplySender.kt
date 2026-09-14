package com.quata.core.notifications

import java.util.UUID
import kotlinx.coroutines.delay

/** One invocation represents one reply; retries retain its server deduplication key. */
internal suspend fun sendNotificationReply(
    send: suspend (clientMessageId: String) -> Unit,
    onFailure: (attempt: Int, failure: Throwable) -> Unit,
    waitBeforeRetry: suspend () -> Unit = { delay(2_000L) }
): Boolean {
    val clientMessageId = "notification-reply-${UUID.randomUUID()}"
    repeat(3) { attempt ->
        val result = runCatching { send(clientMessageId) }
        if (result.isSuccess) return true
        onFailure(attempt + 1, result.exceptionOrNull()!!)
        if (attempt < 2) waitBeforeRetry()
    }
    return false
}
