package com.quata.feature.neighborhoods.data

import android.content.Context
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityPostLike
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.CommunityProfileFollow
import com.quata.data.supabase.CommunityWallStats
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.chat.data.wallConversationId
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.feed.data.toDomain
import com.quata.feature.feed.data.toDomainComments
import com.quata.feature.feed.data.toDomainUser
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.profile.data.ProfileRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.Normalizer

class NeighborhoodRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val chatRepository: ChatRepository,
    private val profileRemote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager
) : NeighborhoodRepository {
    private val profileCacheStore = CommunityProfileCacheStore(appContext)

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            combine(MockData.conversationsFlow, MockData.messagesFlow, MockData.socialFlow) { conversations, messages, _ ->
                buildCommunities(
                    users = MockData.registeredUsers.map { it.toNeighborhoodUser() },
                    conversations = conversations,
                    messages = messages
                )
            }
        } else {
            val communitiesFlow = combine(
                profileRemote.observeDirectoryProfiles(),
                supabaseApi.observeActiveWallsStats()
            ) { profiles, walls ->
                val usersByNeighborhood = profiles
                    .map { it.toNeighborhoodUserReal() }
                    .filter { it.neighborhood.isNotBlank() }
                    .groupBy { it.neighborhood.normalizeName() }
                val wallsByNeighborhood = walls
                    .flatMap { wall -> wall.communityKeys().map { key -> key to wall } }
                    .toMap()

                usersByNeighborhood.mapNotNull { (neighborhoodKey, neighborhoodUsers) ->
                    val neighborhoodName = neighborhoodUsers
                        .firstNotNullOfOrNull { user -> user.neighborhood.trim().takeIf { it.isNotBlank() } }
                        ?: return@mapNotNull null
                    val wall = wallsByNeighborhood[neighborhoodKey]
                    val wallId = wall?.id
                    NeighborhoodCommunity(
                        name = neighborhoodName,
                        users = neighborhoodUsers
                            .distinctBy { it.id }
                            .sortedBy { it.displayName.lowercase() },
                        conversationId = wallId?.let(::wallConversationId),
                        lastMessagePreview = null,
                        lastMessageAtMillis = wall?.chat_last_at?.toEpochMillisOrNull(),
                        messageCount = wall?.chat_count ?: 0
                    )
                }.sortedWith(
                    compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }
                        .thenBy { it.name.lowercase() }
                )
            }.catch { emit(emptyList()) }
            combine(communitiesFlow, chatRepository.observeConversations()) { communities, conversations ->
                communities.map { community ->
                    val communityConversation = conversations.firstOrNull { conversation ->
                        conversation.matchesCommunity(community.name)
                    }
                    community.copy(
                        conversationId = communityConversation?.id ?: community.conversationId,
                        lastMessagePreview = communityConversation
                            ?.lastMessagePreview
                            ?.takeIf { it.isNotBlank() }
                            ?: community.lastMessagePreview,
                        lastMessageAtMillis = communityConversation?.updatedAtMillis ?: community.lastMessageAtMillis
                    )
                }.sortedWith(
                    compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }
                        .thenBy { it.name.lowercase() }
                )
            }
        }
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val cleanNeighborhood = neighborhood.trim()
        if (cleanNeighborhood.isBlank()) error("Barrio no valido")
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")

        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreateNeighborhoodConversation(cleanNeighborhood, session.userId, session.displayName)
        }

        chatRepository.cachedCommunityConversationId(cleanNeighborhood)?.let { return@runCatching it }

        val wall = supabaseApi.getActiveWallsStats()
            .firstOrNull { wall -> wall.matchesNeighborhood(cleanNeighborhood) }
        val communityId = wall?.id ?: cleanNeighborhood.normalizeName()
        val communityTitle = wall?.name?.takeIf { it.isNotBlank() } ?: cleanNeighborhood
        chatRepository.openCommunityConversation(
            communityId = communityId,
            title = communityTitle,
            participantIds = listOf(session.userId)
        ).getOrThrow()
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            val nextFollowing = !MockData.isFollowing(userId)
            MockData.toggleFollowUser(userId)
            return@runCatching FollowUserResult(
                userId = userId,
                isFollowing = nextFollowing,
                currentUser = session.toNeighborhoodUser()
            )
        }
        val toggle = supabaseApi.toggleProfileFollow(session.userId, userId)
        FollowUserResult(
            userId = userId,
            isFollowing = toggle.active == true,
            currentUser = session.toNeighborhoodUser()
        )
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun reportPost(postId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.reportPost(postId)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            supabaseApi.reportUgc(session.userId, "community_post", postId, "other")
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreatePrivateConversation(userId, session.userId, session.displayName)
        }
        chatRepository.cachedPrivateConversationId(userId)?.let { return@runCatching it }
        chatRepository.openGroupConversation(listOf(userId), title = null).getOrThrow()
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

    override suspend fun isCurrentUserAdmin(): Boolean {
        val session = sessionManager.currentSession() ?: return false
        if (AppConfig.USE_MOCK_BACKEND) return session.displayName.equals("Gabriel", ignoreCase = true)
        return profileRemote.getProfile(session.userId)?.is_admin == true
    }

    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> =
        runCatching {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            if (AppConfig.USE_MOCK_BACKEND) {
                val user = MockData.registeredUsers.firstOrNull { it.id == userId } ?: error("Usuario no encontrado")
                return@runCatching user.toNeighborhoodUser().copy(isAdmin = isAdmin, isOfficial = isOfficial)
            }
            val currentProfile = profileRemote.getProfile(session.userId)
            check(currentProfile?.is_admin == true) { "No tienes permisos de administrador" }
            val updated = supabaseApi.updateProfileRoles(userId, isAdmin, isOfficial)
                ?: error("No se pudo actualizar el usuario")
            updated.toNeighborhoodUserReal()
        }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? =
        profileCacheStore.read(userId, maxAgeMillis)

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCacheStore.write(profile)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> {
        if (AppConfig.USE_MOCK_BACKEND) {
            return flowOf(runCatching { mockCommunityUserProfile(userId) })
        }
        val currentUserId = sessionManager.currentSession()?.userId
        return profileRemote.observeProfile(userId)
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf<Result<CommunityUserProfile>>(Result.failure(IllegalStateException("Usuario no encontrado")))
                } else {
                    supabaseApi.observeFeedPosts(profileId = userId)
                        .flatMapLatest { remotePosts ->
                            val postIds = remotePosts.map { it.id }
                            combine(
                                supabaseApi.observeComments(postIds),
                                supabaseApi.observeLikes(postIds)
                            ) { comments, likes ->
                                ProfilePostSnapshot(
                                    profile = profile,
                                    posts = remotePosts,
                                    comments = comments,
                                    likes = likes
                                )
                            }
                        }
                        .flatMapLatest { postSnapshot ->
                            val interactionProfileIds = postSnapshot.interactionProfileIds()
                            val interactionProfilesFlow = if (interactionProfileIds.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                supabaseApi.observeProfiles(interactionProfileIds)
                            }
                            combine(
                                interactionProfilesFlow,
                                supabaseApi.observeProfileFollows(followedProfileId = userId),
                                supabaseApi.observeProfileFollows(followerProfileId = userId),
                                currentUserId
                                    ?.let { supabaseApi.observeProfileFollows(followerProfileId = it) }
                                    ?: flowOf(emptyList())
                            ) { interactionProfiles, followers, following, currentFollowing ->
                                ProfileRelationshipSnapshot(
                                    postSnapshot = postSnapshot,
                                    interactionProfiles = interactionProfiles,
                                    followers = followers,
                                    following = following,
                                    currentFollowing = currentFollowing
                                )
                            }
                        }
                        .flatMapLatest { relationshipSnapshot ->
                            val relatedIds = relationshipSnapshot.relatedProfileIds()
                            val relatedProfilesFlow = if (relatedIds.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                supabaseApi.observeProfiles(relatedIds)
                            }
                            relatedProfilesFlow.map { relatedProfiles ->
                                val profile = buildCommunityUserProfile(
                                    profile = relationshipSnapshot.postSnapshot.profile,
                                    remotePosts = relationshipSnapshot.postSnapshot.posts,
                                    comments = relationshipSnapshot.postSnapshot.comments,
                                    likes = relationshipSnapshot.postSnapshot.likes,
                                    interactionProfiles = relationshipSnapshot.interactionProfiles,
                                    followers = relationshipSnapshot.followers,
                                    following = relationshipSnapshot.following,
                                    relatedProfiles = relatedProfiles,
                                    currentFollowingIds = relationshipSnapshot.currentFollowing.mapNotNull { it.followed_profile_id }.toSet(),
                                    currentUserId = currentUserId,
                                    userId = userId
                                )
                                profileCacheStore.write(profile)
                                Result.success(profile)
                            }
                        }
                }
            }
            .catch { error ->
                emit(Result.failure<CommunityUserProfile>(error).mapFailureToUserFacing(appContext, R.string.error_load_profile))
            }
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching mockCommunityUserProfile(userId)
        }
        val profile = profileRemote.getProfile(userId) ?: error("Usuario no encontrado")
        val currentUserId = sessionManager.currentSession()?.userId
        val remotePosts = supabaseApi.getFeedPosts(profileId = userId)
        val postIds = remotePosts.map { it.id }
        val comments = supabaseApi.getComments(postIds)
        val likes = supabaseApi.getLikes(postIds)
        val interactionProfileIds = (
            comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
        val interactionProfilesById = if (interactionProfileIds.isEmpty()) {
            emptyMap()
        } else {
            supabaseApi.getProfiles(interactionProfileIds).associateBy { it.id }
        }
        val followers = supabaseApi.getProfileFollows(followedProfileId = userId)
        val following = supabaseApi.getProfileFollows(followerProfileId = userId)
        val relatedIds = (
            followers.mapNotNull { it.follower_profile_id } +
                following.mapNotNull { it.followed_profile_id }
            ).distinct()
        val relatedProfiles = if (relatedIds.isEmpty()) emptyList() else supabaseApi.getProfiles(relatedIds)
        val currentFollowingIds = currentUserId
            ?.let { supabaseApi.getProfileFollows(followerProfileId = it).mapNotNull { follow -> follow.followed_profile_id }.toSet() }
            .orEmpty()
        buildCommunityUserProfile(
            profile = profile,
            remotePosts = remotePosts,
            comments = comments,
            likes = likes,
            interactionProfiles = interactionProfilesById.values.toList(),
            followers = followers,
            following = following,
            relatedProfiles = relatedProfiles,
            currentFollowingIds = currentFollowingIds,
            currentUserId = currentUserId,
            userId = userId
        ).also { profileCacheStore.write(it) }
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

    private suspend fun buildCommunityUserProfile(
        profile: CommunityProfile,
        remotePosts: List<CommunityPost>,
        comments: List<CommunityComment>,
        likes: List<CommunityPostLike>,
        interactionProfiles: List<CommunityProfile>,
        followers: List<CommunityProfileFollow>,
        following: List<CommunityProfileFollow>,
        relatedProfiles: List<CommunityProfile>,
        currentFollowingIds: Set<String>,
        currentUserId: String?,
        userId: String
    ): CommunityUserProfile {
        val interactionProfilesById = interactionProfiles.associateBy { it.id }
        val posts = remotePosts.map { post ->
            val postComments = comments
                .filter { it.post_id == post.id }
                .toDomainComments { comment ->
                    interactionProfilesById[comment.profile_id]?.display_name
                        ?: interactionProfilesById[comment.profile_id]?.nombre
                        ?: "Usuario"
                }
            val postLikes = likes.filter { it.post_id == post.id }
            post.toDomain(
                author = profile.toDomainUser(),
                comments = postComments,
                likesCount = postLikes.size,
                likedByCurrentUser = currentUserId != null && postLikes.any { it.profile_id == currentUserId }
            )
        }
        val relatedProfilesById = relatedProfiles.associateBy { it.id }
        val followerUsers = followers.mapNotNull { follow ->
            relatedProfilesById[follow.follower_profile_id]?.toNeighborhoodUserReal(
                isFollowing = follow.follower_profile_id?.let { it in currentFollowingIds } == true
            )
        }
        val followingUsers = following.mapNotNull { follow ->
            relatedProfilesById[follow.followed_profile_id]?.toNeighborhoodUserReal(
                isFollowing = follow.followed_profile_id?.let { it in currentFollowingIds } == true
            )
        }
        val attachments = currentUserId
            ?.let { loadSharedSupabaseAttachments(it, userId, profile.displayName()) }
            .orEmpty()
        return CommunityUserProfile(
            user = profile.toNeighborhoodUserReal(
                isFollowing = currentUserId != null && followers.any { it.follower_profile_id == currentUserId },
                followersCount = followers.size,
                followingCount = following.size,
                postsCount = posts.size
            ),
            posts = posts,
            attachments = attachments,
            followers = followerUsers,
            following = followingUsers
        )
    }

    private fun mockCommunityUserProfile(userId: String): CommunityUserProfile {
        val user = MockData.registeredUsers.firstOrNull { it.id == userId } ?: error("Usuario no encontrado")
        return CommunityUserProfile(
            user = user.toNeighborhoodUser(),
            posts = MockData.posts.filter { it.author.id == userId },
            attachments = MockData.profileAttachmentsWith(
                userId = userId,
                currentUserId = sessionManager.currentSession()?.userId ?: MockData.currentUser.id
            ),
            followers = MockData.followersFor(userId).map { it.toNeighborhoodUser() },
            following = MockData.followingFor(userId).map { it.toNeighborhoodUser() }
        )
    }

    private suspend fun loadSharedSupabaseAttachments(
        currentUserId: String,
        targetUserId: String,
        targetDisplayName: String
    ): List<ProfileAttachment> {
        val payload = supabaseApi.listSharedChatAttachments(
            profileId = currentUserId,
            peerProfileId = targetUserId,
            limit = 120
        )
        return payload.objOrNull
            ?.array("files")
            .orEmpty()
            .mapNotNull { it.objOrNull?.toProfileAttachment(targetDisplayName) }
            .distinctBy { attachment -> attachment.deduplicationKey() }
            .sortedByDescending { attachment -> attachment.sentAtMillis ?: 0L }
    }

    private fun ProfileAttachment.deduplicationKey(): String =
        "${uri.substringBefore('?').lowercase()}|${name.lowercase()}"

    private fun JsonObject.toProfileAttachment(defaultSenderName: String): ProfileAttachment? {
        val uri = string("url")
            ?: string("file_url")
            ?: obj("thumb")?.string("url")
            ?: string("thumb")
            ?: return null
        val name = string("name")
            ?: string("file_name")
            ?: uri.substringBefore('?').substringAfterLast('/').ifBlank { "archivo" }
        return ProfileAttachment(
            id = "sb:${long("id") ?: string("id") ?: uri.hashCode()}",
            name = name,
            uri = uri,
            mimeType = string("mime_type") ?: inferAttachmentMimeType(name, uri, string("ext")),
            sentAtMillis = long("created_at_millis") ?: string("created_at")?.toEpochMillisOrNull(),
            senderName = string("sender_name")?.takeIf { it.isNotBlank() } ?: defaultSenderName
        )
    }

    private fun inferAttachmentMimeType(name: String?, uri: String, ext: String?): String? {
        val extension = ext
            ?.takeIf { it.isNotBlank() }
            ?: name?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
            ?: uri.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        return when (extension?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "rtf" -> "application/rtf"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            else -> null
        }
    }

    private val JsonElement.objOrNull: JsonObject?
        get() = this as? JsonObject

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): List<JsonElement> =
        (get(key) as? JsonArray)?.toList().orEmpty()

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        (get(key) as? JsonPrimitive)?.longOrNull

    private fun buildCommunities(
        users: List<NeighborhoodUser>,
        conversations: List<Conversation>,
        messages: List<Message>
    ): List<NeighborhoodCommunity> {
        val usersByNeighborhood = users
            .filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood.trim() }
        return usersByNeighborhood.map { (neighborhood, neighborhoodUsers) ->
            val conversation = conversations.firstOrNull { it.isNeighborhoodConversation(neighborhood) }
            val conversationMessages = conversation?.let { current ->
                messages.filter { it.conversationId == current.id }
            }.orEmpty()
            val lastMessage = conversationMessages.lastOrNull()
            NeighborhoodCommunity(
                name = neighborhood,
                users = neighborhoodUsers.sortedBy { it.displayName.lowercase() },
                conversationId = conversation?.id,
                lastMessagePreview = lastMessage?.let { "${it.senderName}: ${it.text}" },
                lastMessageAtMillis = conversation?.updatedAtMillis,
                messageCount = conversationMessages.size
            )
        }.sortedWith(
            compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun Conversation.isNeighborhoodConversation(neighborhood: String): Boolean =
        isGroup && !isEmergency && (communityName ?: title).equals(neighborhood, ignoreCase = true)

    private fun Conversation.matchesCommunity(neighborhood: String): Boolean {
        if (!isGroup || isEmergency) return false
        val normalizedNeighborhood = neighborhood.normalizeName()
        return listOf(communityName, title)
            .filterNotNull()
            .any { it.normalizeName() == normalizedNeighborhood }
    }

    private fun User.toNeighborhoodUser(): NeighborhoodUser =
        NeighborhoodUser(
            id = id,
            displayName = displayName,
            email = email,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl,
            isAdmin = isAdmin,
            isOfficial = isOfficial,
            isFollowing = MockData.isFollowing(id),
            followersCount = MockData.followersCount(id),
            followingCount = MockData.followingCount(id),
            postsCount = MockData.posts.count { it.author.id == id }
        )

    private fun AuthSession.toNeighborhoodUser(): NeighborhoodUser {
        MockData.registeredUsers.firstOrNull { it.id == userId }?.let { return it.toNeighborhoodUser() }
        return NeighborhoodUser(
            id = userId,
            displayName = displayName,
            email = email,
            neighborhood = ""
        )
    }

    private fun CommunityProfile.toNeighborhoodUserReal(
        isFollowing: Boolean = false,
        followersCount: Int = followers_count ?: 0,
        followingCount: Int = following_count ?: 0,
        postsCount: Int = 0
    ): NeighborhoodUser =
        NeighborhoodUser(
            id = id,
            displayName = display_name?.takeIf { it.isNotBlank() }
                ?: nombre?.takeIf { it.isNotBlank() }
                ?: phone_local.orEmpty(),
            email = "${country_code.orEmpty()}${phone_local.orEmpty()}@phone.quata.app",
            neighborhood = neighborhood?.takeIf { it.isNotBlank() }
                ?: barrio?.takeIf { it.isNotBlank() }
                ?: barrio_normalized.orEmpty(),
            avatarUrl = avatar_url ?: avatar,
            isAdmin = is_admin == true,
            isOfficial = is_official == true,
            isFollowing = isFollowing,
            followersCount = followersCount,
            followingCount = followingCount,
            postsCount = postsCount
        )

    private fun CommunityProfile.belongsToNeighborhood(neighborhoodName: String): Boolean {
        val cleanName = neighborhoodName.normalizeName()
        return listOf(neighborhood, barrio, barrio_normalized)
            .filterNotNull()
            .any { it.normalizeName() == cleanName }
    }

    private fun CommunityWallStats.matchesNeighborhood(neighborhoodName: String): Boolean =
        neighborhoodName.normalizeName() in communityKeys()

    private fun CommunityWallStats.communityKeys(): Set<String> =
        listOf(name, slug, normalized_name)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .map { it.normalizeName() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun CommunityProfile.displayName(): String =
        display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: phone_local?.takeIf { it.isNotBlank() }
            ?: "Usuario"

    private fun String.toEpochMillisOrNull(): Long? =
        runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()

    private fun String.normalizeName(): String {
        val withoutMarks = Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutMarks
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private data class ProfilePostSnapshot(
        val profile: CommunityProfile,
        val posts: List<CommunityPost>,
        val comments: List<CommunityComment>,
        val likes: List<CommunityPostLike>
    ) {
        fun interactionProfileIds(): List<String> = (
            comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
    }

    private data class ProfileRelationshipSnapshot(
        val postSnapshot: ProfilePostSnapshot,
        val interactionProfiles: List<CommunityProfile>,
        val followers: List<CommunityProfileFollow>,
        val following: List<CommunityProfileFollow>,
        val currentFollowing: List<CommunityProfileFollow>
    ) {
        fun relatedProfileIds(): List<String> = (
            followers.mapNotNull { it.follower_profile_id } +
                following.mapNotNull { it.followed_profile_id }
            ).distinct()
    }
}
