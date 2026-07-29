package com.quata.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserFeedAvatarContentTest {
    @Test
    fun only_http_avatar_urls_reach_the_native_browser_image_element() {
        assertTrue(isBrowserAvatarUrl("https://cdn.example.test/avatar?id=profile-1"))
        assertTrue(isBrowserAvatarUrl("http://localhost/avatar.png"))
        assertFalse(isBrowserAvatarUrl("file:///private/avatar.png"))
        assertFalse(isBrowserAvatarUrl("data:image/png;base64,abc"))
        assertFalse(isBrowserAvatarUrl(""))
    }

    @Test
    fun closing_a_feed_member_profile_consumes_the_request() {
        val route = WebFeedMemberProfileRoute()
        route.open("profile-1")
        assertTrue(route.profileId == "profile-1")

        route.close()

        assertTrue(route.profileId == null)
    }
}
