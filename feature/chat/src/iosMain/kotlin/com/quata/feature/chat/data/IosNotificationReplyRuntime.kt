package com.quata.feature.chat.data

import com.quata.core.platform.IosPreferenceStore
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.time

/** Uses the app's renewable session and existing URLSession adapter; owns no credentials. */
class IosNotificationReplyRuntime internal constructor(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    private val chatRepository: ChatRepository,
    private val outgoingStore: ChatOutgoingStore = PreferenceChatOutgoingStore(IosPreferenceStore()),
    private val actorProvider: suspend () -> String? = {
        IosChatAuthenticatedUserProvider(authSession).currentUserId()
    },
    private val directReply: (suspend (String, String, String, String) -> NotificationReplyOutcome)? = null,
    private val scope: CoroutineScope = MainScope(),
) {
    private var acceptingReplies = true
    private var pendingReplyDelivered: ((String) -> Unit)? = null
    private val watchedPendingReplyIds = mutableSetOf<String>()
    private val scheduledRecoveryFlushActors = mutableSetOf<String>()

    fun sessionEnded() {
        acceptingReplies = false
        scope.coroutineContext.cancelChildren()
    }

    fun resumeAfterSessionValidation() {
        acceptingReplies = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) { recoverPendingReplies() }
    }

    fun setPendingReplyDeliveredHandler(handler: (String) -> Unit) {
        pendingReplyDelivered = handler
    }

    fun send(
        conversationId: String,
        recipientProfileId: String,
        text: String,
        clientMessageId: String,
        completion: (NotificationReplyOutcome) -> Unit,
    ) {
        if (!acceptingReplies) { completion(NotificationReplyOutcome.Rejected); return }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var outcome = NotificationReplyOutcome.Rejected
            try {
                outcome = withTimeoutOrNull(20_000L) {
                    sendDurably(conversationId, recipientProfileId, text, clientMessageId)
                } ?: queuedOutcomeAfterTimeout(recipientProfileId, clientMessageId)
            } catch (_: CancellationException) {
                // Logout/account replacement abandons the old reply and suppresses its OS feedback.
            } finally {
                completion(outcome)
            }
        }
    }

    private suspend fun sendDurably(
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
        if (actorProvider() != recipientProfileId) return NotificationReplyOutcome.Rejected

        val pending = StoredChatOutgoing(
            actorId = recipientProfileId,
            conversationId = conversationId,
            text = text.trim(),
            clientMessageId = clientMessageId,
            createdAtMillis = nowMillis(),
        )
        val inserted = outgoingStore.insert(pending)
        if (!inserted) {
            val existing = outgoingStore.load(recipientProfileId)
                .firstOrNull { it.clientMessageId == clientMessageId }
                ?: return NotificationReplyOutcome.Rejected
            if (existing.conversationId != conversationId || existing.text != text.trim()) {
                return NotificationReplyOutcome.Rejected
            }
            watchPendingReply(recipientProfileId, clientMessageId)
            return NotificationReplyOutcome.Queued
        }

        val leaseToken = "notification-reply-lease-$clientMessageId"
        val claimed = outgoingStore.claim(
            actorId = recipientProfileId,
            clientMessageId = clientMessageId,
            leaseToken = leaseToken,
            nowMillis = nowMillis(),
            leaseUntilMillis = nowMillis() + ReplyLeaseMillis,
        ) ?: run {
            watchPendingReply(recipientProfileId, clientMessageId)
            return NotificationReplyOutcome.Queued
        }
        val directOutcome = try {
            directReply?.invoke(conversationId, recipientProfileId, text, clientMessageId)
                ?: NotificationReplySender(
                IosChatPostgrestTransport(
                    configuration,
                    authSession,
                    requestTimeoutMillis = 5_000L,
                    expectedProfileId = recipientProfileId,
                ),
                IosChatAuthenticatedUserProvider(authSession),
            ).send(conversationId, recipientProfileId, text, clientMessageId)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                releaseClaim(claimed, leaseToken, "notification_reply_transport_cancelled")
            }
            throw cancelled
        }
        return when (directOutcome) {
            NotificationReplyOutcome.Sent -> {
                val delivered = claimed.copy(
                    lastError = null,
                    deliveredAwaitingCleanup = true,
                )
                if (outgoingStore.updateClaimed(delivered, leaseToken)) {
                    outgoingStore.removeClaimed(recipientProfileId, clientMessageId, leaseToken)
                }
                NotificationReplyOutcome.Sent
            }
            NotificationReplyOutcome.Rejected,
            NotificationReplyOutcome.Failed,
            NotificationReplyOutcome.Queued -> {
                // Inputs were validated before persistence. A later rejection can therefore be an
                // actor/session replacement and must not discard the previous actor's durable copy.
                val error = if (directOutcome == NotificationReplyOutcome.Rejected) {
                    "notification_reply_actor_or_session_changed"
                } else {
                    "notification_reply_transport_failed"
                }
                val released = releaseClaim(claimed, leaseToken, error)
                val remainsDurable = released || outgoingStore.load(recipientProfileId)
                    .any { it.clientMessageId == clientMessageId }
                if (!remainsDurable) NotificationReplyOutcome.Failed else {
                    watchPendingReply(recipientProfileId, clientMessageId)
                    triggerPendingFlush(recipientProfileId)
                    NotificationReplyOutcome.Queued
                }
            }
        }
    }

    private suspend fun queuedOutcomeAfterTimeout(
        recipientProfileId: String,
        clientMessageId: String,
    ): NotificationReplyOutcome {
        val remainsDurable = runCatching {
            outgoingStore.load(recipientProfileId).any { it.clientMessageId == clientMessageId }
        }.getOrDefault(false)
        if (!remainsDurable) return NotificationReplyOutcome.Failed
        watchPendingReply(recipientProfileId, clientMessageId)
        triggerPendingFlush(recipientProfileId)
        return NotificationReplyOutcome.Queued
    }

    private suspend fun releaseClaim(
        claimed: StoredChatOutgoing,
        leaseToken: String,
        error: String,
    ): Boolean = outgoingStore.updateClaimed(
        claimed.copy(
            attempts = 0,
            lastError = error,
            leaseToken = null,
            leaseUntilMillis = null,
        ),
        leaseToken,
    )

    private fun triggerPendingFlush(actorId: String) {
        scope.launch {
            val currentActor = runCatching { actorProvider() }.getOrNull()
            if (acceptingReplies && currentActor == actorId) chatRepository.flushPendingMessages()
        }
    }

    private suspend fun recoverPendingReplies() {
        val actorId = runCatching { actorProvider() }.getOrNull() ?: return
        val pendingIds = runCatching { outgoingStore.load(actorId) }.getOrDefault(emptyList())
            .map(StoredChatOutgoing::clientMessageId)
            .filter { it.startsWith(NotificationReplyClientPrefix) }
        if (pendingIds.isEmpty()) return
        chatRepository.flushPendingMessages()
        pendingIds.forEach { watchPendingReply(actorId, it) }
        schedulePostLeaseRecoveryFlush(actorId)
    }

    private fun schedulePostLeaseRecoveryFlush(actorId: String) {
        if (!scheduledRecoveryFlushActors.add(actorId)) return
        scope.launch {
            try {
                // A process can die after persisting a 30-second claim. The first restored flush
                // cannot take that live lease, so retry once after every such claim must be stale.
                delay(ReplyLeaseMillis + PendingObservationMillis)
                val currentActor = runCatching { actorProvider() }.getOrNull()
                if (!acceptingReplies || currentActor != actorId) return@launch
                val stillPending = runCatching { outgoingStore.load(actorId) }.getOrDefault(emptyList())
                    .any { it.clientMessageId.startsWith(NotificationReplyClientPrefix) }
                if (stillPending) chatRepository.flushPendingMessages()
            } finally {
                scheduledRecoveryFlushActors.remove(actorId)
            }
        }
    }

    private fun watchPendingReply(actorId: String, clientMessageId: String) {
        if (!watchedPendingReplyIds.add(clientMessageId)) return
        scope.launch {
            try {
                while (acceptingReplies) {
                    val currentActor = runCatching { actorProvider() }.getOrNull()
                    if (currentActor != actorId) return@launch
                    // The runtime inserted directly into this persistent store. The repository may
                    // still hold an older in-memory outbox snapshot until its flush force-reloads it.
                    val stillPending = runCatching {
                        outgoingStore.load(actorId).any { it.clientMessageId == clientMessageId }
                    }.getOrDefault(true)
                    if (!stillPending) {
                        pendingReplyDelivered?.invoke(clientMessageId)
                        return@launch
                    }
                    delay(PendingObservationMillis)
                }
            } finally {
                watchedPendingReplyIds.remove(clientMessageId)
            }
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun nowMillis(): Long = time(null).toLong() * 1_000L

    internal suspend fun sendDurablyForTest(
        conversationId: String,
        recipientProfileId: String,
        text: String,
        clientMessageId: String,
    ): NotificationReplyOutcome = sendDurably(conversationId, recipientProfileId, text, clientMessageId)

    internal suspend fun recoverPendingRepliesForTest() = recoverPendingReplies()

    private companion object {
        const val NotificationReplyClientPrefix = "notification-reply-"
        const val ReplyLeaseMillis = 30_000L
        const val PendingObservationMillis = 2_000L
    }
}

fun createIosNotificationReplyRuntime(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    chatRepository: ChatRepository,
): IosNotificationReplyRuntime = IosNotificationReplyRuntime(configuration, authSession, chatRepository)
