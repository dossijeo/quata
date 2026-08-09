package com.quata.feature.official.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.quata.MainActivity
import com.quata.QuataApp
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

@RunWith(AndroidJUnit4::class)
class OfficialEditorPermissionInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun nonOfficialAccountCannotOpenOfficialEditor() = runBlocking {
        val credentialsFile = optionalArgument("quataOfficialEditorCredentialsFile")
        val expectIneligible = optionalArgument("quataOfficialEditorExpectIneligible") == "1"
        assumeTrue(
            "OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && expectIneligible,
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue(
            "The Android app must hold a real Supabase-authenticated session before checking Official editor permissions.",
            session?.isSupabaseAuthenticated() == true,
        )
        assertFalse(
            "The Android permission evidence session must not be official.",
            session?.isOfficial == true,
        )

        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag(OfficialEditorRootTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure
            }
            assertTrue(
                "A non-official Android session must not mount the common Official editor root.",
                runCatching {
                    compose.onNodeWithTag(OfficialEditorRootTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure,
            )
            assertTrue(
                "A non-official Android session must not expose the Official create CTA.",
                runCatching {
                    compose.onNodeWithTag(OfficialCreateActionTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure,
            )
            saveScreenshot("android-official-editor-ineligible-blocked")
        }

        writeReport()
    }

    private fun mainIntent(): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "official/editor")

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

    private fun writeReport() {
        File(evidenceDir(), "android-official-editor-permissions-evidence.json").writeText(
            JSONObject()
                .put("check", "OFFICIAL-EDITOR-ANDROID-PERMISSIONS-001")
                .put("status", "passed")
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
