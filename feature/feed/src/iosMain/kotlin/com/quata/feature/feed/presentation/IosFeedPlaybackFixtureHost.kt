package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewController

/**
 * Deterministic iOS UI-test composition for the shared Feed playback chrome.
 *
 * The fixture still mounts [QuataFeedViewController] and the common [FeedScreenHost]. It replaces
 * only the remote read boundary, because the CI public runtime fixture is intentionally
 * non-routable and cannot provide posts.
 */
@Suppress("UNUSED_PARAMETER")
fun QuataIosFeedPlaybackFixtureViewController(mediaFactory: IosFeedMediaFactory): UIViewController =
    QuataFeedViewController(
        dependencies = IosFeedHostDependencies(
            repository = IosFeedPlaybackFixtureRepository(),
            mediaFactory = IosFeedPlaybackFixtureMediaFactory,
            shareService = IosFeedPlaybackFixtureShareService,
            profileOpeningState = IosMemberProfileOpeningState(),
        ),
    )

private object IosFeedPlaybackFixtureMediaFactory : IosFeedMediaFactory {
    override fun createImage(url: String): IosFeedMediaSurface = IosFeedPlaybackFixtureMediaSurface()
    override fun createVideo(url: String): IosFeedMediaSurface = IosFeedPlaybackFixtureMediaSurface()
}

private class IosFeedPlaybackFixtureMediaSurface : IosFeedMediaSurface {
    private val view = UIView().apply {
        backgroundColor = UIColor(red = 0.02, green = 0.08, blue = 0.28, alpha = 1.0)
        opaque = false
    }
    private var snapshot = IosFeedMediaSnapshot(durationMs = 0L)

    override fun nativeView(): UIView = view
    override fun configureBackground(startArgb: Int, endArgb: Int) {
        view.backgroundColor = UIColor(red = 0.02, green = 0.08, blue = 0.28, alpha = 1.0)
    }
    override fun configure(isActive: Boolean, isMuted: Boolean, initialPositionMs: Long) {
        snapshot = snapshot.copy(positionMs = initialPositionMs.coerceAtLeast(0L))
    }
    override fun play() {
        snapshot = snapshot.copy(isPlaying = true, hasStartedPlayback = true, error = null)
    }
    override fun pause() {
        snapshot = snapshot.copy(isPlaying = false, hasStartedPlayback = true)
    }
    override fun seekTo(positionMs: Long) {
        snapshot = snapshot.copy(positionMs = positionMs.coerceAtLeast(0L))
    }
    override fun retry() {
        snapshot = snapshot.copy(error = null)
    }
    override fun snapshot(): IosFeedMediaSnapshot = snapshot
    override fun dispose() = Unit
}

private object IosFeedPlaybackFixtureShareService : ShareService {
    override suspend fun share(payload: SharePayload): PlatformResult<Unit> = PlatformResult.Unsupported
}

private class IosFeedPlaybackFixtureRepository : FeedRepository {
    private val author = User(
        id = "feed-playback-fixture-author",
        email = "playback-fixture@example.invalid",
        displayName = "Nsue",
        neighborhood = "Bovano",
    )
    private val videoPost = Post(
        id = "feed-playback-fixture-video",
        author = author,
        text = "#4myBRODAZ",
        videoUrl = "https://egquata.com/wp-content/uploads/2026/08/feed-playback-fixture.mp4",
        rankingLabel = "#4",
        createdAt = "2026-08-05T00:00:00Z",
        likesCount = 0,
        comments = emptyList(),
    )
    private val posts = listOf(videoPost)
    private val feed = MutableStateFlow(Result.success(posts))

    override fun observeFeed(): Flow<Result<List<Post>>> = feed

    override suspend fun getFeed(): Result<List<Post>> = Result.success(posts)

    override suspend fun refreshFeed(): Result<List<Post>> = Result.success(posts)

    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> =
        Result.success(emptyList())

    override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)

    override suspend fun refreshAuthor(userId: String): Result<User?> = Result.success(author.takeIf { it.id == userId })

    override suspend fun refreshPost(postId: String): Result<Post?> = Result.success(videoPost.takeIf { it.id == postId })

    override suspend fun toggleLike(postId: String): Result<Post?> = fixtureMutation()

    override suspend fun reportPost(postId: String): Result<Post?> = fixtureMutation()

    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = fixtureMutation()

    override suspend fun deletePost(postId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("fixture_feed_mutation_not_supported"))

    private fun <T> fixtureMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("fixture_feed_mutation_not_supported"))
}
