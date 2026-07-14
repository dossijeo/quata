package com.quata.feature.chat.data

import com.quata.core.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatMessageReconcilerTest {

    @Test
    fun incomingUpdatePreservesCachedReplySnapshotWhenTargetIsOutsidePage() {
        val cached = message("20", text = "respuesta", replyToId = "1").copy(
            replyToSenderName = "Ana",
            replyToText = "mensaje antiguo"
        )
        val incoming = cached.copy(replyToSenderName = null, replyToText = null, isEdited = true)

        val result = reconcileChatMessages(listOf(incoming), listOf(cached), retainUnmatchedExisting = false).single()

        assertEquals("Ana", result.replyToSenderName)
        assertEquals("mensaje antiguo", result.replyToText)
        assertEquals(true, result.isEdited)
    }

    @Test
    fun replyContextIsResolvedFromCombinedCachedAndIncomingMessages() {
        val target = message("1", sender = "Bruno", text = "texto citado")
        val reply = message("20", text = "respuesta", replyToId = "1")

        val result = reconcileChatMessages(listOf(reply), listOf(target))

        val restoredReply = result.first { it.id == "20" }
        assertEquals("Bruno", restoredReply.replyToSenderName)
        assertEquals("texto citado", restoredReply.replyToText)
    }

    @Test
    fun fullThreadRefreshRetainsOlderHistoryButAuthoritativeListsCanDropIt() {
        val old = message("1", sentAtMillis = 1L)
        val latest = message("2", sentAtMillis = 2L)

        assertEquals(listOf("1", "2"), reconcileChatMessages(listOf(latest), listOf(old)).map { it.id })
        assertFalse(
            reconcileChatMessages(listOf(latest), listOf(old), retainUnmatchedExisting = false)
                .any { it.id == old.id }
        )
    }

    private fun message(
        id: String,
        sender: String = "Usuario",
        text: String = "mensaje",
        replyToId: String? = null,
        sentAtMillis: Long = id.toLong()
    ) = Message(
        id = id,
        conversationId = "sb:7",
        senderId = "profile-$sender",
        senderName = sender,
        text = text,
        sentAt = "2026-07-14T10:00:00Z",
        sentAtMillis = sentAtMillis,
        replyToMessageId = replyToId
    )
}
