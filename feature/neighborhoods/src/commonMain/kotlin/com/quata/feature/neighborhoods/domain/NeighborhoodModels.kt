package com.quata.feature.neighborhoods.domain

import com.quata.core.model.Post

data class NeighborhoodUser(
    val id: String,
    val displayName: String,
    val email: String,
    val neighborhood: String,
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
    val isOfficial: Boolean = false,
    val isFollowing: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0
)

data class NeighborhoodCommunity(
    val name: String,
    val users: List<NeighborhoodUser>,
    val conversationId: String?,
    val lastMessagePreview: String?,
    val lastMessageAtMillis: Long?,
    val messageCount: Int,
    /** Canonical active wall UUID. Null means chat must fail closed unless a cached thread exists. */
    val wallId: String? = null,
)

data class CommunityUserProfile(
    val user: NeighborhoodUser,
    val posts: List<Post>,
    val attachments: List<ProfileAttachment> = emptyList(),
    val followers: List<NeighborhoodUser> = emptyList(),
    val following: List<NeighborhoodUser> = emptyList(),
    val isBlockedByCurrentUser: Boolean = false,
)

data class FollowUserResult(
    val userId: String,
    val isFollowing: Boolean,
    val currentUser: NeighborhoodUser
)

data class ProfileAttachment(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String?,
    val sentAtMillis: Long?,
    val senderName: String
)
