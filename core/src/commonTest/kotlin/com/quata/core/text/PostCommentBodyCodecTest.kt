package com.quata.core.text

import com.quata.core.model.PostComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostCommentBodyCodecTest {
    @Test
    fun parsesReplyShortcodeOnEveryPlatformRegexEngine() {
        val parsed = " [reply:comment-1:Alicia] Respuesta ".parsePostCommentBody()

        assertEquals("comment-1", parsed.commentId)
        assertEquals("Alicia", parsed.authorName)
        assertEquals("Respuesta", parsed.message)
    }

    @Test
    fun leavesPlainAndMalformedCommentsAsMessages() {
        val plain = " Comentario normal ".parsePostCommentBody()
        val malformed = "[reply:comment-1:Alicia Respuesta".parsePostCommentBody()

        assertEquals("Comentario normal", plain.message)
        assertNull(plain.commentId)
        assertNull(plain.authorName)
        assertEquals("[reply:comment-1:Alicia Respuesta", malformed.message)
        assertNull(malformed.commentId)
        assertNull(malformed.authorName)
    }

    @Test
    fun serializesRemoteRepliesButNeverPersistsLocalReplyIds() {
        val remote = comment(replyId = "comment-1").toRemoteCommentBody()
        val local = comment(replyId = "local_pending").toRemoteCommentBody()

        assertEquals("[reply:comment-1:Alicia] Respuesta", remote)
        assertEquals("Respuesta", local)
    }

    private fun comment(replyId: String) = PostComment(
        id = "reply",
        authorName = "Beto",
        message = " Respuesta ",
        timestamp = "2026-07-26T21:00:00Z",
        replyToAuthorName = "Alicia",
        replyToCommentId = replyId,
    )
}
