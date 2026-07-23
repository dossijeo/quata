package com.quata.core.feed

/** In-memory paging policy shared by every client. */

class QuataPagedFeedStore<T>(
    private val pageSize: Int,
    private val idOf: (T) -> String,
    private val cursorOf: (T) -> String
) {
    private var realtimeItems: List<T> = emptyList()
    private var olderItems: List<T> = emptyList()

    var hasMoreOlderItems: Boolean = true
        private set

    val items: List<T>
        get() = (realtimeItems + olderItems).distinctBy(idOf)

    fun reset() {
        realtimeItems = emptyList()
        olderItems = emptyList()
        hasMoreOlderItems = true
    }

    fun setRealtime(items: List<T>): List<T> {
        realtimeItems = items
        val realtimeIds = items.map(idOf).toSet()
        olderItems = olderItems.filterNot { idOf(it) in realtimeIds }
        hasMoreOlderItems = items.size >= pageSize && hasMoreOlderItems
        return this.items
    }

    fun replaceInitialPage(items: List<T>): List<T> {
        realtimeItems = items
        olderItems = emptyList()
        hasMoreOlderItems = items.size >= pageSize
        return this.items
    }

    fun appendOlder(items: List<T>): List<T> {
        val realtimeIds = realtimeItems.map(idOf).toSet()
        olderItems = (olderItems + items)
            .filterNot { idOf(it) in realtimeIds }
            .distinctBy(idOf)
        hasMoreOlderItems = items.size >= pageSize
        return this.items
    }

    fun replace(item: T): List<T> {
        val id = idOf(item)
        realtimeItems = realtimeItems.map { if (idOf(it) == id) item else it }
        olderItems = olderItems.map { if (idOf(it) == id) item else it }
        return items
    }

    fun prependIfMissing(item: T): List<T> {
        val id = idOf(item)
        if (items.none { idOf(it) == id }) {
            realtimeItems = listOf(item) + realtimeItems
        }
        return items
    }

    fun remove(id: String): List<T> {
        realtimeItems = realtimeItems.filterNot { idOf(it) == id }
        olderItems = olderItems.filterNot { idOf(it) == id }
        return items
    }

    fun olderCursor(): String? =
        items.lastOrNull()
            ?.let(cursorOf)
            ?.takeIf { it.isNotBlank() }
}
