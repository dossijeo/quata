package com.quata.feature.chat.data

import com.quata.core.model.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDeliveryAcknowledgementsTest {
    @Test
    fun acknowledgesOnlyIncomingLiveIdsAndDeduplicatesAcrossRefreshes() = runTest {
        val calls = mutableListOf<List<Long>>()
        val acknowledgements = ChatDeliveryAcknowledgements({ "a" }) { actor, ids, source ->
            assertEquals("a", actor)
            assertEquals("inbox_refresh", source)
            calls += ids
        }
        val incoming = listOf(
            message("1"), message("1"), message("2").copy(isMine = true),
            message("3").copy(isDeleted = true), message("4").copy(senderId = "a"),
            message("0"), message("-1"), message("local-pending"),
        )
        acknowledgements.received("a", incoming, "inbox_refresh")
        acknowledgements.received("a", incoming, "thread_refresh")
        assertEquals(listOf(listOf(1L)), calls)
    }

    @Test
    fun failedReceiptsSurviveEmptyDeltaAndRemainBoundToTheirAccount() = runTest {
        var activeActor: String? = "a"
        var fail = true
        val calls = mutableListOf<Pair<String, List<Long>>>()
        val acknowledgements = ChatDeliveryAcknowledgements({ activeActor }) { actor, ids, _ ->
            calls += actor to ids
            if (fail) error("network lost")
        }
        acknowledgements.received("a", listOf(message("1")), "thread_refresh")
        activeActor = "b"
        fail = false
        acknowledgements.received("a", emptyList(), "inbox_refresh")
        acknowledgements.received("b", listOf(message("2")), "inbox_refresh")
        activeActor = null
        acknowledgements.received("a", emptyList(), "inbox_refresh")
        activeActor = "a"
        acknowledgements.received("a", emptyList(), "inbox_refresh")
        acknowledgements.received("a", emptyList(), "inbox_refresh")
        assertEquals(listOf("a" to listOf(1L), "b" to listOf(2L), "a" to listOf(1L)), calls)
    }

    @Test
    fun concurrentInboxAndThreadDoNotSendDuplicateReceipts() = runTest {
        val sending = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        var calls = 0
        val acknowledgements = ChatDeliveryAcknowledgements({ "a" }) { _, _, _ ->
            calls += 1
            sending.complete(Unit)
            response.await()
        }
        val inbox = async(start = CoroutineStart.UNDISPATCHED) {
            acknowledgements.received("a", listOf(message("1")), "inbox_refresh")
        }
        sending.await()
        val thread = async(start = CoroutineStart.UNDISPATCHED) {
            acknowledgements.received("a", listOf(message("1")), "thread_refresh")
        }
        response.complete(Unit)
        inbox.await()
        thread.await()
        assertEquals(1, calls)
    }

    private fun message(id: String) = Message(
        id = id, conversationId = "sb:7", senderId = "peer", senderName = "Peer",
        text = "Received", sentAt = "now",
    )
}
