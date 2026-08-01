package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebNeighborhoodsActionContractTest {
    @Test fun `follow lookup uses Android equivalent profile follow table columns`() {
        assertEquals(
            mapOf("select" to "id", "follower_profile_id" to "eq.me", "followed_profile_id" to "eq.peer"),
            webNeighborhoodFollowLookup("me", "peer"),
        )
    }

    @Test fun `moderation payloads use current Android RPC argument names`() {
        assertEquals(
            "{\"p_actor_profile_id\":\"me\",\"p_target_type\":\"profile\",\"p_target_id\":\"peer\",\"p_reason\":\"other\"}",
            webNeighborhoodReportPayload("me", "profile", "peer"),
        )
        assertEquals("{\"p_actor_profile_id\":\"me\",\"p_profile_id\":\"peer\"}", webNeighborhoodBlockPayload("me", "peer"))
        assertEquals("{\"p_actor_profile_id\":\"me\",\"p_peer_profile_id\":\"peer\",\"p_limit\":120,\"p_offset\":0}", webNeighborhoodSharedAttachmentsPayload("me", "peer"))
    }

    @Test fun `directory reads remain anonymous while actions require session`() {
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin))
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.UserProfile))
        assertTrue(WebNeighborhoodsPrivateActionsRequireSession)
    }
}
