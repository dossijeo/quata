package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.model.PostComment

const val PublicProfilePostPageTestTagPrefix = "public-profile.gallery.post."

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
    var commentsPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentsPostSnapshot by remember { mutableStateOf<Post?>(null) }
    HorizontalPager(state = pagerState, modifier = modifier.height(440.dp)) { page ->
        val post = posts[page]
        androidx.compose.foundation.layout.Box(
            Modifier.semantics { testTag = PublicProfilePostPageTestTagPrefix + post.id },
        ) {
            postPreview(post, post.comments.size) {
                commentsPostId = post.id
                commentsPostSnapshot = post
            }
        }
    }
    commentsPostId?.let { postId ->
        val refreshedPost = posts.firstOrNull { it.id == postId }
        val post = refreshedPost
            ?: commentsPostSnapshot?.takeIf { it.id == postId }
            ?: return@let
        SideEffect {
            refreshedPost?.let { commentsPostSnapshot = it }
        }
        commentsDialog(
            post,
            { comment ->
                commentsPostSnapshot = post.copy(comments = post.comments + comment)
                onAddComment(post, comment)
            },
            {
                commentsPostId = null
                commentsPostSnapshot = null
            },
        )
    }
}
