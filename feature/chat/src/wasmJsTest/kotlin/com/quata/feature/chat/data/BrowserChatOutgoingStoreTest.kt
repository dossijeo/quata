package com.quata.feature.chat.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserChatOutgoingStoreTest {
    @Test
    fun webLockSerializesTheCompleteMessageOperationAcrossInstances() = runTest {
        val actor = "browser-lock-${Random.nextLong().toString(16)}"
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = BrowserChatOutgoingExecutionLock()
        val second = BrowserChatOutgoingExecutionLock()

        val firstJob = async {
            first.withLock(actor, "client") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()
        val secondJob = async {
            second.withLock(actor, "client") { secondStarted.complete(Unit) }
        }
        yield()
        assertFalse(secondStarted.isCompleted)
        releaseFirst.complete(Unit)
        firstJob.await()
        secondJob.await()
        assertTrue(secondStarted.isCompleted)
    }

    @Test
    fun independentInstancesMergeRecordsAndOnlyOneClaimsAMessage() = runTest {
        val actor = "browser-test-${Random.nextLong().toString(16)}"
        val first = BrowserChatOutgoingStore()
        val second = BrowserChatOutgoingStore()
        val one = StoredChatOutgoing(actor, "sb:7", "one", clientMessageId = "one", createdAtMillis = 1L)
        val two = StoredChatOutgoing(actor, "sb:7", "two", clientMessageId = "two", createdAtMillis = 2L)
        try {
            assertEquals(listOf(true, true), listOf(async { first.insert(one) }, async { second.insert(two) }).awaitAll())
            assertEquals(setOf("one", "two"), first.load(actor).map(StoredChatOutgoing::clientMessageId).toSet())

            val claims = listOf(
                async { first.claim(actor, "one", "lease-a", 10L, 100L) },
                async { second.claim(actor, "one", "lease-b", 10L, 100L) },
            ).awaitAll()
            assertEquals(1, claims.count { it != null })
            val owner = claims.single { it != null }!!
            val staleToken = if (owner.leaseToken == "lease-a") "lease-b" else "lease-a"
            assertEquals(false, second.updateClaimed(owner.copy(text = "stale"), staleToken))
            assertEquals(false, second.removeClaimed(actor, "one", staleToken))
            val next = second.claim(actor, "one", "lease-next", 100L, 200L)!!
            assertEquals(false, first.updateClaimed(owner.copy(text = "expired-owner"), owner.leaseToken!!))
            assertEquals(true, second.updateClaimed(next.copy(text = "owned"), "lease-next"))
            assertEquals("owned", first.load(actor).first { it.clientMessageId == "one" }.text)
        } finally {
            listOf("one", "two").forEach { id ->
                val token = "cleanup-$id"
                first.claim(actor, id, token, 1_000L, 2_000L)
                first.removeClaimed(actor, id, token)
            }
        }
    }
}
