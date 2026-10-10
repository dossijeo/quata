package com.quata.feature.chat.data

import com.quata.core.model.AuthSession
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.valueForHTTPHeaderField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(BetaInteropApi::class)
class IosNotificationReplyHttpBoundaryTest {
    @Test
    fun replyRpcUsesTheExactAuthenticatedPostgrestBoundary() = runTest {
        val requests = mutableListOf<CapturedRequest>()

        val result = executeIosChatPostgrestRequest(
            configuration = configuration(),
            functionName = "quata_chat_send_message",
            body = ReplyBody,
            initialSession = session("old-token"),
            expectedProfileId = ProfileId,
            latestSession = { null },
            forceRefresh = { error("successful request must not refresh") },
            requestTimeoutMillis = 5_000L,
            executeRequest = { request, timeout ->
                requests += request.capture(timeout)
                "accepted"
            },
        )

        assertEquals("accepted", result)
        assertEquals(
            listOf(
                CapturedRequest(
                    url = "https://reply-boundary.invalid/rest/v1/rpc/quata_chat_send_message",
                    method = "POST",
                    apiKey = "sb_publishable_reply_boundary",
                    authorization = "Bearer old-token",
                    accept = "application/json",
                    contentType = "application/json",
                    body = ReplyBody,
                    timeoutMillis = 5_000L,
                ),
            ),
            requests,
        )
    }

    @Test
    fun unauthorizedReplyRefreshesTheSameActorAndReplaysExactlyOnce() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        var refreshCalls = 0

        val result = executeIosChatPostgrestRequest(
            configuration = configuration(),
            functionName = "quata_chat_send_message",
            body = ReplyBody,
            initialSession = session("old-token"),
            expectedProfileId = ProfileId,
            latestSession = { session("old-token") },
            forceRefresh = { refreshCalls += 1; session("new-token") },
            requestTimeoutMillis = 5_000L,
            executeRequest = { request, timeout ->
                requests += request.capture(timeout)
                if (requests.size == 1) throw IosChatHttpStatusException(401)
                "accepted"
            },
        )

        assertEquals("accepted", result)
        assertEquals(1, refreshCalls)
        assertEquals(listOf("Bearer old-token", "Bearer new-token"), requests.map { it.authorization })
        assertEquals(listOf(ReplyBody, ReplyBody), requests.map { it.body })
        assertEquals(2, requests.size)
    }

    @Test
    fun replacementByAnotherActorFailsClosedBeforeAnyReplay() = runTest {
        var attempts = 0
        val failure = runCatching {
            executeIosChatPostgrestRequest(
                configuration = configuration(),
                functionName = "quata_chat_send_message",
                body = ReplyBody,
                initialSession = session("old-token"),
                expectedProfileId = ProfileId,
                latestSession = { session("other-token", "profile-other") },
                forceRefresh = { error("actor replacement must not refresh") },
                requestTimeoutMillis = 5_000L,
                executeRequest = { _, _ ->
                    attempts += 1
                    throw IosChatHttpStatusException(401)
                },
            )
        }.exceptionOrNull()

        assertEquals("ios_chat_session_changed", assertIs<IllegalStateException>(failure).message)
        assertEquals(1, attempts)
    }

    @Test
    fun forbiddenAndServerFailuresAreNotRefreshedOrReplayedInsideOneTransportCall() = runTest {
        for (status in listOf(403, 500)) {
            var attempts = 0
            var refreshCalls = 0
            val failure = runCatching {
                executeIosChatPostgrestRequest(
                    configuration = configuration(),
                    functionName = "quata_chat_send_message",
                    body = ReplyBody,
                    initialSession = session("old-token"),
                    expectedProfileId = ProfileId,
                    latestSession = { session("replacement-token") },
                    forceRefresh = { refreshCalls += 1; session("new-token") },
                    requestTimeoutMillis = 5_000L,
                    executeRequest = { _, _ ->
                        attempts += 1
                        throw IosChatHttpStatusException(status)
                    },
                )
            }.exceptionOrNull()

            assertEquals(status, assertIs<IosChatHttpStatusException>(failure).status)
            assertEquals(1, attempts)
            assertEquals(0, refreshCalls)
        }
    }

    private fun configuration() = IosChatRuntimeConfiguration(
        supabaseUrl = "https://reply-boundary.invalid/",
        supabasePublishableKey = "sb_publishable_reply_boundary",
    )

    private fun session(token: String, userId: String = ProfileId) = AuthSession(
        token = token,
        accessToken = token,
        refreshToken = "refresh-token",
        expiresAt = 4_102_444_800L,
        userId = userId,
        email = "$userId@quata.test",
        displayName = "Reply actor",
    )

    private fun NSMutableURLRequest.capture(timeoutMillis: Long): CapturedRequest = CapturedRequest(
        url = URL?.absoluteString.orEmpty(),
        method = HTTPMethod.orEmpty(),
        apiKey = valueForHTTPHeaderField("apikey").orEmpty(),
        authorization = valueForHTTPHeaderField("Authorization").orEmpty(),
        accept = valueForHTTPHeaderField("Accept").orEmpty(),
        contentType = valueForHTTPHeaderField("Content-Type").orEmpty(),
        body = assertNotNull(HTTPBody)
            .let { NSString.create(data = it, encoding = NSUTF8StringEncoding) }
            .toString(),
        timeoutMillis = timeoutMillis,
    )

    private data class CapturedRequest(
        val url: String,
        val method: String,
        val apiKey: String,
        val authorization: String,
        val accept: String,
        val contentType: String,
        val body: String,
        val timeoutMillis: Long,
    )

    private companion object {
        const val ProfileId = "profile-reply"
        const val ReplyBody = """{"p_actor_profile_id":"profile-reply","p_thread_id":17,"p_message":"reply marker","p_file_ids":[],"p_reply_to_message_id":null,"p_client_message_id":"reply-client-id"}"""
    }
}
