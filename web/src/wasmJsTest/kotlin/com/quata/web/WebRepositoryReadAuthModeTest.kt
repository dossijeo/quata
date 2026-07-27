package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebRepositoryReadAuthModeTest {
    @Test
    fun officialCallSitesKeepFeedPublicAndCurrentUserPrivate() {
        assertEquals(WebPostgrestAuthMode.Public, webOfficialReadAuthMode(WebOfficialReadOperation.Feed))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webOfficialReadAuthMode(WebOfficialReadOperation.CurrentUser))
    }

    @Test
    fun communitiesCallSitesKeepDirectoryPublicAndIdentityReadsPrivate() {
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.UserProfile))
    }
}
