package com.quata.feature.chat.data

import com.quata.core.session.IosRenewableAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Uses the app's renewable session and existing URLSession adapter; owns no credentials. */
class IosNotificationReplyRuntime(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) {
    private val scope = MainScope()
    private var acceptingReplies = true

    fun sessionEnded() {
        acceptingReplies = false
        scope.coroutineContext.cancelChildren()
    }

    fun resumeAfterSessionValidation() { acceptingReplies = true }

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
                    NotificationReplySender(
                        IosChatPostgrestTransport(configuration, authSession,
                            requestTimeoutMillis = 5_000L, expectedProfileId = recipientProfileId),
                        IosChatAuthenticatedUserProvider(authSession),
                    ).send(conversationId, recipientProfileId, text, clientMessageId)
                } ?: NotificationReplyOutcome.Failed
            } catch (_: CancellationException) {
                // Logout/account replacement abandons the old reply and suppresses its OS feedback.
            } finally {
                completion(outcome)
            }
        }
    }
}
