package com.quata.core.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.NotificationItem
import com.quata.core.model.Post
import com.quata.core.model.PostComment
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

    val conversations = listOf(
        Conversation("c1", "Ana", lastMessagePreview = "Te paso luego la configuracion de Supabase", unreadCount = 2, updatedAt = "12:40"),
        Conversation("c2", "Equipo Quata", lastMessagePreview = "La V3 ya tiene estructura fusionada", unreadCount = 5, updatedAt = "11:15"),
        Conversation("c3", "Leo", lastMessagePreview = "Mira el diseno naranja del login", unreadCount = 0, updatedAt = "Ayer")
    )

    val messages = listOf(
        Message("m1", "c1", "u_ana", "Ana", "Ya tienes la base Android montada?", "12:37", false),
        Message("m2", "c1", "u_current", "Gabriel", "Si, estoy fusionando arquitectura y helpers reales.", "12:38", true),
        Message("m3", "c1", "u_ana", "Ana", "Perfecto. Luego conectamos Supabase.", "12:40", false)
    )

    val notifications = listOf(
        NotificationItem("n1", "Nueva respuesta", "Ana respondio a tu publicacion", "Hace 2 min"),
        NotificationItem("n2", "Nuevo mensaje", "Tienes 5 mensajes en Equipo Quata", "Hace 15 min"),
        NotificationItem("n3", "Publicacion creada", "Tu publicacion se guardo correctamente", "Hace 1 h", true)
    )
}
