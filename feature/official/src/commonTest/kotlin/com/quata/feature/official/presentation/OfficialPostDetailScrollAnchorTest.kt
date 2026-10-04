package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OfficialPostDetailScrollAnchorTest {
    @Test
    fun sectionKeysRemainStableWhenOptionalSectionsChange() {
        assertEquals(
            listOf(
                OfficialPostDetailAuthorSectionKey,
                OfficialPostDetailMediaSectionKey,
                OfficialPostDetailArticleSectionKey,
                OfficialPostDetailResourceSectionKey,
                OfficialPostDetailNavigationSectionKey,
            ),
            officialPostDetailSectionKeys(
                hasAuthor = true,
                hasMedia = true,
                hasResource = true,
                hasNavigation = true,
            ),
        )
        assertEquals(
            listOf(
                OfficialPostDetailArticleSectionKey,
                OfficialPostDetailNavigationSectionKey,
            ),
            officialPostDetailSectionKeys(
                hasAuthor = false,
                hasMedia = false,
                hasResource = false,
                hasNavigation = true,
            ),
        )
    }

    @Test
    fun matchingPostRestoresByStableSectionInsteadOfFormerIndex() {
        val anchor = OfficialPostDetailScrollAnchor(
            postId = "official-7",
            sectionKey = OfficialPostDetailArticleSectionKey,
            scrollOffsetPx = 37,
        )

        assertEquals(
            2 to 37,
            officialPostDetailInitialScrollPosition(
                postId = "official-7",
                sectionKeys = officialPostDetailSectionKeys(true, true, true, true),
                anchor = anchor,
            ),
        )
        assertEquals(
            0 to 37,
            officialPostDetailInitialScrollPosition(
                postId = "official-7",
                sectionKeys = officialPostDetailSectionKeys(false, false, true, true),
                anchor = anchor,
            ),
        )
    }

    @Test
    fun anotherPostOrMissingSectionCannotInheritTheOldPosition() {
        val anchor = OfficialPostDetailScrollAnchor(
            postId = "official-7",
            sectionKey = OfficialPostDetailMediaSectionKey,
            scrollOffsetPx = 52,
        )

        assertEquals(
            0 to 0,
            officialPostDetailInitialScrollPosition(
                postId = "official-8",
                sectionKeys = officialPostDetailSectionKeys(true, true, true, true),
                anchor = anchor,
            ),
        )
        assertEquals(
            0 to 0,
            officialPostDetailInitialScrollPosition(
                postId = "official-7",
                sectionKeys = officialPostDetailSectionKeys(true, false, true, true),
                anchor = anchor,
            ),
        )
    }

    @Test
    fun restoredPixelOffsetIsNeverNegative() {
        assertEquals(
            0 to 0,
            officialPostDetailInitialScrollPosition(
                postId = "official-7",
                sectionKeys = listOf(OfficialPostDetailArticleSectionKey),
                anchor = OfficialPostDetailScrollAnchor(
                    postId = "official-7",
                    sectionKey = OfficialPostDetailArticleSectionKey,
                    scrollOffsetPx = -1,
                ),
            ),
        )
    }

    @Test
    fun mediaViewerRoundTripRestoresTheExactObservedAnchor() = runComposeUiTest {
        var panelMounted by mutableStateOf(true)
        var persistedAnchor by mutableStateOf<OfficialPostDetailScrollAnchor?>(
            OfficialPostDetailScrollAnchor(
                postId = "official-scroll-post",
                sectionKey = OfficialPostDetailArticleSectionKey,
                scrollOffsetPx = 37,
            ),
        )
        var lastObservedAnchor: OfficialPostDetailScrollAnchor? = null
        setContent {
            QuataTheme {
                if (panelMounted) {
                    OfficialScrollPanelFixture(
                        initialAnchor = persistedAnchor,
                        onAnchorChanged = {
                            persistedAnchor = it
                            lastObservedAnchor = it
                        },
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 1_500) { lastObservedAnchor == persistedAnchor }
        val beforeMedia = lastObservedAnchor
        runOnIdle {
            panelMounted = false
            lastObservedAnchor = null
        }
        onAllNodesWithTag(OfficialPostDetailPanelTestTag, useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { panelMounted = true }
        waitUntil(timeoutMillis = 5_000) { lastObservedAnchor == beforeMedia }

        runOnIdle { assertEquals(beforeMedia, lastObservedAnchor) }
    }

    @Test
    fun saverRoundTripKeepsPostSectionAndPixelOffset() {
        val anchor = OfficialPostDetailScrollAnchor(
            postId = "official-restore-post",
            sectionKey = OfficialPostDetailResourceSectionKey,
            scrollOffsetPx = 73,
        )
        val saved = with(OfficialPostDetailScrollAnchor.Saver) {
            SaverScope { true }.save(anchor)
        }
        assertEquals(anchor, saved?.let(OfficialPostDetailScrollAnchor.Saver::restore))
    }
}

@Composable
private fun OfficialScrollPanelFixture(
    initialAnchor: OfficialPostDetailScrollAnchor?,
    onAnchorChanged: (OfficialPostDetailScrollAnchor) -> Unit,
) {
    OfficialPostDetailPanelContent(
        postId = "official-scroll-post",
        title = "Scroll restoration",
        closeLabel = "Close",
        link = null,
        onDismiss = {},
        articleContent = { Spacer(it.height(900.dp)) },
        author = { Spacer(it.height(160.dp)) },
        media = { Spacer(it.height(224.dp)) },
        resourceContent = { Spacer(it.height(180.dp)) },
        navigationContent = { Spacer(it.height(160.dp)) },
        initialScrollAnchor = initialAnchor,
        onScrollAnchorChanged = onAnchorChanged,
        modifier = Modifier,
    )
}
