package com.quata.core.startup

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.ui.components.QuataSplashRootTestTag
import com.quata.core.ui.components.QuataSplashScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class StartupSplashCommonInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val finishedCount = mutableIntStateOf(0)

    @Test
    fun sharedSplashRendersAndFinishesFromCommonCallback() {
        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                QuataSplashScreen(
                    onFinished = { finishedCount.intValue += 1 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithTag(QuataSplashRootTestTag, useUnmergedTree = true).fetchSemanticsNode()
        saveScreenshot("android-startup-splash")
        compose.mainClock.advanceTimeBy(4_500)
        compose.runOnIdle {
            check(finishedCount.intValue == 1) { "android_splash_common_callback_not_finished" }
        }

        writeReport(
            screenshots = listOf("android-startup-splash.png"),
            steps = listOf(
                "shared_splash_rendered_with_semantic_anchor",
                "shared_splash_finished_from_common_callback",
            ),
        )
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("android_screenshot_failed:$name")
        val file = File(evidenceDir(), "$name.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "android_screenshot_encode_failed:$name"
            }
        }
    }

    private fun writeReport(screenshots: List<String>, steps: List<String>) {
        File(evidenceDir(), "android-startup-splash-evidence.json").writeText(
            JSONObject()
                .put("check", "FLOW-SPLASH-STARTUP-ANDROID-001")
                .put("status", "passed")
                .put("steps", JSONArray(steps))
                .put("semanticAnchor", QuataSplashRootTestTag)
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("startup-splash-evidence")
            ?: File(instrumentation.targetContext.filesDir, "startup-splash-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}
