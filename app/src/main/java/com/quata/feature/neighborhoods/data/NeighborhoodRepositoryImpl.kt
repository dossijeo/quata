package com.quata.feature.neighborhoods.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.feature.chat.data.ChatRemoteDataSource
import com.quata.feature.chat.data.toDomain
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.profile.data.ProfileRemoteDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class NeighborhoodRepositoryImpl(
    private val profileRemote: ProfileRemoteDataSource,
    private val chatRemote: ChatRemoteDataSource,
    private val sessionManager: SessionManager
) : NeighborhoodRepository {
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
            flow {
                while (true) {
                    val profiles = profileRemote.getDirectoryProfiles().map { dto ->
                        NeighborhoodUser(
                            id = dto.id,
                            displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.email.orEmpty(),
                            email = dto.email.orEmpty(),
                            neighborhood = dto.neighborhood.orEmpty(),
                            avatarUrl = dto.avatarUrl
                        )
                    }
                    val conversations = chatRemote.getConversations().map { it.toDomain() }
                    val messages = conversations.flatMap { chatRemote.getMessages(it.id).map { dto -> dto.toDomain("") } }
                    emit(buildCommunities(profiles, conversations, messages))
                    delay(5_000)
                }
            }
        }
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val cleanNeighborhood = neighborhood.trim()
        if (cleanNeighborhood.isBlank()) error("Barrio no valido")
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")

        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreateNeighborhoodConversation(cleanNeighborhood, session.displayName)
        }

        chatRemote.getConversations()
            .map { it.toDomain() }
            .firstOrNull { it.isNeighborhoodConversation(cleanNeighborhood) }
            ?.let { return@runCatching it.id }

        val profiles = profileRemote.getDirectoryProfiles()
        val participantIds = (
            profiles
                .filter { it.neighborhood.equals(cleanNeighborhood, ignoreCase = true) }
                .map { it.id } + session.userId
            ).distinct()

        chatRemote.createConversation(
            title = cleanNeighborhood,
            participantIds = participantIds,
            lastMessagePreview = "",
            communityName = cleanNeighborhood
        ).firstOrNull()?.id ?: error("No se pudo crear el chat del barrio")
    }

    override suspend fun toggleFollowUser(userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.toggleFollowUser(userId)
            return@runCatching
        }
        // Supabase real: aqui se alternara la relacion current_user -> userId en la tabla de seguidores.
    }

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreatePrivateConversation(userId, session.displayName)
        }
        val target = profileRemote.getDirectoryProfiles().firstOrNull { it.id == userId }
            ?: error("Usuario no encontrado")
        chatRemote.getConversations()
            .map { it.toDomain() }
            .firstOrNull { conversation ->
                !conversation.isGroup &&
                    !conversation.isEmergency &&
                    session.userId in conversation.participantIds &&
                    userId in conversation.participantIds
            }
            ?.let { return@runCatching it.id }

        val conversation = chatRemote.createConversation(
            title = target.displayName?.takeIf { it.isNotBlank() } ?: target.email.orEmpty(),
            participantIds = listOf(session.userId, userId).distinct(),
            lastMessagePreview = ""
        ).firstOrNull() ?: error("No se pudo abrir PRIVI")
        conversation.id
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.registeredUsers.firstOrNull { it.id == userId } ?: error("Usuario no encontrado")
            return@runCatching CommunityUserProfile(
                user = user.toNeighborhoodUser(),
                posts = MockData.posts.filter { it.author.id == userId }
            )
        }
        val profile = profileRemote.getDirectoryProfiles().firstOrNull { it.id == userId } ?: error("Usuario no encontrado")
        CommunityUserProfile(
            user = NeighborhoodUser(
                id = profile.id,
                displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email.orEmpty(),
                email = profile.email.orEmpty(),
                neighborhood = profile.neighborhood.orEmpty(),
                avatarUrl = profile.avatarUrl
            ),
            posts = emptyList()
        )
    }

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
}
