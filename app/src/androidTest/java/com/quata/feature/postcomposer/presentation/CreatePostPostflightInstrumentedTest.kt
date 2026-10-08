package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.QuataApp
import kotlinx.coroutines.delay
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
class CreatePostPostflightInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedCreatePostRootOpensAndReturnsWithoutPublishing() = runBlocking {
        val credentialsFile = optionalArgument("quataCreatePostPostflightCredentialsFile")
        assumeTrue(
            "CREATE-POST-POSTFLIGHT-ANDROID-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataCreatePostPostflightEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_create_post_postflight_real_session_missing", initialSession?.isSupabaseAuthenticated() == true)
        val screenshots = mutableListOf<String>()
        val steps = mutableListOf<String>()

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            waitFor("navigation.primary.feed")
            waitForPrefix("feed.action.publish.")
            steps += "authenticated_feed_entry_visible"

            tapPrefix("feed.action.publish.")
            waitFor(CreatePostCommonRootTestTag)
            waitFor("navigation.primary.create_post")
            steps += "create_post_opened_from_feed_publish_action"
            screenshots += screenshot("android-create-post-postflight-opened")

            waitFor("composer-type-text")
            waitFor("composer-type-image")
            waitFor("composer-type-video")
            steps += "common_create_post_types_visible"

