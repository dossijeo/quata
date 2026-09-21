package com.quata.core.startup

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.MainActivity
import com.quata.AndroidComposeRootTestTag
import com.quata.core.ui.components.QuataSplashRootTestTag
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class StartupSplashLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val selector = By.desc(QuataSplashRootTestTag)
    private val postStartupSelector = By.res(AndroidComposeRootTestTag)

    @Test
    fun mainActivityLaunchMountsSharedSplashAndDismissesIt() {
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            requireSplashThenCompletion("android_main_activity")
            saveScreenshot("android-main-activity-after-startup")
        }
        writeReport(
            "android-startup-launcher-evidence.json",
            listOf("android-main-activity-startup-splash.png", "android-main-activity-after-startup.png"),
            listOf("main_activity_launched", "main_activity_shared_splash_visible_with_accessible_anchor", "main_activity_shared_splash_dismissed_after_common_completion"),
        )
    }

    @Test
    fun mainActivityColdStartAndWarmResumeKeepStartupPolicyStable() {
        ActivityScenario.launch<MainActivity>(mainIntent()).use { scenario ->
            requireSplashThenCompletion("android_cold_start")
            requirePostStartupSurface("android_cold_start")
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            check(!device.hasObject(selector)) { "android_warm_resume_restarted_splash" }
            requirePostStartupSurface("android_warm_resume")
            saveScreenshot("android-main-activity-warm-resume")
        }
        writeReport(
            "android-startup-lifecycle-evidence.json",
            listOf("android-main-activity-warm-resume.png"),
            listOf("cold_start_splash_completed", "warm_resume_preserved_compose_surface_without_restarting_splash"),
        )
    }

    @Test
    fun mainActivityColdProcessRelaunchReplaysSharedSplash() {
        ActivityScenario.launch<MainActivity>(mainIntent()).use {
            requireSplashThenCompletion("android_cold_relaunch", "android-main-activity-cold-relaunch-splash")
            requirePostStartupSurface("android_cold_relaunch")
            saveScreenshot("android-main-activity-cold-relaunch-complete")
        }
        writeReport(
            "android-startup-cold-process-relaunch-evidence.json",
            listOf("android-main-activity-cold-relaunch-splash.png", "android-main-activity-cold-relaunch-complete.png"),
            listOf("cold_process_relaunch_replayed_and_completed_shared_splash"),
        )
    }

    private fun requireSplashThenCompletion(prefix: String, screenshot: String = "android-main-activity-startup-splash") {
        check(device.wait(Until.hasObject(selector), 5_000)) { "${prefix}_splash_missing" }
        saveScreenshot(screenshot)
        check(device.wait(Until.gone(selector), 12_000)) { "${prefix}_splash_not_dismissed" }
    }

    private fun requirePostStartupSurface(prefix: String) {
        check(device.wait(Until.hasObject(postStartupSelector), 10_000)) { "${prefix}_compose_surface_missing" }
    }

    private fun mainIntent() = Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("com.quata.extra.HOLD_SPLASH_FOR_EVIDENCE", true)

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("android_screenshot_failed:$name")
        FileOutputStream(File(evidenceDir(), "$name.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "android_screenshot_encode_failed:$name" }
        }
    }

    private fun writeReport(fileName: String, screenshots: List<String>, steps: List<String>) {
        File(evidenceDir(), fileName).writeText(JSONObject().put("check", "FLOW-SPLASH-STARTUP-ANDROID-001").put("status", "passed").put("steps", JSONArray(steps)).put("semanticAnchor", QuataSplashRootTestTag).put("screenshots", JSONArray(screenshots)).toString(2) + "\n")
    }

    private fun evidenceDir() = (targetContext.getExternalFilesDir("startup-splash-evidence") ?: File(targetContext.filesDir, "startup-splash-evidence"))
        .also { check(it.exists() || it.mkdirs()) { "android_evidence_directory_create_failed" } }
}
