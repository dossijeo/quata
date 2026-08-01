package com.quata.feature.notifications.data

import com.quata.feature.chat.data.ChatAttachmentUploader
import com.quata.feature.chat.data.ChatAuthenticatedUserProvider
import com.quata.feature.chat.data.ChatPostgrestResponse
import com.quata.feature.chat.data.ChatPostgrestTransport
import com.quata.feature.chat.data.PostgrestChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationNotificationsRepositoryTest {
    @Test
    fun `count observation propagates inbox failure instead of reporting zero`() = runTest {
        val chatRepository = PostgrestChatRepository(
            transport = object : ChatPostgrestTransport {
                override suspend fun post(functionName: String, body: String): ChatPostgrestResponse =
                    ChatPostgrestResponse.Failure(IllegalStateException("inbox_rpc_failed"))
            },
            authenticatedUser = ChatAuthenticatedUserProvider { "profile-1" },
            attachmentUploader = ChatAttachmentUploader { _, _ -> error("attachment upload not expected") },
        )
        val repository = ConversationNotificationsRepository(chatRepository)

        val error = assertFailsWith<IllegalStateException> {
            repository.observeNotificationCount().first()
        }

        assertEquals("inbox_rpc_failed", error.message)
    }
}
