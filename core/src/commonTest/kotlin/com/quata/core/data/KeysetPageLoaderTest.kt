package com.quata.core.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeysetPageLoaderTest {
    @Test
    fun accumulatesEveryPageBeyondTheTransportLimit() = runTest {
        val source = (1..1_205).map { it.toString().padStart(4, '0') }
        val requestedCursors = mutableListOf<String?>()

        val result = loadCompleteKeyset(
            pageSize = 500,
            cursorOf = { it },
        ) { after, limit ->
            requestedCursors += after
            source.asSequence().filter { after == null || it > after }.take(limit).toList()
        }

        assertEquals(source, result)
        assertEquals(listOf(null, "0500", "1000"), requestedCursors)
    }

    @Test
    fun exactPageBoundaryRequestsAnEmptyTerminalPage() = runTest {
        val source = listOf("a", "b", "c", "d")
        var calls = 0

        val result = loadCompleteKeyset(pageSize = 2, cursorOf = { it }) { after, limit ->
            calls += 1
            source.asSequence().filter { after == null || it > after }.take(limit).toList()
        }

        assertEquals(source, result)
        assertEquals(3, calls)
    }

    @Test
    fun rejectsAnUnorderedOrRepeatedCursorInsteadOfLoopingOrDroppingRows() = runTest {
        assertFailsWith<IllegalStateException> {
            loadCompleteKeyset(pageSize = 2, cursorOf = { it }) { _, _ -> listOf("b", "a") }
        }
        assertFailsWith<IllegalStateException> {
            loadCompleteKeyset(pageSize = 2, cursorOf = { it }) { _, _ -> listOf("a", "a") }
        }
    }
}
