package com.quata.feature.profile.presentation

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.R
import com.quata.core.ui.components.QuataAccountLifecycleTestTags
import com.quata.core.ui.components.QuataLegalDocumentLinkTestTagPrefix
import com.quata.core.session.AuthState
import com.quata.feature.feed.presentation.FeedRootTestTag
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ProfilePostflightInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedAccountRootNavigatesAndCancelsLifecycleActions() = runBlocking {
        val credentialsFile = optionalArgument("quataAccountPostflightCredentialsFile")
        assumeTrue(
            "ACCOUNT-POSTFLIGHT-ANDROID-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataAccountPostflightEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_account_postflight_real_session_missing", initialSession?.isSupabaseAuthenticated() == true)
        val screenshots = mutableListOf<String>()
        val steps = mutableListOf<String>()

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            waitFor(ProfileManagementOpenTestTag)
            waitFor(ProfileSosOpenTestTag)
            waitFor(ProfileLogoutTestTag)
            waitFor("${QuataLegalDocumentLinkTestTagPrefix}privacy")
            waitFor("${QuataLegalDocumentLinkTestTagPrefix}childsafety")
            screenshots += screenshot("android-account-postflight-overview")
            steps += "account_overview_shared_entries_visible"

            tap(ProfileDetailsOpenTestTag)
            waitFor(ProfileDetailsRootTestTag)
            tap(ProfileDetailsBackTestTag)
            waitForGone(ProfileDetailsRootTestTag)
            waitFor(ProfileManagementOpenTestTag)
            steps += "account_details_opened_and_returned"

            tap(ProfileManagementOpenTestTag)
            waitFor(ProfileManagementRootTestTag)
            screenshots += screenshot("android-account-postflight-management")

            openAndCancel(ProfileDeactivateOpenTestTag)
            steps += "account_deactivate_confirmation_cancelled"
            openAndCancel(ProfileDeleteOpenTestTag)
            steps += "account_delete_confirmation_cancelled"
            screenshots += screenshot("android-account-postflight-management-after-cancel")

            tap(ProfileManagementBackTestTag)
            waitFor(ProfileManagementOpenTestTag)
            steps += "account_management_returned_to_overview"
        }

        val finalSession = app.container.sessionManager.currentSession()
        assertTrue("android_account_postflight_session_lost", finalSession?.isSupabaseAuthenticated() == true)
        assertEquals("android_account_postflight_actor_changed", initialSession?.userId, finalSession?.userId)
        writeReport(initialSession?.userId.orEmpty(), steps, screenshots)
    }

    @Test
    fun authenticatedLogoutReturnsToPublicFeedAndClearsOwnedSession() = runBlocking {
        val credentialsFile = optionalArgument("quataAccountPostflightCredentialsFile")
        assumeTrue(
            "AUTH-LOGOUT-ANDROID-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataAuthLogoutEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_auth_logout_real_session_missing", initialSession?.isSupabaseAuthenticated() == true)
        val authenticatedSession = requireNotNull(initialSession)
        val authClaims = JSONObject(
            String(
                Base64.decode(
                    authenticatedSession.bearerToken.split('.').getOrNull(1)
                        ?: error("android_auth_logout_jwt_payload_missing"),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                ),
                Charsets.UTF_8,
            ),
        )
        val authSessionId = authClaims.optString("session_id").takeIf(String::isNotBlank)
            ?: error("android_auth_logout_session_id_missing")
        val authUserId = authClaims.optString("sub").takeIf(String::isNotBlank)
            ?: authenticatedSession.authUserId?.takeIf(String::isNotBlank)
            ?: error("android_auth_logout_auth_user_missing")

        lateinit var registeredPushToken: String
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            val pushPreferences = targetContext.getSharedPreferences("quata_push_tokens", Context.MODE_PRIVATE)
            compose.waitUntil(20_000) {
                !pushPreferences.getString("registered_token", null).isNullOrBlank()
            }
            registeredPushToken = pushPreferences.getString("registered_token", null)
                ?.takeIf(String::isNotBlank)
                ?: error("android_auth_logout_registered_push_token_missing")
            writePrivateLogoutReceipt(
                profileId = authenticatedSession.userId,
                authUserId = authUserId,
                authSessionId = authSessionId,
                pushToken = registeredPushToken,
            )
            waitFor(ProfileLogoutTestTag)
            tap(ProfileLogoutTestTag)
            compose.waitUntil(10_000) { app.container.sessionManager.currentSession() == null }
            compose.waitUntil(10_000) { app.container.sessionManager.authState.value is AuthState.LoggedOut }
            screenshot("android-auth-logout-after-session-clear")
            waitFor(FeedRootTestTag)
            waitForGone(ProfileLogoutTestTag)
            assertTrue("android_auth_logout_session_not_cleared", app.container.sessionManager.currentSession() == null)
            screenshot("android-auth-logout-public-feed")
        }

        ActivityScenario.launch<MainActivity>(mainIntent("feed")).use {
            waitFor(FeedRootTestTag)
            waitForGone(ProfileLogoutTestTag)
            assertTrue("android_auth_logout_session_restored_after_relaunch", app.container.sessionManager.currentSession() == null)
        }
        writeLogoutReport(
            profileId = authenticatedSession.userId,
            authSessionId = authSessionId,
            pushToken = registeredPushToken,
        )
    }

    @Test
    fun authenticatedAccountLifecycleActionExecutesFromProductUi() = runBlocking {
        val credentialsFile = optionalArgument("quataAccountPostflightCredentialsFile")
        val action = optionalArgument("quataAccountLifecycleAction")
        assumeTrue(
            "ACCOUNT-LIFECYCLE-ANDROID-REAL-001 is opt-in and requires owned synthetic credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataAccountLifecycleEvidence") == "1" &&
                action in setOf("deactivate", "delete"),
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_account_lifecycle_real_session_missing", initialSession?.isSupabaseAuthenticated() == true)

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            tap(ProfileManagementOpenTestTag)
            waitFor(ProfileManagementRootTestTag)
            tap(if (action == "delete") ProfileDeleteOpenTestTag else ProfileDeactivateOpenTestTag)
            waitFor(ProfileDangerDialogTestTag)
            tap(ProfileDangerConfirmTestTag)
            waitFor(QuataAccountLifecycleTestTags.Dialog)
            compose.onNodeWithTag(QuataAccountLifecycleTestTags.Password, useUnmergedTree = true)
                .performTextInput(credentials.password)
            if (action == "delete") {
                compose.onNodeWithTag(QuataAccountLifecycleTestTags.Confirmation, useUnmergedTree = true)
                    .performTextInput(targetContext.getString(R.string.account_delete_confirmation_word))
            }
            screenshot("android-account-lifecycle-$action-confirmed")
            tap(QuataAccountLifecycleTestTags.Confirm)
            compose.waitUntil(30_000) { app.container.sessionManager.currentSession() == null }
            compose.waitUntil(30_000) { app.container.sessionManager.authState.value is AuthState.LoggedOut }
            screenshot("android-account-lifecycle-$action-session-cleared")
        }

        ActivityScenario.launch<MainActivity>(naturalMainIntent()).use {
            waitFor(FeedRootTestTag)
            waitForGone(ProfileLogoutTestTag)
            assertTrue("android_account_lifecycle_session_restored_after_relaunch", app.container.sessionManager.currentSession() == null)
            screenshot("android-account-lifecycle-$action-public-feed-after-relaunch")
        }

        writeLifecycleReport(initialSession?.userId.orEmpty(), action.orEmpty())
    }

    private fun openAndCancel(actionTag: String) {
        tap(actionTag)
        waitFor(ProfileDangerDialogTestTag)
        waitFor(ProfileDangerConfirmTestTag)
        tap(ProfileDangerCancelTestTag)
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag(ProfileDangerDialogTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isFailure
        }
        waitFor(ProfileManagementRootTestTag)
    }

    private fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun waitForGone(tag: String) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
        }
    }

    private fun screenshot(name: String): String {
        val file = File(evidenceDir(), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("android_account_postflight_screenshot_failed:$name")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        return file.name
    }

    private fun writeReport(profileId: String, steps: List<String>, screenshots: List<String>) {
        File(evidenceDir(), "android-account-postflight-evidence.json").writeText(
            JSONObject()
                .put("check", "ACCOUNT-POSTFLIGHT-ANDROID-001")
                .put("status", "passed")
                .put("actorProfileIdSha256", sha256(profileId))
                .put("steps", JSONArray(steps))
                .put("destructiveCallbacksInvoked", false)
                .put("sessionPreserved", true)
                .put("screenshots", JSONArray(screenshots))
                .toString(2) + "\n",
        )
    }

    private fun writePrivateLogoutReceipt(
        profileId: String,
        authUserId: String,
        authSessionId: String,
        pushToken: String,
    ) {
        val directory = File(targetContext.filesDir, "account-postflight-private")
            .also { check(it.exists() || it.mkdirs()) }
        File(directory, "android-auth-logout-private.json").writeText(
            JSONObject()
                .put("profileId", profileId)
                .put("authUserId", authUserId)
                .put("authSessionId", authSessionId)
                .put("pushToken", pushToken)
                .toString() + "\n",
        )
    }

    private fun writeLogoutReport(profileId: String, authSessionId: String, pushToken: String) {
        File(evidenceDir(), "android-auth-logout-evidence.json").writeText(
            JSONObject()
                .put("check", "AUTH-LOGOUT-ANDROID-001")
                .put("status", "passed")
                .put("actorProfileIdSha256", sha256(profileId))
                .put("authSessionIdSha256", sha256(authSessionId))
                .put("pushTokenSha256", sha256(pushToken))
                .put("steps", JSONArray(listOf(
                    "owned_push_token_registered_before_logout",
                    "authenticated_profile_logout_control_activated",
                    "public_feed_visible_after_logout",
                    "owned_session_absent_after_logout",
                    "owned_session_absent_after_relaunch",
                )))
                .put("sessionCleared", true)
                .put("screenshots", JSONArray(listOf(
                    "android-auth-logout-after-session-clear.png",
                    "android-auth-logout-public-feed.png",
                )))
                .toString(2) + "\n",
        )
    }

    private fun writeLifecycleReport(profileId: String, action: String) {
        File(evidenceDir(), "android-account-lifecycle-$action-evidence.json").writeText(
            JSONObject()
                .put("check", "ACCOUNT-LIFECYCLE-ANDROID-REAL-001")
                .put("status", "passed")
                .put("action", action)
                .put("actorProfileIdSha256", sha256(profileId))
                .put("steps", JSONArray(listOf(
                    "authenticated_owned_synthetic_actor_logged_in",
                    "shared_account_management_opened",
                    "profile_danger_confirmation_accepted_once",
                    "shared_password_confirmation_completed",
                    "lifecycle_confirm_activated_once",
                    "owned_session_cleared_after_success",
                    "public_feed_visible_after_relaunch",
                )))
                .put("productControlActivations", 1)
                .put("sessionCleared", true)
                .put("screenshots", JSONArray(listOf(
                    "android-account-lifecycle-$action-confirmed.png",
                    "android-account-lifecycle-$action-session-cleared.png",
                    "android-account-lifecycle-$action-public-feed-after-relaunch.png",
                )))
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File = File(targetContext.filesDir, "account-postflight-evidence")
        .also { check(it.exists() || it.mkdirs()) }

    private fun mainIntent(destination: String = "profile"): Intent = Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
        .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", destination)

    private fun naturalMainIntent(): Intent = Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit().putBoolean("app_links_prompt_seen", true).commit()
    }

    private fun optionalArgument(name: String): String? = arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentialsFromFile(path: String): Credentials {
        val file = if (path.startsWith("app-internal:")) File(targetContext.filesDir, path.removePrefix("app-internal:")) else File(path)
        val json = JSONObject(file.readText())
        return Credentials(json.getString("country_code"), json.getString("phone"), json.getString("password"))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class Credentials(val countryCode: String, val phone: String, val password: String)
}
