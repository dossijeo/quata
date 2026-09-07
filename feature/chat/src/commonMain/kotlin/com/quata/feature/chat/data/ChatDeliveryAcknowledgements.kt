package com.quata.feature.chat.data

import com.quata.core.model.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retries failed delivery receipts on subsequent refreshes, including empty delta responses. */
internal class ChatDeliveryAcknowledgements(
    private val currentActor: suspend () -> String?,
    private val send: suspend (String, List<Long>, String) -> Unit,
) {
    private class Receipts {
        val pending = linkedMapOf<Long, String>()
        val sent = mutableSetOf<Long>()
    }

    private val mutex = Mutex()
    private val receiptsByActor = mutableMapOf<String, Receipts>()

    suspend fun received(actor: String, messages: List<Message>, source: String) = mutex.withLock {
        val receipts = receiptsByActor.getOrPut(actor) { Receipts() }
        messages.forEach { message ->
            val id = message.id.toLongOrNull()?.takeIf { it > 0L } ?: return@forEach
            if (message.isMine || message.senderId == actor || message.isDeleted) return@forEach
            if (id !in receipts.sent && id !in receipts.pending) receipts.pending[id] = source
        }
        // A refresh can finish after logout/account switching. Never send its receipts
        // with the newly active account, and retain failures for the original account.
        for ((receiptSource, entries) in receipts.pending.entries.groupBy { it.value }) {
            val ids = entries.map { it.key }
            try {
                if (currentActor() != actor) return@withLock
                send(actor, ids, receiptSource)
                receipts.sent.addAll(ids)
                ids.forEach(receipts.pending::remove)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Receiving messages remains successful when the separate receipt fails.
                return@withLock
            }
        }
    }
}
