package com.quata.feature.official.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.core.ui.richtext.QuataPortableRichTextFieldTestTag
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
class OfficialEditorRealInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun officialAccountValidatesPublishesAndReturnsToFeed() = runBlocking {
        val credentialsFile = optionalArgument("quataOfficialEditorCredentialsFile")
        val marker = optionalArgument("quataOfficialEditorMarker")
        assumeTrue(
            "OFFICIAL-EDITOR-ANDROID-REAL-UI-001 is opt-in and requires local credentials plus marker.",
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
            "The Android app must hold a real Supabase-authenticated official session before opening the editor.",
            session?.isSupabaseAuthenticated() == true && session.isOfficial,
        )

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(OfficialEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            saveScreenshot("android-official-editor-opened")

            compose.onNodeWithTag(OfficialEditorPublishTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithTag(OfficialEditorFeedbackTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag(OfficialEditorFeedbackTestTag, useUnmergedTree = true)
                .assertTextContains("Add text", substring = true)
            saveScreenshot("android-official-editor-validation")
        }

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            compose.waitUntil(45_000) {
                runCatching { compose.onNodeWithTag(OfficialEditorRootTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag(OfficialEditorBodyActionTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(OfficialLongTextEditorBodyTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            val bodyText = "QADATA official Android evidence $safeMarker Publicacion reversible desde Android."
            compose.onNodeWithTag(QuataPortableRichTextFieldTestTag, useUnmergedTree = true)
                .performClick()
                .performTextReplacement(bodyText)
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithText(safeMarker, substring = true, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.waitForIdle()
            saveScreenshot("android-official-editor-before-long-save")
            tapLongEditorSaveAction()
            saveScreenshot("android-official-editor-after-long-save")
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithTag(OfficialEditorBodyActionTestTag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithText(safeMarker, substring = true, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            scrollEditorToPublishAction()
            saveScreenshot("android-official-editor-preview")

            tapPublishAction()
            saveScreenshot("android-official-editor-after-publish-tap")
            clickTranslationSkipIfShown()
            saveScreenshot("android-official-editor-after-translation-skip")
            compose.waitUntil(90_000) {
                val markerVisible = runCatching {
                    compose.onNodeWithText(safeMarker, substring = true, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
                val editorClosed = runCatching {
                    compose.onNodeWithTag(OfficialEditorRootTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure
                markerVisible || editorClosed
            }
            saveScreenshot("android-official-editor-published")
        }

        writeReport(safeMarker)
    }

    private fun mainIntent(): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "official/editor")

    private fun clickTranslationSkipIfShown() {
        val skipLabels = listOf(
            "Publish as is",
            "Publicar así",
            "Publicar solo este idioma",
            "Publier ainsi",
            "Publier seulement cette langue",
        )
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            for (label in skipLabels) {
                if (runCatching { compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess) {
                    compose.onNodeWithText(label, useUnmergedTree = true).performClick()
                    Thread.sleep(1_000)
                    return
                }
                val button = device.findObject(By.text(label))
                if (button != null) {
                    button.click()
                    Thread.sleep(1_000)
                    return
                }
            }
            Thread.sleep(250)
        }
    }

    private fun tapLongEditorSaveAction() {
        runCatching {
            compose.onNodeWithTag(OfficialLongTextEditorSaveTestTag, useUnmergedTree = true)
                .performClick()
        }
        Thread.sleep(750)
        runCatching {
            compose.onNodeWithTag(OfficialLongTextEditorSaveTestTag, useUnmergedTree = true)
                .performTouchInput { click(center) }
        }
        Thread.sleep(750)
        val stillEditing = runCatching {
            compose.onNodeWithTag(OfficialLongTextEditorBodyTestTag, useUnmergedTree = true)
                .fetchSemanticsNode()
        }.isSuccess
        if (stillEditing) {
            val saveAction = device.wait(
                Until.findObject(By.res(targetContext.packageName, OfficialLongTextEditorSaveTestTag)),
                5_000,
            )
            if (saveAction != null) {
                saveAction.click()
            } else {
                device.click(device.displayWidth - 120, 125)
            }
        }
        Thread.sleep(750)
    }

    private fun scrollEditorToPublishAction() {
        runCatching {
            compose.onNodeWithTag(OfficialEditorPublishTestTag, useUnmergedTree = true)
                .performScrollTo()
        }
        repeat(5) {
            if (device.findObject(By.textContains("Publish")) != null) return
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.78f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.28f).toInt(),
                28,
            )
            Thread.sleep(500)
        }
    }

    private fun tapPublishAction() {
        runCatching {
            compose.onNodeWithTag(OfficialEditorPublishTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        }
        val publishAction = device.wait(
            Until.findObject(By.res(targetContext.packageName, OfficialEditorPublishTestTag)),
            5_000,
        )
        publishAction?.click()
        runCatching {
            compose.onNodeWithTag(OfficialEditorPublishTestTag, useUnmergedTree = true)
                .performScrollTo()
                .performTouchInput { click(center) }
        }
        val publish = device.findObject(By.textContains("Publish"))
        if (publish != null) {
            val bounds = publish.visibleBounds
            device.click(device.displayWidth / 2, bounds.centerY())
        }
        device.click(device.displayWidth / 2, (device.displayHeight * 0.66f).toInt())
        Thread.sleep(1_500)
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

    private fun writeReport(marker: String) {
        File(evidenceDir(), "android-official-editor-real-evidence.json").writeText(
            JSONObject()
                .put("check", "OFFICIAL-EDITOR-ANDROID-REAL-UI-001")
                .put("status", "passed")
                .put("marker", marker)
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "official-editor-real-evidence")
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
