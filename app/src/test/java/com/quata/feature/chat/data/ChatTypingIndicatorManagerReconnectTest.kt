package com.quata.feature.chat.data

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import com.quata.data.supabase.RealtimeBroadcastClient
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

        override fun connectBroadcast(
            accessToken: String,
            presenceKey: String,
            topic: String,
            onEvent: (RealtimeRawEvent) -> Unit,
            onStatus: (RealtimeStatus) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            connections += Connection(topic, onStatus, onFailure)
            connectionCount.incrementAndGet()
        }

        override fun sendBroadcast(event: String, payload: JsonObject): Boolean = true

        override fun disconnect() {
            disconnectCount.incrementAndGet()
        }

        fun status(index: Int, status: RealtimeStatus) = connections[index].onStatus(status)
        fun failure(index: Int) = connections[index].onFailure(IllegalStateException("forced"))

        fun awaitConnections(expected: Int) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (connectionCount.get() < expected && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(expected, connectionCount.get())
        }
    }

    private data class Connection(
        val topic: String,
        val onStatus: (RealtimeStatus) -> Unit,
        val onFailure: (Throwable) -> Unit,
    )
}
