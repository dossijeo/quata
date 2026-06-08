package com.quata.feature.neighborhoods.data

import android.content.Context
import com.quata.R
import com.quata.bettermessages.BetterMessagesRepository
import com.quata.bettermessages.BmThreadAttachmentFile
import com.quata.bettermessages.BmThreadResponse
import com.quata.bettermessages.BmUser
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.AuthSession
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityPostLike
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.CommunityProfileFollow
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.chat.data.BetterMessagesAbandonedConversationStore
import com.quata.feature.chat.data.BetterMessagesConversationCacheStore
import com.quata.feature.chat.data.betterMessagesThreadIdOrNull
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class NeighborhoodRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val betterMessagesRepository: BetterMessagesRepository,
    private val chatRepository: ChatRepository,
    private val profileRemote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager
) : NeighborhoodRepository {
    private val profileCacheStore = CommunityProfileCacheStore(appContext)
    private val conversationCacheStore = BetterMessagesConversationCacheStore(appContext)

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
                val users = profiles.map { it.toNeighborhoodUserReal() }
                walls.map { wall ->
                    val wallName = wall.name ?: wall.slug ?: "Comunidad"
                    val wallUsers = users
                        .filter { user -> user.neighborhood.equals(wall.name, ignoreCase = true) || user.neighborhood.equals(wall.normalized_name, ignoreCase = true) }
                        .sortedBy { it.displayName.lowercase() }
                    NeighborhoodCommunity(
                        name = wallName,
                        users = wallUsers,
                        conversationId = wallConversationId(wall.id),
                        lastMessagePreview = null,
                        lastMessageAtMillis = wall.chat_last_at?.toEpochMillisOrNull(),
                        messageCount = wall.chat_count ?: 0
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
            .firstOrNull { wall ->
                wall.name.equals(cleanNeighborhood, ignoreCase = true) ||
                    wall.slug.equals(cleanNeighborhood, ignoreCase = true) ||
                    wall.normalized_name.equals(cleanNeighborhood, ignoreCase = true)
            }
        val communityId = wall?.id ?: cleanNeighborhood.normalizeName()
        val communityTitle = wall?.name?.takeIf { it.isNotBlank() } ?: cleanNeighborhood
        val memberIds = profileRemote.getDirectoryProfiles()
            .filter { profile -> profile.belongsToNeighborhood(cleanNeighborhood) }
            .map { it.id }
            .distinct()
        if (memberIds.filterNot { it == session.userId }.isEmpty()) {
            error(appContext.getString(R.string.error_group_chat_no_members))
        }
        chatRepository.openCommunityConversation(
            communityId = communityId,
            title = communityTitle,
            participantIds = memberIds + session.userId
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
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreatePrivateConversation(userId, session.userId, session.displayName)
        }
        chatRepository.cachedPrivateConversationId(userId)?.let { return@runCatching it }
        supabaseApi.createOrGetPrivateChat(session.userId, userId)
        val betterUrl = betterMessagesRepository.openOrGetPrivateUrl(session.userId, userId)
        val threadId = betterUrl.threadId?.takeIf { it > 0 }
            ?: betterMessagesRepository.getOrCreatePrivateThread(session.userId, userId)
                .threads
                .firstOrNull { it.threadId > 0 }
                ?.threadId
            ?: error("Better Messages no devolvio thread_id")
        BetterMessagesAbandonedConversationStore(appContext).clearAbandoned(session.userId, threadId)
        "bm:$threadId"
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

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
            ?.let { loadSharedBetterMessagesAttachments(it, userId, profile.displayName()) }
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

    private suspend fun loadSharedBetterMessagesAttachments(
        currentUserId: String,
        targetUserId: String,
        targetDisplayName: String
    ): List<ProfileAttachment> {
        val conversations = conversationCacheStore.cachedConversations(currentUserId)
            .filter { conversation -> conversation.isVisible && !conversation.isEmergency }
        if (conversations.isEmpty()) return emptyList()

        val currentParticipantKeys = participantKeysForProfile(currentUserId, conversations)
        val targetParticipantKeys = participantKeysForProfile(targetUserId, conversations)
        val threadIds = conversations
            .filter { conversation ->
                conversation.hasAnyParticipant(currentParticipantKeys) &&
                    conversation.hasAnyParticipant(targetParticipantKeys) &&
                    conversation.isNotBlockedFor(currentParticipantKeys) &&
                    conversation.isNotBlockedFor(targetParticipantKeys)
            }
            .mapNotNull { conversation -> conversation.id.betterMessagesThreadIdOrNull() }
            .distinct()

        return threadIds
            .flatMap { threadId ->
                val cachedThread = conversationCacheStore.cachedThreadResponse(currentUserId, threadId)
                val senderNamesByMessageId = cachedThread?.senderNamesByMessageId().orEmpty()
                runCatching {
                    betterMessagesRepository.loadThreadAttachments(
                        profileId = currentUserId,
                        threadId = threadId,
                        page = 1,
                        perPage = 20
                    )
                }.getOrNull()
                    ?.files
                    .orEmpty()
                    .mapNotNull { file ->
                        file.toProfileAttachment(
                            threadId = threadId,
                            senderName = file.messageId?.let(senderNamesByMessageId::get) ?: targetDisplayName
                        )
                    }
            }
            .distinctBy { attachment -> attachment.id }
            .sortedByDescending { attachment -> attachment.sentAtMillis ?: 0L }
    }

    private suspend fun participantKeysForProfile(
        profileId: String,
        conversations: List<Conversation>
    ): Set<String> {
        val keys = mutableSetOf(profileId)
        val needsWpKey = conversations.any { conversation ->
            profileId !in conversation.participantIds &&
                (conversation.participantIds.any { it.startsWith("wp:") } ||
                    conversation.blockedUserIds.any { it.startsWith("wp:") })
        }
        if (needsWpKey) {
            runCatching { betterMessagesRepository.lookupWordPressUserId(profileId) }
                .getOrNull()
                ?.takeIf { it > 0 }
                ?.let { keys += "wp:$it" }
        }
        return keys
    }

    private fun Conversation.hasAnyParticipant(keys: Set<String>): Boolean =
        participantIds.any { it in keys }

    private fun Conversation.isNotBlockedFor(keys: Set<String>): Boolean =
        blockedUserIds.none { it in keys }

    private fun BmThreadResponse.senderNamesByMessageId(): Map<Int, String> {
        val usersByWpId = users.associateByWpId()
        return messages.associate { message ->
            message.messageId to (usersByWpId[message.senderId]?.name ?: "Usuario")
        }
    }

    private fun List<BmUser>.associateByWpId(): Map<Int, BmUser> =
        mapNotNull { user ->
            val id = user.userId ?: user.id.toIntOrNull()
            id?.let { it to user }
        }.toMap()

    private fun BmThreadAttachmentFile.toProfileAttachment(
        threadId: Int,
        senderName: String
    ): ProfileAttachment? {
        val attachmentUri = url?.takeIf { it.isNotBlank() }
            ?: thumb?.asStringOrNull()
            ?: return null
        return ProfileAttachment(
            id = "bm:$threadId:$id",
            name = name?.takeIf { it.isNotBlank() } ?: attachmentUri.substringAfterLast('/').ifBlank { "archivo" },
            uri = attachmentUri,
            mimeType = mimeType,
            sentAtMillis = date?.toBetterMessagesAttachmentMillisOrNull(),
            senderName = senderName
        )
    }

    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

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
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            avatarUrl = avatar_url ?: avatar,
            isFollowing = isFollowing,
            followersCount = followersCount,
            followingCount = followingCount,
            postsCount = postsCount
        )

    private fun CommunityProfile.belongsToNeighborhood(neighborhoodName: String): Boolean {
        val cleanName = neighborhoodName.trim()
        return neighborhood.equals(cleanName, ignoreCase = true) ||
            barrio.equals(cleanName, ignoreCase = true)
    }

    private fun CommunityProfile.displayName(): String =
        display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: phone_local?.takeIf { it.isNotBlank() }
            ?: "Usuario"

    private fun String.toEpochMillisOrNull(): Long? =
        runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()

    private fun String.toBetterMessagesAttachmentMillisOrNull(): Long? =
        runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
            ?: try {
                LocalDateTime.parse(this, BETTER_MESSAGES_ATTACHMENT_DATE_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }

    private fun String.normalizeName(): String = trim().lowercase()

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

    private companion object {
        val BETTER_MESSAGES_ATTACHMENT_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
