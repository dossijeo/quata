package com.quata.feature.chat.presentation.conversations

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the product boundary: all launch routes must converge on the shared list host. */
class ConversationsCompositionStaticTest {
    private val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun fourConversationRoutesConvergeOnConversationsScreenHostWithoutBrowserList() {
        val androidRoute = source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt")
        val androidAdapter = source("app/src/main/java/com/quata/feature/chat/presentation/conversations/ConversationsScreen.kt")
        val androidChatAdapter = source("app/src/main/java/com/quata/feature/chat/presentation/chat/ChatScreen.kt")
        val webAdapter = source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt")
        val iosAdapter = source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt")
        val commonBrowserRoute = source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt")

        assertTrue("ConversationsScreen(" in androidRoute)
        assertTrue("ConversationsScreenHost(" in androidAdapter)
        assertTrue("ChatScreenHost(" in androidChatAdapter)
        assertTrue("compactHeader = isLandscapeLayout" in androidRoute)
        assertTrue("appHeaderActions = {" in androidRoute && "QuataAppHeaderActions(" in androidRoute)
        assertTrue("compactHeader = compactHeader" in androidChatAdapter)
        assertTrue("appHeaderActions = appHeaderActions" in androidChatAdapter)
        assertTrue("openingProfileUserId = openingProfileUserId" in androidChatAdapter)
        assertFalse("navigationMessage = openingProfileUserId" in androidChatAdapter)
        assertTrue("ChatScreenHost(" in webAdapter && "ConversationsScreenHost(" in webAdapter)
        assertTrue("ChatScreenHost(" in iosAdapter && "ConversationsScreenHost(" in iosAdapter)
        assertTrue("profileAvatar = ::AndroidChatProfileAvatar" in androidChatAdapter)
        assertTrue("profileAvatar = { presentation" in webAdapter)
        assertTrue("profileAvatar = { presentation" in iosAdapter)
        assertTrue("onOpenAvatar = onOpenUserProfile" in webAdapter)
        assertTrue("onOpenMap = { value -> value.safeWebMapUrl()?.let(::openWebExternalLink) }" in webAdapter)
        assertTrue("onTranslateMessage = { text -> openWebExternalLink(webTranslationUrl(text)) }" in webAdapter)
        assertTrue("onOpenMessageConversation = onOpenMessageConversation" in webAdapter)
        assertTrue("onOpenAvatar = dependencies.onOpenAvatar" in iosAdapter)
        assertTrue("onOpenMap = dependencies.onOpenMap" in iosAdapter)
        assertTrue("onTranslateMessage = dependencies.onTranslateMessage" in iosAdapter)
        assertTrue("onOpenMessageConversation = dependencies.onOpenMessageConversation" in iosAdapter)
        assertFalse("val onOpenAvatar: (String) -> Unit = {}" in iosAdapter)
        assertFalse("val onOpenMap: (String) -> Unit = {}" in iosAdapter)
        assertFalse("val onTranslateMessage: (String) -> Unit = {}" in iosAdapter)
        assertFalse("onOpenAvatar: (String) -> Unit = {}" in commonBrowserRoute)
        assertFalse("onOpenMap: (String) -> Unit = {}" in commonBrowserRoute)
        assertFalse("onTranslateMessage: (String) -> Unit = {}" in commonBrowserRoute)
        assertTrue("conversationListHost" in commonBrowserRoute)
        assertTrue("ChatScreenHost(" in commonBrowserRoute)
        assertTrue(androidChatAdapter.lineSequence().count() < 130)
        assertFalse(listOf(androidAdapter, androidChatAdapter, webAdapter, iosAdapter, commonBrowserRoute).any { "ChatBrowser" in it })
    }

    private fun source(path: String): String = File(root, path).readText()
}
