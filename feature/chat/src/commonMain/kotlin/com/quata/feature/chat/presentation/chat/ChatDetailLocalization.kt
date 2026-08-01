package com.quata.feature.chat.presentation.chat

/** Visible conversation-detail copy shared by Android, Web and iOS. */
data class ChatDetailStrings(
    val image: String, val video: String, val loadMessagesError: String, val attachment: String,
    val message: String, val memberCount: (Int) -> String, val unmute: String, val mute: String,
    val addParticipants: String, val allowMemberInvites: String, val deleteConversation: String,
    val openMaps: String, val leaveConversation: String, val leaveConfirm: String, val deleteConfirm: String,
    val promoteModerator: String, val removeModerator: String, val blockUser: String, val removeParticipant: String,
    val copyMessage: String, val replyMessage: String, val forwardMessage: String, val editMessage: String,
    val favoriteMessage: String, val removeFavoriteMessage: String, val deleteMessage: String,
    val reportMessage: String, val translateMessage: String, val forwardedFrom: String,
    val editingMessage: (String) -> String, val replyingTo: (String, String) -> String,
    val deletedMessage: String, val edited: String, val camera: String, val recordAudio: String,
    val file: String, val gallery: String, val favorites: String, val back: String,
    val group: String, val participant: String, val send: String,
)

fun chatDetailStringsForLanguage(languageTag: String?): ChatDetailStrings = when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
    "es" -> ChatDetailStrings(
        "Imagen", "Vídeo", "No se pudieron cargar los mensajes.", "Adjunto", "Mensaje", { "$it miembros" },
        "Reactivar notificaciones", "Silenciar conversación", "Añadir participantes", "Permitir que los miembros inviten", "Eliminar conversación",
        "Abrir ubicación en Google Maps", "Abandonar conversación", "Dejarás de ser miembro de esta conversación.",
        "La conversación desaparecerá ahora. Podrás deshacerlo unos segundos desde la lista de conversaciones.",
        "Ascender a moderador", "Quitar de moderador", "Bloquear usuario", "Expulsar participante", "Copiar texto", "Responder", "Reenviar", "Editar",
        "Favorito", "Quitar favorito", "Eliminar mensaje", "Reportar mensaje", "Traducir", "Mensaje reenviado", { "Editando: $it" },
        { sender, text -> "Respondiendo a $sender: $text" }, "💬 Este mensaje fue eliminado", "editado", "Abrir cámara", "Grabar audio",
        "Elegir archivo", "Foto/vídeo de galería", "Mensajes favoritos", "Volver", "Grupo", "Participante", "Enviar",
    )
    "fr" -> ChatDetailStrings(
        "Image", "Vidéo", "Impossible de charger les messages.", "Pièce jointe", "Message", { "$it membres" },
        "Réactiver les notifications", "Mettre la conversation en sourdine", "Ajouter des participants", "Autoriser les membres à inviter", "Supprimer la conversation",
        "Ouvrir la position dans Google Maps", "Quitter la conversation", "Tu ne seras plus membre de cette conversation.",
        "La conversation disparaîtra maintenant. Tu pourras l’annuler quelques secondes depuis la liste.",
        "Nommer modérateur", "Retirer le rôle de modérateur", "Bloquer l’utilisateur", "Expulser le participant", "Copier le texte", "Répondre", "Transférer", "Modifier",
        "Favori", "Retirer des favoris", "Supprimer le message", "Signaler le message", "Traduire", "Message transféré", { "Modification : $it" },
        { sender, text -> "Réponse à $sender : $text" }, "💬 Ce message a été supprimé", "modifié", "Ouvrir la caméra", "Enregistrer un audio",
        "Choisir un fichier", "Photo/vidéo de la galerie", "Messages favoris", "Retour", "Groupe", "Participant", "Envoyer",
    )
    else -> ChatDetailStrings(
        "Image", "Video", "Messages could not be loaded.", "Attachment", "Message", { "$it members" },
        "Reactivate notifications", "Mute conversation", "Add participants", "Allow members to invite", "Delete conversation",
        "Open location in Google Maps", "Leave conversation", "You will no longer be a member of this conversation.",
        "The conversation will disappear now. You can undo this for a few seconds from the conversations list.",
        "Promote to moderator", "Remove moderator", "Block user", "Remove participant", "Copy text", "Reply", "Forward", "Edit",
        "Favorite", "Remove favorite", "Delete message", "Report message", "Translate", "Forwarded message", { "Editing: $it" },
        { sender, text -> "Replying to $sender: $text" }, "💬 This message was deleted", "edited", "Open camera", "Record audio",
        "Choose file", "Photo/video from gallery", "Favorite messages", "Back", "Group", "Participant", "Send",
    )
}
