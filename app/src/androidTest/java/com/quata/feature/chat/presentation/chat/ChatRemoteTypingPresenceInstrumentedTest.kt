package com.quata.feature.chat.presentation.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.QuataApp
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatRemoteTypingPresenceInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedPeerAndComposerExchangeEphemeralTypingPresence() = runBlocking {
        val credentialsPath = argument("quataTypingCredentialsFile")
        val chatUrl = argument("quataTypingChatUrl")
        val draft = argument("quataTypingDraft")
        assumeTrue(
            "CHAT-TYPING Android evidence is opt-in.",
            credentialsPath != null && chatUrl != null && draft != null,
        )

        suppressStartupPrompts()
        val credentials = credentials(credentialsPath!!)
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        assertTrue(
            "The product must hold a real Supabase-authenticated session.",
            app.container.sessionManager.currentSession()?.isSupabaseAuthenticated() == true,
        )

        ActivityScenario.launch<MainActivity>(chatIntent(chatUrl!!)).use {
            compose.waitUntil(30_000) { visible(ChatComposerInputTestTag) }

            stage("READY_REMOTE")
            compose.waitUntil(30_000) { visible(ChatRemoteTypingIndicatorTestTag) }
            compose.onNodeWithTag(ChatRemoteTypingIndicatorTestTag, useUnmergedTree = true).assertIsDisplayed()
            saveScreenshot("android-chat-remote-typing-visible")
            stage("REMOTE_VISIBLE")

            compose.waitUntil(15_000) { !visible(ChatRemoteTypingIndicatorTestTag) }
            stage("REMOTE_STOPPED")

            val input = compose.onNodeWithTag(ChatComposerInputTestTag, useUnmergedTree = true)
            stage("LOCAL_READY")
            input.performClick()
            input.performTextReplacement(draft!!)
            compose.waitForIdle()
            Thread.sleep(4_200)
            input.performTextClearance()
            compose.waitForIdle()
            stage("LOCAL_CLEARED")

            stage("EXPIRY_READY")
            compose.waitUntil(15_000) { visible(ChatRemoteTypingIndicatorTestTag) }
            stage("EXPIRY_VISIBLE")
            compose.waitUntil(8_000) { !visible(ChatRemoteTypingIndicatorTestTag) }
            stage("EXPIRY_COMPLETE")

            writeReport(
                JSONObject()
                    .put("check", "CHAT-REMOTE-TYPING-PRESENCE-ANDROID-001")
                    .put("status", "passed")
                    .put("remoteStopRemovedIndicator", true)
                    .put("remotePresenceExpiredWithoutStop", true)
                    .put("productComposerExercised", true),
            )
        }
    }

    private fun visible(tag: String): Boolean = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    }.isSuccess

    private fun chatIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url), targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("app_links_prompt_seen", true)
            .commit()
    }

    private fun stage(value: String) {
        Log.i(LOG_TAG, value)
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("android_screenshot_failed:$name")
        val file = File(evidenceDirectory(), "$name.png")
        file.outputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        check(file.length() > 0L)
    }

    private fun writeReport(report: JSONObject) {
        File(evidenceDirectory(), "android-chat-typing-presence-evidence.json")
            .writeText("${report.toString(2)}\n")
    }

    private fun evidenceDirectory(): File =
        File(targetContext.filesDir, "chat-typing-presence-evidence")
            .also { directory -> check(directory.exists() || directory.mkdirs()) }

    private fun argument(name: String): String? = arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentials(path: String): EvidenceCredentials {
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

    private companion object {
        const val LOG_TAG = "QuataTypingEvidence"
    }
}
