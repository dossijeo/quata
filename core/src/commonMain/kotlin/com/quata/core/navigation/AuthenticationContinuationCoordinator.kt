package com.quata.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Product actions that may begin on a public surface but require an authenticated actor. */
enum class AuthenticationContinuationKind {
    FeedTogglePostLike,
    FeedReportPost,
    FeedOpenComposer,
    FeedAddComment,
    FeedReportComment,
    OfficialTogglePostLike,
    OfficialAddComment,
    OfficialReportComment,
    CommunitiesOpenNeighborhoodChat,
    CommunitiesToggleFollow,
    CommunitiesOpenPrivateChat,
    CommunityProfileEnsureFollow,
    CommunityProfileOpenPrivateChat,
    CommunityProfileEnsurePostLike,
    CommunityProfileAddComment,
    CommunityProfileReportPost,
    CommunityProfileConfirmReport,
    CommunityProfileConfirmBlock,
}

/**
 * Immutable, platform-neutral description of the exact gesture that opened Auth.
 *
 * Only primitive product identifiers and the minimum user-authored text needed to resume are
 * retained. Credentials, repositories, callbacks and transport objects never belong here.
 */
data class AuthenticationContinuationIntent(
    val kind: AuthenticationContinuationKind,
    val originRoute: String,
    val targetId: String? = null,
    val relatedId: String? = null,
    val contextId: String? = null,
    val text: String? = null,
    val desiredState: Boolean? = null,
) {
    init {
        require(originRoute.isNotBlank()) { "authentication_continuation_origin_required" }
        require(targetId == null || targetId.isNotBlank()) { "authentication_continuation_target_invalid" }
        require(relatedId == null || relatedId.isNotBlank()) { "authentication_continuation_related_invalid" }
        require(contextId == null || contextId.isNotBlank()) { "authentication_continuation_context_invalid" }
        require(text == null || text.isNotBlank()) { "authentication_continuation_text_invalid" }
    }
}

data class PendingAuthenticationContinuation(
    val requestId: Long,
    val intent: AuthenticationContinuationIntent,
)

data class AuthenticationContinuationMarker(
    val requestId: Long,
    val kind: AuthenticationContinuationKind,
    val originRoute: String,
)

/**
 * App-scoped single owner for an action waiting on authentication.
 *
 * A matching product surface atomically claims the action after Auth restores its origin. Claiming
 * removes it before dispatch, so recomposition or duplicate login callbacks cannot repeat it.
 */
@OptIn(ExperimentalAtomicApi::class)
class AuthenticationContinuationCoordinator {
    private val mutationInProgress = AtomicBoolean(false)
    private var nextRequestId = 0L
    private val _pending = MutableStateFlow<PendingAuthenticationContinuation?>(null)
    val pending: StateFlow<PendingAuthenticationContinuation?> = _pending.asStateFlow()

    fun request(intent: AuthenticationContinuationIntent): PendingAuthenticationContinuation = mutate {
        nextRequestId = if (nextRequestId == Long.MAX_VALUE) 1L else nextRequestId + 1L
        PendingAuthenticationContinuation(nextRequestId, intent).also { _pending.value = it }
    }

    fun marker(): AuthenticationContinuationMarker? = mutate {
        _pending.value?.let { pending ->
            AuthenticationContinuationMarker(
                requestId = pending.requestId,
                kind = pending.intent.kind,
                originRoute = pending.intent.originRoute,
            )
        }
    }

    fun pendingIntent(): AuthenticationContinuationIntent? = mutate { _pending.value?.intent }

    fun claim(requestId: Long): AuthenticationContinuationIntent? = mutate {
        val current = _pending.value ?: return@mutate null
        if (current.requestId != requestId) return@mutate null
        _pending.value = null
        current.intent
    }

    fun clear(requestId: Long): Boolean = mutate {
        val current = _pending.value ?: return@mutate false
        if (current.requestId != requestId) return@mutate false
        _pending.value = null
        true
    }

    fun clearAll() = mutate {
        _pending.value = null
    }

    private inline fun <T> mutate(block: () -> T): T {
        while (!mutationInProgress.compareAndSet(expectedValue = false, newValue = true)) {
            // Mutations contain no suspension and only copy small immutable values.
        }
        return try {
            block()
        } finally {
            mutationInProgress.store(false)
        }
    }
}