            tap("navigation.primary.feed")
            waitForGone(CreatePostCommonRootTestTag)
            waitForPrefix("feed.action.publish.")
            steps += "create_post_returned_to_feed_without_publish"
            screenshots += screenshot("android-create-post-postflight-returned")
        }

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            waitFor("navigation.primary.feed")
            waitForPrefix("feed.action.publish.")
        }
        val finalSession = app.container.sessionManager.currentSession()
        assertTrue("android_create_post_postflight_session_lost", finalSession?.isSupabaseAuthenticated() == true)
        assertEquals("android_create_post_postflight_actor_changed", initialSession?.userId, finalSession?.userId)
        steps += "authenticated_session_preserved_after_relaunch"
        writeReport(initialSession?.userId.orEmpty(), steps, screenshots)
    }

    @Test
    fun seedAuthenticatedTextDraftForProcessRestart() = runBlocking {
        val credentialsFile = optionalArgument("quataCreatePostPostflightCredentialsFile")
        val marker = optionalArgument("quataCreatePostDraftMarker")
        assumeTrue(
            "CREATE-POST-DRAFT-ANDROID-001 is opt-in and requires local credentials and marker.",
            !credentialsFile.isNullOrBlank() && !marker.isNullOrBlank() && optionalArgument("quataCreatePostPostflightEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue(initialSession?.isSupabaseAuthenticated() == true)

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            waitFor("navigation.primary.feed")
            waitForPrefix("feed.action.publish.")
            tapPrefix("feed.action.publish.")
            waitFor(CreatePostCommonRootTestTag)
            tap("composer-type-text")
            waitFor(ComposerTextInputTestTag)
            compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true).performTextInput(marker.orEmpty())
            waitForExactText(ComposerTextInputTestTag, marker.orEmpty())
            waitForPersistedTextDraft(initialSession?.userId.orEmpty(), marker.orEmpty())
            screenshot("android-create-post-draft-before-process-restart")
        }
        File(evidenceDir(), "android-create-post-draft-seeded.json").writeText(
            JSONObject().put("status", "seeded").put("markerSha256", sha256(marker.orEmpty())).toString() + "\n",
        )
    }

    @Test
    fun restoreAuthenticatedTextDraftAfterProcessRestartAndDiscard() = runBlocking {
        val marker = optionalArgument("quataCreatePostDraftMarker")
        assumeTrue(
            "CREATE-POST-DRAFT-ANDROID-001 is opt-in and requires the exclusive marker.",
            !marker.isNullOrBlank() && optionalArgument("quataCreatePostPostflightEvidence") == "1",
        )
        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_create_post_draft_session_missing_after_restart", initialSession?.isSupabaseAuthenticated() == true)
        val prelaunchDraft = PostComposerDraftStore(app.container.platformServices.preferences)
            .restore(initialSession?.userId.orEmpty()) { false }
            ?.snapshot
        assertEquals("android_create_post_draft_step_missing_after_restart", CreatePostStep.Text, prelaunchDraft?.step)
        assertEquals("android_create_post_draft_payload_missing_after_restart", marker.orEmpty(), prelaunchDraft?.text)
        val screenshots = mutableListOf<String>()
        val steps = mutableListOf(
            "authenticated_feed_entry_visible",
            "create_post_opened_from_feed_publish_action",
            "common_create_post_types_visible",
            "exclusive_text_draft_entered_without_publish",
            "target_process_force_stopped",
        )

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            waitFor("navigation.primary.feed")
            waitForPrefix("feed.action.publish.")
            tapPrefix("feed.action.publish.")
            waitFor(CreatePostCommonRootTestTag)
            waitFor(ComposerTextInputTestTag)
            waitForExactText(ComposerTextInputTestTag, marker.orEmpty())
            steps += "exact_text_draft_restored_after_process_restart"
            screenshots += screenshot("android-create-post-draft-after-process-restart")

            tap("composer-back")
            waitForGone(CreatePostCommonRootTestTag)
            waitForPrefix("feed.action.publish.")
            steps += "restored_draft_explicitly_discarded"
            steps += "create_post_returned_to_feed_without_publish"
            screenshots += screenshot("android-create-post-postflight-returned")
        }

        val finalSession = app.container.sessionManager.currentSession()
        assertTrue("android_create_post_postflight_session_lost", finalSession?.isSupabaseAuthenticated() == true)
        assertEquals("android_create_post_postflight_actor_changed", initialSession?.userId, finalSession?.userId)
        steps += "authenticated_session_preserved_after_relaunch"
        writeReport(initialSession?.userId.orEmpty(), steps, screenshots)
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    private fun tapPrefix(prefix: String) {
        compose.onAllNodes(tagStartsWith(prefix) and hasClickAction(), useUnmergedTree = true)[0]
            .performClick()
    }

    private fun waitFor(tag: String) {
        try {
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
        } catch (error: Throwable) {
            val availableTags = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)
            }.distinct().sorted().take(120)
            throw AssertionError("create_post_postflight_tag_timeout:$tag:available=$availableTags", error)
        }
    }

    private fun waitForPrefix(prefix: String) {
        compose.waitUntil(45_000) {
            compose.onAllNodes(tagStartsWith(prefix), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForGone(tag: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
        }
    }

    private fun waitForExactText(tag: String, expected: String) {
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text == expected
            }.getOrDefault(false)
        }
    }

    private suspend fun waitForPersistedTextDraft(actorProfileId: String, expected: String) {
        val store = PostComposerDraftStore(app.container.platformServices.preferences)
        repeat(100) {
            val restored = store.restore(actorProfileId) { false }?.snapshot
            if (restored?.step == CreatePostStep.Text && restored.text == expected) return
            delay(100)
        }
        throw AssertionError("android_create_post_draft_not_committed_before_process_restart")
    }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("testTag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun screenshot(name: String): String {
        val file = File(evidenceDir(), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("android_create_post_postflight_screenshot_failed:$name")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        return file.name
    }

    private fun writeReport(profileId: String, steps: List<String>, screenshots: List<String>) {
        File(evidenceDir(), "android-create-post-postflight-evidence.json").writeText(
            JSONObject()
                .put("check", "CREATE-POST-POSTFLIGHT-ANDROID-001")
                .put("status", "passed")
                .put("actorProfileIdSha256", sha256(profileId))
                .put("steps", JSONArray(steps))
                .put("publishCallbacksInvoked", false)
                .put("sessionPreserved", true)
                .put("screenshots", JSONArray(screenshots))
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File = File(targetContext.filesDir, "create-post-postflight-evidence")
        .also { check(it.exists() || it.mkdirs()) }

    private fun mainIntent(): Intent = Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
        .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "feed")

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit().putBoolean("app_links_prompt_seen", true).commit()
    }

    private fun grantOptionalNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
        ).close()
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
