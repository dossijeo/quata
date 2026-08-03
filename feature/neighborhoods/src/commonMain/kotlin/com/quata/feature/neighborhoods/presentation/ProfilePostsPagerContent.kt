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

/** Shared profile gallery pager; the screen model owns optimistic comment state and rollback. */
@Composable
fun ProfilePostsPagerContent(
    posts: List<Post>,
    pagerState: PagerState,
    onAddComment: (Post, PostComment) -> Unit,
    postPreview: @Composable (post: Post, commentsCount: Int, onOpenComments: () -> Unit) -> Unit,
    commentsDialog: @Composable (
        post: Post,
        onAddComment: (PostComment) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsPost = remember { mutableStateOf<Post?>(null) }
    HorizontalPager(state = pagerState, modifier = modifier.height(440.dp)) { page ->
        val post = posts[page]
        postPreview(post, post.comments.size) { commentsPost.value = post }
    }
    commentsPost.value?.id?.let { postId ->
        val post = posts.firstOrNull { it.id == postId } ?: return@let
        commentsDialog(
            post,
            { comment -> onAddComment(post, comment) },
            { commentsPost.value = null },
        )
    }
}
