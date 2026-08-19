package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
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
        val destinationWallId = optionalArgument("quataPostPublishDestinationWallId")
        val mode = optionalArgument("quataPostPublishMode") ?: "text"
        val locationLabel = optionalArgument("quataPostPublishLocationLabel")
        assumeTrue(
            "POST-PUBLISH-ANDROID-REAL-001 is opt-in and requires local credentials plus marker.",
            !credentialsFile.isNullOrBlank() && !marker.isNullOrBlank() && !destinationWallId.isNullOrBlank(),
        )
        require(mode == "text" || mode == "image-location") { "unsupported_post_publish_mode:$mode" }
        val evidenceImageUri = if (mode == "image-location") {
            require(!locationLabel.isNullOrBlank()) { "image-location mode requires quataPostPublishLocationLabel" }
            createImageLocationEvidenceUri()
        } else null
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

        ActivityScenario.launch<MainActivity>(mainIntent(evidenceImageUri, locationLabel)).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-publish-composer-opened")

            if (mode == "text") {
                compose.onNodeWithTag("composer-type-text", useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
                }
            } else {
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithTag(ComposerLocationValueTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
                }
                compose.onNodeWithTag(ComposerLocationSectionTestTag, useUnmergedTree = true)
                    .performScrollTo()
            }
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag("composer-destination-option.$destinationWallId", useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag("composer-destination-option.$destinationWallId", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            if (mode == "text") {
                val bodyText = "$safeMarker Publicacion reversible POST-PUBLISH Android"
                compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true)
                    .performClick()
                compose.onNodeWithTag(ComposerTextInputTestTag, useUnmergedTree = true)
                    .performTextReplacement(bodyText)
            } else {
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithTag(ComposerLocationValueTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
                }
            }
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

        writeReport(safeMarker, destinationWallId.orEmpty())
    }

    private fun mainIntent(evidenceImageUri: String? = null, evidenceLocationLabel: String? = null): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "create_post")
            .apply {
                evidenceImageUri?.let { putExtra("com.quata.extra.POST_PUBLISH_EVIDENCE_IMAGE_URI", it) }
                evidenceLocationLabel?.let { putExtra("com.quata.extra.POST_PUBLISH_EVIDENCE_LOCATION_LABEL", it) }
            }

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

    private fun writeReport(marker: String, destinationWallId: String) {
        File(evidenceDir(), "android-post-publish-evidence.json").writeText(
            JSONObject()
                .put("check", "POST-PUBLISH-ANDROID-REAL-001")
                .put("status", "passed")
                .put("marker", marker)
                .put("destinationWallId", destinationWallId)
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "post-publish-evidence")
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed:${dir.absolutePath}" } }

    private fun createImageLocationEvidenceUri(): String {
        val file = File(targetContext.filesDir, "post-publish-evidence-image.png")
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    bitmap.setPixel(x, y, if ((x + y) % 2 == 0) Color.rgb(252, 132, 43) else Color.rgb(26, 68, 168))
                }
            }
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "android_evidence_png_encode_failed" }
            }
            bitmap.recycle()
        }
        return Uri.fromFile(file).toString()
    }

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
