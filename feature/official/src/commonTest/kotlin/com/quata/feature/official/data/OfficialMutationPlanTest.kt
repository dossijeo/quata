package com.quata.feature.official.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialMutationPlanTest {
    @Test fun plansUseReviewedOfficialTablesAndFilters() {
        assertEquals("GET", officialLikeLookupPlan("p", "u").method)
        assertEquals("official_post_likes", officialLikeInsertPlan("p", "u").table)
        assertEquals("eq.u", officialLikeDeletePlan("p", "u").filter["profile_id"])
        assertEquals("eq.g", officialSoftDeletePlan("p", "g", "now").filter["translation_group_id"])
        assertEquals("eq.p", officialSoftDeletePlan("p", null, "now").filter["id"])
    }
    @Test fun payloadEscapesQuotesAndBackslashes() { assertEquals("\"a\\\"b\\\\c\"", officialJson("a\"b\\c")) }
}
