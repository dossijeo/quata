package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserChatMediaUrlTest {
    @Test
    fun acceptsOnlyConfiguredPublicChatStorageOrLocalBlobMedia() {
        val attachment = "https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/chat-attachments/user-7/message-4/photo.png"

        assertEquals(attachment, attachment.safeBrowserChatMediaUrl())
        assertEquals("blob:http://127.0.0.1:4174/local-media", "blob:http://127.0.0.1:4174/local-media".safeBrowserChatMediaUrl())
        assertNull("https://cdn.quata.example/object?token=signed".safeBrowserChatMediaUrl())
        assertNull("HTTP://cdn.quata.example/media/opaque-id".safeBrowserChatMediaUrl())
        assertNull("$attachment?token=signed".safeBrowserChatMediaUrl())
        assertNull(attachment.replace("chat-attachments", "community-media").safeBrowserChatMediaUrl())
        assertNull("javascript:alert(1)".safeBrowserChatMediaUrl())
    }
}
