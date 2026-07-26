package com.quata.feature.neighborhoods.domain

/**
 * Product containment for Communities mutations while SB-07 has an unresolved RLS finding.
 *
 * This is intentionally fail-closed: a platform must not opt into one of these operations merely
 * because it has a transport. Re-enabling an operation requires a reviewed policy change and a
 * new two-identity E2E result recorded in `docs/RLS_FINDINGS.md`. Conversation creation remains
 * a Chat-domain operation and is governed by its separately verified SB-04 contract.
 */
enum class CommunityMutationOperation {
    CreateComment,
    DeleteComment,
    FollowUser,
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
