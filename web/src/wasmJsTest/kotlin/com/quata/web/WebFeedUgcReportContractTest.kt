package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebFeedUgcReportContractTest {
    @Test
    fun reportPayloadDistinguishesPostAndCommentTargets() {
        assertEquals(
            "{\"p_actor_profile_id\":\"user-7\",\"p_target_type\":\"community_post\",\"p_target_id\":\"post-9\",\"p_reason\":\"other\"}",
            webFeedUgcReportBody("user-7", WebFeedReportTarget.CommunityPost, "post-9"),
        )
        assertEquals(
            "{\"p_actor_profile_id\":\"user-7\",\"p_target_type\":\"community_comment\",\"p_target_id\":\"comment-11\",\"p_reason\":\"other\"}",
            webFeedUgcReportBody("user-7", WebFeedReportTarget.CommunityComment, "comment-11"),
        )
    }
}
