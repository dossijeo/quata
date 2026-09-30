package com.quata.feature.neighborhoods.presentation

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommunityProfileAuthenticationContinuationTest {
    private val reply = PostComment("comment-1", "Ada", "Hola", "Ahora")
    private val post = Post(
        id = "post-1",
        author = User("profile-1", "", "Ada"),
        text = "Publicación",
        createdAt = "Ahora",
        comments = listOf(reply),
    )
    private val profile = CommunityUserProfile(
        user = NeighborhoodUser("profile-1", "Ada", "", "Centro"),
        posts = listOf(post),
        followers = listOf(NeighborhoodUser("member-1", "Lin", "", "Centro")),
    )

    @Test
    fun preservesDesiredFollowStateAndProfileContext() {
        val intent = communityProfileAuthenticationContinuation(
            AuthenticationContinuationKind.CommunityProfileEnsureFollow,
            originRoute = "communities",
            profileId = profile.user.id,
            targetId = "member-1",
            desiredState = true,
        )

        assertEquals(profile.user.id, intent.contextId)
        assertEquals(
            CommunityProfileAuthenticationContinuationResolution.EnsureFollow("member-1", true),
            resolve(intent),
        )
    }

    @Test
    fun restoresExactCommentDraftAndReplyTarget() {
        val resolution = resolve(
            communityProfileAuthenticationContinuation(
                AuthenticationContinuationKind.CommunityProfileAddComment,
                originRoute = "communities",
                profileId = profile.user.id,
                targetId = post.id,
                relatedId = reply.id,
                text = "Respuesta exacta",
            ),
        )

        val add = assertIs<CommunityProfileAuthenticationContinuationResolution.AddComment>(resolution)
        assertEquals(post.id, add.post.id)
        assertEquals("Respuesta exacta", add.text)
        assertEquals(reply, add.replyTarget)
    }

    @Test
    fun clearsMissingReplyAndWrongProfileWithoutDispatch() {
        val missingReply = communityProfileAuthenticationContinuation(
            AuthenticationContinuationKind.CommunityProfileAddComment,
            originRoute = "communities",
            profileId = profile.user.id,
            targetId = post.id,
            relatedId = "missing",
            text = "Respuesta",
        )
        val wrongProfile = missingReply.copy(relatedId = null, contextId = "profile-2")

        assertEquals(CommunityProfileAuthenticationContinuationResolution.Clear, resolve(missingReply))
        assertEquals(CommunityProfileAuthenticationContinuationResolution.Clear, resolve(wrongProfile))
    }

    @Test
    fun ignoresOtherOriginsAndWaitsForAnActiveMutation() {
        val intent = communityProfileAuthenticationContinuation(
            AuthenticationContinuationKind.CommunityProfileEnsurePostLike,
            originRoute = "feed",
            profileId = profile.user.id,
            targetId = post.id,
            desiredState = true,
        )
        assertEquals(CommunityProfileAuthenticationContinuationResolution.Ignore, resolve(intent))
        assertEquals(
            CommunityProfileAuthenticationContinuationResolution.Wait,
            resolve(intent.copy(originRoute = "communities"), actionInProgress = true),
        )
    }

    @Test
    fun restoresModerationAsConfirmationRatherThanMutation() {
        val report = communityProfileAuthenticationContinuation(
            AuthenticationContinuationKind.CommunityProfileConfirmReport,
            "communities",
            profile.user.id,
            targetId = profile.user.id,
        )
        val unblock = communityProfileAuthenticationContinuation(
            AuthenticationContinuationKind.CommunityProfileConfirmBlock,
            "communities",
            profile.user.id,
            targetId = profile.user.id,
            desiredState = false,
        )

        assertEquals(
            CommunityProfileAuthenticationContinuationResolution.ConfirmModeration(ProfileModerationAction.Report),
            resolve(report),
        )
        assertEquals(
            CommunityProfileAuthenticationContinuationResolution.ConfirmModeration(ProfileModerationAction.Unblock),
            resolve(unblock),
        )
    }

    private fun resolve(
        intent: com.quata.core.navigation.AuthenticationContinuationIntent,
        actionInProgress: Boolean = false,
    ) = resolveCommunityProfileAuthenticationContinuation(
        intent = intent,
        originRoute = "communities",
        profile = profile,
        actionInProgress = actionInProgress,
    )
}
