package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebNeighborhoodsActionContractTest {
    @Test fun `follow lookup uses Android equivalent profile follow table columns`() {
        assertEquals(
            mapOf("select" to "id", "follower_profile_id" to "eq.me", "followed_profile_id" to "eq.peer"),
            webNeighborhoodFollowLookup("me", "peer"),
        )
    }

    @Test fun `directory reads remain anonymous while actions require session`() {
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin))
    }
}
