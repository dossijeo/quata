package com.quata.feature.chat.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ChatRpcPayloadEnvelopeTest {
    @Test
    fun resolvesPrivateAttachmentFromStableStorageIdentity() = runTest {
        val payload = Json.parseToJsonElement(
            """{"messages":[{"id":10,"thread_id":20,"sender_profile_id":"author","attachments":[{"url":"https://project.supabase.co/storage/v1/object/public/chat-attachments/author/id/file.pdf","storage_bucket":"chat-attachments","storage_path":"author/id/file.pdf","name":"file.pdf","mime_type":"application/pdf"}]}]}""",
        )
        val signed = "https://project.supabase.co/storage/v1/object/sign/chat-attachments/author/id/file.pdf?token=header.payload.signature"
        val observed = mutableListOf<ChatAttachmentStorageObject>()

        val message = parseChatRpcPayloadEnvelope(payload).toSignedChatRpcMessages(
            currentProfileId = "reader",
            signer = ChatAttachmentUrlSigner { storageObject ->
                observed += storageObject
                signed
            },
        ).single()

        assertEquals(listOf(ChatAttachmentStorageObject("chat-attachments", "author/id/file.pdf")), observed)
        assertEquals(signed, message.attachmentUri)
    }

    @Test
    fun privateAttachmentMappingFailsClosedWithoutStorageIdentity() = runTest {
        val payload = Json.parseToJsonElement(
            """{"messages":[{"id":10,"thread_id":20,"sender_profile_id":"author","attachments":[{"url":"https://project.supabase.co/storage/v1/object/public/chat-attachments/author/id/file.pdf"}]}]}""",
        )

        assertFailsWith<IllegalStateException> {
            parseChatRpcPayloadEnvelope(payload).toSignedChatRpcMessages("reader", ChatAttachmentUrlSigner { "unused" })
        }
    }

    @Test
    fun deletedMessageDoesNotSignOrExposeItsAttachmentAndDoesNotBlockValidMessages() = runTest {
        val payload = Json.parseToJsonElement(
            """{"messages":[{"id":10,"thread_id":20,"sender_profile_id":"author","is_deleted":true,"attachments":[{"url":"https://project.supabase.co/storage/v1/object/public/chat-attachments-private/author/chat/deleted/file.pdf","storage_bucket":"chat-attachments-private","storage_path":"author/chat/deleted/file.pdf","name":"deleted.pdf","mime_type":"application/pdf"}]},{"id":11,"thread_id":20,"sender_profile_id":"author","attachments":[{"url":"https://project.supabase.co/storage/v1/object/public/chat-attachments-private/author/chat/valid/file.pdf","storage_bucket":"chat-attachments-private","storage_path":"author/chat/valid/file.pdf","name":"valid.pdf","mime_type":"application/pdf"}]}]}""",
        )
        val signed = mutableListOf<ChatAttachmentStorageObject>()

        val messages = parseChatRpcPayloadEnvelope(payload).toSignedChatRpcMessages(
            currentProfileId = "reader",
            signer = ChatAttachmentUrlSigner { objectRef ->
                signed += objectRef
                "https://project.supabase.co/storage/v1/object/sign/${objectRef.bucket}/${objectRef.path}?token=signed"
            },
        )

        assertEquals(listOf(ChatAttachmentStorageObject("chat-attachments-private", "author/chat/valid/file.pdf")), signed)
        assertTrue(messages.first().isDeleted)
        assertEquals(null, messages.first().attachmentUri)
        assertEquals(null, messages.first().attachmentName)
        assertTrue(messages.last().attachmentUri?.contains("/object/sign/") == true)
    }

    @Test
    fun mergesRootAndUpdateVariantsWithoutDuplicatingRecords() {
        val payload = Json.parseToJsonElement(
            """
            {
              "threads":[{"id":1}],
              "messages":[{"id":10}],
              "profiles":[{"id":"author"}],
              "update":{
                "thread":{"thread_id":1},
                "message":{"id":11},
                "profiles":[{"id":"author"},{"id":"reader"}]
              }
            }
            """.trimIndent(),
        )

        val envelope = parseChatRpcPayloadEnvelope(payload)

        assertEquals(listOf(1L), envelope.threads.map { (it["id"] ?: it["thread_id"])!!.jsonPrimitive.long })
        assertEquals(listOf(10L, 11L), envelope.messages.map { it["id"]!!.jsonPrimitive.long })
        assertEquals(listOf("author", "reader"), envelope.profiles.map { it["id"].toString().trim('"') })
    }

    @Test
    fun ignoresNonObjectPayloads() {
        val envelope = parseChatRpcPayloadEnvelope(Json.parseToJsonElement("[]"))

        assertEquals(emptyList(), envelope.threads)
        assertEquals(emptyList(), envelope.messages)
        assertEquals(emptyList(), envelope.profiles)
    }

    @Test
    fun acceptsTheEmptyInboxEnvelopeUsedByTheWebNotificationFixture() {
        val envelope = parseChatRpcPayloadEnvelope(
            Json.parseToJsonElement("""{"threads":[],"messages":[],"profiles":[]}"""),
        )

        assertEquals(emptyList(), envelope.threads)
        assertEquals(emptyList(), envelope.messages)
        assertEquals(emptyList(), envelope.profiles)
    }

    @Test
    fun parsesForwardRpcSentAndErrorsInsteadOfTreatingThePayloadAsUnit() {
        val result = parseChatForwardResult(
            """
            {
              "result": true,
              "sent": {"42": 1001, "43": 1002},
              "errors": ["not participant in thread 44"]
            }
            """.trimIndent(),
            requestedCount = 3,
        )

        assertEquals(3, result.requestedCount)
        assertEquals(2, result.sentCount)
        assertEquals(1, result.errorCount)
        assertEquals(false, result.isComplete)
    }

    @Test
    fun mapsConversationParticipantsNamesAvatarsAndModeratorIdsWithAlignedIndexes() {
        val payload = Json.parseToJsonElement(
            """
            {
              "threads":[{
                "thread_id":1285,
                "type":"group",
                "participants":["gabrielo","gabrielu","temp-profile"],
                "moderators":["gabrielu"]
              }],
              "profiles":[
                {"id":"gabrielo","display_name":"Gabrielo","avatar_url":"https://cdn.test/gabrielo.jpg"},
                {"id":"gabrielu","display_name":"Gabrielu","avatar_url":"https://cdn.test/gabrielu.jpg"},
                {"id":"temp-profile","display_name":"QADATA Forward 123","avatar_url":"https://cdn.test/temp.jpg"}
              ]
            }
            """.trimIndent(),
        )

        val conversation = parseChatRpcPayloadEnvelope(payload)
            .toChatRpcConversations(currentProfileId = "gabrielu")
            .single()

        assertEquals(listOf("gabrielo", "gabrielu", "temp-profile"), conversation.participantIds)
        assertEquals(listOf("Gabrielo", "Gabrielu", "QADATA Forward 123"), conversation.participantNames)
        assertEquals(
            listOf("https://cdn.test/gabrielo.jpg", "https://cdn.test/gabrielu.jpg", "https://cdn.test/temp.jpg"),
            conversation.participantAvatarUrls,
        )
        assertEquals(listOf("gabrielu"), conversation.moderatorIds)
        assertTrue(conversation.isGroup)
    }
}
