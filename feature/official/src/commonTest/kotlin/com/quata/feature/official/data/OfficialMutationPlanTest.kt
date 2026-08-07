package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfficialMutationPlanTest {
    @Test fun officialPostCreatePlanUsesSharedReviewedPostgrestPayload() {
        val body = requireNotNull(
            officialPostCreatePlan(
                profileId = "profile-1",
                draft = OfficialPostDraft(
                    title = " Title ",
                    summary = " Summary ",
                    contentHtml = "<p>Hello</p>",
                    readMoreLabel = " details ",
                    language = OfficialPostLanguage.English,
                    translationGroupId = null,
                    type = OfficialPostType.News,
                    mediaUrl = "https://cdn.example/image.jpg",
                    mediaType = OfficialMediaType.Image,
                    linkUrl = " https://example.com ",
                ),
                translationGroupId = "group-1",
                defaultTitle = "Cuenta oficial",
                publishedAt = "2026-08-07T12:34:56Z",
            ).body,
        )
        assertTrue("\"profile_id\":\"profile-1\"" in body)
        assertTrue("\"title\":\"Title\"" in body)
        assertTrue("\"summary\":\"Summary\"" in body)
        assertTrue("\"post_type\":\"news\"" in body)
        assertTrue("\"content_html\":\"<p>Hello</p>\"" in body)
        assertTrue("\"read_more_label\":\"details\"" in body)
        assertTrue("\"language\":\"en\"" in body)
        assertTrue("\"translation_group_id\":\"group-1\"" in body)
        assertTrue("\"media_url\":\"https://cdn.example/image.jpg\"" in body)
        assertTrue("\"media_type\":\"image\"" in body)
        assertTrue("\"link_url\":\"https://example.com\"" in body)
        assertTrue("\"is_published\":true" in body)
        assertTrue("\"published_at\":\"2026-08-07T12:34:56Z\"" in body)
    }
    @Test fun officialPostCreatePlanFallsBackToPersistableHtmlAndRejectsLocalMedia() {
        val body = requireNotNull(
            officialPostCreatePlan(
                profileId = "profile",
                draft = OfficialPostDraft(
                    title = "",
                    summary = "Fallback",
                    contentHtml = "   ",
                    type = OfficialPostType.Announcement,
                ),
                translationGroupId = "group",
                defaultTitle = "Cuenta oficial",
                publishedAt = "2026-08-07T12:34:56Z",
            ).body,
        )
        assertTrue("\"content_html\":\"<p>Fallback</p>\"" in body)
        assertTrue("\"summary\":\"Fallback\"" in body)
        assertTrue("\"media_url\":null" in body)
        assertFailsWith<IllegalArgumentException> {
            officialPostCreatePlan(
                profileId = "profile",
                draft = OfficialPostDraft(
                    title = "Local",
                    summary = "",
                    contentHtml = "<p>Local</p>",
                    type = OfficialPostType.Announcement,
                    mediaUrl = "blob:local",
                    mediaType = OfficialMediaType.Image,
                ),
                translationGroupId = "group",
                defaultTitle = "Cuenta oficial",
                publishedAt = "2026-08-07T12:34:56Z",
            )
        }
    }
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
}
