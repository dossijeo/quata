package com.quata.core.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.NotificationItem
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MockData {
    val currentUser = User(
        id = "u_current",
        email = "gabriel@quata.app",
        displayName = "Gabriel"
    )

    private val ana = User("u_ana", "ana@quata.app", "Ana")
    private val leo = User("u_leo", "leo@quata.app", "Leo")
    private val sara = User("u_sara", "sara@quata.app", "Sara")
    private val ji = User("u_ji", "ji@quata.app", "JI")
    private val marcelino = User("u_marcelino", "marcelino@quata.app", "Marcelino")
    private val obiang = User("u_obiang", "obiang@quata.app", "Obiang")
    private val maribel = User("u_maribel", "maribel@quata.app", "maribelamdemeekandoh")

    val registeredUsers = listOf(currentUser, ana, leo, sara, ji, marcelino, obiang, maribel)

    private val mutablePosts = mutableListOf(
        Post(
            id = "p1",
            author = ana,
            text = "La tarde cae sobre Malabo y todo parece moverse mas lento.",
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

    val posts: List<Post>
        get() = mutablePosts

    fun addPost(draft: PostComposerDraft) {
        val post = Post(
            id = "local_post_${System.currentTimeMillis()}",
            author = currentUser,
            text = draft.text,
            imageUrl = draft.imageUri.takeIf { draft.type == PostComposerType.Image },
            videoUrl = draft.videoUri.takeIf { draft.type == PostComposerType.Video },
            placeName = draft.locationLabel.takeIf { draft.type == PostComposerType.Image },
            rankingLabel = "#0",
            createdAt = "Ahora",
            likesCount = 0,
            comments = emptyList()
        )
        mutablePosts.add(0, post)
    }

    private val mockNow: Long
        get() = System.currentTimeMillis()

    private val mutableMessages = mutableListOf(
        Message("m1", "c1", "u_ana", "Ana", "Ya tienes la base Android montada?", "12:37", false),
        Message("m2", "c1", "u_current", "Gabriel", "Si, estoy fusionando arquitectura y helpers reales.", "12:38", true),
        Message("m3", "c1", "u_ana", "Ana", "Perfecto. Luego conectamos Supabase.", "12:40", false),
        Message("m4", "c2", "u_ana", "Ana", "La V3 ya tiene estructura fusionada", "11:15", false),
        Message("m5", "c3", "u_leo", "Leo", "Mira el diseno naranja del login", "Ayer", false)
    )
    private val messagesState = MutableStateFlow(mutableMessages.toList())

    private val mutableConversations = mutableListOf(
        Conversation(
            "c1",
            "Ana",
            lastMessagePreview = "",
            unreadCount = 2,
            updatedAt = "12:40",
            updatedAtMillis = mockNow - 6L * 60L * 1000L,
            participantNames = listOf("Ana")
        ),
        Conversation(
            "c2",
            "Equipo Quata",
            lastMessagePreview = "",
            unreadCount = 5,
            updatedAt = "11:15",
            updatedAtMillis = mockNow - 3L * 60L * 60L * 1000L,
            participantNames = listOf("Ana", "Leo", "Sara", "Gabriel"),
            isGroup = true
        ),
        Conversation(
            "c3",
            "Leo",
            lastMessagePreview = "",
            unreadCount = 0,
            updatedAt = "Ayer",
            updatedAtMillis = mockNow - 12L * 24L * 60L * 60L * 1000L,
            participantNames = listOf("Leo")
        )
    )
    private val conversationsState = MutableStateFlow(mutableConversations.toList())

    val conversations: List<Conversation>
        get() = mutableConversations

    val conversationsFlow: StateFlow<List<Conversation>>
        get() = conversationsState.asStateFlow()

    val messages: List<Message>
        get() = mutableMessages

    val messagesFlow: StateFlow<List<Message>>
        get() = messagesState.asStateFlow()

    fun addMessage(conversationId: String, text: String, senderName: String): Unit {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        mutableMessages.add(
            Message(
                id = "m_${now}",
                conversationId = conversationId,
                senderId = currentUser.id,
                senderName = senderName,
                text = text,
                sentAt = "Ahora",
                isMine = true
            )
        )
        messagesState.value = mutableMessages.toList()
        val index = mutableConversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            val updated = mutableConversations[index].copy(
                lastMessagePreview = text,
                updatedAt = "Ahora",
                updatedAtMillis = now
            )
            mutableConversations.removeAt(index)
            mutableConversations.add(0, updated)
            conversationsState.value = mutableConversations.toList()
        }
    }

    fun addSosConversation(contactIds: List<String>, text: String, senderName: String): String {
        val now = System.currentTimeMillis()
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
                senderId = currentUser.id,
                senderName = senderName,
                text = text,
                sentAt = "Ahora",
                isMine = true
            )
        )
        messagesState.value = mutableMessages.toList()
        return id
    }

    val notifications = listOf(
        NotificationItem("n1", "Nueva respuesta", "Ana respondio a tu publicacion", "Hace 2 min"),
        NotificationItem("n2", "Nuevo mensaje", "Tienes 5 mensajes en Equipo Quata", "Hace 15 min"),
        NotificationItem("n3", "Publicacion creada", "Tu publicacion se guardo correctamente", "Hace 1 h", true)
    )
}
