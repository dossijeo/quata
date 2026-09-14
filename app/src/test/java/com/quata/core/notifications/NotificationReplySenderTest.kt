package com.quata.core.notifications

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReplySenderTest {
    @Test
    fun committedSendWithLostResponseRetriesWithoutCreatingAnotherMessage() = runBlocking {
        val serverMessages = mutableSetOf<String>()
        val attempts = mutableListOf<String>()
        var waits = 0
        val failures = mutableListOf<Int>()
        val sent = sendNotificationReply(
            send = { id ->
                attempts += id
                serverMessages += id
                if (attempts.size == 1) throw IOException("response lost after commit")
            },
            onFailure = { attempt, _ -> failures += attempt },
            waitBeforeRetry = { waits++ }
        )
        assertTrue(sent)
        assertEquals(2, attempts.size)
        assertEquals(attempts[0], attempts[1])
        assertEquals(1, serverMessages.size)
        assertEquals(listOf(1), failures)
        assertEquals(1, waits)
    }

    @Test
    fun exhaustedRetriesReportFailureAndWaitOnlyBetweenAttempts() = runBlocking {
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<Int>()
        var waits = 0
        val sent = sendNotificationReply(
            send = { id -> attempts += id; throw IOException("offline") },
            onFailure = { attempt, _ -> failures += attempt },
            waitBeforeRetry = { waits++ }
        )
        assertFalse(sent)
        assertEquals(3, attempts.size)
        assertEquals(1, attempts.toSet().size)
        assertEquals(listOf(1, 2, 3), failures)
        assertEquals(2, waits)
    }

    @Test
    fun separateRepliesHaveSeparateIdentitiesAndStopAfterSuccess() = runBlocking {
        val attempts = mutableListOf<String>()
        repeat(2) {
            assertTrue(sendNotificationReply(
                send = { attempts += it },
                onFailure = { _, _ -> error("unexpected failure") },
                waitBeforeRetry = { error("unexpected retry") }
            ))
        }
        assertEquals(2, attempts.size)
        assertNotEquals(attempts[0], attempts[1])
    }
}
