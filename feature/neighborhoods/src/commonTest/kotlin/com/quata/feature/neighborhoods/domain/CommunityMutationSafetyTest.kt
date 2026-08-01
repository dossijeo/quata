package com.quata.feature.neighborhoods.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityMutationSafetyTest {
    @Test
    fun `keeps only deferred CommunityProfile moderation capabilities fail closed`() {
        CommunityMutationOperation.entries.forEach { operation ->
            assertFalse(CommunityMutationSafety.isEnabled(operation), "$operation must remain disabled")
            val result = CommunityMutationSafety.blocked<Unit>(operation)
            assertTrue(result.isFailure, "$operation must fail instead of reaching a transport")
            assertTrue(
                requireNotNull(result.exceptionOrNull()).message.orEmpty().contains("pending_rls_review_RLS-001"),
                "$operation failure must retain the RLS evidence identifier",
            )
        }
    }
}
