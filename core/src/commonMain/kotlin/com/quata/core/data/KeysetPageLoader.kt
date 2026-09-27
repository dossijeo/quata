package com.quata.core.data

/**
 * Reads a complete, strictly ordered keyset without relying on a server-side maximum row count.
 * The page loader must apply `cursor > afterExclusive` and return rows ordered by the same cursor.
 */
suspend fun <T> loadCompleteKeyset(
    pageSize: Int,
    cursorOf: (T) -> String,
    loadPage: suspend (afterExclusive: String?, limit: Int) -> List<T>,
): List<T> {
    require(pageSize > 0) { "keyset_page_size_invalid" }

    val result = mutableListOf<T>()
    var cursor: String? = null
    while (true) {
        val page = loadPage(cursor, pageSize)
        check(page.size <= pageSize) { "keyset_page_exceeds_requested_limit" }
        var pageCursor = cursor
        page.forEach { item ->
            val next = cursorOf(item)
            check(next.isNotBlank()) { "keyset_cursor_blank" }
            check(pageCursor == null || next > pageCursor!!) { "keyset_page_not_strictly_ordered" }
            pageCursor = next
            result += item
        }
        if (page.size < pageSize) return result
        cursor = checkNotNull(pageCursor) { "keyset_full_page_without_cursor" }
    }
}
