@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.navigation.quataPostUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebFeedLiveRouteTest {
    @Test
    fun replacingFocusedPostKeepsBrowserFragmentAndComposeRouteInAgreement() {
        val original = liveTestBrowserFragment()
        val navigation = WebNavigationController("post-a")
        try {
            for (postId in listOf("b", "a")) {
                navigation.replace(quataPostUrl(postId).substringAfter('#'))
                assertEquals("post-$postId", liveTestBrowserFragment())
                assertEquals(postId, navigation.postId)
                assertEquals("post/$postId", navigation.route)
                assertEquals(navigation.state, liveTestBrowserFragment().toWebNavigationState())
            }
            navigation.replace("feed")
            assertEquals("feed", liveTestBrowserFragment())
            assertEquals("feed", navigation.route)
            assertNull(navigation.postId)
        } finally {
            navigation.replace(original)
        }
    }
}

private fun liveTestBrowserFragment(): String = js("globalThis.location.hash.replace(/^#/, '')")
