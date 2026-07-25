package com.quata.feature.official.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.quata.feature.official.domain.OfficialPostItem

/**
 * Shared official-feed viewport and vertical pager mechanics.
 *
 * The host owns state restoration and focused-post navigation through [pagerState], and supplies
 * cards, pull-to-refresh UI and platform-specific overlays as slots. This content only decides
 * when older posts should be requested and lays out the pager, so it is usable by every Compose
 * target without bringing Android media, navigation or localization APIs into common code.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficialFeedPagerContent(
    padding: PaddingValues,
    pagerState: PagerState,
    posts: List<OfficialPostItem>,
    hasMoreOlderPosts: Boolean,
    isLoadingOlder: Boolean,
    isInitialLoading: Boolean,
    onLoadOlder: () -> Unit,
    emptyContent: @Composable (isInitialLoading: Boolean) -> Unit,
    pageContent: @Composable (page: Int, post: OfficialPostItem, isCurrentPage: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    prefetchDistance: Int = OfficialOlderPostsPrefetchDistance,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val latestOnLoadOlder = rememberUpdatedState(onLoadOlder)

    LaunchedEffect(
        pagerState.currentPage,
        posts.size,
        hasMoreOlderPosts,
        isLoadingOlder,
        prefetchDistance,
    ) {
        val shouldLoadOlder =
            posts.isNotEmpty() &&
                hasMoreOlderPosts &&
                !isLoadingOlder &&
                pagerState.currentPage >= posts.lastIndex - prefetchDistance
        if (shouldLoadOlder) {
            latestOnLoadOlder.value()
        }
    }

    OfficialFeedViewportContent(padding = padding, modifier = modifier) {
        if (posts.isEmpty()) {
            emptyContent(isInitialLoading)
        } else {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val post = posts[page]
                pageContent(page, post, pagerState.currentPage == page)
            }
        }
        overlay()
    }
}

const val OfficialOlderPostsPrefetchDistance = 8
