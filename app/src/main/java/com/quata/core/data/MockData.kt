package com.quata.core.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.NotificationItem
import com.quata.core.model.Post
import com.quata.core.model.User

object MockData {
    val currentUser = User(
        id = "u_current",
        email = "gabriel@quata.app",
        displayName = "Gabriel"
    )

    private val ana = User("u_ana", "ana@quata.app", "Ana")
    private val leo = User("u_leo", "leo@quata.app", "Leo")
    private val sara = User("u_sara", "sara@quata.app", "Sara")

    val posts = listOf(
        Post(
            id = "p1",
            author = ana,
            text = "Primer prototipo de Qüata funcionando. Feed, chat y publicación van tomando forma.",
            imageUrl = null,
            createdAt = "Hace 3 min",
            likesCount = 42,
            commentsCount = 7
        ),
        Post(
            id = "p2",
            author = leo,
            text = "La idea es simple: compartir momentos, conversaciones y publicaciones sin ruido.",
            imageUrl = null,
            createdAt = "Hace 18 min",
            likesCount = 18,
            commentsCount = 3
        ),
        Post(
            id = "p3",
            author = sara,
            text = "Probando el estilo oscuro con naranja. Tiene un aire bastante premium.",
            imageUrl = null,
            createdAt = "Hace 1 h",
            likesCount = 87,
            commentsCount = 12
        )
    )

    val conversations = listOf(
        Conversation("c1", "Ana", lastMessagePreview = "Te paso luego la configuración de Supabase", unreadCount = 2, updatedAt = "12:40"),
        Conversation("c2", "Equipo Qüata", lastMessagePreview = "La V3 ya tiene estructura fusionada", unreadCount = 5, updatedAt = "11:15"),
        Conversation("c3", "Leo", lastMessagePreview = "Mira el diseño naranja del login", unreadCount = 0, updatedAt = "Ayer")
    )

    val messages = listOf(
        Message("m1", "c1", "u_ana", "Ana", "¿Ya tienes la base Android montada?", "12:37", false),
        Message("m2", "c1", "u_current", "Gabriel", "Sí, estoy fusionando arquitectura y helpers reales.", "12:38", true),
        Message("m3", "c1", "u_ana", "Ana", "Perfecto. Luego conectamos Supabase.", "12:40", false)
    )

    val notifications = listOf(
        NotificationItem("n1", "Nueva respuesta", "Ana respondió a tu publicación", "Hace 2 min"),
        NotificationItem("n2", "Nuevo mensaje", "Tienes 5 mensajes en Equipo Qüata", "Hace 15 min"),
        NotificationItem("n3", "Publicación creada", "Tu publicación se guardó correctamente", "Hace 1 h", true)
    )
}
