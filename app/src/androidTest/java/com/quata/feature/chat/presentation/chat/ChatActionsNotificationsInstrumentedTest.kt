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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
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
import com.quata.core.ui.components.QuataConfirmationDialogConfirmTestTag
import com.quata.core.ui.components.QuataConfirmationDialogTestTag
import com.quata.feature.chat.presentation.conversations.ConversationListTestTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ChatActionsNotificationsInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device: UiDevice by lazy { connectUiDevice() }
    private val communityEmojiPanelProbeSections = listOf(
        "recent",
        "frequent",
        "gestures",
        "people",
        "animals_nature",
        "food_drink",
        "objects_symbols",
        "flags",
    )
    private val arguments = InstrumentationRegistry.getArguments()
    private val audioProgressStarted = Regex(""" ([1-9][0-9]?|100)%""")

    private fun connectUiDevice(): UiDevice {
        var lastError: RuntimeException? = null
        repeat(4) { attempt ->
            try {
                return UiDevice.getInstance(instrumentation)
            } catch (error: RuntimeException) {
                lastError = error
                if (attempt < 3) {
                    SystemClock.sleep(1_000)
                }
            }
        }
        throw lastError ?: IllegalStateException("ui_device_unavailable")
    }

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
        val feedPostBody = optionalArgument("quataChatActionsFeedPostBody")
        val officialTitle = optionalArgument("quataChatActionsOfficialTitle")
        val officialArticle = optionalArgument("quataChatActionsOfficialArticle")
        val officialLink = optionalArgument("quataChatActionsOfficialLink")
        val commentId = optionalArgument("quataChatActionsCommentId")
        val attachmentId = optionalArgument("quataChatActionsAttachmentId")
        val documentProbe = optionalArgument("quataChatActionsDocumentProbe")
        val documentName = optionalArgument("quataChatActionsDocumentName")
        val documentMessageId = optionalArgument("quataChatActionsDocumentMessageId")
        val audioProbe = optionalArgument("quataChatActionsAudioProbe")
        val audioName = optionalArgument("quataChatActionsAudioName")
        val audioUrl = optionalArgument("quataChatActionsAudioUrl")
        val audioMessageId = optionalArgument("quataChatActionsAudioMessageId")
        val nextAudioMessageId = optionalArgument("quataChatActionsNextAudioMessageId")
        val nextAudioName = optionalArgument("quataChatActionsNextAudioName")
        val imageProbe = optionalArgument("quataChatActionsImageProbe")
        val imageMessageId = optionalArgument("quataChatActionsImageMessageId")
        val videoProbe = optionalArgument("quataChatActionsVideoProbe")
        val videoMessageId = optionalArgument("quataChatActionsVideoMessageId")
        val audioRecordingMarker = optionalArgument("quataChatActionsAudioRecordingMarker")
        val attachmentPickerSource = optionalArgument("quataChatActionsAttachmentPickerSource")
        val attachmentPickerOutcome = optionalArgument("quataChatActionsAttachmentPickerOutcome") ?: "success"
        val attachmentPickerName = optionalArgument("quataChatActionsAttachmentPickerName")
        val attachmentPickerMarker = optionalArgument("quataChatActionsAttachmentPickerMarker")
        val groupAdminProfileId = optionalArgument("quataChatGroupAdminProfileId")
        val groupAdminDisplayName = optionalArgument("quataChatGroupAdminDisplayName")
        val groupAdminSearchQuery = optionalArgument("quataChatGroupAdminSearchQuery")
        val groupRemoveProfileId = optionalArgument("quataChatGroupRemoveProfileId")
        val groupRemoveDisplayName = optionalArgument("quataChatGroupRemoveDisplayName")
        val groupRemoveSearchQuery = optionalArgument("quataChatGroupRemoveSearchQuery")
        val groupBlockProfileId = optionalArgument("quataChatGroupBlockProfileId")
        val groupBlockDisplayName = optionalArgument("quataChatGroupBlockDisplayName")
        val groupBlockSearchQuery = optionalArgument("quataChatGroupBlockSearchQuery")
        val profileContentComment = optionalArgument("quataChatActionsProfileContentComment")
        val profileContentReplyComment = optionalArgument("quataChatActionsProfileContentReplyComment")
        val communityName = optionalArgument("quataChatActionsCommunityName")
        val feedComment = optionalArgument("quataChatActionsFeedComment")
        val feedCommentId = optionalArgument("quataChatActionsFeedCommentId")
        val feedReplyComment = optionalArgument("quataChatActionsFeedReplyComment")
        val officialComment = optionalArgument("quataChatActionsOfficialComment")
        val officialCommentId = optionalArgument("quataChatActionsOfficialCommentId")
        val officialReplyComment = optionalArgument("quataChatActionsOfficialReplyComment")
        val actorProfileId = optionalArgument("quataChatActionsActorProfileId")
        val profileNeighborhood = optionalArgument("quataChatActionsProfileNeighborhood")
        val stage = optionalArgument("quataChatActionsStage") ?: "full"
        val credentials = credentialsFile?.let(::credentialsFromFile)
        val hasRequiredStageArguments = when (stage) {
            "menu-surface" -> !chatUrl.isNullOrBlank() && !ownProbe.isNullOrBlank()
            "profile", "profile-follow", "profile-roles-safety" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank()
            "profile-lists" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank()
            "profile-private-chat" -> !chatUrl.isNullOrBlank() && !peerProbe.isNullOrBlank() && !profileId.isNullOrBlank() && !privateProbe.isNullOrBlank()
            "post-detail" -> listOf(postId, officialPostId, officialArticle, officialLink, profileId).all { !it.isNullOrBlank() }
            "profile-entry" -> listOf(chatUrl, peerProbe, profileId, postId, officialPostId).all { !it.isNullOrBlank() }
            "community-chat" -> !communityName.isNullOrBlank()
            "feed-official-comments" -> listOf(postId, officialPostId, feedComment, feedCommentId, feedReplyComment, officialComment, officialCommentId, officialReplyComment, actorProfileId).all { !it.isNullOrBlank() }
            "feed-official-comments-error" -> listOf(postId, officialPostId, feedComment, officialComment).all { !it.isNullOrBlank() }
            "feed-official-comments-selector-states" -> listOf(postId, officialPostId).all { !it.isNullOrBlank() }
            "profile-content" -> listOf(chatUrl, peerProbe, profileId, postId, commentId, attachmentId, profileContentComment, profileContentReplyComment, actorProfileId).all { !it.isNullOrBlank() }
            "attachments-audio" -> listOf(chatUrl, documentProbe, documentName, documentMessageId, audioProbe, audioName, audioUrl, audioMessageId, nextAudioMessageId, nextAudioName, imageProbe, imageMessageId, videoProbe, videoMessageId, audioRecordingMarker).all { !it.isNullOrBlank() }
            "attachment-picker" -> listOf(chatUrl, attachmentPickerSource, attachmentPickerName, attachmentPickerMarker).all { !it.isNullOrBlank() }
            "composer-emoji" -> listOf(chatUrl, ownProbe, composerMarker).all { !it.isNullOrBlank() }
            "group-sos" -> !chatUrl.isNullOrBlank() && !ownProbe.isNullOrBlank()
            "group-admin" -> listOf(chatUrl, ownProbe, groupAdminProfileId, groupAdminDisplayName, groupAdminSearchQuery).all { !it.isNullOrBlank() }
            "group-moderation" -> listOf(chatUrl, ownProbe, groupRemoveProfileId, groupRemoveDisplayName, groupRemoveSearchQuery, groupBlockProfileId, groupBlockDisplayName, groupBlockSearchQuery).all { !it.isNullOrBlank() }
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

        if (stage == "post-detail") {
            runPostDetailStage(
                feedPostId = postId.orEmpty(),
                officialPostId = officialPostId.orEmpty(),
                feedPostBody = feedPostBody.orEmpty(),
                officialTitle = officialTitle.orEmpty(),
                officialArticle = officialArticle.orEmpty(),
                officialLink = officialLink.orEmpty(),
                officialProfileId = profileId.orEmpty(),
            )
            writeReport(
                JSONObject()
                    .put("check", "POST-DETAIL-ANDROID-COMMON-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }
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
        if (stage == "community-chat") {
            runCommunityChatStage(communityName.orEmpty())
            writeReport(
                JSONObject()
                    .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }
        if (stage == "feed-official-comments") {
            runFeedOfficialCommentsStage(
                feedPostId = postId.orEmpty(),
                officialPostId = officialPostId.orEmpty(),
                feedComment = feedComment.orEmpty(),
                feedCommentId = feedCommentId.orEmpty(),
                feedReplyComment = feedReplyComment.orEmpty(),
                officialComment = officialComment.orEmpty(),
                officialCommentId = officialCommentId.orEmpty(),
                officialReplyComment = officialReplyComment.orEmpty(),
                actorProfileId = actorProfileId.orEmpty(),
            )
            writeReport(
                JSONObject()
                    .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }
        if (stage == "feed-official-comments-error") {
            runFeedOfficialCommentsErrorStage(
                feedPostId = postId.orEmpty(),
                officialPostId = officialPostId.orEmpty(),
                feedComment = feedComment.orEmpty(),
                officialComment = officialComment.orEmpty(),
            )
            writeReport(
                JSONObject()
                    .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }
        if (stage == "feed-official-comments-selector-states") {
            runFeedOfficialCommentsSelectorStatesStage(
                feedPostId = postId.orEmpty(),
                officialPostId = officialPostId.orEmpty(),
            )
            writeReport(
                JSONObject()
                    .put("check", "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001")
                    .put("status", "passed")
                    .put("evidenceDirectory", evidenceDir().absolutePath),
            )
            return@runBlocking
        }

        if (stage == "attachments-audio") {
            runAttachmentsAudioStage(
                chatUrl = chatUrl.orEmpty(),
                documentProbe = documentProbe.orEmpty(),
                documentName = documentName.orEmpty(),
                documentMessageId = documentMessageId.orEmpty(),
                audioUrl = audioUrl.orEmpty(),
                audioMessageId = audioMessageId.orEmpty(),
                audioProbe = audioProbe.orEmpty(),
                audioName = audioName.orEmpty(),
                nextAudioMessageId = nextAudioMessageId.orEmpty(),
                nextAudioName = nextAudioName.orEmpty(),
                imageProbe = imageProbe.orEmpty(),
                imageMessageId = imageMessageId.orEmpty(),
                videoProbe = videoProbe.orEmpty(),
                videoMessageId = videoMessageId.orEmpty(),
                audioRecordingMarker = audioRecordingMarker.orEmpty(),
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
                "attachment-picker" -> runAttachmentPickerStage(attachmentPickerSource.orEmpty(), attachmentPickerOutcome, attachmentPickerName.orEmpty(), attachmentPickerMarker.orEmpty())
                "composer-emoji" -> runComposerEmojiStage(ownProbe.orEmpty(), composerMarker.orEmpty())
                "group-sos" -> runGroupSosStage(ownProbe.orEmpty())
                "group-admin" -> runGroupAdminStage(
                    ownProbe = ownProbe.orEmpty(),
                    profileId = groupAdminProfileId.orEmpty(),
                    displayName = groupAdminDisplayName.orEmpty(),
                    searchQuery = groupAdminSearchQuery.orEmpty(),
                )
                "group-moderation" -> runGroupModerationStage(
                    ownProbe = ownProbe.orEmpty(),
                    removeProfileId = groupRemoveProfileId.orEmpty(),
                    removeDisplayName = groupRemoveDisplayName.orEmpty(),
                    removeSearchQuery = groupRemoveSearchQuery.orEmpty(),
                    blockProfileId = groupBlockProfileId.orEmpty(),
                    blockDisplayName = groupBlockDisplayName.orEmpty(),
                    blockSearchQuery = groupBlockSearchQuery.orEmpty(),
                )
                "profile-content" -> {
                    openProfileFromPeerMessage(peerProbe.orEmpty(), profileId.orEmpty())
                    assertProfileContentStage(profileId.orEmpty(), actorProfileId.orEmpty(), postId.orEmpty(), commentId.orEmpty(), attachmentId.orEmpty(), profileContentComment.orEmpty(), profileContentReplyComment.orEmpty())
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

    private fun runPostDetailStage(
        feedPostId: String,
        officialPostId: String,
        feedPostBody: String,
        officialTitle: String,
        officialArticle: String,
        officialLink: String,
        officialProfileId: String,
    ) {
        ActivityScenario.launch<MainActivity>(chatIntent("quata://egquata.com/#post-${Uri.encode(feedPostId)}")).use {
            waitForTag("feed.detail.chrome", "feed detail common chrome", 45_000)
            waitForTag("feed.detail.back", "feed detail common back", 20_000)
            waitForVisibleText(feedPostBody, "feed detail post body", 45_000)
            saveScreenshot("android-post-detail-feed-open")
            clickMergedTagWithAction("feed.detail.back")
            waitForTagGone("feed.detail.chrome", "feed detail closed after back", 20_000)
            SystemClock.sleep(800)
            saveScreenshot("android-post-detail-feed-back")
        }
        ActivityScenario.launch<MainActivity>(chatIntent("quata://egquata.com/#official-${Uri.encode(officialPostId)}")).use {
            waitForTag("official.detail.chrome", "official detail common chrome", 45_000)
            waitForTag("official.detail.back", "official detail common back", 20_000)
            waitForVisibleText(officialTitle, "official detail title", 45_000)
            clickMergedTagWithAction("official.detail.read-more.$officialPostId")
            waitForTag("official.detail.panel", "official detail common panel", 20_000)
            waitForTag("official.detail.article", "official detail article container", 20_000)
            waitForTag("official.detail.link", "official detail link action", 20_000)
            waitForTag("official.detail.profile", "official detail profile action", 20_000)
            waitForVisibleText(officialArticle, "official detail article body", 20_000)
            waitForVisibleText(officialLink, "official detail external link", 20_000)
            saveScreenshot("android-post-detail-official-panel")
            clickMergedTagWithAction("official.detail.profile")
            waitForTag("public-profile.user.$officialProfileId", "official detail public profile route", 30_000)
            saveScreenshot("android-post-detail-official-profile")
            val closedByCommonBack = runCatching {
                compose.onNodeWithTag("public-profile.back", useUnmergedTree = true)
                    .performTouchInput { click(center) }
                true
            }.getOrDefault(false)
            if (!closedByCommonBack) device.pressBack()
            waitForTagGone("public-profile.user.$officialProfileId", "official detail public profile closed", 20_000)
            clickMergedTagWithAction("official.detail.panel.close")
            saveScreenshot("android-post-detail-official-open")
            clickMergedTagWithAction("official.detail.back")
            waitForTagGone("official.detail.chrome", "official detail closed after back", 20_000)
            SystemClock.sleep(800)
            saveScreenshot("android-post-detail-official-back")
        }
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

    private fun runCommunityChatStage(communityName: String) {
        ActivityScenario.launch<MainActivity>(evidenceStartIntent(AppDestinations.Neighborhoods.route)).use {
            val chatTag = "neighborhood.chat.${communityName.toNeighborhoodTagSuffix()}"
            waitForTag(chatTag, "community chat action $chatTag", 45_000)
            waitForText(communityName, communityName, 20_000)
            saveScreenshot("android-community-chat-list")
            clickStableTag(chatTag)
            waitForTag(ChatConversationTitleBarTestTag, "community chat opened", 45_000)
            waitForText(communityName, communityName, 20_000)
            saveScreenshot("android-community-chat-opened")
            device.pressBack()
            waitForTag(chatTag, "community chat returned", 20_000)
        }
    }

    private fun runFeedOfficialCommentsStage(
        feedPostId: String,
        officialPostId: String,
        feedComment: String,
        feedCommentId: String,
        feedReplyComment: String,
        officialComment: String,
        officialCommentId: String,
        officialReplyComment: String,
        actorProfileId: String,
    ) {
        ActivityScenario.launch<MainActivity>(chatIntent(quataPostUrl(feedPostId))).use {
            sendEmojiCommentFromOpenPost(
                actionTag = "feed.action.comments.$feedPostId",
                replyTagPrefix = "feed.comments",
                replyToCommentId = feedCommentId,
                replyComment = feedReplyComment,
                inputTag = "feed.comments.input",
                emojiTag = "feed.comments.emoji",
                sendTag = "feed.comments.send",
                comment = feedComment,
                authorTag = "feed.comments.author.$actorProfileId",
                beforeScreenshot = "android-feed-comments-emoji-before",
                afterScreenshot = "android-feed-comments-emoji-after",
            )
        }
        ActivityScenario.launch<MainActivity>(chatIntent(quataOfficialPostUrl(officialPostId))).use {
            sendEmojiCommentFromOpenPost(
                actionTag = "official.action.comments.$officialPostId",
                replyTagPrefix = "official.comments",
                replyToCommentId = officialCommentId,
                replyComment = officialReplyComment,
                inputTag = "official.comments.input",
                emojiTag = "official.comments.emoji",
                sendTag = "official.comments.send",
                comment = officialComment,
                authorTag = "official.comments.author.$actorProfileId",
                beforeScreenshot = "android-official-comments-emoji-before",
                afterScreenshot = "android-official-comments-emoji-after",
            )
        }
    }

    private fun runFeedOfficialCommentsErrorStage(
        feedPostId: String,
        officialPostId: String,
        feedComment: String,
        officialComment: String,
    ) {
        targetContext.getSharedPreferences("quata_feed_official_comments_evidence", Context.MODE_PRIVATE)
            .edit()
            .putString("comments.optIn", "I_ACCEPT_FEED_OFFICIAL_COMMENTS_FORCED_FAILURE_EVIDENCE")
            .commit()
        try {
            ActivityScenario.launch<MainActivity>(chatIntent(quataPostUrl(feedPostId))).use {
                sendFailingEmojiCommentFromOpenPost(
                    actionTag = "feed.action.comments.$feedPostId",
                    inputTag = "feed.comments.input",
                    emojiTag = "feed.comments.emoji",
                    sendTag = "feed.comments.send",
                    errorTag = "feed.comments.error",
                    comment = feedComment,
                    beforeScreenshot = "android-feed-comments-error-before",
                    afterScreenshot = "android-feed-comments-error-after",
                )
            }
            ActivityScenario.launch<MainActivity>(chatIntent(quataOfficialPostUrl(officialPostId))).use {
                sendFailingEmojiCommentFromOpenPost(
                    actionTag = "official.action.comments.$officialPostId",
                    inputTag = "official.comments.input",
                    emojiTag = "official.comments.emoji",
                    sendTag = "official.comments.send",
                    errorTag = "official.comments.error",
                    comment = officialComment,
                    beforeScreenshot = "android-official-comments-error-before",
                    afterScreenshot = "android-official-comments-error-after",
                )
            }
        } finally {
            targetContext.getSharedPreferences("quata_feed_official_comments_evidence", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun runFeedOfficialCommentsSelectorStatesStage(
        feedPostId: String,
        officialPostId: String,
    ) {
        verifyEmojiSelectorState(
            url = quataPostUrl(feedPostId),
            actionTag = "feed.action.comments.$feedPostId",
            emojiTag = "feed.comments.emoji",
            mode = "error",
            screenshot = "android-feed-comments-emoji-selector-error",
        )
        verifyEmojiSelectorState(
            url = quataOfficialPostUrl(officialPostId),
            actionTag = "official.action.comments.$officialPostId",
            emojiTag = "official.comments.emoji",
            mode = "empty",
            screenshot = "android-official-comments-emoji-selector-empty",
        )
    }

    private fun verifyEmojiSelectorState(
        url: String,
        actionTag: String,
        emojiTag: String,
        mode: String,
        screenshot: String,
    ) {
        val preferences = targetContext.getSharedPreferences("quata_community_emoji_selector_evidence", Context.MODE_PRIVATE)
        preferences.edit()
            .putString("optIn", "I_ACCEPT_COMMUNITY_EMOJI_SELECTOR_STATE_EVIDENCE")
            .putString("mode", mode)
            .putString("message", "Emoji selector evidence failure")
            .commit()
        try {
            ActivityScenario.launch<MainActivity>(chatIntent(url)).use {
                waitForFeedOfficialActionTag(actionTag, screenshot, timeoutMillis = 90_000)
                clickStableTag(actionTag)
                waitForTag(emojiTag, "comments emoji trigger $emojiTag", 20_000)
                compose.onNodeWithTag(emojiTag, useUnmergedTree = true)
                    .performTouchInput { click(center) }
                waitForTag("community.emoji.panel", "emoji selector $mode panel", 10_000)
                if (mode == "error") {
                    waitForTag("community.emoji.error", "emoji selector error", 5_000)
                    waitForTag("community.emoji.retry", "emoji selector retry", 5_000)
                    clickStableTag("community.emoji.retry")
                    waitForTag("community.emoji.error", "emoji selector error after retry", 5_000)
                } else {
                    waitForTag("community.emoji.empty", "emoji selector empty", 5_000)
                    assertFalse(
                        "An empty selector state must not expose stale emoji cells.",
                        nodeWithTagVisible("community.emoji.cell.frequent.0"),
                    )
                }
                saveScreenshot(screenshot)
            }
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun sendEmojiCommentFromOpenPost(
        actionTag: String,
        replyTagPrefix: String,
        replyToCommentId: String,
        replyComment: String,
        inputTag: String,
        emojiTag: String,
        sendTag: String,
        comment: String,
        authorTag: String,
        beforeScreenshot: String,
        afterScreenshot: String,
    ) {
        waitForFeedOfficialActionTag(actionTag, beforeScreenshot, timeoutMillis = 90_000)
        saveScreenshot(beforeScreenshot)
        clickStableTag(actionTag)
        waitForTag(inputTag, "comments input $inputTag", 20_000)
        sendReplyCommentFromOpenPanel(
            prefix = replyTagPrefix,
            replyToCommentId = replyToCommentId,
            inputTag = inputTag,
            emojiTag = emojiTag,
            sendTag = sendTag,
            replyComment = replyComment,
            screenshotPrefix = beforeScreenshot,
        )
        compose.onNodeWithTag(emojiTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        waitForTag("community.emoji.panel", "comments emoji panel", 10_000)
        verifyCommunityEmojiPanelSections("$beforeScreenshot-panel")
        clickStableTag("community.emoji.cell.frequent.0")
        compose.onNodeWithTag(inputTag, useUnmergedTree = true)
            .performTextInput(comment.removePrefix("😀").trimStart())
        val visibleCommentText = comment.removePrefix("😀").trimStart()
        submitTaggedComment(inputTag, sendTag, visibleCommentText, "$afterScreenshot-missing-comment")
        waitForTag(authorTag, "comment author profile anchor $authorTag", 20_000)
        saveScreenshot(afterScreenshot)
    }

    private fun sendFailingEmojiCommentFromOpenPost(
        actionTag: String,
        inputTag: String,
        emojiTag: String,
        sendTag: String,
        errorTag: String,
        comment: String,
        beforeScreenshot: String,
        afterScreenshot: String,
    ) {
        waitForFeedOfficialActionTag(actionTag, beforeScreenshot, timeoutMillis = 90_000)
        saveScreenshot(beforeScreenshot)
        clickStableTag(actionTag)
        waitForTag(inputTag, "comments input $inputTag", 20_000)
        compose.onNodeWithTag(emojiTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        waitForTag("community.emoji.panel", "comments emoji panel", 10_000)
        verifyCommunityEmojiPanelSections("$beforeScreenshot-panel")
        clickStableTag("community.emoji.cell.frequent.0")
        compose.onNodeWithTag(inputTag, useUnmergedTree = true)
            .performTextInput(comment.removePrefix("😀").trimStart())
        clickStableTag(sendTag)
        waitForTag(errorTag, "forced comment error $errorTag", 20_000)
        waitForFailedCommentGone(comment, afterScreenshot)
        saveScreenshot(afterScreenshot)
    }

    private fun waitForFailedCommentGone(comment: String, failureScreenshot: String) {
        val visibleText = comment.removePrefix("😀").trimStart()
        val gone = runCatching {
            compose.waitUntil(10_000) { visibleNonEditableTextNodeCount(visibleText) == 0 }
            true
        }.getOrDefault(false)
        if (gone) return
        saveScreenshot("$failureScreenshot-ui-residue")
        File(evidenceDir(), "$failureScreenshot-ui-residue-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        assertTrue(
            "The failed optimistic comment must disappear from visible non-editable UI after rollback: $visibleText",
            false,
        )
    }

    private fun verifyCommunityEmojiPanelSections(screenshotPrefix: String) {
        communityEmojiPanelProbeSections.forEachIndexed { index, section ->
            val sectionTag = "community.emoji.section.$section"
            val gridTag = "community.emoji.grid.$section"
            val firstCellTag = "community.emoji.cell.$section.0"
            scrollEmojiSectionIntoView(sectionTag, index)
            clickEmojiPanelSection(sectionTag, "$screenshotPrefix-$section")
            waitForEmojiPanelTag(gridTag, "emoji panel selected grid $section", "$screenshotPrefix-$section")
            waitForEmojiPanelTag(firstCellTag, "emoji panel first cell $section", "$screenshotPrefix-$section")
            saveScreenshot("$screenshotPrefix-$section")
        }
        scrollEmojiSectionIntoView("community.emoji.section.frequent", 1)
        clickEmojiPanelSection("community.emoji.section.frequent", "$screenshotPrefix-reset-frequent")
        waitForEmojiPanelTag("community.emoji.cell.frequent.0", "emoji panel frequent reset", "$screenshotPrefix-reset-frequent")
    }

    private fun scrollEmojiSectionIntoView(sectionTag: String, sectionIndex: Int) {
        if (visibleEmojiSectionNodes(sectionTag).isNotEmpty()) return
        repeat(12) {
            if (visibleEmojiSectionNodes(sectionTag).isNotEmpty()) return
            val row = visibleTaggedNodes("community.emoji.sections").firstOrNull()
            if (row != null) {
                val bounds = row.boundsInRoot
                val y = bounds.center.y.roundToInt()
                val startX = if (sectionIndex >= 3) (bounds.right - 12f).roundToInt() else (bounds.left + 12f).roundToInt()
                val endX = if (sectionIndex >= 3) (bounds.left + 12f).roundToInt() else (bounds.right - 12f).roundToInt()
                device.swipe(startX, y, endX, y, 16)
                compose.waitForIdle()
                return@repeat
            }
            runCatching {
                compose.onNodeWithTag("community.emoji.sections", useUnmergedTree = true)
                    .performTouchInput {
                        if (sectionIndex >= 3) swipeLeft() else swipeRight()
                    }
                compose.waitForIdle()
            }
        }
    }

    private fun waitForEmojiPanelTag(tag: String, context: String, failureScreenshot: String, timeoutMillis: Long = 10_000) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { visibleTaggedNodes(tag).isNotEmpty() }
            true
        }.getOrDefault(false)
        if (visible) return
        saveScreenshot("$failureScreenshot-missing-panel-tag")
        File(evidenceDir(), "$failureScreenshot-missing-panel-tag-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        assertTrue("The semantic tag must be visible in $context.", false)
    }

    private fun clickEmojiPanelSection(sectionTag: String, failureScreenshot: String) {
        val visibleSection = visibleEmojiSectionNodes(sectionTag).firstOrNull()
        if (visibleSection != null) {
            val center = visibleSection.boundsInRoot.center
            check(device.click(center.x.roundToInt(), center.y.roundToInt())) { "emoji_section_visible_tap_failed:$sectionTag" }
            compose.waitForIdle()
            return
        }
        val clickedByTag = runCatching {
            compose.onNodeWithTag(sectionTag, useUnmergedTree = true)
                .performClick()
            compose.waitForIdle()
            true
        }.getOrDefault(false)
        if (clickedByTag) return
        val clickedByDescription = runCatching {
            compose.onNodeWithContentDescription(sectionTag, useUnmergedTree = true)
                .performClick()
            compose.waitForIdle()
            true
        }.getOrDefault(false)
        if (clickedByDescription) return
        saveScreenshot("$failureScreenshot-section-not-clickable")
        File(evidenceDir(), "$failureScreenshot-section-not-clickable-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        clickStableTag(sectionTag)
    }

    private fun visibleEmojiSectionNodes(sectionTag: String) =
        runCatching {
            val rowBounds = visibleTaggedNodes("community.emoji.sections").firstOrNull()?.boundsInRoot
            compose.onAllNodes(hasTestTag(sectionTag), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { node ->
                    val bounds = node.boundsInRoot
                    val center = bounds.center
                    val insideDisplay = bounds.width > 0f &&
                        bounds.height > 0f &&
                        center.x >= 0f &&
                        center.x <= device.displayWidth &&
                        center.y >= 0f &&
                        center.y <= device.displayHeight
                    val insideRow = rowBounds == null ||
                        (center.x >= rowBounds.left + 16f && center.x <= rowBounds.right - 16f)
                    insideDisplay && insideRow
                }
        }.getOrDefault(emptyList())

    private fun sendReplyCommentFromOpenPanel(
        prefix: String,
        replyToCommentId: String,
        inputTag: String,
        emojiTag: String,
        sendTag: String,
        replyComment: String,
        screenshotPrefix: String,
    ) {
        val replyTag = "$prefix.reply.$replyToCommentId"
        waitForTag(replyTag, "reply action $replyTag", 20_000)
        compose.onNodeWithTag(replyTag, useUnmergedTree = true).performClick()
        waitForTag("$prefix.replyTarget.$replyToCommentId", "reply target banner $replyToCommentId", 10_000)
        compose.onNodeWithTag(emojiTag, useUnmergedTree = true)
            .performTouchInput { click(center) }
        waitForTag("community.emoji.panel", "comments emoji panel", 10_000)
        clickStableTag("community.emoji.cell.frequent.0")
        compose.onNodeWithTag(inputTag, useUnmergedTree = true)
            .performTextInput(replyComment.removePrefix("😀").trimStart())
        val visibleReplyText = replyComment.removePrefix("😀").trimStart()
        submitTaggedComment(inputTag, sendTag, visibleReplyText, "$screenshotPrefix-missing-reply-comment")
    }

    private fun waitForFeedOfficialActionTag(actionTag: String, screenshotPrefix: String, timeoutMillis: Long) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { nodeWithTagVisible(actionTag) }
            true
        }.getOrDefault(false)
        if (visible) return
        val scrolled = scrollFeedOfficialActionIntoView(actionTag)
        if (scrolled) return
        saveScreenshot("$screenshotPrefix-missing-action")
        File(evidenceDir(), "$screenshotPrefix-semantics.txt")
            .writeText(
                runCatching {
                    compose.onRoot(useUnmergedTree = true)
                        .printToString(maxDepth = 20)
                }.getOrElse { error ->
                    error.stackTraceToString()
                },
            )
        assertTrue("The feed/official comments action tag must be visible: $actionTag.", false)
    }

    private fun scrollFeedOfficialActionIntoView(actionTag: String): Boolean {
        val postId = actionTag.substringAfterLast('.', missingDelimiterValue = "")
        val containers = when {
            actionTag.startsWith("feed.action.") -> listOf("feed.post.$postId", "feed.reel", "feed.pager")
            actionTag.startsWith("official.action.") -> listOf("official-post-card-$postId", "official.feed", "official.pager")
            else -> emptyList()
        } + listOf(ChatConversationMessagesListTestTag)
        for (container in containers.distinct()) {
            val scrolled = runCatching {
                compose.onNodeWithTag(container, useUnmergedTree = true)
                    .performScrollToNode(hasTestTag(actionTag))
                compose.waitUntil(5_000) { nodeWithTagVisible(actionTag) }
                true
            }.getOrDefault(false)
            if (scrolled) return true
        }
        return false
    }

    private fun String.toNeighborhoodTagSuffix(): String =
        trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .ifBlank { "unknown" }

    private fun openProfileFromAuthorTag(
        tag: String,
        openScreenshot: String,
        returnScreenshot: String,
        requireReturnTag: Boolean = true,
    ) {
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
        if (requireReturnTag) {
            waitForTag(tag, "profile entry return $tag", 20_000)
        } else {
            compose.waitUntil(20_000) { !publicProfileVisible(profileId) }
        }
        saveScreenshot(returnScreenshot)
    }

    private suspend fun runSendReplyStage(ownProbe: String, composerMarker: String, replyMarker: String) {
        runComposerEmojiStage(ownProbe, composerMarker)

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

    private suspend fun runComposerEmojiStage(ownProbe: String, composerMarker: String) {
        waitForMarker(ownProbe, "initial chat thread")
        saveScreenshot("android-chat-actions-thread-initial")

        fillComposer(composerMarker)
        flushPendingChatMessages()
        waitForMarker(composerMarker.take(28), "composer message")
        saveScreenshot("android-chat-composer-sent")
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

    private fun runAttachmentsAudioStage(chatUrl: String, documentProbe: String, documentName: String, documentMessageId: String, audioUrl: String, audioMessageId: String, audioProbe: String, audioName: String, nextAudioMessageId: String, nextAudioName: String, imageProbe: String, imageMessageId: String, videoProbe: String, videoMessageId: String, audioRecordingMarker: String) {
        withShellLaunchedChat(chatUrl) {
            verifyAttachmentsMediaAndDocument(chatUrl, documentProbe, documentName, documentMessageId, imageProbe, imageMessageId, videoProbe, videoMessageId)
        }

        withShellLaunchedChat(audioUrl) {
            verifyAttachmentsAudioPlayback(audioMessageId, audioName, nextAudioMessageId, nextAudioName)
        }

        withShellLaunchedChat(chatUrl) {
            verifyAndroidAudioRecordingComposer(audioRecordingMarker)
        }
    }

    private fun withShellLaunchedChat(url: String, block: () -> Unit) {
        launchChatWithAmStart(url)
        try {
            block()
        } finally {
            device.pressHome()
            SystemClock.sleep(500)
        }
    }

    private fun launchChatWithAmStart(url: String) {
        val component = "${targetContext.packageName}/.MainActivity"
        val command = listOf(
            "am",
            "start",
            "-W",
            "-a",
            Intent.ACTION_VIEW,
            "-d",
            url,
            "-f",
            "0x14008000",
            "-n",
            component,
            "--ez",
            "com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE",
            "true",
        ).joinToString(" ")
        val output = device.executeShellCommand(command)
        val launchCompleted = output.contains("Status: ok") ||
            (
                output.contains("Status: timeout") &&
                    output.contains("Activity: $component") &&
                    output.contains("Complete")
                )
        assertTrue("Android shell launch must reach MainActivity for attachments/audio route.\n$output", launchCompleted)
        SystemClock.sleep(1_000)
    }

    private fun verifyAttachmentsMediaAndDocument(chatUrl: String, documentProbe: String, documentName: String, documentMessageId: String, imageProbe: String, imageMessageId: String, videoProbe: String, videoMessageId: String) {
        waitForMarker(videoProbe.take(28), "video attachment message")
        openChatMediaAttachmentViewer(
            contentDescription = ChatVideoAttachmentContentDescription,
            openTag = "$ChatVideoAttachmentContentDescription.$videoMessageId$ChatMediaAttachmentOpenTestTagSuffix",
        )
        compose.onNodeWithTag("fullscreen-media.title", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.close", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.media-close", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-attachment-video-viewer")
        closeFullscreenMediaViewer(".mp4")
        saveScreenshot("android-chat-attachment-video-viewer-closed")

        waitForMarker(imageProbe.take(28), "image attachment message")
        openChatMediaAttachmentViewer(
            contentDescription = ChatImageAttachmentContentDescription,
            openTag = "$ChatImageAttachmentContentDescription.$imageMessageId$ChatMediaAttachmentOpenTestTagSuffix",
        )
        compose.onNodeWithTag("fullscreen-media.title", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.close", useUnmergedTree = true)
            .fetchSemanticsNode()
        compose.onNodeWithTag("fullscreen-media.media-close", useUnmergedTree = true)
            .fetchSemanticsNode()
        saveScreenshot("android-chat-attachment-media-viewer")
        closeFullscreenMediaViewer(".png")
        saveScreenshot("android-chat-attachment-media-viewer-closed")

        waitForMarker(documentProbe.take(28), "document attachment message")
        waitForDocumentAttachment(documentName, "document attachment message", messageId = documentMessageId)
        listOf(
            ChatDocumentAttachmentOpenTestTag,
            ChatDocumentAttachmentDownloadTestTag,
            ChatDocumentAttachmentShareTestTag,
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        saveScreenshot("android-chat-attachment-document-visible")
        clickVisibleDocumentAttachmentOpen(documentName)
        SystemClock.sleep(700)
        if (waitForDocumentViewerStatusRoot(2_000)) {
            saveScreenshot("android-chat-attachment-document-viewer-status")
            compose.onNodeWithTag("document-viewer-status-close", useUnmergedTree = true)
                .performClick()
        } else {
            assertTrue(
                "android_document_reader_missing_stable_anchor:$documentName",
                waitForAndroidDocumentReader(documentName),
            )
            saveScreenshot("android-chat-attachment-document-reader")
            device.pressBack()
            if (!documentAttachmentVisible(documentName, timeoutMillis = 5_000, messageId = documentMessageId)) {
                launchChatWithAmStart("$chatUrl?message=${Uri.encode(documentMessageId)}")
            }
            waitForDocumentAttachment(documentName, "document attachment after reader back", messageId = documentMessageId)
        }
    }

    private fun verifyAttachmentsAudioPlayback(audioMessageId: String, audioName: String, nextAudioMessageId: String, nextAudioName: String) {
        waitForAudioAttachment(audioMessageId, audioName, "audio attachment message")
        dismissComposerImeIfFocused()
        scrollToAudioAttachmentToggle(
            name = audioName,
            context = "audio attachment toggle",
            messageId = audioMessageId,
            followingAudioName = nextAudioName,
            followingAudioMessageId = nextAudioMessageId,
        )
        saveScreenshot("android-chat-audio-player-visible")
        compose.onNode(audioAttachmentToggleMatcher(audioName), useUnmergedTree = true)
            .performClick()
        compose.waitForIdle()
        val playingObserved = runCatching {
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNode(
                        audioAttachmentStateMatcher(audioName, ChatAudioAttachmentStatePlaying),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode()
                }.isSuccess
            }
            true
        }.getOrDefault(false)
        assertTrue(
            "Audio attachment must report Playing only after native playback confirmation. " +
                audioAttachmentVisibilityDebug(audioAttachmentToggleMatcher(audioName)),
            playingObserved,
        )
        val failedObserved = runCatching {
            compose.onNode(
                audioAttachmentStateMatcher(audioName, ChatAudioAttachmentStateFailed),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
        }.isSuccess
        assertFalse("Audio attachment entered Failed after native play request.", failedObserved)
        saveScreenshot("android-chat-audio-toggle-attempted")
        waitForAudioProgressToStart(audioName)
        compose.onNode(hasTestTag(ChatAudioAttachmentProgressTestTag) and hasAudioDescription(audioName), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { seek -> seek(0.8f) }
        compose.waitForIdle()
        waitForAudioProgressAtLeast(audioName, 70)
        saveScreenshot("android-chat-audio-seek-attempted")
        val nextPlayingObserved = runCatching {
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNode(
                        audioAttachmentStateMatcher(nextAudioName, ChatAudioAttachmentStatePlaying),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode()
                }.isSuccess
            }
            true
        }.getOrDefault(false)
        assertTrue(
            "Native ended event must advance to the next consecutive audio exactly once. " +
                audioAttachmentVisibilityDebug(audioAttachmentToggleMatcher(nextAudioName)) +
                " current=" + audioAttachmentVisibilityDebug(audioAttachmentToggleMatcher(audioName)),
            nextPlayingObserved,
        )
        saveScreenshot("android-chat-audio-consecutive-next-playing")
        waitForConsecutiveAudioChainToStop(nextAudioName)
        saveScreenshot("android-chat-audio-consecutive-chain-stopped")
    }

    private fun waitForDocumentViewerStatusRoot(timeoutMs: Long): Boolean {
        return runCatching {
            compose.waitUntil(timeoutMs) {
                runCatching {
                    compose.onNodeWithTag("document-viewer-status-root", useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
            }
            true
        }.getOrDefault(false)
    }

    private fun waitForAndroidDocumentReader(documentName: String): Boolean {
        val exactName = documentName.takeIf { it.isNotBlank() } ?: return false
        val basename = exactName.substringBeforeLast('.', exactName)
        return device.wait(
            Until.hasObject(By.text(exactName)),
            20_000,
        ) || device.wait(
            Until.hasObject(By.textContains(basename)),
            2_000,
        ) || device.wait(
            Until.hasObject(By.text("1 / 1")),
            2_000,
        )
    }

    private fun waitForDocumentAttachment(name: String, context: String, timeoutMillis: Long = 45_000, messageId: String? = null) {
        assertTrue(
            "The document attachment must be visible in $context.",
            documentAttachmentVisible(name, timeoutMillis, messageId),
        )
    }

    private fun documentAttachmentVisible(name: String, timeoutMillis: Long = 45_000, messageId: String? = null): Boolean {
        val documentMatcher = documentAttachmentMatcher(name)
        return runCatching {
            val targetMatcher = messageId
                ?.takeIf(String::isNotBlank)
                ?.let(::chatMessageMatcher)
                ?: documentMatcher
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .performScrollToNode(targetMatcher)
            compose.waitUntil(timeoutMillis) {
                visibleNodes(documentMatcher).isNotEmpty() ||
                    visibleNodes(documentAttachmentOpenMatcher(name)).isNotEmpty()
            }
            true
        }.getOrDefault(false)
    }

    private fun documentAttachmentMatcher(name: String): SemanticsMatcher =
        hasTestTag(ChatDocumentAttachmentTestTag) and hasAnyDescendant(hasText(name, substring = true))

    private fun documentAttachmentOpenMatcher(name: String): SemanticsMatcher =
        hasTestTag(ChatDocumentAttachmentOpenTestTag) and hasAnyDescendant(hasText(name, substring = true))

    private fun clickVisibleDocumentAttachmentOpen(name: String) {
        val node = visibleNodes(documentAttachmentOpenMatcher(name)).firstOrNull()
            ?: error("document_attachment_open_anchor_not_visible:$name")
        val center = node.boundsInRoot.center
        check(device.click(center.x.roundToInt(), center.y.roundToInt())) { "document_attachment_open_tap_failed:$name" }
    }

    private fun hasAudioDescription(name: String, vararg actions: String): SemanticsMatcher =
        SemanticsMatcher("audio description contains $name and ${actions.joinToString()}") { node ->
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            descriptions.any { description ->
                description.contains(name, ignoreCase = true) &&
                    (actions.isEmpty() || actions.any { action -> description.contains(action, ignoreCase = true) })
            }
        }

    private fun waitForAudioAttachment(messageId: String, name: String, context: String, timeoutMillis: Long = 45_000) {
        val audioMatcher = hasTestTag(ChatAudioAttachmentPlayerTestTag) and hasAnyDescendant(hasAudioDescription(name))
        val scrolled = runCatching {
            if (messageId.isNotBlank()) {
                compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                    .performScrollToNode(chatMessageMatcher(messageId))
            } else {
                compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                    .performScrollToNode(audioMatcher)
            }
            compose.waitUntil(timeoutMillis) {
                visibleNodes(audioMatcher).isNotEmpty()
            }
            true
        }.getOrDefault(false)
        assertTrue("The audio attachment player must be visible in $context.", scrolled)
    }

    private fun scrollToAudioAttachmentToggle(name: String, context: String, messageId: String? = null, followingAudioName: String? = null, followingAudioMessageId: String? = null, timeoutMillis: Long = 15_000) {
        val audioMatcher = hasTestTag(ChatAudioAttachmentPlayerTestTag) and hasAnyDescendant(hasAudioDescription(name))
        val toggleMatcher = audioAttachmentToggleMatcher(name)
        var visibilityDebug = ""
        val visible = runCatching {
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .performScrollToNode(audioMatcher)
            repeat(8) {
                if (visibleAboveComposerNodes(toggleMatcher).isNotEmpty()) return@repeat
                scrollSemanticAudioToggleAwayFromComposer(toggleMatcher)
                compose.waitForIdle()
            }
            compose.waitUntil(timeoutMillis) {
                visibleAboveComposerNodes(toggleMatcher).isNotEmpty()
            }
            visibilityDebug = audioAttachmentVisibilityDebug(toggleMatcher)
            true
        }.getOrElse { error ->
            visibilityDebug = audioAttachmentVisibilityDebug(toggleMatcher, error)
            false
        }
        if (!visible) {
            saveScreenshot("android-chat-audio-toggle-not-visible")
            File(evidenceDir(), "android-chat-audio-toggle-not-visible-semantics.txt")
                .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 24) }.getOrElse { it.stackTraceToString() })
        }
        assertTrue("The audio attachment toggle must be visible in $context. $visibilityDebug", visible)
    }

    private fun chatMessageMatcher(messageId: String): SemanticsMatcher =
        hasTestTag("chat.message.$messageId") or hasTestTag("chat.message.$messageId.selected")

    private fun audioAttachmentToggleMatcher(name: String): SemanticsMatcher =
        hasTestTag(ChatAudioAttachmentToggleTestTag) and hasAudioDescription(name)

    private fun audioAttachmentStateMatcher(name: String, state: String): SemanticsMatcher =
        hasTestTag(ChatAudioAttachmentToggleTestTag) and hasAudioDescription(name) and
            SemanticsMatcher("audio attachment state $state") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith(state) == true
            }

    private fun scrollSemanticAudioToggleAwayFromComposer(matcher: SemanticsMatcher) {
        val node = visibleNodes(matcher).firstOrNull()
        val composerTop = runCatching {
            compose.onNodeWithTag(ChatComposerRootTestTag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        }.getOrDefault(device.displayHeight.toFloat())
        val scrollBy = node
            ?.let { (it.boundsInRoot.bottom - composerTop + it.boundsInRoot.height).coerceAtLeast(1f) }
            ?: (device.displayHeight * 0.25f)
        compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, scrollBy) }
    }

    private fun audioAttachmentVisibilityDebug(
        matcher: SemanticsMatcher,
        error: Throwable? = null,
    ): String {
        val toggleBounds = visibleNodes(matcher).map { it.boundsInRoot.toString() }
        val anyToggleBounds = visibleNodes(hasTestTag(ChatAudioAttachmentToggleTestTag)).map { node ->
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            "${node.boundsInRoot}:$descriptions"
        }
        val anyPlayerBounds = visibleNodes(hasTestTag(ChatAudioAttachmentPlayerTestTag)).map { node ->
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            "${node.boundsInRoot}:$descriptions"
        }
        val composerBounds = runCatching {
            compose.onNodeWithTag(ChatComposerRootTestTag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .toString()
        }.getOrElse { "missing:${it.message}" }
        val listBounds = runCatching {
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .toString()
        }.getOrElse { "missing:${it.message}" }
        return buildString {
            append("debug={toggleBounds=")
            append(toggleBounds)
            append(", anyToggleBounds=")
            append(anyToggleBounds)
            append(", anyPlayerBounds=")
            append(anyPlayerBounds)
            append(", composerBounds=")
            append(composerBounds)
            append(", listBounds=")
            append(listBounds)
            error?.let {
                append(", waitError=")
                append(it::class.simpleName)
                append(":")
                append(it.message)
            }
            append("}")
        }
    }

    private fun waitForAudioProgressToStart(name: String, timeoutMillis: Long = 20_000) {
        val progressMatcher = hasTestTag(ChatAudioAttachmentProgressTestTag) and hasAudioDescription(name)
        val started = runCatching {
            compose.waitUntil(timeoutMillis) {
                runCatching {
                    compose.onNode(progressMatcher, useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        .orEmpty()
                        .any { description -> audioProgressStarted.containsMatchIn(description) }
                }.getOrDefault(false)
            }
            true
        }.getOrDefault(false)
        assertTrue("The audio attachment progress must advance before scrubber seek.", started)
    }

    private fun waitForAudioProgressAtLeast(name: String, minimumPercent: Int, timeoutMillis: Long = 10_000) {
        val progressMatcher = hasTestTag(ChatAudioAttachmentProgressTestTag) and hasAudioDescription(name)
        val reached = runCatching {
            compose.waitUntil(timeoutMillis) {
                runCatching {
                    compose.onNode(progressMatcher, useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        .orEmpty()
                        .any { description -> audioProgressPercent(description) >= minimumPercent }
                }.getOrDefault(false)
            }
            true
        }.getOrDefault(false)
        assertTrue(
            "Semantic seek must move the audio progress to at least $minimumPercent%. " +
                audioAttachmentVisibilityDebug(progressMatcher),
            reached,
        )
    }

    private fun audioProgressPercent(description: String): Int =
        Regex(""" ([0-9]{1,3})%""").find(description)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1

    private fun waitForConsecutiveAudioChainToStop(name: String, timeoutMillis: Long = 45_000) {
        val nextEndedMatcher = audioAttachmentStateMatcher(name, ChatAudioAttachmentStateEnded)
        val stopped = runCatching {
            compose.waitUntil(timeoutMillis) {
                runCatching {
                    compose.onNode(nextEndedMatcher, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
            }
            true
        }.getOrDefault(false)
        assertTrue("Consecutive audio must be finite and expose Ended after the next message.", stopped)
    }

    private fun verifyAndroidAudioRecordingComposer(audioRecordingMarker: String) {
        grantRecordAudioPermission()
        prepareComposerForAudioRecording()
        saveScreenshot("android-chat-audio-recording-ready")
        compose.onNodeWithTag(ChatComposerRecordAudioTestTag, useUnmergedTree = true)
            .performClick()
        val recordingStarted = runCatching {
            compose.waitUntil(10_000) { nodeWithTagVisible(ChatComposerRecordingTestTag) }
            true
        }.getOrDefault(false)
        if (!recordingStarted) {
            compose.onNodeWithTag(ChatComposerRecordAudioTestTag, useUnmergedTree = true)
                .performTouchInput { click(center) }
            compose.waitUntil(10_000) { nodeWithTagVisible(ChatComposerRecordingTestTag) }
        }
        compose.waitUntil(10_000) { nodeWithTagVisible("chat.composer.recording.stop") }
        SystemClock.sleep(1_500)
        saveScreenshot("android-chat-audio-recording-active")
        compose.onNodeWithTag("chat.composer.recording.stop", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.waitUntil(15_000) { nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag) }
        saveScreenshot("android-chat-audio-recording-pending-attachment")
        fillComposer(
            audioRecordingMarker,
            beforeSendScreenshotName = "android-chat-audio-recording-ready-to-send",
            afterSendScreenshotName = "android-chat-audio-recording-sent",
        )
        waitForMarker(audioRecordingMarker.take(28), "audio recording sent message")
        check(!nodeWithTagVisible(ChatPendingAttachmentClearTestTag)) {
            "Pending audio attachment controls remained visible after sending recording"
        }
    }

    private fun prepareComposerForAudioRecording() {
        waitForComposerInput()
        if (nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag)) {
            compose.onNodeWithTag(ChatPendingAttachmentClearTestTag, useUnmergedTree = true)
                .performTouchInput { click(center) }
            compose.waitUntil(8_000) { !nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag) }
        }
        dismissComposerImeIfFocused()
        compose.waitUntil(10_000) { nodeWithTagVisible(ChatComposerRecordAudioTestTag) }
    }

    private suspend fun runAttachmentPickerStage(source: String, outcome: String, name: String, marker: String) {
        val fixture = createAttachmentPickerFixture(source, name)
        targetContext.getSharedPreferences("quata_chat_evidence", Context.MODE_PRIVATE)
            .edit()
            .putString("attachmentPicker.optIn", "I_ACCEPT_ANDROID_CHAT_ATTACHMENT_PICKER_FIXTURE")
            .putString("attachmentPicker.source", source)
            .putString("attachmentPicker.outcome", outcome)
            .putString("attachmentPicker.reason", "${source}_permission_denied")
            .putString("attachmentPicker.path", fixture.absolutePath)
            .putString("attachmentPicker.name", name)
            .putString("attachmentPicker.mime", if (source == "document") "text/plain" else "image/png")
            .commit()
        try {
            when (source) {
                "document", "gallery" -> {
                    compose.onNodeWithTag(ChatComposerAttachTestTag, useUnmergedTree = true)
                        .performTouchInput { click(center) }
                    compose.onNodeWithTag(ChatAttachmentQuickPanelTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                    val tag = if (source == "document") ChatAttachmentPickFileTestTag else ChatAttachmentPickGalleryTestTag
                    compose.onNodeWithTag(tag, useUnmergedTree = true)
                        .performTouchInput { click(center) }
                }
                "camera" -> {
                    compose.onNodeWithTag(ChatComposerCameraTestTag, useUnmergedTree = true)
                        .performTouchInput { click(center) }
                }
                else -> error("unknown_attachment_picker_source:$source")
            }
            if (outcome != "success" && outcome != "register-failure") {
                compose.waitForIdle()
                assertFalse(
                    "A $outcome picker result must not create a pending attachment.",
                    nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag),
                )
                if (outcome == "failure" || outcome == "unsupported") {
                    compose.waitUntil(8_000) { nodeWithTagVisible(ChatAttachmentErrorTestTag) }
                }
                saveScreenshot("android-chat-attachment-picker-$outcome-$source")
                return
            }
            compose.waitUntil(15_000) { nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag) }
            waitForText(name.take(24), name.take(24), timeoutMillis = 10_000)
                ?: error("attachment_picker_pending_name_not_visible:$name")
            saveScreenshot("android-chat-attachment-picker-pending-$source")
            fillComposer(
                text = marker,
                forceNativeSend = outcome == "register-failure",
                afterSendScreenshotName = "android-chat-attachment-picker-sent-$source",
            )
            if (outcome == "register-failure") {
                compose.waitUntil(10_000) { nodeWithTagVisible(ChatAttachmentErrorTestTag) }
                assertFalse(
                    "A register failure must not leave the picked attachment pending after rollback.",
                    nodeWithTagVisible(ChatPendingAttachmentOverlayTestTag),
                )
                saveScreenshot("android-chat-attachment-picker-register-failure-$source")
                return
            }
            flushPendingChatMessages()
            waitForMarker(marker.take(28), "attachment picker message")
        } finally {
            targetContext.getSharedPreferences("quata_chat_evidence", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            fixture.delete()
        }
    }

    private fun createAttachmentPickerFixture(source: String, name: String): File {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank {
            if (source == "document") "quata-picker.txt" else "quata-picker.png"
        }
        val file = File(targetContext.cacheDir, "chat_attachment_picker_$safeName")
        if (source == "document") {
            file.writeText("QADATA Android attachment picker fixture\n", Charsets.UTF_8)
        } else {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF1E64FF.toInt())
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "attachment_picker_png_fixture_failed" }
            }
            bitmap.recycle()
        }
        check(file.isFile && file.length() > 0L) { "attachment_picker_fixture_empty:$source" }
        return file
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
        closeGroupOptionsMenuForSos()

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
        compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(ChatSosLocationOpenMapsTestTag))
        compose.waitForIdle()
        compose.onNodeWithTag(ChatSosLocationOpenMapsTestTag, useUnmergedTree = true)
            .performClick()
        if (waitForPackageToLeaveApp(timeoutMillis = 8_000)) {
            repeat(5) {
                if (device.currentPackageName == targetContext.packageName) return@repeat
                device.pressBack()
                waitForPackageToReturnToApp(timeoutMillis = 1_500)
            }
        }
        compose.waitForIdle()
        val mapFeedbackVisible = waitForAnyComposeText(
            "Abriendo ubicación en mapas",
            "Opening location in maps",
            "No se pudo abrir la ubicación",
            "The location could not be opened",
            "No hay una aplicación de mapas disponible",
            "No maps app is available",
            timeoutMillis = 12_000,
        )
        if (!mapFeedbackVisible) {
            saveScreenshot("android-chat-sos-location-map-feedback-missing")
            error("sos_map_open_feedback_not_visible")
        }
        waitForMarker(ownProbe, "group/SOS chat after map return")
        waitForTag(ChatSosLocationUnavailableTestTag, "SOS unavailable message")
        if (!waitForAnyComposeText(
            "Ubicacion no disponible: permiso denegado",
            "Ubicación no disponible: permiso denegado",
            "Location unavailable: permission denied",
            timeoutMillis = 2_000,
        )) {
            saveScreenshot("android-chat-sos-location-unavailable-reason-missing")
            error("sos_permission_denied_reason_not_visible")
        }
        saveScreenshot("android-chat-sos-location-map-return")
    }

    private fun runGroupAdminStage(
        ownProbe: String,
        profileId: String,
        displayName: String,
        searchQuery: String,
    ) {
        waitForMarker(ownProbe, "group admin initial chat thread")
        openOptionsMenu()
        clickStableTag(ChatGroupMenuAddParticipantsTestTag)
        waitForTag(ChatGroupParticipantPickerSearchTestTag, "group participant picker search")
        compose.onNodeWithTag(ChatGroupParticipantPickerSearchTestTag, useUnmergedTree = true)
            .performTextReplacement(searchQuery)
        compose.waitForIdle()
        device.pressBack()
        compose.waitForIdle()
        val candidateTag = ChatGroupParticipantPickerCandidateTestTagPrefix + profileId
        compose.waitUntil(20_000) { nodeWithTagVisible(candidateTag) }
        saveScreenshot("android-chat-group-admin-participant-picker")
        compose.onNodeWithTag(candidateTag, useUnmergedTree = true)
            .performTouchInput { click(Offset(24f, center.y)) }
        compose.waitForIdle()
        saveScreenshot("android-chat-group-admin-participant-selected")
        compose.onNodeWithTag(ChatGroupParticipantPickerConfirmTestTag, useUnmergedTree = true)
            .performClick()
        compose.waitUntil(30_000) {
            runCatching {
                compose.onNodeWithTag(ChatGroupParticipantPickerRootTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }

        val memberRowTag = ChatGroupMemberRowTestTagPrefix + profileId
        SystemClock.sleep(1_500)
        for (attempt in 0 until 4) {
            compose.onNodeWithTag(ChatConversationTitleBarTestTag, useUnmergedTree = true)
                .performClick()
            compose.waitForIdle()
            if (nodeWithTagVisible(memberRowTag) || waitForText(displayName, displayName, timeoutMillis = 750) != null) {
                break
            }
            if (attempt < 3) SystemClock.sleep(750)
        }
        saveScreenshot("android-chat-group-admin-member-list")
        waitForTag(memberRowTag, "group admin member row")
        waitForText(displayName, displayName, timeoutMillis = 10_000)
            ?: error("group_admin_member_name_not_visible")
        compose.onNodeWithTag(ChatGroupMemberManageTestTagPrefix + profileId, useUnmergedTree = true)
            .performTouchInput { click(center) }
        waitForTag(ChatGroupMemberPromoteDemoteTestTagPrefix + profileId, "group admin member menu")
        saveScreenshot("android-chat-group-admin-member-menu")
        clickStableTag(ChatGroupMemberPromoteDemoteTestTagPrefix + profileId)
        waitForTag(QuataConfirmationDialogTestTag, "group admin promote confirmation", timeoutMillis = 10_000)
        saveScreenshot("android-chat-group-admin-promote-confirmation")
        clickStableTag(QuataConfirmationDialogConfirmTestTag)
        SystemClock.sleep(1_500)
        saveScreenshot("android-chat-group-admin-member-promoted")
    }

    private fun runGroupModerationStage(
        ownProbe: String,
        removeProfileId: String,
        removeDisplayName: String,
        removeSearchQuery: String,
        blockProfileId: String,
        blockDisplayName: String,
        blockSearchQuery: String,
    ) {
        waitForMarker(ownProbe, "group moderation initial chat thread")
        addGroupParticipantFromPicker(
            profileId = removeProfileId,
            searchQuery = removeSearchQuery,
            screenshotPrefix = "android-chat-group-moderation-remove",
        )
        openGroupMemberMenu(removeProfileId, removeDisplayName, "android-chat-group-moderation-remove")
        clickStableTag(ChatGroupMemberRemoveTestTagPrefix + removeProfileId)
        waitForTag(QuataConfirmationDialogTestTag, "group moderation remove confirmation", timeoutMillis = 10_000)
        clickStableTag(QuataConfirmationDialogConfirmTestTag)
        SystemClock.sleep(1_500)
        saveScreenshot("android-chat-group-moderation-member-removed")

        addGroupParticipantFromPicker(
            profileId = blockProfileId,
            searchQuery = blockSearchQuery,
            screenshotPrefix = "android-chat-group-moderation-block",
        )
        openGroupMemberMenu(blockProfileId, blockDisplayName, "android-chat-group-moderation-block")
        clickStableTag(ChatGroupMemberBlockTestTagPrefix + blockProfileId)
        waitForTag(QuataConfirmationDialogTestTag, "group moderation block confirmation", timeoutMillis = 10_000)
        clickStableTag(QuataConfirmationDialogConfirmTestTag)
        SystemClock.sleep(1_500)
        saveScreenshot("android-chat-group-moderation-member-blocked")
    }

    private fun addGroupParticipantFromPicker(
        profileId: String,
        searchQuery: String,
        screenshotPrefix: String,
    ) {
        openOptionsMenu()
        clickStableTag(ChatGroupMenuAddParticipantsTestTag)
        waitForTag(ChatGroupParticipantPickerSearchTestTag, "group participant picker search")
        compose.onNodeWithTag(ChatGroupParticipantPickerSearchTestTag, useUnmergedTree = true)
            .performTextReplacement(searchQuery)
        compose.waitForIdle()
        device.pressBack()
        compose.waitForIdle()
        val candidateTag = ChatGroupParticipantPickerCandidateTestTagPrefix + profileId
        compose.waitUntil(20_000) { nodeWithTagVisible(candidateTag) }
        saveScreenshot("$screenshotPrefix-participant-picker")
        compose.onNodeWithTag(candidateTag, useUnmergedTree = true)
            .performTouchInput { click(Offset(24f, center.y)) }
        compose.waitForIdle()
        compose.onNodeWithTag(ChatGroupParticipantPickerConfirmTestTag, useUnmergedTree = true)
            .performClick()
        compose.waitUntil(30_000) {
            runCatching {
                compose.onNodeWithTag(ChatGroupParticipantPickerRootTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
    }

    private fun openGroupMemberMenu(
        profileId: String,
        displayName: String,
        screenshotPrefix: String,
    ) {
        val memberRowTag = ChatGroupMemberRowTestTagPrefix + profileId
        SystemClock.sleep(1_500)
        for (attempt in 0 until 4) {
            compose.onNodeWithTag(ChatConversationTitleBarTestTag, useUnmergedTree = true)
                .performClick()
            compose.waitForIdle()
            if (nodeWithTagVisible(memberRowTag) || waitForText(displayName, displayName, timeoutMillis = 750) != null) {
                break
            }
            if (attempt < 3) SystemClock.sleep(750)
        }
        saveScreenshot("$screenshotPrefix-member-list")
        waitForTag(memberRowTag, "group moderation member row")
        waitForText(displayName, displayName, timeoutMillis = 10_000)
            ?: error("group_moderation_member_name_not_visible")
        compose.onNodeWithTag(ChatGroupMemberManageTestTagPrefix + profileId, useUnmergedTree = true)
            .performTouchInput { click(center) }
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

    private fun openChatMediaAttachmentViewer(contentDescription: String, openTag: String? = null) {
        if (!openTag.isNullOrBlank()) {
            val tagMatcher = hasTestTag(openTag)
            compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
                .performScrollToNode(tagMatcher)
            compose.waitUntil(10_000) {
                visibleNodes(tagMatcher).isNotEmpty()
            }
            compose.onNodeWithTag(openTag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            if (runCatching {
                    compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
            ) {
                return
            }
        }
        val matcher = hasContentDescription(contentDescription)
        compose.onNodeWithTag(ChatConversationMessagesListTestTag, useUnmergedTree = true)
            .performScrollToNode(matcher)
        compose.waitUntil(10_000) {
            visibleNodes(matcher).isNotEmpty()
        }
        runCatching {
            compose.onNode(matcher, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
        }
        if (runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        ) {
            return
        }
        val visibleNode = visibleNodes(matcher).firstOrNull()
            ?: error("chat_media_attachment_missing_visible_anchor:$contentDescription")
        val center = visibleNode.boundsInRoot.center
        check(device.click(center.x.roundToInt(), center.y.roundToInt())) {
            "chat_media_attachment_visible_tap_failed:$contentDescription"
        }
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag("fullscreen-media.root", useUnmergedTree = true)
            .fetchSemanticsNode()
    }

    private fun closeFullscreenMediaViewer(titleNeedle: String) {
        listOf(
            "fullscreen-media.media-close",
            "fullscreen-media.close",
            "fullscreen-media.back",
        ).forEach { tag ->
            runCatching {
                clickStableTag(tag)
                compose.waitForIdle()
            }
            if (waitForFullscreenMediaClosed(titleNeedle, 2_000)) {
                compose.waitUntil(10_000) {
                    nodeWithTagVisible(ChatConversationMessagesListTestTag)
                }
                return
            }
        }
        listOf(
            "fullscreen-media.media-close",
            "fullscreen-media.close",
            "fullscreen-media.back",
        ).forEach { tag ->
            waitForObject(By.res(targetContext.packageName, tag), tag, 500)?.click()
                ?: waitForObject(By.descContains(tag), tag, 500)?.click()
            if (waitForFullscreenMediaClosed(titleNeedle, 2_000)) {
                compose.waitUntil(10_000) {
                    nodeWithTagVisible(ChatConversationMessagesListTestTag)
                }
                return
            }
        }
        if (isFullscreenMediaAccessible(titleNeedle)) {
            device.pressBack()
            waitForFullscreenMediaClosed(titleNeedle, 10_000)
        }
        ensureFullscreenMediaVisuallyDismissed(titleNeedle)
        compose.waitUntil(10_000) {
            nodeWithTagVisible(ChatConversationMessagesListTestTag)
        }
    }

    private fun waitForFullscreenMediaClosed(titleNeedle: String, timeoutMillis: Long): Boolean {
        return runCatching {
            compose.waitUntil(timeoutMillis) {
                !isFullscreenMediaAccessible(titleNeedle)
            }
            true
        }.getOrDefault(false)
    }

    private fun ensureFullscreenMediaVisuallyDismissed(titleNeedle: String) {
        waitForFullscreenMediaTitleGone(titleNeedle)
        val nativeClose = By.descContains("fullscreen-media.close")
        val nativeMediaClose = By.descContains("fullscreen-media.media-close")
        if (device.hasObject(nativeClose) || device.hasObject(nativeMediaClose)) {
            waitForObject(nativeMediaClose, "fullscreen-media.media-close", 500)?.click()
            waitForObject(nativeClose, "fullscreen-media.close", 500)?.click()
            device.wait(Until.gone(nativeClose), 2_000)
            device.wait(Until.gone(nativeMediaClose), 2_000)
        }
        // Some Android media surfaces can remain visible after Compose semantics are removed.
        // Visible fallbacks target the close affordances in the top chrome and media surface.
        waitForFullscreenMediaTitleGone(titleNeedle)
        assertFalse(
            "Fullscreen media viewer remained visible after close attempts for $titleNeedle",
            isFullscreenMediaAccessible(titleNeedle),
        )
        compose.waitUntil(10_000) {
            nodeWithTagVisible(ChatConversationMessagesListTestTag)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun isFullscreenMediaAccessible(titleNeedle: String): Boolean {
        val tags = listOf(
            "fullscreen-media.root",
            "fullscreen-media.title",
            "fullscreen-media.close",
            "fullscreen-media.media-close",
            "fullscreen-media.back",
        )
        return tags.any { tag ->
            nodeWithTagVisible(tag) ||
                visibleObject(By.res(targetContext.packageName, tag)) ||
                visibleObject(By.descContains(tag))
        }
    }

    private fun visibleObject(selector: BySelector): Boolean =
        runCatching {
            val bounds = device.findObject(selector)?.visibleBounds ?: return@runCatching false
            !bounds.isEmpty &&
                bounds.width() > 0 &&
                bounds.height() > 0 &&
                bounds.right > 0 &&
                bounds.bottom > 0 &&
                bounds.left < device.displayWidth &&
                bounds.top < device.displayHeight
        }.getOrDefault(false)

    private fun waitForFullscreenMediaTitleGone(titleNeedle: String) {
        if (isFullscreenMediaAccessible(titleNeedle)) {
            listOf(
                "fullscreen-media.close",
                "fullscreen-media.back",
            ).forEach { tag ->
                runCatching {
                    compose.onNodeWithTag(tag, useUnmergedTree = true)
                        .performClick()
                }
                waitForFullscreenMediaClosed(titleNeedle, 2_000)
            }
        }
        if (isFullscreenMediaAccessible(titleNeedle)) {
            device.pressBack()
        }
        waitForFullscreenMediaClosed(titleNeedle, 5_000)
    }

    private fun assertProfileContentStage(profileId: String, actorProfileId: String, postId: String, commentId: String, attachmentId: String, uiComment: String, replyComment: String) {
        openPublicProfilePosts(profileId, postId)
        listOf(
            "public-profile.gallery.header.$profileId",
            "public-profile.gallery.$profileId",
            "public-profile.gallery.post.$postId",
            "public-profile.post.preview.$postId",
            "public-profile.post.action.comments.$postId",
            "public-profile.attachments",
            "public-profile.attachments.item.sb:$attachmentId",
        ).forEach { tag ->
            waitForTag(tag, "public profile content stage", 20_000)
        }
        val mediaOpenTag = "public-profile.post.media.open.$postId"
        bringPublicProfilePostIntoView(profileId, postId)
        waitForTag(mediaOpenTag, "public profile media open action", 20_000)
        clickStableTag(mediaOpenTag)
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
        bringPublicProfilePostIntoView(profileId, postId)
        ensurePublicProfileCommentsPanelOpen(profileId, postId, "initial")
        listOf(
            "public-profile.comments.panel",
            "public-profile.comments.list",
            "public-profile.comments.row.$commentId",
            "public-profile.comments.author.$actorProfileId",
            "public-profile.comments.translator",
            "public-profile.comments.emoji",
            "public-profile.comments.input",
            "public-profile.comments.send",
        ).forEach { tag ->
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }
        sendReplyCommentFromOpenPanel(
            prefix = "public-profile.comments",
            replyToCommentId = commentId,
            inputTag = "public-profile.comments.input",
            emojiTag = "public-profile.comments.emoji",
            sendTag = "public-profile.comments.send",
            replyComment = replyComment,
            screenshotPrefix = "android-chat-profile-content",
        )
        val visibleCommentText = uiComment.removePrefix("😀").trimStart()
        performProfileCommentTextInput(postId, visibleCommentText, "after-reply")
        submitTaggedComment("public-profile.comments.input", "public-profile.comments.send", visibleCommentText, "android-chat-profile-content-missing-comment")
        waitForTagGone("public-profile.comments.pending.$postId", "public profile comment persistence", 45_000)
    }

    private fun openPublicProfilePosts(profileId: String, postId: String) {
        val postsTag = "public-profile.kpi.posts.$profileId"
        val galleryTag = "public-profile.gallery.post.$postId"
        repeat(3) { attempt ->
            scrollPublicProfileToTag(postsTag)
            clickSemanticTagPreferCompose(postsTag)
            val opened = runCatching {
                compose.waitUntil(10_000) { nodeWithTagVisible(galleryTag) }
                true
            }.getOrDefault(false)
            if (opened) {
                saveScreenshot("android-chat-profile-content")
                return
            }
            if (attempt == 0) {
                saveScreenshot("android-chat-profile-content-posts-retry")
            }
        }
        saveScreenshot("android-chat-profile-content-missing-posts-gallery")
        compose.onNodeWithTag(galleryTag, useUnmergedTree = true)
            .fetchSemanticsNode()
    }

    private fun performProfileCommentTextInput(postId: String, text: String, context: String) {
        repeat(3) { attempt ->
            ensurePublicProfileCommentsPanelOpen(null, postId, "$context-$attempt")
            val typed = runCatching {
                compose.onNodeWithTag("public-profile.comments.input", useUnmergedTree = true)
                    .performTextInput(text)
                true
            }.getOrDefault(false)
            if (typed && taggedInputText("public-profile.comments.input").contains(text)) return
            SystemClock.sleep(750)
        }
        saveScreenshot("android-chat-profile-comments-input-missing-$context")
        File(evidenceDir(), "android-chat-profile-comments-input-missing-$context-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        assertTrue("Public profile comments input must remain available after reply submission.", false)
    }

    private fun runProfilePrivateChatStage(peerProbe: String, profileId: String, privateProbe: String) {
        openPeerProfile(peerProbe, profileId)
        saveScreenshot("android-chat-profile-private-chat-before")
        compose.onNodeWithTag("public-profile.chat.$profileId", useUnmergedTree = true)
            .performClick()
        waitForMarker(privateProbe, "private conversation opened from public profile")
        saveScreenshot("android-chat-profile-private-chat-opened")
    }

    private fun ensurePublicProfileCommentsPanelOpen(profileId: String?, postId: String, context: String) {
        if (nodeWithTagExists("public-profile.comments.input")) return
        val screenshot = "android-chat-profile-comments-panel-reopen-$context"
        val commentsTag = "public-profile.post.action.comments.$postId"
        repeat(3) { attempt ->
            if (profileId != null) bringPublicProfilePostIntoView(profileId, postId)
            bringPublicProfileTagIntoView(commentsTag)
            clickSemanticTagPreferCompose(commentsTag)
            val opened = runCatching {
                waitForTagExists("public-profile.comments.input", "public profile comments input $context-$attempt", 12_000)
                true
            }.getOrDefault(false)
            if (opened) return
            if (attempt == 0) {
                saveScreenshot(screenshot)
                File(evidenceDir(), "$screenshot-semantics.txt")
                    .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
            }
        }
        waitForTagExists("public-profile.comments.input", "public profile comments input $context", 1_000)
    }

    private fun bringPublicProfileTagIntoView(tag: String) {
        repeat(8) {
            scrollPublicProfileToTag(tag)
            if (nodeWithTagVisible(tag)) return
            val detailsNode = visibleTaggedNodes("public-profile.details").firstOrNull()
            if (detailsNode != null) {
                val bounds = detailsNode.boundsInRoot
                val x = bounds.center.x.roundToInt().coerceIn(1, device.displayWidth - 1)
                val startY = (bounds.bottom - bounds.height * 0.18f).roundToInt().coerceIn(1, device.displayHeight - 1)
                val endY = (bounds.top + bounds.height * 0.18f).roundToInt().coerceIn(1, device.displayHeight - 1)
                device.swipe(x, startY, x, endY, 18)
                compose.waitForIdle()
            }
            if (nodeWithTagVisible(tag)) return
        }
    }

    private fun bringPublicProfilePostIntoView(profileId: String, postId: String) {
        val galleryTag = "public-profile.gallery.$profileId"
        val pageTag = "public-profile.gallery.post.$postId"
        val mediaOpenTag = "public-profile.post.media.open.$postId"
        val commentsTag = "public-profile.post.action.comments.$postId"
        repeat(6) { attempt ->
            scrollPublicProfileToTag("public-profile.gallery.header.$profileId")
            if (nodeWithTagVisible(mediaOpenTag) || nodeWithTagVisible(commentsTag)) return
            runCatching {
                compose.onNodeWithTag(galleryTag, useUnmergedTree = true)
                    .performScrollToNode(hasTestTag(pageTag))
                compose.waitForIdle()
            }
            if (nodeWithTagVisible(mediaOpenTag) || nodeWithTagVisible(commentsTag)) return
            val galleryNode = visibleTaggedNodes(galleryTag).firstOrNull()
            if (galleryNode != null) {
                val bounds = galleryNode.boundsInRoot
                val startX = (bounds.right - bounds.width * 0.18f).roundToInt()
                val endX = (bounds.left + bounds.width * 0.18f).roundToInt()
                val y = bounds.center.y.roundToInt()
                device.swipe(startX, y, endX, y, 24)
                compose.waitForIdle()
            }
            if (attempt == 4) saveScreenshot("android-chat-profile-content-gallery-page-retry")
        }
        saveScreenshot("android-chat-profile-content-gallery-page-missing")
        File(evidenceDir(), "android-chat-profile-content-gallery-page-missing-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        compose.onNodeWithTag(pageTag, useUnmergedTree = true)
            .fetchSemanticsNode()
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
        waitForComposerInput()
        val input = compose.onNode(composerInputMatcher(), useUnmergedTree = true)
        input.performClick()
        input.performTextReplacement(text)
        compose.waitForIdle()
        compose.waitUntil(15_000) { composerInputText() == text }
        beforeSendScreenshotName?.let(::saveScreenshot)
        val sentByNative = forceNativeSend && clickComposerSendNative()
        if (!sentByNative) {
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
        }
        compose.waitForIdle()
        dismissComposerImeIfFocused()
        afterSendScreenshotName?.let(::saveScreenshot)
    }

    private fun dismissComposerImeIfFocused() {
        val focused = runCatching {
            compose.onNodeWithTag(ChatComposerInputTestTag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }.getOrDefault(false)
        if (!focused) return
        device.pressBack()
        compose.waitForIdle()
        SystemClock.sleep(500)
    }

    private fun waitForComposerInput(timeoutMillis: Long = 15_000) {
        compose.waitUntil(timeoutMillis) {
            runCatching {
                compose.onNode(composerInputMatcher(), useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun composerInputMatcher(): SemanticsMatcher =
        hasTestTag(ChatComposerInputTestTag) or
            (hasSetTextAction() and hasText("Message", substring = true))

    private fun composerInputText(): String =
        runCatching {
            compose.onNode(composerInputMatcher(), useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
                .orEmpty()
        }.getOrDefault("")

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

    private fun waitForPackageToLeaveApp(timeoutMillis: Long = 2_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName != targetContext.packageName) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun waitForPackageToReturnToApp(timeoutMillis: Long = 8_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName == targetContext.packageName) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun closeGroupOptionsMenuForSos() {
        device.pressBack()
        compose.waitForIdle()
        if (!nodeWithTagVisible(ChatGroupMenuAllowInvitesTestTag)) return
        val muteToggleTag = when {
            nodeWithTagVisible(ChatGroupMenuMuteTestTag) -> ChatGroupMenuMuteTestTag
            nodeWithTagVisible(ChatGroupMenuUnmuteTestTag) -> ChatGroupMenuUnmuteTestTag
            else -> error("group_options_menu_close_action_missing")
        }
        compose.onNodeWithTag(muteToggleTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { !nodeWithTagVisible(ChatGroupMenuAllowInvitesTestTag) }
    }

    private fun waitForAnyComposeText(vararg candidates: String, timeoutMillis: Long = 10_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            val visible = candidates.any { candidate ->
                runCatching {
                    compose.onAllNodes(hasText(candidate, substring = true), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }.getOrDefault(false)
            }
            if (visible) return true
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
            val scrollContainerTag = when {
                tag.startsWith("conversation.avatar.") -> ConversationListTestTag
                tag.startsWith("public-profile.") -> "public-profile.details"
                else -> ChatConversationMessagesListTestTag
            }
            compose.onNodeWithTag(scrollContainerTag, useUnmergedTree = true)
                .performScrollToNode(hasTestTag(tag))
            compose.waitUntil(10_000) { nodeWithTagVisible(tag) }
            true
        }.getOrDefault(false)
        assertTrue("The semantic tag must be visible in $context.", scrolled)
    }

    private fun clickStableTag(tag: String) {
        val visibleNode = visibleTaggedNodes(tag).firstOrNull()
        if (visibleNode != null) {
            val center = visibleNode.boundsInRoot.center
            check(device.click(center.x.roundToInt(), center.y.roundToInt())) { "stable_tag_visible_tap_failed:$tag" }
            return
        }
        val clickedByCompose = runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performClick()
            true
        }.getOrDefault(false)
        if (clickedByCompose) return
        val nativeNode = waitForObject(By.res(targetContext.packageName, tag), tag, 1_000)
            ?: waitForObject(By.descContains(tag), tag, 1_000)
        check(nativeNode != null) { "stable_tag_not_clickable:$tag" }
        nativeNode.click()
    }

    private fun clickSemanticTagPreferCompose(tag: String) {
        val clickedByCompose = runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performClick()
            compose.waitForIdle()
            true
        }.getOrDefault(false)
        if (clickedByCompose) return
        clickStableTag(tag)
    }

    private fun clickMergedTagWithAction(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = false)
            .assertHasClickAction()
            .performClick()
        compose.waitForIdle()
    }

    private fun clickComposeTag(tag: String) {
        val visibleNode = visibleTaggedNodes(tag)
            .maxWithOrNull(compareBy({ it.boundsInRoot.center.y }, { it.boundsInRoot.center.x }))
        if (visibleNode != null) {
            val center = visibleNode.boundsInRoot.center
            check(device.click(center.x.roundToInt(), center.y.roundToInt())) { "compose_tag_visible_tap_failed:$tag" }
        } else {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performClick()
        }
        compose.waitForIdle()
    }

    private fun submitTaggedComment(inputTag: String, sendTag: String, visibleText: String, failureScreenshot: String) {
        repeat(3) { attempt ->
            when (attempt) {
                0 -> clickComposeTag(sendTag)
                1 -> runCatching {
                    compose.onNodeWithTag(sendTag, useUnmergedTree = true).performClick()
                    compose.waitForIdle()
                }
                else -> clickComposeTag(sendTag)
            }
            val visible = runCatching {
                compose.waitUntil(45_000) {
                    compose.onAllNodes(hasText(visibleText, substring = true), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty() && !taggedInputText(inputTag).contains(visibleText)
                }
                true
            }.getOrDefault(false)
            if (visible) return
            if (!taggedInputText(inputTag).contains(visibleText) && scrollCommentListToText(inputTag, visibleText)) return
            if (attempt == 0) {
                device.pressBack()
                compose.waitForIdle()
            }
        }
        saveScreenshot(failureScreenshot)
        File(evidenceDir(), "$failureScreenshot-semantics.txt")
            .writeText(runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20) }.getOrElse { it.stackTraceToString() })
        assertTrue(
            "The submitted comments text must be visible outside the input: $visibleText. Remaining input=${taggedInputText(inputTag)}",
            false,
        )
    }

    private fun scrollCommentListToText(inputTag: String, visibleText: String): Boolean {
        val listTag = when {
            inputTag.startsWith("public-profile.comments.") -> "public-profile.comments.list"
            inputTag.startsWith("feed.comments.") -> "feed.comments.list"
            inputTag.startsWith("official.comments.") -> "official.comments.list"
            else -> return false
        }
        return runCatching {
            compose.onNodeWithTag(listTag, useUnmergedTree = true)
                .performScrollToNode(hasText(visibleText, substring = true))
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(visibleText, substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            true
        }.getOrDefault(false)
    }

    private fun taggedInputText(tag: String): String =
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
                .orEmpty()
        }.getOrDefault("")

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
        visibleTaggedNodes(tag).isNotEmpty()

    private fun nodeWithTagExists(tag: String): Boolean =
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
            true
        }.getOrDefault(false)

    private fun visibleNonEditableTextNodeCount(text: String): Int =
        runCatching {
            compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .count { node ->
                    val bounds = node.boundsInRoot
                    val isVisible = bounds.width > 0f &&
                        bounds.height > 0f &&
                        bounds.right > 0f &&
                        bounds.bottom > 0f &&
                        bounds.left < device.displayWidth &&
                        bounds.top < device.displayHeight
                    isVisible && node.config.getOrNull(SemanticsProperties.EditableText) == null
                }
        }.getOrDefault(0)

    private fun waitForTagExists(tag: String, context: String, timeoutMillis: Long = 45_000) {
        val exists = runCatching {
            compose.waitUntil(timeoutMillis) { nodeWithTagExists(tag) }
            true
        }.getOrDefault(false)
        assertTrue("The semantic tag must exist in $context.", exists)
    }

    private fun waitForTagGone(tag: String, context: String, timeoutMillis: Long = 45_000) {
        val gone = runCatching {
            compose.waitUntil(timeoutMillis) { !nodeWithTagExists(tag) }
            true
        }.getOrDefault(false)
        assertTrue("The semantic tag must disappear in $context.", gone)
    }

    private fun waitForVisibleText(text: String, context: String, timeoutMillis: Long = 45_000) {
        val visible = runCatching {
            compose.waitUntil(timeoutMillis) { visibleNonEditableTextNodeCount(text) > 0 }
            true
        }.getOrDefault(false)
        assertTrue("The expected text must be visible in $context: $text", visible)
    }

    private fun visibleTaggedNodes(tag: String) =
        visibleNodes(hasTestTag(tag))

    private fun visibleNodes(matcher: SemanticsMatcher) =
        runCatching {
            compose.onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { node ->
                    val bounds = node.boundsInRoot
                    bounds.width > 0f &&
                        bounds.height > 0f &&
                        bounds.right > 0f &&
                        bounds.bottom > 0f &&
                        bounds.left < device.displayWidth &&
                        bounds.top < device.displayHeight
                }
        }.getOrDefault(emptyList())

    private fun visibleAboveComposerNodes(matcher: SemanticsMatcher) =
        visibleNodes(matcher).filter { node ->
            val composerTop = runCatching {
                compose.onNodeWithTag(ChatComposerRootTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            }.getOrDefault(device.displayHeight.toFloat())
            node.boundsInRoot.bottom < composerTop
        }

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
        check(file.length() > 0L) { "android_screenshot_empty:$name" }
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

    private fun grantRecordAudioPermission() {
        if (targetContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return
        instrumentation.uiAutomation.executeShellCommand("pm grant ${targetContext.packageName} ${Manifest.permission.RECORD_AUDIO}")
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
