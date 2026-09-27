package com.quata.feature.chat.data

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import com.quata.data.supabase.RealtimeBroadcastClient
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ChatTypingIndicatorManagerReconnectTest {
    @Test
    fun errorAndFailureScheduleOneReconnect() {
        val client = FakeBroadcastClient()
        val manager = manager(client)

        manager.setVisibleConversation("sb:42", visible = true)
        manager.setAppForeground(true)
        client.awaitConnections(1)
        client.status(0, RealtimeStatus.Subscribed)
        client.status(0, RealtimeStatus.Error)
        client.failure(0)

        client.awaitConnections(2)
        Thread.sleep(700)
        assertEquals(2, client.connectionCount.get())
        assertEquals("realtime:quata-typing-sb:42", client.connections[1].topic)
    }

    @Test
    fun leavingForegroundCancelsPendingReconnect() {
        val client = FakeBroadcastClient()
        val manager = manager(client)

        manager.setVisibleConversation("sb:7", visible = true)
        manager.setAppForeground(true)
        client.awaitConnections(1)
        val disconnectsBeforeBackground = client.disconnectCount.get()
        client.status(0, RealtimeStatus.Subscribed)
        client.status(0, RealtimeStatus.Closed)
        manager.setAppForeground(false)

        Thread.sleep(700)
        assertEquals(1, client.connectionCount.get())
        assertEquals(disconnectsBeforeBackground + 1, client.disconnectCount.get())
    }

    @Test
    fun staleSubscribedCallbackCannotResurrectChannelAfterLifecycleBoundary() {
        val client = FakeBroadcastClient()
        val manager = manager(client)

        manager.setVisibleConversation("sb:8", visible = true)
        manager.setAppForeground(true)
        client.awaitConnections(1)

        val callbackReady = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val callback = thread(start = true) {
            callbackReady.countDown()
            releaseCallback.await(3, TimeUnit.SECONDS)
            client.status(0, RealtimeStatus.Subscribed)
        }
        callbackReady.await(3, TimeUnit.SECONDS)
        manager.setAppForeground(false)
        releaseCallback.countDown()
        callback.join(3_000)

        manager.setAppForeground(true)
        client.awaitConnections(2)
        assertEquals("realtime:quata-typing-sb:8", client.connections[1].topic)
    }

    @Test
    fun staleEventCannotPublishTypingAfterLifecycleBoundary() {
        val client = FakeBroadcastClient()
        val manager = manager(client)

        manager.setVisibleConversation("sb:9", visible = true)
        manager.setAppForeground(true)
        client.awaitConnections(1)
        manager.setAppForeground(false)

        client.event(
            0,
            RealtimeRawEvent(
                event = "broadcast",
                payload = buildJsonObject {
                    put("event", "typing")
                    put(
                        "payload",
                        buildJsonObject {
                            put("profile_id", "profile-peer")
                            put("is_typing", true)
                        },
                    )
                },
            ),
        )

        assertEquals(emptySet<String>(), manager.typingProfileIds.value)
    }

    @Test
    fun pendingTypingSendCannotCrossConversationAndStopPrecedesDisconnect() {
        val client = FakeBroadcastClient()
        val manager = manager(client)

        manager.setVisibleConversation("sb:a", visible = true)
        manager.setAppForeground(true)
        client.awaitConnections(1)
        client.status(0, RealtimeStatus.Subscribed)
        client.blockNextSend()
        manager.setTyping("sb:a", isTyping = true)
        client.awaitBlockedSend()

        val switchCompleted = CountDownLatch(1)
        val switchThread = thread(start = true) {
            manager.setVisibleConversation("sb:b", visible = true)
            switchCompleted.countDown()
        }
        assertFalse(switchCompleted.await(200, TimeUnit.MILLISECONDS))

        client.releaseBlockedSend()
        switchThread.join(3_000)
        client.awaitConnections(2)

        assertEquals(
            listOf(
                "connect:realtime:quata-typing-sb:a",
                "send:realtime:quata-typing-sb:a:true",
                "send:realtime:quata-typing-sb:a:false",
                "disconnect:realtime:quata-typing-sb:a",
                "connect:realtime:quata-typing-sb:b",
            ),
            client.lifecycleEvents.filterNot { it == "disconnect:null" },
        )
    }

    private fun manager(client: FakeBroadcastClient): ChatTypingIndicatorManager {
        val storage = InMemorySessionStorage().apply {
            saveSession(
                AuthSession(
                    token = "access",
                    userId = "profile-self",
                    email = "self@example.invalid",
                    displayName = "Self",
                    accessToken = "access",
                    refreshToken = "refresh",
                ),
            )
        }
        return ChatTypingIndicatorManager(client, SessionManager(storage))
    }

    private class InMemorySessionStorage : SessionStorage {
        private var session: AuthSession? = null
        override fun saveSession(session: AuthSession) { this.session = session }
        override fun getSession(): AuthSession? = session
        override fun clear() { session = null }
    }

    private class FakeBroadcastClient : RealtimeBroadcastClient {
        val connectionCount = AtomicInteger()
        val disconnectCount = AtomicInteger()
        val connections = CopyOnWriteArrayList<Connection>()
        val lifecycleEvents = CopyOnWriteArrayList<String>()
        @Volatile private var activeTopic: String? = null
        @Volatile private var sendStarted: CountDownLatch? = null
        @Volatile private var sendRelease: CountDownLatch? = null

        override fun connectBroadcast(
            accessToken: String,
            presenceKey: String,
            topic: String,
            onEvent: (RealtimeRawEvent) -> Unit,
            onStatus: (RealtimeStatus) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            activeTopic = topic
            lifecycleEvents += "connect:$topic"
            connections += Connection(topic, onEvent, onStatus, onFailure)
            connectionCount.incrementAndGet()
        }

        override fun sendBroadcast(event: String, payload: JsonObject): Boolean {
            val topicAtSend = activeTopic
            sendStarted?.countDown()
            sendRelease?.await(3, TimeUnit.SECONDS)
            lifecycleEvents += "send:$topicAtSend:${payload["is_typing"]?.jsonPrimitive?.content}"
            sendStarted = null
            sendRelease = null
            return true
        }

        override fun disconnect() {
            lifecycleEvents += "disconnect:$activeTopic"
            activeTopic = null
            disconnectCount.incrementAndGet()
        }

        fun blockNextSend() {
            sendStarted = CountDownLatch(1)
            sendRelease = CountDownLatch(1)
        }

        fun awaitBlockedSend() {
            assertEquals(true, sendStarted?.await(3, TimeUnit.SECONDS))
        }

        fun releaseBlockedSend() {
            sendRelease?.countDown()
        }

        fun status(index: Int, status: RealtimeStatus) = connections[index].onStatus(status)
        fun event(index: Int, event: RealtimeRawEvent) = connections[index].onEvent(event)
        fun failure(index: Int) = connections[index].onFailure(IllegalStateException("forced"))

        fun awaitConnections(expected: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (connectionCount.get() < expected && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(expected, connectionCount.get())
        }
    }

    private data class Connection(
        val topic: String,
        val onEvent: (RealtimeRawEvent) -> Unit,
        val onStatus: (RealtimeStatus) -> Unit,
        val onFailure: (Throwable) -> Unit,
    )
}
