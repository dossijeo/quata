package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals

class IosFeedReportPostRpcContractTest {
    @Test
    fun reportPostUsesTheReviewedAuthenticatedRpcEndpointAndPayload() {
        val body = "{\"p_actor_profile_id\":\"user-7\",\"p_target_type\":\"community_post\",\"p_target_id\":\"post-9\",\"p_reason\":\"other\"}"
        val request = iosFeedReportPostRpcRequest("https://deployment.invalid/", body)

        assertEquals("POST", request.method)
        assertEquals("https://deployment.invalid/rest/v1/rpc/quata_ugc_report", request.url)
        assertEquals(body, request.body)
    }
}
