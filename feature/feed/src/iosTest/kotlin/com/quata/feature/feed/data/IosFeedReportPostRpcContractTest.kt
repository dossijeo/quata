package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals

class IosFeedReportRpcContractTest {
    @Test
    fun reportsUseTheReviewedAuthenticatedRpcEndpointAndExactTargetPayloads() {
        val postBody = iosFeedUgcReportBody("user-7", IosFeedReportTarget.CommunityPost, "post-9")
        val commentBody = iosFeedUgcReportBody("user-7", IosFeedReportTarget.CommunityComment, "comment-11")
        val request = iosFeedReportRpcRequest("https://deployment.invalid/", commentBody)

        assertEquals("POST", request.method)
        assertEquals("https://deployment.invalid/rest/v1/rpc/quata_ugc_report", request.url)
        assertEquals("{\"p_actor_profile_id\":\"user-7\",\"p_target_type\":\"community_post\",\"p_target_id\":\"post-9\",\"p_reason\":\"other\"}", postBody)
        assertEquals("{\"p_actor_profile_id\":\"user-7\",\"p_target_type\":\"community_comment\",\"p_target_id\":\"comment-11\",\"p_reason\":\"other\"}", request.body)
    }
}
