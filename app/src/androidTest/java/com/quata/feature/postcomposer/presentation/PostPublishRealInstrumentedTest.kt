package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PostPublishRealInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedUserPublishesTextPostFromCommonComposer() = runBlocking {
        val credentialsFile = optionalArgument("quataPostPublishCredentialsFile")
        val marker = optionalArgument("quataPostPublishMarker")
        assumeTrue(
            "POST-PUBLISH-ANDROID-REAL-001 is opt-in and requires local credentials plus marker.",
            !credentialsFile.isNullOrBlank() && !marker.isNullOrBlank(),
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        val safeMarker = marker.orEmpty()

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue(
            "Android must hold a real Supabase-authenticated session before publishing a post.",
            session?.isSupabaseAuthenticated() == true,
        )

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-publish-composer-opened")

            compose.onNodeWithTag("composer-type-text", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            val bodyText = "$safeMarker Publicacion reversible POST-PUBLISH Android"
            compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true)
                .performClick()
            compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true)
                .performTextReplacement(bodyText)
            compose.waitForIdle()
            saveScreenshot("android-post-publish-composer-filled")

            compose.onNodeWithTag(ComposerPublishButtonTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            saveScreenshot("android-post-publish-after-publish-tap")
            compose.waitUntil(90_000) {
                val markerVisible = runCatching {
                    compose.onNodeWithText(safeMarker, substring = true, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
                val composerClosed = runCatching {
                    compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure
                markerVisible || composerClosed
            }
            saveScreenshot("android-post-publish-published")
        }

        writeReport(safeMarker)
    }

    private fun mainIntent(): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "create_post")

    private fun saveScreenshot(name: String) {
        val file = File(evidenceDir(), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null) {
            check(device.takeScreenshot(file)) { "android_screenshot_failed:$name" }
        } else {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "android_screenshot_encode_failed:$name"
                }
            }
        }
    }

    private fun writeReport(marker: String) {
        File(evidenceDir(), "android-post-publish-evidence.json").writeText(
            JSONObject()
                .put("check", "POST-PUBLISH-ANDROID-REAL-001")
                .put("status", "passed")
                .put("marker", marker)
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "post-publish-evidence")
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
