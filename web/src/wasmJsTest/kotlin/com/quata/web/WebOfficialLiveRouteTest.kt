@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.navigation.quataOfficialPostUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebOfficialLiveRouteTest {
    @Test
    fun replacingFocusedOfficialPostKeepsBrowserFragmentAndComposeRouteInAgreement() {
        val original = officialLiveTestBrowserFragment()
        val navigation = WebNavigationController("official-a")
        try {
            for (postId in listOf("b", "a")) {
                navigation.replace(quataOfficialPostUrl(postId).substringAfter('#'))
                assertEquals("official-$postId", officialLiveTestBrowserFragment())
                assertEquals(postId, navigation.officialPostId)
                assertEquals("official/$postId", navigation.route)
                assertEquals(navigation.state, officialLiveTestBrowserFragment().toWebNavigationState())
            }
            navigation.replace("official")
            assertEquals("official", officialLiveTestBrowserFragment())
            assertEquals("official", navigation.route)
            assertNull(navigation.officialPostId)
        } finally {
            navigation.replace(original)
        }
    }
}

private fun officialLiveTestBrowserFragment(): String = js("globalThis.location.hash.replace(/^#/, '')")
