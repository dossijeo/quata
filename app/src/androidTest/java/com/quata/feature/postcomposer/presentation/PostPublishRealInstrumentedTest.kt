package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.quata.feature.postcomposer.imageeditor.PostImageEditorRootTestTag
import com.quata.feature.postcomposer.imageeditor.PostImageEditorResetTestTag
import com.quata.feature.postcomposer.imageeditor.PostImageEditorRotateTestTag
import com.quata.feature.postcomposer.imageeditor.PostImageEditorSaveTestTag
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportTestTag
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorMuteTestTag
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorPreviewTestTag
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorRootTestTag
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorTimelineTestTag
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
        val failOnce = optionalArgument("quataPostProgressRollbackFailOnce") == "1"
        val failAfterUpload = optionalArgument("quataPostStorageRollbackFailAfterUpload") == "1"
        assumeTrue(
            "POST-PUBLISH-ANDROID-REAL-001 is opt-in and requires local credentials plus marker.",
            !credentialsFile.isNullOrBlank() && !marker.isNullOrBlank() && !destinationWallId.isNullOrBlank(),
        )
        require(mode == "text" || mode == "image-location") { "unsupported_post_publish_mode:$mode" }
        require(!failAfterUpload || mode == "image-location") { "post_storage_rollback_requires_image_location_mode" }
        val evidenceImageUri = if (mode == "image-location") {
            require(!locationLabel.isNullOrBlank()) { "image-location mode requires quataPostPublishLocationLabel" }
            createImageLocationEvidenceUri()
        } else null
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        val safeMarker = marker.orEmpty()

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        grantOptionalLocationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue(
            "Android must hold a real Supabase-authenticated session before publishing a post.",
            session?.isSupabaseAuthenticated() == true,
        )

        ActivityScenario.launch<MainActivity>(
            mainIntent(evidenceImageUri, locationLabel, failOnce = failOnce, failAfterUpload = failAfterUpload),
        ).use {
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
                device.pressBack()
                device.waitForIdle(1_000)
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
            if (failOnce || failAfterUpload) {
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithTag(ComposerFeedbackErrorTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess &&
                        runCatching { compose.onNodeWithTag(ComposerFeedbackRetryTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
                }
                compose.onNodeWithTag(ComposerFeedbackErrorTestTag, useUnmergedTree = true)
                    .performScrollTo()
                compose.onNodeWithTag(ComposerFeedbackRetryTestTag, useUnmergedTree = true)
                    .performScrollTo()
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight - 360,
                    device.displayWidth / 2,
                    device.displayHeight / 2,
                    24,
                )
                device.waitForIdle(1_000)
                saveScreenshot(if (failAfterUpload) "android-post-storage-rollback-after-error" else "android-post-progress-rollback-after-error")
                compose.onNodeWithTag(ComposerFeedbackRetryTestTag, useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()
            }
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

    @Test
    fun authenticatedUserExercisesMediaSourceActionsFromCommonComposer() = runBlocking {
        val credentialsFile = optionalArgument("quataPostPublishCredentialsFile")
        val source = optionalArgument("quataPostComposerPickerSource")
        val outcome = optionalArgument("quataPostComposerPickerOutcome") ?: "success"
        assumeTrue(
            "POST-PICKER-CAMERA-ANDROID-REAL-001 is opt-in and requires local credentials plus source.",
            !credentialsFile.isNullOrBlank() && !source.isNullOrBlank(),
        )
        val pickerSource = source.orEmpty()
        require(pickerSource in setOf("gallery-image", "camera-image", "gallery-video", "camera-video")) {
            "unsupported_post_composer_picker_source:$pickerSource"
        }
        require(outcome in setOf("success", "cancelled", "failure", "unsupported")) {
            "unsupported_post_composer_picker_outcome:$outcome"
        }
        val fixturePath = if (outcome == "success") {
            if (pickerSource.endsWith("image")) {
                Uri.parse(createImageLocationEvidenceUri()).path ?: error("android_image_fixture_path_missing")
            } else {
                createVideoEvidenceFile().absolutePath
            }
        } else null
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        grantOptionalLocationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()

        ActivityScenario.launch<MainActivity>(
            mainIntent(
                pickerSource = pickerSource,
                pickerOutcome = outcome,
                pickerPath = fixturePath,
            ),
        ).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag("composer-picker-evidence-ready.$pickerSource.$outcome", useUnmergedTree = true)
                .fetchSemanticsNode()
            saveScreenshot("android-post-picker-camera-composer-opened")
            val typeTag = if (pickerSource.endsWith("image")) "composer-type-image" else "composer-type-video"
            compose.onNodeWithTag(typeTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            val actionTag = when (pickerSource) {
                "gallery-image" -> ComposerPickImageTestTag
                "camera-image" -> ComposerCaptureImageTestTag
                "gallery-video" -> ComposerPickVideoTestTag
                else -> ComposerCaptureVideoTestTag
            }
            compose.onNodeWithTag(actionTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitForIdle()
            saveScreenshot("android-post-picker-camera-after-tap-$pickerSource-$outcome")
            val selectedTag = if (pickerSource.endsWith("image")) ComposerSelectedImagePreviewTestTag else ComposerSelectedVideoPreviewTestTag
            if (outcome == "success") {
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithTag(selectedTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
                }
            } else {
                compose.waitUntil(5_000) {
                    runCatching { compose.onNodeWithTag(selectedTag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
                }
            }
            saveScreenshot("android-post-picker-camera-after-action-$pickerSource-$outcome")
        }

        writePickerReport(pickerSource, outcome)
    }

    @Test
    fun authenticatedUserExercisesPostImageEditorFromCommonComposer() = runBlocking {
        val credentialsFile = optionalArgument("quataPostPublishCredentialsFile")
        assumeTrue(
            "POST-IMAGE-EDITOR-ANDROID-REAL-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataPostImageEditorEvidence") == "1",
        )
        val fixturePath = Uri.parse(createImageLocationEvidenceUri()).path ?: error("android_image_fixture_path_missing")
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        grantOptionalLocationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()

        ActivityScenario.launch<MainActivity>(
            mainIntent(
                pickerSource = "gallery-image",
                pickerOutcome = "success",
                pickerPath = fixturePath,
            ),
        ).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-image-editor-composer-opened")
            compose.onNodeWithTag("composer-type-image", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.onNodeWithTag(ComposerPickImageTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(ComposerSelectedImagePreviewTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-image-editor-image-selected")
            compose.onNodeWithTag(ComposerEditImageTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-image-editor-opened")
            if (runCatching { compose.onNodeWithText("Cancelar", useUnmergedTree = true).performClick() }.isFailure) {
                compose.onNodeWithText("Cancel", useUnmergedTree = true)
                    .performClick()
            }
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(ComposerSelectedImagePreviewTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess &&
                    runCatching { compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
            }
            saveScreenshot("android-post-image-editor-after-cancel")
            compose.onNodeWithTag(ComposerEditImageTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-image-editor-reopened")
            compose.onAllNodesWithTag(PostImageEditorRotateTestTag, useUnmergedTree = true)
                .filterToOne(hasClickAction())
                .performClick()
            compose.onAllNodesWithTag(PostImageEditorResetTestTag, useUnmergedTree = true)
                .filterToOne(hasClickAction())
                .performClick()
            compose.onAllNodesWithTag(PostImageEditorSaveTestTag, useUnmergedTree = true)
                .filterToOne(hasClickAction())
                .performClick()
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithTag(ComposerSelectedImagePreviewTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess &&
                    runCatching { compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
            }
            saveScreenshot("android-post-image-editor-saved-preview")
        }

        writePickerReport("image-editor", "success")
    }

    @Test
    fun authenticatedUserExercisesPostVideoEditorFromCommonComposer() = runBlocking {
        val credentialsFile = optionalArgument("quataPostPublishCredentialsFile")
        val fixturePath = optionalArgument("quataPostVideoEditorFixturePath")
        assumeTrue(
            "POST-VIDEO-EDITOR-ANDROID-REAL-001 is opt-in and requires local credentials plus a valid MP4 fixture.",
            !credentialsFile.isNullOrBlank() &&
                optionalArgument("quataPostVideoEditorEvidence") == "1" &&
                !fixturePath.isNullOrBlank(),
        )
        require(File(fixturePath.orEmpty()).isFile) { "android_video_fixture_missing:$fixturePath" }
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        grantOptionalLocationPermission()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()

        ActivityScenario.launch<MainActivity>(
            mainIntent(
                pickerSource = "gallery-video",
                pickerOutcome = "success",
                pickerPath = fixturePath,
            ),
        ).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(CreatePostCommonRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-video-editor-composer-opened")
            compose.onNodeWithTag("composer-type-video", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.onNodeWithTag(ComposerPickVideoTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(ComposerSelectedVideoPreviewTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-post-video-editor-video-selected")
            compose.onNodeWithTag(ComposerEditVideoTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithTag(PostVideoEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag(PostVideoEditorPreviewTestTag, useUnmergedTree = true).fetchSemanticsNode()
            compose.onNodeWithTag(PostVideoEditorTimelineTestTag, useUnmergedTree = true).fetchSemanticsNode()
            compose.onAllNodesWithTag(PostVideoEditorMuteTestTag, useUnmergedTree = true)
                .filterToOne(hasClickAction())
                .performClick()
            saveScreenshot("android-post-video-editor-opened")
            compose.onAllNodesWithTag(PostVideoEditorExportTestTag, useUnmergedTree = true)
                .filterToOne(hasClickAction())
                .performClick()
            compose.waitUntil(120_000) {
                runCatching { compose.onNodeWithTag(ComposerSelectedVideoPreviewTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess &&
                    runCatching { compose.onNodeWithTag(PostVideoEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isFailure
            }
            saveScreenshot("android-post-video-editor-exported-preview")
        }

        writePickerReport("video-editor", "success")
    }

    private fun mainIntent(
        evidenceImageUri: String? = null,
        evidenceLocationLabel: String? = null,
        pickerSource: String? = null,
        pickerOutcome: String? = null,
        pickerPath: String? = null,
        failOnce: Boolean = false,
        failAfterUpload: Boolean = false,
    ): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "create_post")
            .putExtra("com.quata.extra.POST_PROGRESS_ROLLBACK_FAIL_ONCE_FOR_EVIDENCE", failOnce)
            .putExtra("com.quata.extra.POST_STORAGE_ROLLBACK_FAIL_AFTER_UPLOAD_FOR_EVIDENCE", failAfterUpload)
            .apply {
                evidenceImageUri?.let { putExtra("com.quata.extra.POST_PUBLISH_EVIDENCE_IMAGE_URI", it) }
                evidenceLocationLabel?.let { putExtra("com.quata.extra.POST_PUBLISH_EVIDENCE_LOCATION_LABEL", it) }
                pickerSource?.let { putExtra("com.quata.extra.POST_COMPOSER_PICKER_EVIDENCE_SOURCE", it) }
                pickerOutcome?.let { putExtra("com.quata.extra.POST_COMPOSER_PICKER_EVIDENCE_OUTCOME", it) }
                pickerPath?.let { putExtra("com.quata.extra.POST_COMPOSER_PICKER_EVIDENCE_PATH", it) }
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

    private fun writePickerReport(source: String, outcome: String) {
        File(evidenceDir(), "android-post-picker-camera-evidence.json").writeText(
            JSONObject()
                .put("check", "POST-PICKER-CAMERA-ANDROID-REAL-001")
                .put("status", "passed")
                .put("source", source)
                .put("outcome", outcome)
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

    private fun createVideoEvidenceFile(): File =
        File(targetContext.filesDir, "post-picker-camera-evidence-video.mp4").also { file ->
            if (!file.exists()) {
                file.writeBytes(ByteArray(32) { index -> (index * 7).toByte() })
            }
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

    private fun grantOptionalLocationPermission() {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).forEach { permission ->
            if (targetContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                instrumentation.uiAutomation.executeShellCommand("pm grant ${targetContext.packageName} $permission")
                    .close()
            }
        }
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
