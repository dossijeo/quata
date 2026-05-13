package com.quata.core.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.NotificationItem
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

object MockData {
    data class MockUserProfile(
        val id: String,
        val email: String,
        var displayName: String,
        var neighborhood: String,
        var countryCode: String,
        var phone: String,
        var password: String,
        var secretQuestion: String,
        var secretAnswer: String,
        var avatarUrl: String? = null,
        var emergencyContactIds: List<String> = emptyList(),
        var emergencyMessage: String? = null,
        var emergencyMessageIsDefault: Boolean = true
    ) {
        fun toUser(): User = User(
            id = id,
            email = email,
            displayName = displayName,
            neighborhood = neighborhood,
            avatarUrl = avatarUrl
        )
    }

    private val mockProfiles = mutableListOf(
        MockUserProfile("u_current", "gabriel@quata.app", "Gabriel", "La Chana", "240", "680242606", "Gabriel2026!", "barrio", "La Chana"),
        MockUserProfile("u_ana", "ana@quata.app", "Ana", "Sampaka", "240", "680100101", "Ana2026!", "madre", "Maria"),
        MockUserProfile("u_leo", "leo@quata.app", "Leo", "Bikuy", "240", "680100102", "Leo2026!", "amigo", "Ondo"),
        MockUserProfile("u_sara", "sara@quata.app", "Sara", "Malabo", "240", "680100103", "Sara2026!", "comida", "Sopa de pescado"),
        MockUserProfile("u_ji", "ji@quata.app", "JI", "La ferme", "240", "680100104", "Ji2026!", "barrio", "La ferme"),
        MockUserProfile("u_marcelino", "marcelino@quata.app", "Marcelino", "Molyko", "240", "680100105", "Marcelino2026!", "madre", "Teresa"),
        MockUserProfile("u_obiang", "obiang@quata.app", "Obiang", "Mindoube", "240", "680100106", "Obiang2026!", "amigo", "Marcelino"),
        MockUserProfile("u_maribel", "maribel@quata.app", "maribelamdemeekandoh", "Iyubu", "240", "680100107", "Maribel2026!", "comida", "Arroz"),
        MockUserProfile("u_ondo", "ondo@quata.app", "Ondo", "Molyko", "240", "680100108", "Ondo2026!", "barrio", "Molyko"),
        MockUserProfile("u_melo", "melo@quata.app", "Melo", "Sampaka", "240", "680100109", "Melo2026!", "madre", "Francisca")
    )

    val mockAuthProfiles: List<MockUserProfile>
        get() = mockProfiles

    val currentUser: User
        get() = profileById("u_current")?.toUser() ?: mockProfiles.first().toUser()

    private val ana: User get() = userById("u_ana")
    private val leo: User get() = userById("u_leo")
    private val sara: User get() = userById("u_sara")
    private val ji: User get() = userById("u_ji")
    private val marcelino: User get() = userById("u_marcelino")
    private val obiang: User get() = userById("u_obiang")
    private val maribel: User get() = userById("u_maribel")
    private val ondo: User get() = userById("u_ondo")
    private val melo: User get() = userById("u_melo")

    val registeredUsers: List<User>
        get() = mockProfiles.map { it.toUser() }

    fun profileById(id: String): MockUserProfile? = mockProfiles.firstOrNull { it.id == id }

    fun profileByPhone(countryCode: String, phone: String): MockUserProfile? {
        val cleanCode = countryCode.onlyDigits()
        val cleanPhone = phone.onlyDigits()
        return mockProfiles.firstOrNull {
            it.countryCode.onlyDigits() == cleanCode && it.phone.onlyDigits() == cleanPhone
        }
    }

    fun validatePassword(profile: MockUserProfile, password: String): Boolean =
        profile.password == password

    fun validateSecretAnswer(profile: MockUserProfile, answer: String): Boolean =
        profile.secretAnswer.trim().equals(answer.trim(), ignoreCase = true)

