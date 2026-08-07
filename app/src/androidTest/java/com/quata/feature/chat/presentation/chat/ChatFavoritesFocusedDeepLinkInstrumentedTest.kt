package com.quata.feature.chat.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.MainActivity
import com.quata.QuataApp
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ChatFavoritesFocusedDeepLinkInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun opensFavoritesSourceAndFocusedMessageWithRealSession() = runBlocking {
        val countryCode = optionalArgument("quataChatEvidenceCountryCode")
        val phone = optionalArgument("quataChatEvidencePhone")
        val password = optionalArgument("quataChatEvidencePassword")
        val credentialsFile = optionalArgument("quataChatEvidenceCredentialsFile")
        val favoritesUrl = optionalArgument("quataChatEvidenceFavoritesUrl")
        val focusedUrl = optionalArgument("quataChatEvidenceFocusedUrl")
        val markerProbe = optionalArgument("quataChatEvidenceMarkerProbe")
        val credentials = credentialsFile?.let(::credentialsFromFile)
        assumeTrue(
            "CHAT-FAVORITES-FOCUSED Android evidence is opt-in.",
            (credentials != null || listOf(countryCode, phone, password).all { !it.isNullOrBlank() }) &&
                listOf(favoritesUrl, focusedUrl, markerProbe).all { !it.isNullOrBlank() },
        )
        val safeCountryCode = credentials?.countryCode ?: countryCode.orEmpty()
        val safePhone = credentials?.phone ?: phone.orEmpty()
        val safePassword = credentials?.password ?: password.orEmpty()
        val safeFavoritesUrl = favoritesUrl.orEmpty()
        val safeFocusedUrl = focusedUrl.orEmpty()
        val safeMarkerProbe = markerProbe.orEmpty()

        suppressStartupPrompts()
        grantOptionalNotificationPermission()
        app.container.authRepository.login(safeCountryCode, safePhone, safePassword).getOrThrow()
        assertTrue(
            "The Android app must hold a real Supabase-authenticated session before opening Chat.",
            app.container.sessionManager.currentSession()?.isSupabaseAuthenticated() == true,
        )

        ActivityScenario.launch<MainActivity>(chatIntent(safeFavoritesUrl)).use {
            waitForMarker(safeMarkerProbe, "favorites route")
            saveScreenshot("android-favorites-list")
            device.findObject(By.textContains(safeMarkerProbe))?.click()
                ?: error("favorite_message_open_failed")
            waitForMarker(safeMarkerProbe, "source conversation")
            saveScreenshot("android-favorites-open-source")
        }

        ActivityScenario.launch<MainActivity>(chatIntent(safeFocusedUrl)).use {
            waitForMarker(safeMarkerProbe, "focused deep link")
            saveScreenshot("android-focused-message")
        }

        writeReport(
            JSONObject()
                .put("check", "CHAT-FAVORITES-FOCUSED-ANDROID-001")
                .put("status", "passed")
                .put("evidenceDirectory", evidenceDir().absolutePath),
        )
    }

    private fun chatIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url), targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun waitForMarker(markerProbe: String, context: String) {
        val visible = device.wait(Until.hasObject(By.textContains(markerProbe)), 45_000)
        assertTrue("The marker must be visible in $context.", visible)
    }

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
    }

    private fun writeReport(report: JSONObject) {
        File(evidenceDir(), "android-chat-favorites-focused-evidence.json")
            .writeText("${report.toString(2)}\n")
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "chat-favorites-focused-evidence")
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
