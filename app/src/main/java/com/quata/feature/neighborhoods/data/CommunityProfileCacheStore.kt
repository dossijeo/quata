package com.quata.feature.neighborhoods.data

import android.content.Context
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal class CommunityProfileCacheStore(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun read(userId: String, maxAgeMillis: Long? = null): CommunityUserProfile? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = cacheFile(userId)
            if (!file.exists()) return@withContext null
            runCatching {
                val stored = json.decodeFromString<StoredCommunityUserProfile>(file.readText())
                if (maxAgeMillis != null && stored.isOlderThan(maxAgeMillis)) return@runCatching null
                stored.toDomain()
            }
                .getOrNull()
        }
    }

    suspend fun write(profile: CommunityUserProfile) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = cacheFile(profile.user.id)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(profile.toStored()))
        }
    }

    private fun cacheFile(userId: String): File {
        val safeUserId = userId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeUserId.json")
    }

    private fun CommunityUserProfile.toStored(): StoredCommunityUserProfile =
        StoredCommunityUserProfile(
            user = user.toStored(),
            posts = posts.map { it.toStored() },
            attachments = attachments.map { it.toStored() },
            followers = followers.map { it.toStored() },
            following = following.map { it.toStored() },
            cachedAtMillis = System.currentTimeMillis()
        )

    private fun StoredCommunityUserProfile.toDomain(): CommunityUserProfile =
        CommunityUserProfile(
            user = user.toDomain(),
            posts = posts.map { it.toDomain() },
            attachments = attachments.map { it.toDomain() },
            followers = followers.map { it.toDomain() },
            following = following.map { it.toDomain() }
        )

    private fun NeighborhoodUser.toStored(): StoredNeighborhoodUser =
        StoredNeighborhoodUser(
            id = id,
            displayName = displayName,
            email = email,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl,
            isFollowing = isFollowing,
            followersCount = followersCount,
            followingCount = followingCount,
            postsCount = postsCount
        )

    private fun StoredNeighborhoodUser.toDomain(): NeighborhoodUser =
        NeighborhoodUser(
            id = id,
            displayName = displayName,
            email = email,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl,
            isFollowing = isFollowing,
            followersCount = followersCount,
            followingCount = followingCount,
            postsCount = postsCount
        )

    private fun Post.toStored(): StoredPost =
        StoredPost(
            id = id,
            author = author.toStored(),
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            placeName = placeName,
            rankingLabel = rankingLabel,
            createdAt = createdAt,
            likesCount = likesCount,
            isLikedByCurrentUser = isLikedByCurrentUser,
            isReportedByCurrentUser = isReportedByCurrentUser,
            comments = comments.map { it.toStored() }
        )

    private fun StoredPost.toDomain(): Post =
        Post(
            id = id,
            author = author.toDomain(),
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            placeName = placeName,
            rankingLabel = rankingLabel,
            createdAt = createdAt,
            likesCount = likesCount,
            isLikedByCurrentUser = isLikedByCurrentUser,
            isReportedByCurrentUser = isReportedByCurrentUser,
            comments = comments.map { it.toDomain() }
        )

    private fun User.toStored(): StoredUser =
        StoredUser(
            id = id,
            email = email,
            displayName = displayName,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl
        )

    private fun StoredUser.toDomain(): User =
        User(
            id = id,
            email = email,
            displayName = displayName,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl
        )

    private fun PostComment.toStored(): StoredPostComment =
        StoredPostComment(
            id = id,
            authorName = authorName,
            message = message,
            timestamp = timestamp,
            replyToAuthorName = replyToAuthorName,
            replyToMessage = replyToMessage,
            replyToCommentId = replyToCommentId
        )

    private fun StoredPostComment.toDomain(): PostComment =
        PostComment(
            id = id,
            authorName = authorName,
            message = message,
            timestamp = timestamp,
            replyToAuthorName = replyToAuthorName,
            replyToMessage = replyToMessage,
            replyToCommentId = replyToCommentId
        )

    private fun ProfileAttachment.toStored(): StoredProfileAttachment =
        StoredProfileAttachment(
            id = id,
            name = name,
            uri = uri,
            mimeType = mimeType,
            sentAtMillis = sentAtMillis,
            senderName = senderName
        )

    private fun StoredProfileAttachment.toDomain(): ProfileAttachment =
        ProfileAttachment(
            id = id,
            name = name,
            uri = uri,
            mimeType = mimeType,
            sentAtMillis = sentAtMillis,
            senderName = senderName
        )

    private fun StoredCommunityUserProfile.isOlderThan(maxAgeMillis: Long): Boolean =
        cachedAtMillis <= 0L || System.currentTimeMillis() - cachedAtMillis > maxAgeMillis

    @Serializable
    private data class StoredCommunityUserProfile(
        val user: StoredNeighborhoodUser,
        val posts: List<StoredPost> = emptyList(),
        val attachments: List<StoredProfileAttachment> = emptyList(),
        val followers: List<StoredNeighborhoodUser> = emptyList(),
        val following: List<StoredNeighborhoodUser> = emptyList(),
        val cachedAtMillis: Long = 0L
    )

    @Serializable
    private data class StoredNeighborhoodUser(
        val id: String,
        val displayName: String,
        val email: String,
        val neighborhood: String,
        val avatarUrl: String? = null,
        val isFollowing: Boolean = false,
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val postsCount: Int = 0
    )

    @Serializable
    private data class StoredPost(
        val id: String,
        val author: StoredUser,
        val text: String,
        val imageUrl: String? = null,
        val videoUrl: String? = null,
        val placeName: String? = null,
        val rankingLabel: String = "#1",
        val createdAt: String,
        val likesCount: Int = 0,
        val isLikedByCurrentUser: Boolean = false,
        val isReportedByCurrentUser: Boolean = false,
        val comments: List<StoredPostComment> = emptyList()
    )

    @Serializable
    private data class StoredUser(
        val id: String,
        val email: String,
        val displayName: String,
        val neighborhood: String = "",
        val avatarUrl: String? = null
    )

    @Serializable
    private data class StoredPostComment(
        val id: String,
        val authorName: String,
        val message: String,
        val timestamp: String,
        val replyToAuthorName: String? = null,
        val replyToMessage: String? = null,
        val replyToCommentId: String? = null
    )

    @Serializable
    private data class StoredProfileAttachment(
        val id: String,
        val name: String,
        val uri: String,
        val mimeType: String? = null,
        val sentAtMillis: Long? = null,
        val senderName: String
    )

    private companion object {
        const val DIRECTORY_NAME = "community_profile_cache"
    }
}
