package com.quata.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class QuataFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // En producción, envía este token a Supabase/WordPress cuando tengas sesión de usuario.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Aquí puedes enrutar payloads a chat, feed o notificaciones locales.
    }
}
