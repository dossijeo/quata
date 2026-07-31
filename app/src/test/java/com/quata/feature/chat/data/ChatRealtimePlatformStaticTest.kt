package com.quata.feature.chat.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the productive Phoenix adapters, not merely the common fake gateway contract. */
class ChatRealtimePlatformStaticTest {
    private val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun webAndIosUseConversationTopicsReducerAndLifecycleReconnect() {
        val web = source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRealtimeGateway.kt")
        val ios = source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosChatRealtimeGateway.kt")
        val iosHost = source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt")
        val webHost = source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt")

        listOf(web, ios).forEach { gateway ->
            assertTrue("presenceSnapshot.reduce(event, payload)" in gateway)
            assertTrue("typingProfileIds(" in gateway)
            assertTrue("topicFor(visible" in gateway || "topicFor(visibleConversation" in gateway)
            assertTrue("setVisibleConversation" in gateway && "reconcile()" in gateway)
            assertTrue("presence_state" in gateway && "presence_diff" in gateway)
        }
        assertTrue("UIApplicationDidEnterBackgroundNotification" in iosHost)
        assertTrue("UIApplicationWillEnterForegroundNotification" in iosHost)
        assertTrue("UIApplicationDidBecomeActiveNotification" in iosHost)
        assertTrue("UIApplicationWillResignActiveNotification" in iosHost)
        assertTrue("center.removeObserver" in iosHost)
        assertTrue("chatHostIsForeground(ChatHostLifecycleEvent.Dispose)" in iosHost)
        assertTrue("onOpenAvatar = dependencies.onOpenAvatar" in iosHost)
        assertTrue("onOpenAvatar = onOpenUserProfile" in webHost)
    }

    private fun source(path: String): String = File(root, path).readText()
}