    fun updatePassword(profileId: String, newPassword: String) {
        profileById(profileId)?.password = newPassword
    }

    fun updateProfile(
        profileId: String,
        displayName: String,
        neighborhood: String,
        countryCode: String,
        phone: String,
        avatarUrl: String?,
        secretQuestion: String,
        secretAnswer: String,
        emergencyContactIds: List<String>,
        emergencyMessage: String,
        emergencyMessageIsDefault: Boolean
    ) {
        profileById(profileId)?.let { profile ->
            profile.displayName = displayName
            profile.neighborhood = neighborhood
            profile.countryCode = countryCode
            profile.phone = phone
            profile.avatarUrl = avatarUrl
            if (secretQuestion.isNotBlank()) profile.secretQuestion = secretQuestion
            if (secretAnswer.isNotBlank()) profile.secretAnswer = secretAnswer
            profile.emergencyContactIds = emergencyContactIds
            profile.emergencyMessage = emergencyMessage
            profile.emergencyMessageIsDefault = emergencyMessageIsDefault
            socialState.value = socialState.value + 1
        }
    }

    fun createProfile(
        displayName: String,
        neighborhood: String,
        countryCode: String,
        phone: String,
        password: String,
        secretQuestion: String,
        secretAnswer: String
    ): MockUserProfile {
        val id = "u_mock_${System.currentTimeMillis()}"
        val email = "${countryCode.onlyDigits()}${phone.onlyDigits()}@phone.quata.app"
        val profile = MockUserProfile(
            id = id,
            email = email,
            displayName = displayName,
            neighborhood = neighborhood,
            countryCode = countryCode,
            phone = phone,
            password = password,
            secretQuestion = secretQuestion,
            secretAnswer = secretAnswer
        )
        mockProfiles.add(profile)
        socialState.value = socialState.value + 1
        return profile
    }

    fun userById(id: String): User =
        profileById(id)?.toUser() ?: currentUser

    private fun String.onlyDigits(): String = filter(Char::isDigit)

    private val mutableFollowing = mutableSetOf("u_ana")
    private val followerCounts = mutableMapOf(
        "u_current" to 1,
        "u_ana" to 4,
        "u_leo" to 2,
        "u_sara" to 3,
        "u_ji" to 1,
        "u_marcelino" to 0,
        "u_obiang" to 0,
        "u_maribel" to 0,
        "u_ondo" to 1,
        "u_melo" to 2
    )
    private val followingCounts = mutableMapOf(
        "u_current" to mutableFollowing.size,
        "u_ana" to 2,
        "u_leo" to 1,
        "u_sara" to 1,
        "u_ji" to 0,
        "u_marcelino" to 1,
        "u_obiang" to 0,
        "u_maribel" to 0,
        "u_ondo" to 0,
        "u_melo" to 3
    )
    private val socialState = MutableStateFlow(0)

    val socialFlow: StateFlow<Int>
        get() = socialState.asStateFlow()

    fun isFollowing(userId: String): Boolean = userId in mutableFollowing
    fun followersCount(userId: String): Int = followerCounts[userId] ?: 0
    fun followingCount(userId: String): Int = followingCounts[userId] ?: 0

    fun toggleFollowUser(userId: String) {
        if (userId == currentUser.id) return
        if (userId in mutableFollowing) {
            mutableFollowing.remove(userId)
            followerCounts[userId] = ((followerCounts[userId] ?: 0) - 1).coerceAtLeast(0)
        } else {
            mutableFollowing.add(userId)
            followerCounts[userId] = (followerCounts[userId] ?: 0) + 1
        }
        followingCounts[currentUser.id] = mutableFollowing.size
        socialState.value = socialState.value + 1
    }

