package com.quata.feature.chat.data

import com.quata.core.model.Message
import com.quata.feature.chat.domain.ChatFavoriteCursor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AndroidFavoritePage(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextCursor: ChatFavoriteCursor?,
)

internal data class AndroidFavoriteSnapshot(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextCursor: ChatFavoriteCursor?,
    val loadedPageCount: Int,
    val generation: Long,
)

/** Serializes refresh and deep paging and keeps their cursor state owned by one actor. */
internal class AndroidChatFavoritesPager(
    private val pageSize: Int = 250,
) {
    private val mutex = Mutex()
    private var ownerActorId: String? = null
    private var generation: Long = 0L
    private var loadedPageCount: Int = 0
    private var nextCursor: ChatFavoriteCursor? = null
    private var hasMore: Boolean = false

    suspend fun switchActor(actorId: String?): Boolean = mutex.withLock {
        activateActorLocked(actorId)
    }

    suspend fun refresh(
        actorId: String,
        cachedMessages: suspend () -> List<Message>,
        loadPage: suspend (ChatFavoriteCursor?, Int) -> AndroidFavoritePage,
        commit: suspend (AndroidFavoriteSnapshot) -> Unit,
    ): AndroidFavoriteSnapshot = mutex.withLock {
        val actorChanged = activateActorLocked(actorId)
        val pagesToRetain = loadedPageCount.coerceAtLeast(1)
        var cursor: ChatFavoriteCursor? = null
        var pageHasMore = true
        var loadedPages = 0
        val refreshed = mutableListOf<Message>()
        while (loadedPages < pagesToRetain && pageHasMore) {
            val page = loadPage(cursor, pageSize)
            refreshed += page.messages
            cursor = page.nextCursor
            pageHasMore = page.hasMore
            loadedPages += 1
        }
        val messages = reconcileChatMessages(
            incoming = refreshed.distinctBy(Message::id),
            existing = if (actorChanged) emptyList() else cachedMessages(),
            retainUnmatchedExisting = false,
        ).sortedByDescending { it.sentAtMillis ?: 0L }
        loadedPageCount = loadedPages.coerceAtLeast(1)
        nextCursor = cursor
        hasMore = pageHasMore
        val snapshot = snapshot(messages)
        commit(snapshot)
        snapshot
    }

    suspend fun loadOlder(
        actorId: String,
        limit: Int,
        currentMessages: suspend () -> List<Message>,
        loadPage: suspend (ChatFavoriteCursor?, Int) -> AndroidFavoritePage,
        commit: suspend (AndroidFavoriteSnapshot) -> Unit,
    ): AndroidFavoriteSnapshot = mutex.withLock {
        val actorChanged = activateActorLocked(actorId)
        if (!hasMore) {
            val snapshot = snapshot(if (actorChanged) emptyList() else currentMessages())
            commit(snapshot)
            return@withLock snapshot
        }
        val cursor = nextCursor ?: error("chat_favorites_cursor_missing")
        val page = loadPage(cursor, limit.coerceIn(1, pageSize))
        val messages = reconcileChatMessages(
            incoming = page.messages,
            existing = if (actorChanged) emptyList() else currentMessages(),
            retainUnmatchedExisting = true,
        ).sortedByDescending { it.sentAtMillis ?: 0L }
        loadedPageCount += 1
        nextCursor = page.nextCursor
        hasMore = page.hasMore
        val snapshot = snapshot(messages)
        commit(snapshot)
        snapshot
    }

    private fun activateActorLocked(actorId: String?): Boolean {
        if (ownerActorId == actorId) return false
        ownerActorId = actorId
        generation += 1L
        loadedPageCount = 0
        nextCursor = null
        hasMore = false
        return true
    }

    private fun snapshot(messages: List<Message>) = AndroidFavoriteSnapshot(
        messages = messages,
        hasMore = hasMore,
        nextCursor = nextCursor,
        loadedPageCount = loadedPageCount,
        generation = generation,
    )
}
