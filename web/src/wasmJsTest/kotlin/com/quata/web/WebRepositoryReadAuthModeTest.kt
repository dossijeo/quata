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
    fun `communities directory is public while private identity reads require session`() {
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin))
        assertEquals(WebPostgrestAuthMode.Public, webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.UserProfile))
    }

    @Test
    fun feedCallSitesKeepOnlyRenderingReadsPublic() {
        assertEquals(WebPostgrestAuthMode.Public, webFeedReadAuthMode(WebFeedReadOperation.Feed))
        assertEquals(WebPostgrestAuthMode.Public, webFeedReadAuthMode(WebFeedReadOperation.Detail))
        assertEquals(WebPostgrestAuthMode.Public, webFeedReadAuthMode(WebFeedReadOperation.FeedProfiles))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webFeedReadAuthMode(WebFeedReadOperation.CurrentUser))
        assertEquals(WebPostgrestAuthMode.SessionRequired, webFeedReadAuthMode(WebFeedReadOperation.Author))
    }
}
