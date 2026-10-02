package com.quata.feature.chat.data

import com.quata.core.model.Message
import com.quata.feature.chat.domain.ChatFavoriteCursor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidChatFavoritesPagerTest {
    @Test
    fun exhaustsSixHundredAndOneRowsAndRefreshPreservesAllLoadedPages() = runBlocking {
        val pager = AndroidChatFavoritesPager()
        var visible = emptyList<Message>()
        val requests = mutableListOf<Long?>()
        val loadPage: suspend (ChatFavoriteCursor?, Int) -> AndroidFavoritePage = { cursor, _ ->
            requests += cursor?.messageId
            when (cursor?.messageId) {
                352L -> page(351 downTo 102, hasMore = true, cursorId = 102)
                102L -> page(101 downTo 1, hasMore = false, cursorId = null)
                else -> page(601 downTo 352, hasMore = true, cursorId = 352)
            }
        }
        val commit: suspend (AndroidFavoriteSnapshot) -> Unit = { visible = it.messages }

        pager.refresh("profile-a", { visible }, loadPage, commit)
        assertTrue(pager.loadOlder("profile-a", 250, { visible }, loadPage, commit).hasMore)
        assertFalse(pager.loadOlder("profile-a", 250, { visible }, loadPage, commit).hasMore)
        assertEquals(601, visible.size)
        assertEquals(601, visible.map(Message::id).distinct().size)

        val refreshed = pager.refresh("profile-a", { visible }, loadPage, commit)
        assertEquals(601, refreshed.messages.size)
        assertEquals(6, requests.size)
        assertEquals(listOf(null, 352L, 102L, null, 352L, 102L), requests)
    }

    @Test
    fun refreshAndOlderLoadAreSerializedWithoutDepthTruncation() = runBlocking {
        val pager = AndroidChatFavoritesPager()
        var visible = emptyList<Message>()
        var firstPageCalls = 0
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val loadPage: suspend (ChatFavoriteCursor?, Int) -> AndroidFavoritePage = { cursor, _ ->
            if (cursor?.messageId == 352L) {
                page(351 downTo 102, hasMore = false, cursorId = null)
            } else {
                firstPageCalls += 1
                if (firstPageCalls == 2) {
                    refreshStarted.complete(Unit)
                    releaseRefresh.await()
                }
                page(601 downTo 352, hasMore = true, cursorId = 352)
            }
        }
        val commit: suspend (AndroidFavoriteSnapshot) -> Unit = { visible = it.messages }

        pager.refresh("profile-a", { visible }, loadPage, commit)
        val refresh = async { pager.refresh("profile-a", { visible }, loadPage, commit) }
        refreshStarted.await()
        val older = async { pager.loadOlder("profile-a", 250, { visible }, loadPage, commit) }
        releaseRefresh.complete(Unit)
        refresh.await()
        older.await()

        assertEquals(500, visible.size)
        assertEquals(500, visible.map(Message::id).distinct().size)
    }

    @Test
    fun actorSwitchClearsCursorAndRejectsStaleInFlightLoader() = runBlocking {
        val pager = AndroidChatFavoritesPager()
        var visible = emptyList<Message>()
        var currentActor = "profile-a"
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val stale = async {
            runCatching {
                pager.refresh(
                    actorId = "profile-a",
                    cachedMessages = { visible },
                    loadPage = { _, _ ->
                        requestStarted.complete(Unit)
                        releaseRequest.await()
                        check(currentActor == "profile-a") { "chat_favorites_session_actor_mismatch" }
                        page(11 downTo 11, hasMore = false, cursorId = null)
                    },
                    commit = { visible = it.messages },
                )
            }
        }
        requestStarted.await()
        currentActor = "profile-b"
        releaseRequest.complete(Unit)
        assertTrue(stale.await().isFailure)

        assertTrue(pager.switchActor("profile-b"))
        visible = emptyList()
        val snapshot = pager.refresh(
            actorId = "profile-b",
            cachedMessages = { visible },
            loadPage = { _, _ -> page(22 downTo 22, hasMore = false, cursorId = null) },
            commit = { visible = it.messages },
        )
        assertEquals(listOf("22"), snapshot.messages.map(Message::id))
        assertEquals(2L, snapshot.generation)
    }

    private fun page(ids: IntProgression, hasMore: Boolean, cursorId: Int?): AndroidFavoritePage =
        AndroidFavoritePage(
            messages = ids.map(::message),
            hasMore = hasMore,
            nextCursor = cursorId?.let { ChatFavoriteCursor("2026-10-01T00:00:00Z", it.toLong()) },
        )

    private fun message(id: Int) = Message(
        id = id.toString(),
        conversationId = "sb:77",
        senderId = "profile-a",
        senderName = "Actor",
        text = "favorite-$id",
        sentAt = "2026-10-01T00:00:00Z",
        sentAtMillis = id.toLong(),
        isFavorite = true,
    )
}
