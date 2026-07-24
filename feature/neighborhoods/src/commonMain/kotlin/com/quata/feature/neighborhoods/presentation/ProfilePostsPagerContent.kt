package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.model.PostComment

/** Shared profile gallery pager and its local comment overlay state; hosts render post media and comments. */
@Composable
fun ProfilePostsPagerContent(
    posts: List<Post>,
    pagerState: PagerState,
    postPreview: @Composable (post: Post, commentsCount: Int, onOpenComments: () -> Unit) -> Unit,
    commentsDialog: @Composable (
        post: Post,
        localComments: List<PostComment>,
        onAddComment: (PostComment) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsPost = remember { mutableStateOf<Post?>(null) }
    val localComments = remember { mutableStateMapOf<String, List<PostComment>>() }
    HorizontalPager(state = pagerState, modifier = modifier.height(440.dp)) { page ->
        val post = posts[page]
        postPreview(post, post.comments.size + localComments[post.id].orEmpty().size) { commentsPost.value = post }
    }
    commentsPost.value?.let { post ->
        commentsDialog(
            post,
            localComments[post.id].orEmpty(),
            { comment -> localComments[post.id] = localComments[post.id].orEmpty() + comment },
            { commentsPost.value = null },
        )
    }
}
