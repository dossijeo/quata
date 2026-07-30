package com.quata.feature.feed.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.quata.core.model.Post

/**
 * Shared vertical reel pager mechanics.
 *
 * Platform hosts keep media rendering, navigation and platform services in [pageContent]. This
 * component owns only paging: it reports the visible post plus its successor and asks the caller
 * for an older page shortly before the user reaches the end of the loaded posts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedReelPagerContent(
    pagerState: PagerState,
    posts: List<Post>,
    hasMoreOlderPosts: Boolean,
    isLoadingOlder: Boolean,
    onPostDisplayed: (visiblePost: Post, nextPost: Post?) -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    prefetchDistance: Int = DefaultFeedOlderPostsPrefetchDistance,
    pageContent: @Composable (page: Int, post: Post, isCurrentPage: Boolean) -> Unit
) {
    // A FeedScreenHost is composed with its initial loading state before its first emission.
    // On Wasm, a VerticalPager may still request page 0 during that composition even when its
    // page count is zero. Never let that transient state reach posts[page].
    if (!canRenderFeedPager(posts)) return

    val latestOnPostDisplayed = rememberUpdatedState(onPostDisplayed)
    val latestOnLoadOlder = rememberUpdatedState(onLoadOlder)
    val visiblePost = posts.getOrNull(pagerState.currentPage)
    val nextPost = posts.getOrNull(pagerState.currentPage + 1)

    LaunchedEffect(visiblePost?.id, nextPost?.id) {
        visiblePost?.let { latestOnPostDisplayed.value(it, nextPost) }
    }

    LaunchedEffect(
        pagerState.currentPage,
        posts.size,
        hasMoreOlderPosts,
        isLoadingOlder,
        prefetchDistance
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

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = modifier.fillMaxSize()
    ) { page ->
        val post = posts[page]
        pageContent(page, post, pagerState.currentPage == page)
    }
}

/** A pager page is meaningful only when there is a post available for that page. */
internal fun canRenderFeedPager(posts: List<Post>): Boolean = posts.isNotEmpty()

const val DefaultFeedOlderPostsPrefetchDistance = 8
