package com.quata.feature.chat.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.quata.core.navigation.AppDestinations
import com.quata.core.navigation.quataOfficialPostUrl
import com.quata.core.navigation.quataPostUrl
import com.quata.feature.chat.presentation.conversations.ConversationListTestTag
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
        val privateProbe = optionalArgument("quataChatActionsPrivateProbe")
        val composerMarker = optionalArgument("quataChatActionsComposerMarker")
        val replyMarker = optionalArgument("quataChatActionsReplyMarker")
        val editMarker = optionalArgument("quataChatActionsEditMarker")
        val forwardQuery = optionalArgument("quataChatActionsForwardQuery")
        val postId = optionalArgument("quataChatActionsPostId")
        val officialPostId = optionalArgument("quataChatActionsOfficialPostId")
        val commentId = optionalArgument("quataChatActionsCommentId")
        val attachmentId = optionalArgument("quataChatActionsAttachmentId")
        val documentProbe = optionalArgument("quataChatActionsDocumentProbe")
        val audioProbe = optionalArgument("quataChatActionsAudioProbe")
        val imageProbe = optionalArgument("quataChatActionsImageProbe")
        val videoProbe = optionalArgument("quataChatActionsVideoProbe")
        val profileContentComment = optionalArgument("quataChatActionsProfileContentComment")
        val profileNeighborhood = optionalArgument("quataChatActionsProfileNeighborhood")
        val stage = optionalArgument("quataChatActionsStage") ?: "full"
        val credentials = credentialsFile?.let(::credentialsFromFile)
        val hasRequiredStageArguments = when (stage) {
            "menu-surface" -> !chatUrl.isNullOrBlank() && !ownProbe.isNullOrBlank()
            "profile", "profile-follow", "profile-roles-safety" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank()
            "profile-lists" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank()
            "profile-private-chat" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank() && !privateProbe.isNullOrBlank()
            "profile-entry" -> listOf(chatUrl, peerProbe, profileId, postId, officialPostId).all { !it.isNullOrBlank() }
            "profile-content" -> listOf(chatUrl, peerProbe, profileId, postId, commentId, attachmentId, profileContentComment).all { !it.isNullOrBlank() }
            "attachments-audio" -> listOf(chatUrl, documentProbe, audioProbe, imageProbe, videoProbe).all { !it.isNullOrBlank() }
            "group-sos" -> !chatUrl.isNullOrBlank() && !ownProbe.isNullOrBlank()
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

        if (stage == "profile-entry") {
            runProfileEntryStage(
                profileId = profileId.orEmpty(),
                feedPostId = postId.orEmpty(),
                officialPostId = officialPostId.orEmpty(),
                chatUrl = chatUrl.orEmpty(),
                peerProbe = peerProbe.orEmpty(),
                profileNeighborhood = profileNeighborhood.orEmpty(),
            )
            writeReport(
                JSONObject()
                    .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }

        ActivityScenario.launch<MainActivity>(chatIntent(chatUrl.orEmpty())).use {
            when (stage) {
                "send-reply" -> runSendReplyStage(ownProbe.orEmpty(), composerMarker.orEmpty(), replyMarker.orEmpty())
                "edit-favorite" -> runEditFavoriteStage(ownProbe.orEmpty(), composerMarker.orEmpty(), editMarker.orEmpty())
                "forward" -> runForwardStage(editMarker.orEmpty(), forwardQuery.orEmpty())
                "translation" -> runTranslationStage(ownProbe.orEmpty())
                "menu-surface" -> runMenuSurfaceStage(ownProbe.orEmpty())
                "profile" -> runProfileStage(peerProbe.orEmpty(), profileId.orEmpty())
                "profile-follow" -> runProfileFollowStage(peerProbe.orEmpty(), profileId.orEmpty())
                "profile-roles-safety" -> runProfileRolesSafetyStage(peerProbe.orEmpty(), profileId.orEmpty())
                "profile-lists" -> runProfileListsStage(peerProbe.orEmpty(), profileId.orEmpty())
                "attachments-audio" -> runAttachmentsAudioStage(documentProbe.orEmpty(), audioProbe.orEmpty(), imageProbe.orEmpty(), videoProbe.orEmpty())
                "group-sos" -> runGroupSosStage(ownProbe.orEmpty())
                "profile-content" -> {
                    openProfileFromPeerMessage(peerProbe.orEmpty(), profileId.orEmpty())
                    assertProfileContentStage(profileId.orEmpty(), postId.orEmpty(), commentId.orEmpty(), attachmentId.orEmpty(), profileContentComment.orEmpty())
                    closePublicProfile(peerProbe.orEmpty())
                    saveScreenshot("android-chat-profile-return")
                }
                "profile-private-chat" -> runProfilePrivateChatStage(peerProbe.orEmpty(), profileId.orEmpty(), privateProbe.orEmpty())
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

    private fun evidenceStartIntent(route: String): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", route)

    private fun runProfileEntryStage(
        profileId: String,
        feedPostId: String,
        officialPostId: String,
        chatUrl: String,
        peerProbe: String,
        profileNeighborhood: String,
    ) {
        ActivityScenario.launch<MainActivity>(chatIntent(quataPostUrl(feedPostId))).use {
            openProfileFromAuthorTag(
                tag = "feed.author.avatar.$profileId",
                openScreenshot = "android-profile-entry-feed",
                returnScreenshot = "android-profile-entry-feed-return",
            )
        }
        ActivityScenario.launch<MainActivity>(chatIntent(quataOfficialPostUrl(officialPostId))).use {
            openProfileFromAuthorTag(
                tag = "official.author.avatar.$profileId",
                openScreenshot = "android-profile-entry-official",
                returnScreenshot = "android-profile-entry-official-return",
            )
        }
        ActivityScenario.launch<MainActivity>(evidenceStartIntent(AppDestinations.Conversations.route)).use {
            openProfileFromAuthorTag(
                tag = "conversation.avatar.$profileId",
                openScreenshot = "android-profile-entry-conversations",
                returnScreenshot = "android-profile-entry-conversations-return",
            )
        }
        ActivityScenario.launch<MainActivity>(evidenceStartIntent(AppDestinations.Neighborhoods.route)).use {
            val communityTag = "neighborhood.members.${profileNeighborhood.toNeighborhoodTagSuffix()}"
            waitForTag(communityTag, "profile entry communities members", 45_000)
            saveScreenshot("android-profile-entry-communities-source")
            clickStableTag(communityTag)
            openProfileFromAuthorTag(
                tag = "neighborhood.user.avatar.$profileId",
                openScreenshot = "android-profile-entry-communities",
                returnScreenshot = "android-profile-entry-communities-return",
            )
        }
        ActivityScenario.launch<MainActivity>(chatIntent(chatUrl)).use {
            openProfileFromPeerMessage(peerProbe, profileId)
            closePublicProfile(peerProbe)
            saveScreenshot("android-profile-entry-chat-return")
        }
    }

    private fun String.toNeighborhoodTagSuffix(): String =
        trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .ifBlank { "unknown" }

    private fun openProfileFromAuthorTag(tag: String, openScreenshot: String, returnScreenshot: String) {
        waitForTag(tag, "profile entry source $tag", 45_000)
        saveScreenshot("$openScreenshot-source")
        clickStableTag(tag)
        val profileId = tag.substringAfterLast('.')
        compose.waitUntil(30_000) { publicProfileVisible(profileId) }
        saveScreenshot(openScreenshot)
        val closedByCommonBack = runCatching {
            compose.onNodeWithTag("public-profile.back", useUnmergedTree = true)
                .performTouchInput { click(center) }
            true
        }.getOrDefault(false)
        if (!closedByCommonBack) device.pressBack()
        waitForTag(tag, "profile entry return $tag", 20_000)
        saveScreenshot(returnScreenshot)
    }

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

    private suspend fun runMenuSurfaceStage(ownProbe: String) {
        waitForMarker(ownProbe, "initial chat thread")
        openOptionsMenu()
        waitForText("Silenciar conversaci", "Mute conversation", timeoutMillis = 10_000)
        saveScreenshot("android-chat-options-menu-surface")
        clickChatMenuMuteAction()
        compose.waitForIdle()
        SystemClock.sleep(800)
    }

    private fun runAttachmentsAudioStage(documentProbe: String, audioProbe: String, imageProbe: String, videoProbe: String) {
        waitForMarker(videoProbe.take(28), "video attachment message")
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithContentDescription(ChatVideoAttachmentContentDescription, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        openChatMediaAttachmentViewer(ChatVideoAttachmentContentDescription)
        compose.onNodeWithTag("fullscreen-media.title", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.close", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.media-close", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-attachment-video-viewer")
        compose.onNodeWithTag("fullscreen-media.back", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }

        waitForMarker(imageProbe.take(28), "image attachment message")
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithContentDescription(ChatImageAttachmentContentDescription, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        openChatMediaAttachmentViewer(ChatImageAttachmentContentDescription)
        compose.onNodeWithTag("fullscreen-media.title", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.close", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.media-close", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-attachment-media-viewer")
        compose.onNodeWithTag("fullscreen-media.back", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }

        waitForMarker(documentProbe.take(28), "document attachment message")
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag(ChatDocumentAttachmentTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-chat-attachment-document-visible")

        waitForMarker(audioProbe.take(28), "audio attachment message")
        compose.waitUntil(20_000) {
            listOf(
                ChatAudioAttachmentPlayerTestTag,
                ChatAudioAttachmentToggleTestTag,
                ChatAudioAttachmentProgressTestTag,
            ).all { tag ->
                runCatching {
                    compose.onNodeWithTag(tag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
            }
        }
        saveScreenshot("android-chat-audio-player-visible")
        compose.onNodeWithTag(ChatAudioAttachmentToggleTestTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitForIdle()
        saveScreenshot("android-chat-audio-toggle-attempted")
    }

    private fun runGroupSosStage(ownProbe: String) {
        waitForMarker(ownProbe, "group/SOS initial chat thread")
        openOptionsMenu()
        for (tag in listOf(
            ChatGroupMenuAllowInvitesTestTag,
            ChatGroupMenuAddParticipantsTestTag,
            ChatGroupMenuLeaveTestTag,
            ChatGroupMenuDeleteTestTag,
        )) {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        saveScreenshot("android-chat-group-menu-shared-anchors")
        device.pressBack()
        compose.waitForIdle()

        waitForText("Actualizacion de ubicacion SOS", "SOS location update", timeoutMillis = 20_000)
            ?: error("sos_location_title_not_visible")
        for (tag in listOf(
            ChatSosLocationRootTestTag,
            ChatSosLocationMapPreviewTestTag,
            ChatSosLocationOpenMapsTestTag,
        )) {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        waitForTag(ChatSosLocationUnavailableTestTag, "SOS unavailable message")
        waitForText("Ubicacion no disponible", "Location unavailable", timeoutMillis = 2_000)
        saveScreenshot("android-chat-sos-location-shared-anchors")
    }

    private fun openOptionsMenu() {
        repeat(5) { attempt ->
            clickOptionsButtonFallback(attempt)
            compose.waitForIdle()
            if (optionsMenuVisible(timeoutMillis = 1_500)) {
                return
            }
            val nativeOptions = waitForObject(By.descContains("Opciones"), "Opciones", 750)
                ?: waitForObject(By.descContains("Options"), "Options", 750)
            if (nativeOptions != null) {
                nativeOptions.click()
                if (optionsMenuVisible(timeoutMillis = 1_500)) {
                    return
                }
            }
            if (attempt == 2) {
                device.click(device.displayWidth - 72, (device.displayHeight * 0.17f).toInt())
                compose.waitForIdle()
            }
        }
        if (!optionsMenuVisible(timeoutMillis = 2_000)) {
            error("chat_options_menu_not_visible")
        }
    }

    private fun optionsMenuVisible(timeoutMillis: Long): Boolean =
        runCatching {
            compose.waitUntil(timeoutMillis) {
                listOf(
                    ChatGroupMenuMuteTestTag,
                    ChatGroupMenuUnmuteTestTag,
                    ChatGroupMenuAllowInvitesTestTag,
                    ChatGroupMenuAddParticipantsTestTag,
                ).any { tag ->
                    runCatching {
                        compose.onNodeWithTag(tag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
            }
            true
        }.getOrDefault(false) ||
            waitForText("Silenciar conversaci", "Mute conversation", timeoutMillis = 250) != null ||
            waitForText("Reactivar notificaciones", "Unmute", timeoutMillis = 250) != null

    private fun clickChatMenuMuteAction() {
        val clickedByTag = listOf(ChatGroupMenuMuteTestTag, ChatGroupMenuUnmuteTestTag).any { tag ->
            runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .performClick()
            }.isSuccess
        }
        if (clickedByTag) return
        waitForText("Silenciar conversaci", "Mute conversation", timeoutMillis = 2_000)?.click()
            ?: waitForText("Reactivar notificaciones", "Unmute", timeoutMillis = 2_000)?.click()
            ?: error("chat_options_mute_action_not_found")
    }

    private fun clickOptionsButtonFallback(attempt: Int) {
        if (runCatching {
                compose.onNodeWithTag(ChatGroupMenuOptionsTestTag, useUnmergedTree = true)
                    .performClick()
            }.isSuccess
        ) return
        val nativeOptions = waitForObject(By.descContains("Opciones"), "Opciones", 500)
            ?: waitForObject(By.descContains("Options"), "Options", 500)
        if (nativeOptions != null) {
            nativeOptions.click()
            return
        }
        if (attempt >= 2) {
            device.click(device.displayWidth - 72, (device.displayHeight * 0.13f).toInt())
        }
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

    private fun runProfileRolesSafetyStage(peerProbe: String, profileId: String) {
        openPeerProfile(peerProbe, profileId)
        listOf(
            "public-profile.roles.$profileId",
            "public-profile.roles.admin.$profileId",
            "public-profile.roles.official.$profileId",
            "public-profile.safety.$profileId",
            "public-profile.safety.report.$profileId",
            "public-profile.safety.block.$profileId",
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        saveScreenshot("android-chat-profile-roles-safety-initial")

        compose.onNodeWithTag("public-profile.roles.official.$profileId", useUnmergedTree = true)
            .performClick()
        saveScreenshot("android-chat-profile-roles-safety-role-updating")

        scrollPublicProfileToTag("public-profile.safety.report.$profileId")
        compose.onNodeWithTag("public-profile.safety.report.$profileId", useUnmergedTree = true)
            .performClick()
        compose.onNodeWithTag("public-profile.safety.dialog.report", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-profile-safety-report-dialog")
        compose.onNodeWithTag("public-profile.safety.dialog.confirm.report", useUnmergedTree = true)
            .performClick()
        dismissProfileSafetyDialogIfPresent("report")

        scrollPublicProfileToTag("public-profile.safety.block.$profileId")
        compose.onNodeWithTag("public-profile.safety.block.$profileId", useUnmergedTree = true)
            .performClick()
        compose.onNodeWithTag("public-profile.safety.dialog.block", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-profile-safety-block-dialog")
        compose.onNodeWithTag("public-profile.safety.dialog.confirm.block", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag("public-profile.safety.unblock.$profileId", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-chat-profile-roles-safety-after-block")
    }

    private fun scrollPublicProfileToTag(tag: String) {
        runCatching {
            compose.onNodeWithTag("public-profile.details", useUnmergedTree = true)
                .performScrollToNode(hasTestTag(tag))
            compose.waitForIdle()
        }
    }

    private fun dismissProfileSafetyDialogIfPresent(action: String) {
        val dialogTag = "public-profile.safety.dialog.$action"
        val stillOpen = runCatching {
            compose.onNodeWithTag(dialogTag, useUnmergedTree = true)
                .fetchSemanticsNode()
            true
        }.getOrDefault(false)
        if (!stillOpen) return
        val dismissedByCancel = runCatching {
            compose.onNodeWithTag("public-profile.safety.dialog.cancel", useUnmergedTree = true)
                .performClick()
            true
        }.getOrDefault(false)
        if (!dismissedByCancel) device.pressBack()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(dialogTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
    }

    private fun openChatMediaAttachmentViewer(contentDescription: String) {
        val matcher = hasTestTag(ChatMediaAttachmentTestTag) and hasContentDescription(contentDescription)
        compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
            .performScrollToNode(matcher)
        compose.onNode(matcher, useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNode(matcher, useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
            .fetchSemanticsNode()
    }

    private fun assertProfileContentStage(profileId: String, postId: String, commentId: String, attachmentId: String, uiComment: String) {
        compose.onNodeWithTag("public-profile.kpi.posts.$profileId", useUnmergedTree = true)
            .performClick()
        saveScreenshot("android-chat-profile-content")
        listOf(
            "public-profile.gallery.header.$profileId",
            "public-profile.gallery.$profileId",
            "public-profile.gallery.post.$postId",
            "public-profile.post.preview.$postId",
            "public-profile.post.action.comments.$postId",
            "public-profile.attachments",
            "public-profile.attachments.item.sb:$attachmentId",
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        compose.onNodeWithTag("public-profile.post.media.open.$postId", useUnmergedTree = true)
            .performClick()
        compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.title", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.close", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.media-close", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-profile-media-viewer")
        compose.onNodeWithTag("fullscreen-media.back", useUnmergedTree = true)
            .performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
        compose.onNodeWithTag("public-profile.post.action.comments.$postId", useUnmergedTree = true)
            .performClick()
        listOf(
            "public-profile.comments.panel",
            "public-profile.comments.list",
            "public-profile.comments.row.$commentId",
            "public-profile.comments.input",
            "public-profile.comments.send",
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        compose.onNodeWithTag("public-profile.comments.input", useUnmergedTree = true)
            .performTextReplacement(uiComment)
        compose.onNodeWithTag("public-profile.comments.send", useUnmergedTree = true)
            .performClick()
    }

    private fun runProfilePrivateChatStage(peerProbe: String, profileId: String, privateProbe: String) {
        openPeerProfile(peerProbe, profileId)
        saveScreenshot("android-chat-profile-private-chat-before")
        compose.onNodeWithTag("public-profile.chat.$profileId", useUnmergedTree = true)
            .performClick()
        waitForMarker(privateProbe, "private conversation opened from public profile")
        saveScreenshot("android-chat-profile-private-chat-opened")
    }

    private fun openPeerProfile(peerProbe: String, profileId: String) {
        waitForMarkerOrProfileAvatar(peerProbe, profileId, "peer message for profile entry")
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
        if (!openedFromMessage) saveScreenshot("android-chat-profile-message-avatar-open-failed")
        if (!openedFromMessage) {
            val memberTag = "chat.profile.member.$profileId"
            val clickedMemberAvatar = runCatching {
                compose.onNodeWithTag(memberTag, useUnmergedTree = true)
                    .performTouchInput { click(center) }
                true
            }.getOrDefault(false)
            if (!clickedMemberAvatar) clickVisibleMessageAvatarWithUiAutomator(peerProbe, profileId)
            runCatching {
                compose.waitUntil(30_000) { publicProfileVisible(profileId) }
            }.onFailure {
                saveScreenshot("android-chat-profile-open-failed")
                throw AssertionError("public_profile_not_visible_after_avatar_click:$profileId", it)
            }.getOrThrow()
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
            if (!clickedMemberAvatar) clickVisibleMessageAvatarWithUiAutomator(peerProbe, profileId)
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
        listOf(
            "public-profile.user.$profileId",
            "public-profile.avatar.$profileId",
            "public-profile.name.$profileId",
            "public-profile.kpi.posts.$profileId",
        ).any { tag ->
            runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }

    private fun profileListVisible(listKind: String): Boolean =
        runCatching {
            compose.onNodeWithTag("public-profile.list.$listKind", useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess

    private fun clickVisibleMessageAvatarWithUiAutomator(peerProbe: String, profileId: String) {
        clickMessageAvatarBySemanticsBounds(profileId).takeIf { it }?.let { return }
        val probe = peerProbe.take(28)
        dismissSystemAnrDialogIfVisible()
        val message = device.wait(Until.findObject(By.textContains(probe)), 10_000)
            ?: error("profile_message_probe_not_visible:$probe")
        val bounds = message.visibleBounds
        val x = (bounds.left - 72).coerceAtLeast(20)
        val y = (bounds.top + 34).coerceAtLeast(20)
        assertTrue("UIAutomator must dispatch a real tap on the visible message avatar.", device.click(x, y))
        SystemClock.sleep(1_000)
    }

    private fun clickMessageAvatarBySemanticsBounds(profileId: String): Boolean {
        val avatarTag = ChatProfileMessageAvatarTestTagPrefix + profileId
        return runCatching {
            val bounds = compose.onNodeWithTag(avatarTag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val center: Offset = bounds.center
            assertTrue(
                "UIAutomator must dispatch a real tap on the semantic message avatar.",
                device.click(center.x.toInt(), center.y.toInt()),
            )
            SystemClock.sleep(1_000)
            true
        }.getOrDefault(false)
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

    private fun waitForText(primary: String, fallback: String, timeoutMillis: Long = 10_000): androidx.test.uiautomator.UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            val primaryObject = waitForObject(By.textContains(primary), primary, 250)
                ?: waitForObject(By.descContains(primary), primary, 250)
            if (primaryObject != null) return primaryObject
            val fallbackObject = waitForObject(By.textContains(fallback), fallback, 250)
                ?: waitForObject(By.descContains(fallback), fallback, 250)
            if (fallbackObject != null) return fallbackObject
            SystemClock.sleep(250)
        }
        return null
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

    private fun waitForTag(tag: String, context: String, timeoutMillis: Long = 45_000) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { nodeWithTagVisible(tag) }
            true
        }.getOrDefault(false)
        if (visible) return
        val nativeVisible = waitForObject(By.res(targetContext.packageName, tag), tag, 1_000)
            ?: waitForObject(By.descContains(tag), tag, 1_000)
        if (nativeVisible != null) return
        val scrolled = runCatching {
            val scrollContainerTag = if (tag.startsWith("conversation.avatar.")) {
                ConversationListTestTag
            } else {
                ChatConversationMessagesListTestTag
            }
            compose.onNodeWithTag(scrollContainerTag, useUnmergedTree = true)
                .performScrollToNode(hasTestTag(tag))
            compose.waitUntil(10_000) { nodeWithTagVisible(tag) }
            true
        }.getOrDefault(false)
        assertTrue("The semantic tag must be visible in $context.", scrolled)
    }

    private fun clickStableTag(tag: String) {
        val clickedByCompose = runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performTouchInput { click(center) }
            true
        }.getOrDefault(false)
        if (clickedByCompose) return
        val nativeNode = waitForObject(By.res(targetContext.packageName, tag), tag, 1_000)
            ?: waitForObject(By.descContains(tag), tag, 1_000)
        check(nativeNode != null) { "stable_tag_not_clickable:$tag" }
        nativeNode.click()
    }

    private fun waitForMarkerOrProfileAvatar(markerProbe: String, profileId: String, context: String, timeoutMillis: Long = 45_000) {
        val avatarTag = ChatProfileMessageAvatarTestTagPrefix + profileId
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) {
                messageNodeVisible(markerProbe) ||
                    runCatching {
                        compose.onNodeWithTag(avatarTag, useUnmergedTree = true).fetchSemanticsNode()
                    }.isSuccess
            }
            true
        }.getOrDefault(false)
        if (visible) return
        val scrolled = runCatching {
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .performScrollToNode(messageNodeMatcher(markerProbe))
            compose.waitUntil(10_000) {
                messageNodeVisible(markerProbe) ||
                    runCatching {
                        compose.onNodeWithTag(avatarTag, useUnmergedTree = true).fetchSemanticsNode()
                    }.isSuccess
            }
            true
        }.getOrDefault(false)
        assertTrue("The marker or avatar must be visible in $context.", scrolled)
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

    private fun nodeWithTagVisible(tag: String): Boolean =
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
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
