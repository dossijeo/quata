package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    @Test fun replyCommentPlanPreservesTheRemoteReplyEnvelope() {
        val comment = PostComment(
            id = "local",
            authorId = "u",
            authorName = "User",
            message = "Reply",
            timestamp = "now",
            replyToCommentId = "root",
            replyToAuthorName = "Author",
        )
        val body = requireNotNull(officialCommentPlan("p", "u", comment).body)
        assertTrue("[reply:root:Author] Reply" in body)
    }
    @Test fun postInsertCarriesTranslationGroupAndNeverCredentials() {
        val body = requireNotNull(officialPostInsertPlan("author", OfficialPostDraft(
            title = "Title", summary = "Summary", contentHtml = "<p>Body</p>",
            language = OfficialPostLanguage.French, translationGroupId = "group", type = OfficialPostType.News,
        )).body)
        assertTrue("\"profile_id\":\"author\"" in body)
        assertTrue("\"translation_group_id\":\"group\"" in body)
        assertTrue("\"language\":\"fr\"" in body)
        assertFalse("service_role" in body)
    }
}
