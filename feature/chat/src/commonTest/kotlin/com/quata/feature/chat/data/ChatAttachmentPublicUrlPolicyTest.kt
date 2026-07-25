package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatAttachmentPublicUrlPolicyTest {
    private val supabaseUrl = "https://project-ref.supabase.co"
    private val attachment = "https://project-ref.supabase.co/storage/v1/object/public/chat-attachments/user-7/message-4/photo.png"

    @Test
    fun acceptsOnlyCanonicalPublicChatBucketObjectUrls() {
        assertEquals(attachment, ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment))
    }

    @Test
    fun rejectsDifferentOriginsPortsQueriesAndFragments() {
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("project-ref.supabase.co", "attacker.invalid")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("https://", "https://project-ref.supabase.co@")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace(".co/", ".co:443/")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, "$attachment?download=1"))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, "$attachment#fragment"))
    }

    @Test
    fun rejectsTraversalEncodingAndOtherStoragePaths() {
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("user-7", "..")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("user-7", "%2e%2e")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("photo.png", "photo name.png")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(supabaseUrl, attachment.replace("chat-attachments", "avatars")))
        assertNull(ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull("http://project-ref.supabase.co", attachment))
    }
}
