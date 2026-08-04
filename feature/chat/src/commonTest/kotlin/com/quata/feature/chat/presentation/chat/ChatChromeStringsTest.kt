package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatChromeStringsTest {
    @Test
    fun spanishCopyMatchesTheAndroidChatContract() {
        val strings = chatChromeStringsForLanguage("es-ES")

        assertEquals("Silenciar conversación", strings.muteConversation)
        assertEquals("Mensajes favoritos", strings.favoriteMessages)
        assertEquals("Añadir nuevos participantes", strings.addParticipants)
        assertEquals("Abandonar conversación", strings.leaveConversation)
        assertEquals("Borrar conversación", strings.deleteConversation)
        assertEquals("Ascender a moderador", strings.promoteModerator)
        assertEquals("Quitar de moderador", strings.removeModerator)
        assertEquals("Editando mensaje", strings.editingMessage)
        assertEquals("Respondiendo a Gabrielu", strings.replyingTo("Gabrielu"))
        assertEquals("Foto/vídeo de galería", strings.chooseGallery)
        assertEquals("Grabando 00:08", strings.recording("00:08"))
        assertEquals("No se pudo abrir la cámara.", strings.cameraError)
        assertEquals("La grabación de audio no está disponible.", strings.audioUnsupported)
        assertEquals("Reproducir vídeo", strings.playVideo)
        assertEquals("Reproducir audio", strings.playAudio)
        assertEquals("Pausar audio", strings.pauseAudio)
        assertEquals("Gabrielu (tú)", strings.memberLabel("Gabrielu", true))
        assertEquals("3 miembros", strings.memberCount(3))
    }

    @Test
    fun unsupportedLocaleUsesAndroidDefaultEnglishCopy() {
        val strings = chatChromeStringsForLanguage("de-DE")

        assertEquals("Mute conversation", strings.muteConversation)
        assertEquals("Delete message", strings.deleteMessage)
        assertEquals("The gallery could not be opened.", strings.galleryError)
        assertEquals("2 members", strings.memberCount(2))
    }
}
