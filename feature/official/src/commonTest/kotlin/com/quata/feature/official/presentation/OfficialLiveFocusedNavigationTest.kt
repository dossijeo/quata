package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs

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

    @Test
    fun remoteRankingTargetLoadsThenScrollsWhenItEntersThePager() {
        assertEquals(
            OfficialRankingTargetAction.RequestLoad,
            resolveOfficialRankingTarget("remote", listOf("visible"), loadState = null),
        )
        assertEquals(
            OfficialRankingTargetAction.WaitForLoad,
            resolveOfficialRankingTarget("remote", listOf("visible"), OfficialFocusedPostLoad.Loading),
        )
        val scroll = assertIs<OfficialRankingTargetAction.Scroll>(
            resolveOfficialRankingTarget("remote", listOf("visible", "remote"), OfficialFocusedPostLoad.Loaded),
        )
        assertEquals(1, scroll.index)
    }

    @Test
    fun failedOrMissingRankingTargetClearsForANewSelection() {
        listOf(OfficialFocusedPostLoad.Failed, OfficialFocusedPostLoad.NotFound).forEach { failure ->
            assertEquals(
                OfficialRankingTargetAction.ClearFailedTarget,
                resolveOfficialRankingTarget("remote", listOf("visible"), failure),
            )
        }
    }
}
