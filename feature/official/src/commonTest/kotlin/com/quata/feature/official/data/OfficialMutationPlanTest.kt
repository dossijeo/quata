package com.quata.feature.official.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OfficialMutationPlanTest {
    @Test fun commentReportUsesExactUgcContract() {
        val payload = officialCommentReportPayload("actor", "comment")
        assertEquals("{\"p_actor_profile_id\":\"actor\",\"p_target_type\":\"official_comment\",\"p_target_id\":\"comment\",\"p_reason\":\"other\"}", payload)
        assertFalse("p_reporter_id" in payload)
        assertFalse("p_details" in payload)
    }
    @Test fun plansUseReviewedOfficialTablesAndFilters() {
        assertEquals("GET", officialLikeLookupPlan("p", "u").method)
        assertEquals("official_post_likes", officialLikeInsertPlan("p", "u").table)
        assertEquals("eq.u", officialLikeDeletePlan("p", "u").filter["profile_id"])
        assertEquals("eq.g", officialSoftDeletePlan("p", "g", "now").filter["translation_group_id"])
        assertEquals("eq.p", officialSoftDeletePlan("p", null, "now").filter["id"])
    }
    @Test fun payloadEscapesQuotesAndBackslashes() { assertEquals("\"a\\\"b\\\\c\"", officialJson("a\"b\\c")) }
}
