package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PostgrestChatRepositoryTest {
    @Test
    fun candidatePageUsesPortableRpcShapeAndSafeDefaults() {
        val page = """
            {"items":[
              {"profile_id":"p-1","display_name":"","existing_thread_id":44},
              {"display_name":"missing id"}
            ],"has_more":false}
        """.trimIndent().toChatConversationCandidatePage(requestOffset = 10)

        assertEquals(1, page.candidates.size)
        assertEquals("p-1", page.candidates.single().profileId)
        assertEquals("Usuario", page.candidates.single().displayName)
        assertEquals("sb:44", page.candidates.single().existingConversationId)
        assertEquals(11, page.nextOffset)
        assertFalse(page.hasMore)
        assertNull(page.candidates.single().avatarUrl)
    }
}