    private val mutablePosts = mutableListOf(
        Post(
            id = "p1",
            author = ana,
            text = "",
            imageUrl = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80",
            placeName = "Sampaka- MALABO",
            rankingLabel = "#6",
            createdAt = "Hace 3 min",
            likesCount = 42,
            comments = listOf(
                PostComment("c_p1_1", "Juan", "Leyendas", "19/4/2026, 1:23:17"),
                PostComment("c_p1_2", "Juan", "🔥", "19/4/2026, 1:26:12"),
                PostComment("c_p1_3", "Melo", "Ese cielo esta brutal", "19/4/2026, 1:27:08")
            )
        ),
        Post(
            id = "p2",
            author = leo,
            text = "A veces una sola frase basta para contar el dia.",
            placeName = null,
            rankingLabel = "#11",
            createdAt = "Hace 18 min",
            likesCount = 18,
            comments = listOf(
                PostComment("c_p2_1", "Bikuy", "Totalmente", "19/4/2026, 2:02:44"),
                PostComment("c_p2_2", "Ana", "Me quedo con esa frase", "19/4/2026, 2:05:10")
            )
        ),
        Post(
            id = "p3",
            author = sara,
            text = "Un clip rapido desde la costa. El ambiente estaba increible y queria guardar este momento para compartirlo con todos.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            placeName = null,
            rankingLabel = "#3",
            createdAt = "Hace 1 h",
            likesCount = 87,
            comments = listOf(
                PostComment("c_p3_1", "Melo", "Dale play otra vez", "19/4/2026, 3:12:03"),
                PostComment("c_p3_2", "Sara", "🔥🔥🔥", "19/4/2026, 3:13:42"),
                PostComment("c_p3_3", "Leo", "Muy buen clip", "19/4/2026, 3:15:19"),
                PostComment("c_p3_4", "Juan", "Guardado", "19/4/2026, 3:18:51")
            )
        )
    )
    private val postsVersionState = MutableStateFlow(0)

    val posts: List<Post>
        get() = mutablePosts.map { post ->
            post.copy(author = userById(post.author.id))
        }

    val postsFlow: Flow<List<Post>>
        get() = combine(postsVersionState, socialState) { _, _ -> posts }

    fun addPost(draft: PostComposerDraft, authorId: String): String {
        val postId = "local_post_${System.currentTimeMillis()}"
        val post = Post(
            id = postId,
            author = userById(authorId),
            text = if (draft.type == PostComposerType.Image) "" else draft.text,
            imageUrl = draft.imageUri.takeIf { draft.type == PostComposerType.Image },
            videoUrl = draft.videoUri.takeIf { draft.type == PostComposerType.Video },
            placeName = draft.locationLabel.takeIf { draft.type == PostComposerType.Image },
            rankingLabel = "#0",
            createdAt = "Ahora",
            likesCount = 0,
            comments = emptyList()
        )
        mutablePosts.add(0, post)
        postsVersionState.value = postsVersionState.value + 1
        return postId
    }

    private val mockNow: Long
        get() = System.currentTimeMillis()

    private val mutableMessages = mutableListOf(
        Message("m1", "c1", "u_ana", "Ana", "Ya tienes la base Android montada?", "12:37", false, false),
        Message("m2", "c1", "u_current", "Gabriel", "Si, estoy fusionando arquitectura y helpers reales.", "12:38", true, true),
        Message("m3", "c1", "u_ana", "Ana", "Perfecto. Luego conectamos Supabase.", "12:40", false, false),
        Message("m4", "c2", "u_ana", "Ana", "La V3 ya tiene estructura fusionada", "11:15", false, false),
        Message("m4b", "c2", "u_leo", "Leo", "Tenemos que cerrar los detalles de perfiles.", "11:18", false, false),
        Message("m4c", "c2", "u_sara", "Sara", "Yo reviso las notificaciones.", "11:22", false, false),
        Message("m4d", "c2", "u_ana", "Ana", "Genial, lo dejamos listo hoy.", "11:27", false, false),
        Message("m4e", "c2", "u_leo", "Leo", "Avisad cuando este para probar.", "11:31", false, false),
        Message("m5", "c3", "u_leo", "Leo", "Mira el diseno naranja del login", "Ayer", false, true),
        Message("m6", "barrio_molyko", "u_marcelino", "Marcelino", "Los chicos de Molyko ya estan por aqui.", "11:34", false, true),
        Message("m7", "barrio_sampaka", "u_ana", "Ana", "Sampaka se mueve hoy.", "10:12", false, false)
    )
    private val messagesState = MutableStateFlow(mutableMessages.toList())

