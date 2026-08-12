package com.quata.feature.chat.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.MainActivity
import com.quata.QuataApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ChatActionsNotificationsInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun composerReplyEditActionsAndFavoriteUseSharedChatUi() = runBlocking {
        val credentialsFile = optionalArgument("quataChatActionsCredentialsFile")
        val chatUrl = optionalArgument("quataChatActionsUrl")
        val ownProbe = optionalArgument("quataChatActionsOwnProbe")
        val peerProbe = optionalArgument("quataChatActionsPeerProbe")
        val profileId = optionalArgument("quataChatActionsProfileId")
        val composerMarker = optionalArgument("quataChatActionsComposerMarker")
        val replyMarker = optionalArgument("quataChatActionsReplyMarker")
        val editMarker = optionalArgument("quataChatActionsEditMarker")
        val forwardQuery = optionalArgument("quataChatActionsForwardQuery")
        val stage = optionalArgument("quataChatActionsStage") ?: "full"
        val credentials = credentialsFile?.let(::credentialsFromFile)
    val hasRequiredStageArguments = when (stage) {
        "profile", "profile-follow", "profile-lists" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank()
            else -> listOf(chatUrl, ownProbe, composerMarker, replyMarker, editMarker).all { !it.isNullOrBlank() }
        }
        assumeTrue(
            "CHAT-ACTIONS-NOTIFICATIONS Android evidence is opt-in.",
            credentials != null && hasRequiredStageArguments,
        )

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        app.container.authRepository.login(credentials!!.countryCode, credentials.phone, credentials.password).getOrThrow()
        assertTrue(
            "The Android app must hold a real Supabase-authenticated session before opening Chat.",
            app.container.sessionManager.currentSession()?.isSupabaseAuthenticated() == true,
        )

        ActivityScenario.launch<MainActivity>(chatIntent(chatUrl.orEmpty())).use {
            when (stage) {
                "send-reply" -> runSendReplyStage(ownProbe.orEmpty(), composerMarker.orEmpty(), replyMarker.orEmpty())
                "edit-favorite" -> runEditFavoriteStage(ownProbe.orEmpty(), composerMarker.orEmpty(), editMarker.orEmpty())
                "forward" -> runForwardStage(editMarker.orEmpty(), forwardQuery.orEmpty())
                "translation" -> runTranslationStage(ownProbe.orEmpty())
            "profile" -> runProfileStage(peerProbe.orEmpty(), profileId.orEmpty())
            "profile-follow" -> runProfileFollowStage(peerProbe.orEmpty(), profileId.orEmpty())
            "profile-lists" -> runProfileListsStage(peerProbe.orEmpty(), profileId.orEmpty())
                "full" -> {
                    runSendReplyStage(ownProbe.orEmpty(), composerMarker.orEmpty(), replyMarker.orEmpty())
                    runEditFavoriteStage(ownProbe.orEmpty(), composerMarker.orEmpty(), editMarker.orEmpty())
                    runForwardStage(editMarker.orEmpty(), forwardQuery.orEmpty())
                }
                else -> error("unknown_chat_actions_stage:$stage")
            }
        }

        writeReport(
            JSONObject()
                .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                .put("status", "passed")
                .put("evidenceDirectory", evidenceDir().absolutePath),
        )
    }

    private suspend fun runTranslationStage(markerProbe: String) {
        waitForMarker(markerProbe, "initial chat translation thread")
        saveScreenshot("android-chat-translation-before")
        compose.onNodeWithTag(ChatTranslatorTriggerTestTag, useUnmergedTree = true)
            .performClick()
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag(ChatTranslatorOverlayTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag(ChatTranslatorInstructionTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-chat-translation-overlay")
        compose.onNode(
            SemanticsMatcher("translator message contains marker") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChatTranslatorMessageTestTagPrefix) == true &&
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(markerProbe) } == true
            },
            useUnmergedTree = true,
        ).performClick()
        compose.waitForIdle()
        device.click((device.displayWidth * 0.62f).toInt(), (device.displayHeight * 0.36f).toInt())
        waitForAnyTranslatorMarker(listOf("pan de trigo", "The bread", "Le pain"), "translated chat message", 90_000)
        waitForAnyTranslatorMarker(listOf("FAN->ES", "FAN->EN", "FAN->FR"), "translation direction", 10_000)
        saveScreenshot("android-chat-translation-result")
        compose.onNodeWithTag(ChatTranslatorExitTestTag, useUnmergedTree = true)
            .performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(ChatTranslatorOverlayTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
        waitForMarker(markerProbe, "chat translation return")
        saveScreenshot("android-chat-translation-return")
    }

    private fun chatIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url), targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)

    private suspend fun runSendReplyStage(ownProbe: String, composerMarker: String, replyMarker: String) {
        waitForMarker(ownProbe, "initial chat thread")
        saveScreenshot("android-chat-actions-thread-initial")

        fillComposer(composerMarker)
        flushPendingChatMessages()
        waitForMarker(composerMarker.take(28), "composer message")
        saveScreenshot("android-chat-composer-sent")

        openMessageActions(ownProbe)
        clickAction("chat.action.favorite", "Favorito")
        SystemClock.sleep(800)

        openMessageActions(ownProbe)
        clickAction("chat.action.reply", "Responder")
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(ChatComposerReplyBannerTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        fillComposer(replyMarker)
        flushPendingChatMessages()
        waitForMarker(replyMarker.take(28), "reply message")
        saveScreenshot("android-chat-composer-reply-sent")
    }

    private suspend fun runEditFavoriteStage(ownProbe: String, composerMarker: String, editMarker: String) {
        waitForMarker(ownProbe, "initial chat thread")
        openMessageActions(composerMarker.take(28))
        clickAction("chat.action.edit", "Editar")
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(ChatComposerEditingBannerTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-chat-composer-edit-mode")
        fillComposer(
            editMarker,
            beforeSendScreenshotName = "android-chat-composer-edit-filled",
            afterSendScreenshotName = "android-chat-composer-edit-submitted",
        )
        flushPendingChatMessages()
        waitForMarker(editMarker.take(28), "edited message")
        saveScreenshot("android-chat-composer-edit-sent")

        openMessageActions(editMarker.take(28))
        waitForAction("chat.action.copy", "Copiar")
        waitForAction("chat.action.reply", "Responder")
        waitForAction("chat.action.forward", "Reenviar")
        waitForAction("chat.action.edit", "Editar")
        waitForAction("chat.action.favorite", "Favorito")
        waitForAction("chat.action.delete", "Eliminar")
        saveScreenshot("android-chat-actions-own-selected")
    }

    private suspend fun runForwardStage(editMarker: String, forwardQuery: String) {
        check(forwardQuery.isNotBlank()) { "forward_destination_query_missing" }
        waitForMarker(editMarker.take(28), "edited message for forward")
        openMessageActions(editMarker.take(28))
        clickAction("chat.action.forward", "Reenviar")
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag(ChatForwardPickerRootTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag(ChatForwardPickerSearchTestTag, useUnmergedTree = true)
            .performTextReplacement(forwardQuery)
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNode(hasText(forwardQuery, substring = true), useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess || waitForObject(By.textContains(forwardQuery), forwardQuery, 250) != null
        }
        runCatching {
            compose.onNode(
                SemanticsMatcher("forward candidate contains query") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChatForwardPickerCandidateTestTagPrefix) == true &&
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(forwardQuery) } == true
                },
                useUnmergedTree = true,
            ).performClick()
        }.getOrElse {
            val nativeDestination = waitForObject(By.textContains(forwardQuery), forwardQuery, 2_000)
            check(nativeDestination != null) { "forward_destination_not_visible:$forwardQuery" }
            nativeDestination.click()
        }
        SystemClock.sleep(500)
        device.click(device.displayWidth / 2, (device.displayHeight * 0.35f).toInt())
        compose.waitForIdle()
        SystemClock.sleep(500)
        saveScreenshot("android-chat-forward-picker-selected")
        compose.onNodeWithTag(ChatForwardPickerSendTestTag, useUnmergedTree = true)
            .performClick()
        SystemClock.sleep(300)
        val nativeForward = waitForObject(By.textContains("Forward"), "Forward", 1_500)
            ?: waitForObject(By.textContains("Reenviar"), "Reenviar", 1_500)
        if (nativeForward != null) {
            nativeForward.click()
        } else {
            device.click((device.displayWidth * 0.72f).toInt(), (device.displayHeight * 0.472f).toInt())
        }
        compose.waitForIdle()
        saveScreenshot("android-chat-forward-submitted")
        compose.waitUntil(90_000) {
            runCatching {
                compose.onNodeWithTag(ChatForwardPickerRootTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
        delay(1_500)
    }

    private fun runProfileStage(peerProbe: String, profileId: String) {
        openPeerProfile(peerProbe, profileId)
        saveScreenshot("android-chat-profile-open")
        closePublicProfile(peerProbe)
        saveScreenshot("android-chat-profile-return")
    }

    private fun runProfileFollowStage(peerProbe: String, profileId: String) {
        openPeerProfile(peerProbe, profileId)
        saveScreenshot("android-chat-profile-follow-before")
        compose.onNodeWithTag("public-profile.follow.$profileId", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag("public-profile.follow.loading.$profileId", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
        saveScreenshot("android-chat-profile-follow-after")
        closePublicProfile(peerProbe)
        saveScreenshot("android-chat-profile-follow-return")
    }

    private fun openPeerProfile(peerProbe: String, profileId: String) {
        waitForMarker(peerProbe, "peer message for profile entry")
        dismissTranslatorOverlayIfActive()
        saveScreenshot("android-chat-profile-thread-initial")
        val avatarTag = "chat.profile.message.$profileId"
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag(avatarTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag(avatarTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        val openedFromMessage = runCatching {
            compose.waitUntil(12_000) { publicProfileVisible(profileId) }
            true
        }.getOrDefault(false)
        if (!openedFromMessage) {
            val memberTag = "chat.profile.member.$profileId"
            val clickedMemberAvatar = runCatching {
                compose.onNodeWithTag(memberTag, useUnmergedTree = true)
                    .performTouchInput { click(center) }
                true
            }.getOrDefault(false)
            if (!clickedMemberAvatar) clickVisibleMessageAvatarWithUiAutomator(peerProbe)
            compose.waitUntil(30_000) { publicProfileVisible(profileId) }
        }
        listOf(
            "public-profile.avatar.$profileId",
            "public-profile.name.$profileId",
            "public-profile.neighborhood.$profileId",
            "public-profile.kpi.posts.$profileId",
            "public-profile.kpi.followers.$profileId",
            "public-profile.kpi.following.$profileId",
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
    }

    private fun closePublicProfile(peerProbe: String) {
        val closedByCommonBack = runCatching {
            compose.onNodeWithTag("public-profile.back", useUnmergedTree = true)
                .performTouchInput { click(center) }
            true
        }.getOrDefault(false)
        if (!closedByCommonBack) device.pressBack()
        compose.waitUntil(20_000) { messageNodeVisible(peerProbe) }
    }

    private fun runProfileListsStage(peerProbe: String, profileId: String) {
        openProfileFromPeerMessage(peerProbe, profileId)
        openAndAssertProfileList(profileId, "followers")
        openAndAssertProfileList(profileId, "following")
        val closedByCommonBack = runCatching {
            compose.onNodeWithTag("public-profile.back", useUnmergedTree = true)
                .performTouchInput { click(center) }
            true
        }.getOrDefault(false)
        if (!closedByCommonBack) device.pressBack()
        compose.waitUntil(20_000) { messageNodeVisible(peerProbe) }
        saveScreenshot("android-chat-profile-lists-return")
    }

    private fun openProfileFromPeerMessage(peerProbe: String, profileId: String) {
        waitForMarker(peerProbe, "peer message for profile entry")
        dismissTranslatorOverlayIfActive()
        dismissSystemAnrDialogIfVisible()
        saveScreenshot("android-chat-profile-lists-thread-initial")
        val avatarTag = "chat.profile.message.$profileId"
        compose.waitUntil(20_000) {
            dismissSystemAnrDialogIfVisible()
            runCatching {
                compose.onNodeWithTag(avatarTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag(avatarTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        val openedFromMessage = runCatching {
            compose.waitUntil(12_000) { publicProfileVisible(profileId) }
            true
        }.getOrDefault(false)
        if (!openedFromMessage) {
            val memberTag = "chat.profile.member.$profileId"
            val clickedMemberAvatar = runCatching {
                compose.onNodeWithTag(memberTag, useUnmergedTree = true)
                    .performTouchInput { click(center) }
                true
            }.getOrDefault(false)
            if (!clickedMemberAvatar) clickVisibleMessageAvatarWithUiAutomator(peerProbe)
            compose.waitUntil(30_000) { publicProfileVisible(profileId) }
        }
        saveScreenshot("android-chat-profile-lists-open")
    }

    private fun openAndAssertProfileList(profileId: String, listKind: String) {
        compose.onNodeWithTag("public-profile.kpi.$listKind.$profileId", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitUntil(20_000) { profileListVisible(listKind) }
        saveScreenshot("android-chat-profile-list-$listKind")
        assertTrue(
            "Public profile $listKind list must expose at least one visible test-profile row.",
            waitForObject(By.textContains("Gabriel"), "public profile $listKind row", 5_000) != null,
        )
        compose.onNodeWithTag("public-profile.list.back.$listKind", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitUntil(20_000) { publicProfileVisible(profileId) }
    }

    private fun publicProfileVisible(profileId: String): Boolean =
        runCatching {
            compose.onNodeWithTag("public-profile.user.$profileId", useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess

    private fun profileListVisible(listKind: String): Boolean =
        runCatching {
            compose.onNodeWithTag("public-profile.list.$listKind", useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess

    private fun clickVisibleMessageAvatarWithUiAutomator(peerProbe: String) {
        val probe = peerProbe.take(28)
        dismissSystemAnrDialogIfVisible()
        val message = device.wait(Until.findObject(By.textContains(probe)), 10_000)
            ?: error("profile_message_probe_not_visible:$probe")
        val bounds = message.visibleBounds
        val x = (bounds.left - 72).coerceAtLeast(20)
        val y = bounds.centerY().coerceAtLeast(20)
        assertTrue("UIAutomator must dispatch a real tap on the visible message avatar.", device.click(x, y))
        SystemClock.sleep(1_000)
    }

    private fun dismissTranslatorOverlayIfActive() {
        val dismissed = runCatching {
            compose.onNodeWithTag(ChatTranslatorExitTestTag, useUnmergedTree = true)
                .performTouchInput { click(center) }
            true
        }.getOrDefault(false)
        if (dismissed) {
            compose.waitUntil(10_000) {
                runCatching {
                    compose.onNodeWithTag(ChatTranslatorOverlayTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure
            }
        }
    }

    private fun dismissSystemAnrDialogIfVisible() {
        val waitButton = device.findObject(By.text("Wait"))
            ?: device.findObject(By.text("Esperar"))
        if (waitButton != null) {
            waitButton.click()
            device.waitForIdle()
            SystemClock.sleep(1_000)
        }
    }

    private suspend fun flushPendingChatMessages() {
        delay(1_500)
        repeat(5) {
            if (app.container.chatRepository.flushPendingMessages()) return
            delay(800)
        }
        error("chat_pending_outbox_not_flushed")
    }

    private fun fillComposer(
        text: String,
        forceNativeSend: Boolean = false,
        beforeSendScreenshotName: String? = null,
        afterSendScreenshotName: String? = null,
    ) {
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag(ChatComposerInputTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        val input = compose.onNodeWithTag(ChatComposerInputTestTag, useUnmergedTree = true)
        input.performClick()
        input.performTextReplacement(text)
        compose.waitForIdle()
        beforeSendScreenshotName?.let(::saveScreenshot)
        if (forceNativeSend && clickComposerSendNative()) return
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(ChatComposerSendTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        runCatching {
            compose.onNodeWithTag(ChatComposerSendTestTag, useUnmergedTree = true)
                .performTouchInput { click(center) }
        }.getOrElse {
            tapComposerPrimaryAction()
        }
        compose.waitForIdle()
        afterSendScreenshotName?.let(::saveScreenshot)
    }

    private fun clickComposerSendNative(): Boolean {
        val action = waitForObject(By.descContains("Enviar"), "Enviar", 1_500)
            ?: waitForObject(By.descContains("Send"), "Send", 1_500)
            ?: return false
        action.click()
        return true
    }

    private fun openMessageActions(markerProbe: String) {
        compose.waitUntil(20_000) {
            messageNodeVisible(markerProbe)
        }
        clickMessageNode(markerProbe)
        compose.waitForIdle()
        if (waitForAction("chat.action.copy", "Copiar", timeoutMillis = 2_000)) return
        longClickMessageNode(markerProbe)
        compose.waitForIdle()
        if (!waitForAction("chat.action.copy", "Copiar", timeoutMillis = 5_000)) {
            error("action_bar_not_visible:$markerProbe")
        }
    }

    private fun clickAction(tag: String, description: String) {
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performTouchInput { click(center) }
        }.onSuccess {
            return
        }
        val nativeAction = waitForObject(By.res(targetContext.packageName, tag), tag, 1_000)
            ?: waitForObject(By.descContains(description), description, 1_000)
        check(nativeAction != null) { "chat_action_not_found:$tag" }
        nativeAction.click()
    }

    private fun waitForAction(tag: String, description: String, timeoutMillis: Long = 10_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess) return true
            if (waitForObject(By.res(targetContext.packageName, tag), tag, 250) != null) return true
            if (waitForObject(By.descContains(description), description, 250) != null) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private fun waitForMarker(markerProbe: String, context: String, timeoutMillis: Long = 45_000) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { messageNodeVisible(markerProbe) }
            true
        }.getOrDefault(false)
        if (visible) return
        val scrolled = runCatching {
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .performScrollToNode(messageNodeMatcher(markerProbe))
            compose.waitUntil(10_000) { messageNodeVisible(markerProbe) }
            true
        }.getOrDefault(false)
        assertTrue("The marker must be visible in $context.", scrolled)
    }

    private fun waitForAnyMarker(markerProbes: List<String>, context: String, timeoutMillis: Long = 45_000) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { markerProbes.any(::messageNodeVisible) }
            true
        }.getOrDefault(false)
        assertTrue("One of the expected markers must be visible in $context.", visible)
    }

    private fun waitForAnyTranslatorMarker(markerProbes: List<String>, context: String, timeoutMillis: Long) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { markerProbes.any(::translatorNodeVisible) }
            true
        }.getOrDefault(false)
        assertTrue("One of the expected translator markers must be visible in $context.", visible)
    }

    private fun messageNodeVisible(markerProbe: String): Boolean =
        runCatching {
            compose.onNode(messageNodeMatcher(markerProbe), useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess

    private fun translatorNodeVisible(markerProbe: String): Boolean =
        runCatching {
            compose.onNode(translatorNodeMatcher(markerProbe), useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess

    private fun clickMessageNode(markerProbe: String) {
        compose.onNode(messageNodeMatcher(markerProbe), useUnmergedTree = true)
            .performClick()
    }

    private fun longClickMessageNode(markerProbe: String) {
        compose.onNode(messageNodeMatcher(markerProbe), useUnmergedTree = true)
            .performTouchInput { longClick(center) }
    }

    private fun messageNodeMatcher(markerProbe: String): SemanticsMatcher =
        hasContentDescription(markerProbe, substring = true) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
            SemanticsMatcher("testTag starts with chat.message.") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("chat.message.") == true
            }

    private fun translatorNodeMatcher(markerProbe: String): SemanticsMatcher =
        hasContentDescription(markerProbe, substring = true) and
            SemanticsMatcher("testTag starts with chat.translator.message.") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChatTranslatorMessageTestTagPrefix) == true
            }

    private fun tapComposerPrimaryAction() {
        val x = device.displayWidth - 86
        val y = device.displayHeight - 92
        check(device.click(x, y)) { "composer_primary_action_tap_failed" }
    }

    private fun waitForObject(selector: BySelector, context: String, timeoutMillis: Long = 10_000) =
        device.wait(Until.findObject(selector), timeoutMillis)

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("android_screenshot_failed:$name")
        val file = File(evidenceDir(), "$name.png")
        check(file.parentFile?.exists() == true) { "android_evidence_directory_missing:${file.parent}" }
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "android_screenshot_encode_failed:$name"
            }
        }
    }

    private fun writeReport(report: JSONObject) {
        File(evidenceDir(), "android-chat-actions-notifications-evidence.json")
            .writeText("${report.toString(2)}\n")
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "chat-actions-notifications-evidence")
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed:${dir.absolutePath}" } }

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("app_links_prompt_seen", true)
            .commit()
    }

    private fun grantOptionalNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        instrumentation.uiAutomation.executeShellCommand("pm grant ${targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            .close()
    }

    private fun optionalArgument(name: String): String? =
        arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentialsFromFile(path: String): EvidenceCredentials {
        val file = if (path.startsWith("app-internal:")) {
            File(targetContext.filesDir, path.removePrefix("app-internal:"))
        } else {
            File(path)
        }
        val json = JSONObject(file.readText())
        return EvidenceCredentials(
            countryCode = json.getString("country_code"),
            phone = json.getString("phone"),
            password = json.getString("password"),
        )
    }

    private data class EvidenceCredentials(
        val countryCode: String,
        val phone: String,
        val password: String,
    )
}
