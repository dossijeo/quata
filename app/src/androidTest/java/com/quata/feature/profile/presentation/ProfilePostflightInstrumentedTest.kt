package com.quata.feature.profile.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.core.ui.components.QuataLegalDocumentLinkTestTagPrefix
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

            tap(ProfileSosOpenTestTag)
            waitFor(ProfileSosRootTestTag)
            screenshots += screenshot("android-account-postflight-sos-open")
            pressBack()
            waitForGone(ProfileSosRootTestTag)
            waitFor(ProfileManagementOpenTestTag)
            steps += "account_sos_opened_and_dismissed_without_save"

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
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
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

    private fun evidenceDir(): File = File(targetContext.filesDir, "account-postflight-evidence")
        .also { check(it.exists() || it.mkdirs()) }

    private fun mainIntent(): Intent = Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
        .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "profile")

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
