package com.quata.feature.neighborhoods.domain

/**
 * Legacy containment for CommunityProfile-only moderation work that is not rendered by the
 * directory/member flow.
 *
 * This is intentionally fail-closed: a platform must not opt into one of these operations merely
 * Directory chat and follow use authenticated production contracts directly. This legacy switch
 * must not be wired to visible directory callbacks.
 */
enum class CommunityMutationOperation {
    CreateComment,
    DeleteComment,
    ReportPost,
    SetUserRoles,
}

object CommunityMutationSafety {
    const val EvidenceId = "RLS-001"

    fun isEnabled(operation: CommunityMutationOperation): Boolean = false

    fun unsupportedReason(operation: CommunityMutationOperation): String =
        "communities_mutation_blocked_${operation.name.lowercase()}_pending_rls_review_$EvidenceId"

    fun <T> blocked(operation: CommunityMutationOperation): Result<T> =
        Result.failure(UnsupportedOperationException(unsupportedReason(operation)))
}