    private val mutableConversations = mutableListOf(
        Conversation(
            "c1",
            "Ana",
            lastMessagePreview = "Perfecto. Luego conectamos Supabase.",
            unreadCount = 0,
            updatedAt = "12:40",
            updatedAtMillis = mockNow - 6L * 60L * 1000L,
            participantIds = listOf("u_ana", currentUser.id),
            participantNames = listOf("Ana")
        ),
        Conversation(
            "c2",
            "Equipo Quata",
            lastMessagePreview = "La V3 ya tiene estructura fusionada",
            unreadCount = 0,
            updatedAt = "11:15",
            updatedAtMillis = mockNow - 3L * 60L * 60L * 1000L,
            participantIds = listOf("u_ana", "u_leo", "u_sara", currentUser.id),
            participantNames = listOf("Ana", "Leo", "Sara", "Gabriel"),
            isGroup = true
        ),
        Conversation(
            "c3",
            "Leo",
            lastMessagePreview = "Mira el diseno naranja del login",
            unreadCount = 0,
            updatedAt = "Ayer",
            updatedAtMillis = mockNow - 12L * 24L * 60L * 60L * 1000L,
            participantIds = listOf("u_leo", currentUser.id),
            participantNames = listOf("Leo")
        ),
        Conversation(
            "barrio_molyko",
            "Molyko",
            lastMessagePreview = "Los chicos de Molyko ya estan por aqui.",
            unreadCount = 0,
            updatedAt = "11:34",
            updatedAtMillis = mockNow - 90L * 60L * 1000L,
            participantIds = listOf("u_marcelino", "u_ondo", currentUser.id),
            participantNames = listOf("Marcelino", "Ondo", "Gabriel"),
            isGroup = true,
            communityName = "Molyko"
        ),
        Conversation(
            "barrio_sampaka",
            "Sampaka",
            lastMessagePreview = "Sampaka se mueve hoy.",
            unreadCount = 0,
            updatedAt = "10:12",
            updatedAtMillis = mockNow - 3L * 60L * 60L * 1000L,
            participantIds = listOf("u_ana", "u_melo", currentUser.id),
            participantNames = listOf("Ana", "Melo", "Gabriel"),
            isGroup = true,
            communityName = "Sampaka"
        )
    )
    private val conversationsState = MutableStateFlow(mutableConversations.toList())

    val conversations: List<Conversation>
        get() = mutableConversations.withLiveUnreadCounts()

    val conversationsFlow: Flow<List<Conversation>>
        get() = combine(conversationsState, messagesState) { conversations, _ ->
            conversations.withLiveUnreadCounts()
        }

    val messages: List<Message>
        get() = mutableMessages

    val messagesFlow: StateFlow<List<Message>>
        get() = messagesState.asStateFlow()

    private fun List<Conversation>.withLiveUnreadCounts(): List<Conversation> =
        map { conversation ->
            conversation.copy(
                unreadCount = mutableMessages.count { message ->
                    message.conversationId == conversation.id && !message.isMine && !message.isRead
                }
            )
        }

