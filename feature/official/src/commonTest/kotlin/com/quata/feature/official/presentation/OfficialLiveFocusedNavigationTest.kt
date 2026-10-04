package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialLiveFocusedNavigationTest {
    @Test
    fun focusedDetailSelectionChangesTheExternalRoute() {
        var routeTarget: String? = null
        var pagerTarget: String? = null

        dispatchOfficialLiveSelection(
            focusedPostId = "a",
            selectedPostId = "b",
            onFocusedDetail = { routeTarget = it },
            onFeedPager = { pagerTarget = it },
        )

        assertEquals("b", routeTarget)
        assertNull(pagerTarget)
    }

    @Test
    fun ordinaryFeedSelectionKeepsNavigationInThePager() {
        var routeTarget: String? = null
        var pagerTarget: String? = null

        dispatchOfficialLiveSelection(
            focusedPostId = null,
            selectedPostId = "b",
            onFocusedDetail = { routeTarget = it },
            onFeedPager = { pagerTarget = it },
        )

        assertNull(routeTarget)
        assertEquals("b", pagerTarget)
    }
}
