package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatAttachmentStorageAccessPolicyTest {
    private val base = "https://project-ref.supabase.co"
    private val objectRef = ChatAttachmentStorageObject("chat-attachments", "profile-1/message-2/file.pdf")
    private val token = "header.payload.signature-1234"

    @Test
    fun acceptsOnlyCanonicalChatAttachmentStorageIdentity() {
        assertEquals(
            objectRef,
            ChatAttachmentStorageAccessPolicy.storageObjectOrNull(objectRef.bucket, objectRef.path),
        )
        assertNull(ChatAttachmentStorageAccessPolicy.storageObjectOrNull("community-media", objectRef.path))
        assertNull(ChatAttachmentStorageAccessPolicy.storageObjectOrNull(objectRef.bucket, "../secret"))
        assertNull(ChatAttachmentStorageAccessPolicy.storageObjectOrNull(objectRef.bucket, "profile-1/file%2epdf"))
        assertNull(ChatAttachmentStorageAccessPolicy.storageObjectOrNull(objectRef.bucket, "profile-1/file name.pdf"))
        assertNotNull(ChatAttachmentStorageAccessPolicy.storageObjectOrNull("chat-attachments-private", objectRef.path))
    }

    @Test
    fun acceptsOnlyExactShortLivedSignedObjectUrlShape() {
        val signed = "$base/storage/v1/object/sign/chat-attachments/${objectRef.path}?token=$token"
        assertEquals(signed, ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, signed))
        assertNull(ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, signed.replace("project-ref", "attacker")))
        assertNull(ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, "$signed&download=1"))
        assertNull(ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, "$signed#fragment"))
        assertNull(ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, signed.replace("https://", "https://user@")))
        assertNull(ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, objectRef, signed.replace(".co/", ".co:443/")))
    }
}
