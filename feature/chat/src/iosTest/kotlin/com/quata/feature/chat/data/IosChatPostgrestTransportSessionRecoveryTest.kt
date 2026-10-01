package com.quata.feature.chat.data

import com.quata.core.model.AuthSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IosChatPostgrestTransportSessionRecoveryTest {
    @Test
    fun unauthorizedRequestRefreshesAndRetriesExactlyOnce() = runTest {
        val initial = session("old-token")
        val refreshed = session("new-token")
        val usedTokens = mutableListOf<String>()
        var refreshCalls = 0

        val result = executeIosChatRequestWithSingleRefresh(
            initialSession = initial,
            expectedProfileId = initial.userId,
            latestSession = { initial },
            forceRefresh = { refreshCalls += 1; refreshed },
        ) { session ->
            usedTokens += session.bearerToken
            if (session == initial) throw IosChatHttpStatusException(401)
            "accepted"
        }

        assertEquals("accepted", result)
        assertEquals(listOf("old-token", "new-token"), usedTokens)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun concurrentReplacementForSameActorIsRetriedWithoutRefreshingIt() = runTest {
        val initial = session("old-token")
        val replacement = session("replacement-token")
        var refreshCalls = 0
        val usedTokens = mutableListOf<String>()

        val result = executeIosChatRequestWithSingleRefresh(
            initialSession = initial,
            expectedProfileId = initial.userId,
            latestSession = { replacement },
            forceRefresh = { refreshCalls += 1; null },
        ) { session ->
            usedTokens += session.bearerToken
            if (session == initial) throw IosChatHttpStatusException(401)
            "accepted"
        }

        assertEquals("accepted", result)
        assertEquals(listOf("old-token", "replacement-token"), usedTokens)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun concurrentReplacementForAnotherActorFailsClosed() = runTest {
        val initial = session("old-token")
        val replacement = session("replacement-token", userId = "member-8")
        var attempts = 0

        val failure = runCatching {
            executeIosChatRequestWithSingleRefresh(
                initialSession = initial,
                expectedProfileId = initial.userId,
                latestSession = { replacement },
                forceRefresh = { error("must not refresh replacement") },
            ) {
                attempts += 1
                throw IosChatHttpStatusException(401)
            }
        }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertEquals("ios_chat_session_changed", failure.message)
        assertEquals(1, attempts)
    }

    @Test
    fun nonUnauthorizedFailureAndUnchangedTokenAreNeverReplayed() = runTest {
        for (status in listOf(403, 500)) {
            var refreshCalls = 0
            var attempts = 0
            val failure = runCatching {
                executeIosChatRequestWithSingleRefresh(
                    initialSession = session("token"),
                    expectedProfileId = null,
                    latestSession = { session("token") },
                    forceRefresh = { refreshCalls += 1; session("new-token") },
                ) {
                    attempts += 1
                    throw IosChatHttpStatusException(status)
                }
            }.exceptionOrNull()
            assertEquals(status, assertIs<IosChatHttpStatusException>(failure).status)
            assertEquals(1, attempts)
            assertEquals(0, refreshCalls)
        }

        var attempts = 0
        val unchanged = runCatching {
            executeIosChatRequestWithSingleRefresh(
                initialSession = session("token"),
                expectedProfileId = null,
                latestSession = { session("token") },
                forceRefresh = { session("token") },
            ) {
                attempts += 1
                throw IosChatHttpStatusException(401)
            }
        }.exceptionOrNull()
        assertEquals(401, assertIs<IosChatHttpStatusException>(unchanged).status)
        assertEquals(1, attempts)
    }

    private fun session(token: String, userId: String = "member-7") = AuthSession(
        token = token,
        accessToken = token,
        refreshToken = "refresh-token",
        expiresAt = 4_102_444_800L,
        userId = userId,
        email = "$userId@quata.test",
        displayName = "Member",
    )
}