    fun addMessage(conversationId: String, text: String, senderId: String, senderName: String): Unit {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        mutableMessages.add(
            Message(
                id = "m_${now}",
                conversationId = conversationId,
                senderId = senderId,
                senderName = senderName,
                text = text,
                sentAt = "Ahora",
                isMine = true,
                isRead = true
            )
        )
        messagesState.value = mutableMessages.toList()
        val index = mutableConversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            val updated = mutableConversations[index].copy(
                lastMessagePreview = text,
                updatedAt = "Ahora",
                updatedAtMillis = now,
                isVisible = true
            )
            mutableConversations.removeAt(index)
            mutableConversations.add(0, updated)
            conversationsState.value = mutableConversations.toList()
        }
    }

    fun addIncomingMockMessage(
        conversationId: String,
        text: String,
        currentUserId: String = currentUser.id,
        currentUserName: String = currentUser.displayName,
        incrementUnread: Boolean = true
    ) {
        if (text.isBlank()) return
        val conversation = mutableConversations.firstOrNull { it.id == conversationId } ?: return
        val sender = conversation.participantIds
            .firstOrNull { it != currentUserId }
            ?.let { id -> registeredUsers.firstOrNull { it.id == id } }
        val senderName = sender?.displayName
            ?: conversation.participantNames.firstOrNull { !it.equals(currentUserName, ignoreCase = true) }
            ?: "QÜATA"
        val senderId = sender?.id ?: "mock_contact"
        val now = System.currentTimeMillis()
        mutableMessages.add(
            Message(
                id = "m_in_$now",
                conversationId = conversationId,
                senderId = senderId,
                senderName = senderName,
                text = text,
                sentAt = "Ahora",
                isMine = false,
                isRead = !incrementUnread
            )
        )
        messagesState.value = mutableMessages.toList()
        val index = mutableConversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            val updated = mutableConversations[index].copy(
                lastMessagePreview = text,
                updatedAt = "Ahora",
                updatedAtMillis = now,
                isVisible = true
            )
            mutableConversations.removeAt(index)
            mutableConversations.add(0, updated)
            conversationsState.value = mutableConversations.toList()
        }
    }

    fun markConversationRead(conversationId: String) {
        mutableMessages.replaceAll { message ->
            if (message.conversationId == conversationId && !message.isMine) {
                message.copy(isRead = true)
            } else {
                message
            }
        }
        messagesState.value = mutableMessages.toList()
    }

    fun addSosConversation(contactIds: List<String>, text: String, senderId: String, senderName: String): String {
        val now = System.currentTimeMillis()
        val participantIds = (contactIds + senderId).distinct()
        mutableConversations.firstOrNull { conversation ->
            conversation.isEmergency &&
                conversation.participantIds.size == participantIds.size &&
                conversation.participantIds.toSet() == participantIds.toSet()
        }?.let { existing ->
            val lastMessage = mutableMessages.lastOrNull { it.conversationId == existing.id }
            if (lastMessage?.senderId == senderId && lastMessage.text.isSosText()) {
                return existing.id
            }
            addMessage(existing.id, text, senderId, senderName)
            return existing.id
        }

        val id = "sos_$now"
        val memberNames = contactIds.mapNotNull { contactId ->
            registeredUsers.firstOrNull { it.id == contactId }?.displayName
        }
        mutableConversations.add(
            0,
            Conversation(
                id = id,
                title = "\uD83D\uDEA8 SOS",
                lastMessagePreview = text,
                unreadCount = 0,
                updatedAt = "Ahora",
                updatedAtMillis = now,
                participantIds = participantIds,
                participantNames = (memberNames + senderName).distinct(),
                isGroup = true,
                isEmergency = true
            )
        )
        conversationsState.value = mutableConversations.toList()
        mutableMessages.add(
            Message(
                id = "m_$id",
                conversationId = id,
                senderId = senderId,
                senderName = senderName,
                text = text,
                sentAt = "Ahora",
                isMine = true,
                isRead = true
            )
        )
        messagesState.value = mutableMessages.toList()
        return id
    }

    private fun String.isSosText(): Boolean =
        contains("SOS", ignoreCase = true) || contains("https://maps.google.com/?q=")

    fun findOrCreateNeighborhoodConversation(neighborhood: String, senderId: String, senderName: String): String {
        val cleanNeighborhood = neighborhood.trim()
        if (cleanNeighborhood.isBlank()) error("Barrio no valido")
        mutableConversations.firstOrNull {
            it.isGroup &&
                (it.communityName ?: it.title).equals(cleanNeighborhood, ignoreCase = true) &&
                !it.isEmergency
        }?.let { return it.id }

        val now = System.currentTimeMillis()
        val id = "barrio_${cleanNeighborhood.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_$now"
        val memberNames = registeredUsers
            .filter { it.neighborhood.equals(cleanNeighborhood, ignoreCase = true) }
            .map { it.displayName }
        val memberIds = registeredUsers
            .filter { it.neighborhood.equals(cleanNeighborhood, ignoreCase = true) }
            .map { it.id }
        mutableConversations.add(
            0,
            Conversation(
                id = id,
                title = cleanNeighborhood,
                lastMessagePreview = "",
                unreadCount = 0,
                updatedAt = "",
                updatedAtMillis = null,
                participantIds = (memberIds + senderId).distinct(),
                participantNames = (memberNames + senderName).distinct(),
                isGroup = true,
                communityName = cleanNeighborhood
            )
        )
        conversationsState.value = mutableConversations.toList()
        return id
    }

    fun setConversationMuted(conversationId: String, muted: Boolean) {
        updateConversation(conversationId) { copy(isMuted = muted) }
    }

    fun hideConversation(conversationId: String) {
        updateConversation(conversationId) { copy(isVisible = false) }
    }

    fun addParticipants(conversationId: String, participantIds: List<String>, currentUserId: String, currentUserName: String) {
        if (participantIds.isEmpty()) return
        val participants = participantIds.mapNotNull { id -> registeredUsers.firstOrNull { it.id == id } }
        updateConversation(conversationId) {
            copy(
                participantIds = (this.participantIds + participantIds + currentUserId).distinct(),
                participantNames = (participantNames + participants.map { it.displayName } + currentUserName).distinct(),
                isGroup = true,
                isVisible = true
            )
        }
    }

    fun findOrCreatePrivateConversation(userId: String, senderId: String, senderName: String): String {
        if (userId == senderId) error("No puedes abrir un PRIVI contigo mismo")
        val user = registeredUsers.firstOrNull { it.id == userId } ?: error("Usuario no encontrado")
        mutableConversations.firstOrNull { conversation ->
            !conversation.isGroup &&
                !conversation.isEmergency &&
                setOf(senderId, userId).all { it in conversation.participantIds }
        }?.let { return it.id }

        val now = System.currentTimeMillis()
        val id = "priv_${userId}_$now"
        mutableConversations.add(
            0,
            Conversation(
                id = id,
                title = user.displayName,
                lastMessagePreview = "",
                unreadCount = 0,
                updatedAt = "",
                updatedAtMillis = null,
                participantIds = listOf(senderId, user.id),
                participantNames = listOf(user.displayName, senderName),
                isGroup = false
            )
        )
        conversationsState.value = mutableConversations.toList()
        return id
    }

    private fun updateConversation(conversationId: String, transform: Conversation.() -> Conversation) {
        val index = mutableConversations.indexOfFirst { it.id == conversationId }
        if (index < 0) return
        mutableConversations[index] = mutableConversations[index].transform()
        conversationsState.value = mutableConversations.toList()
    }

    val notifications: List<NotificationItem>
        get() = conversations
            .filter { it.isVisible && !it.isMuted && it.unreadCount > 0 }
            .map {
                NotificationItem(
                    id = "notification_${it.id}",
                    conversationId = it.id,
                    title = it.title,
                    body = it.lastMessagePreview,
                    createdAt = it.updatedAt,
                    unreadCount = it.unreadCount
                )
            }
}
