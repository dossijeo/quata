package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import kotlinx.coroutines.flow.collectLatest

const val PublicProfilePostPageTestTagPrefix = "public-profile.gallery.post."
const val PublicProfilePostDetailContentTestTagPrefix = "public-profile.post.detail.content."
const val PublicProfilePostDetailScrollTestTagPrefix = "public-profile.post.detail.scroll."

data class PublicProfilePostDetailScrollAnchor(
    val postId: String,
    val scrollOffsetPx: Int,
) {
    companion object {
        val Empty = PublicProfilePostDetailScrollAnchor("", 0)
        val Saver: Saver<PublicProfilePostDetailScrollAnchor, Any> = listSaver(
            save = { listOf(it.postId, it.scrollOffsetPx) },
            restore = {
                PublicProfilePostDetailScrollAnchor(
                    postId = it[0] as String,
                    scrollOffsetPx = it[1] as Int,
                )
            },
        )
    }
}

internal fun publicProfilePostDetailInitialScrollOffset(
    postId: String,
    anchor: PublicProfilePostDetailScrollAnchor?,
): Int = anchor
    ?.takeIf { it.postId == postId }
    ?.scrollOffsetPx
    ?.coerceAtLeast(0)
    ?: 0

/** Shared profile gallery pager; the screen model owns optimistic comment state and rollback. */
@Composable
fun ProfilePostsPagerContent(
    posts: List<Post>,
    pagerState: PagerState,
    onAddComment: (Post, PostComment) -> Unit,
    postPreview: @Composable (
        post: Post,
        commentsCount: Int,
        onOpenComments: () -> Unit,
        onOpenDetail: (() -> Unit)?,
    ) -> Unit,
    detailChrome: @Composable (post: Post, onBack: () -> Unit) -> Unit,
    commentsDialog: @Composable (
        post: Post,
        onAddComment: (PostComment) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var commentsPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentsPostSnapshot by remember { mutableStateOf<Post?>(null) }
    var detailPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailScrollAnchor by rememberSaveable(stateSaver = PublicProfilePostDetailScrollAnchor.Saver) {
        mutableStateOf(PublicProfilePostDetailScrollAnchor.Empty)
    }
    val detailPost = detailPostId?.let { id -> posts.firstOrNull { it.id == id } }
    if (detailPost != null) {
        val detailScrollState = rememberScrollState(
            initial = publicProfilePostDetailInitialScrollOffset(detailPost.id, detailScrollAnchor),
        )
        LaunchedEffect(detailPost.id, detailScrollState) {
            snapshotFlow { detailScrollState.value }.collectLatest { offset ->
                detailScrollAnchor = PublicProfilePostDetailScrollAnchor(detailPost.id, offset)
            }
        }
        Column(modifier.height(440.dp)) {
            detailChrome(detailPost) { detailPostId = null }
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics {
                        testTag = PublicProfilePostDetailContentTestTagPrefix + detailPost.id
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(detailScrollState)
                        .semantics {
                            testTag = PublicProfilePostDetailScrollTestTagPrefix + detailPost.id
                        },
                ) {
                    postPreview(
                        detailPost,
                        detailPost.comments.size,
                        {
                            commentsPostId = detailPost.id
                            commentsPostSnapshot = detailPost
                        },
                        null,
                    )
                }
            }
        }
    } else {
        HorizontalPager(state = pagerState, modifier = modifier.height(440.dp)) { page ->
            val post = posts[page]
            androidx.compose.foundation.layout.Box(
                Modifier.semantics { testTag = PublicProfilePostPageTestTagPrefix + post.id },
            ) {
                postPreview(
                    post,
                    post.comments.size,
                    {
                        commentsPostId = post.id
                        commentsPostSnapshot = post
                    },
                    { detailPostId = post.id },
                )
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
