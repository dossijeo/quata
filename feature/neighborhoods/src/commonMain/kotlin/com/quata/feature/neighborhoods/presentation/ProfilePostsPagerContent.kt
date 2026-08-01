package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.model.PostComment

/** Shared profile gallery pager. Comment identity and persistence always come from the repository. */
@Composable
fun ProfilePostsPagerContent(
    posts: List<Post>,
    pagerState: PagerState,
    postPreview: @Composable (post: Post, commentsCount: Int, isCurrent: Boolean, onOpenComments: () -> Unit) -> Unit,
    commentsDialog: @Composable (
        post: Post,
        comments: List<PostComment>,
        onAddComment: (String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onAddComment: (postId: String, draft: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsPostId = remember { mutableStateOf<String?>(null) }
    HorizontalPager(state = pagerState, modifier = modifier.height(440.dp)) { page ->
        val post = posts[page]
        postPreview(post, post.comments.size, page == pagerState.currentPage) { commentsPostId.value = post.id }
    }
    commentsPostId.value?.let { postId ->
        val post = posts.firstOrNull { it.id == postId }
        if (post == null) {
            commentsPostId.value = null
            return@let
        }
        commentsDialog(
            post,
            post.comments,
            { draft -> onAddComment(post.id, draft) },
            { commentsPostId.value = null },
        )
    }
}
