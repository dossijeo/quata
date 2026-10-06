package com.quata.feature.chat.presentation.chat

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentRetryEvidenceChatRepositoryTest {
    @Test
    fun failOnceModeRequiresASecondObservationAndThenReturnsTheExactFixture() = runTest {
        val repository = DocumentRetryEvidenceChatRepository(
            attachmentReference = "local-fixture.docx",
            failFirstMessageObservation = true,
        )

        assertFailsWith<IllegalStateException> {
            repository.observeMessages(DocumentRetryEvidenceConversationId).first()
        }

        val recovered = repository.observeMessages(DocumentRetryEvidenceConversationId).first()
        assertEquals(listOf(DocumentRetryEvidenceMessageId), recovered.map { it.id })
        assertEquals(DocumentRetryEvidenceConversationId, recovered.single().conversationId)
    }

    @Test
    fun defaultModeKeepsTheExistingFixtureImmediate() = runTest {
        val repository = DocumentRetryEvidenceChatRepository("local-fixture.docx")

        val messages = repository.observeMessages(DocumentRetryEvidenceConversationId).first()

        assertEquals(listOf(DocumentRetryEvidenceMessageId), messages.map { it.id })
    }
}
