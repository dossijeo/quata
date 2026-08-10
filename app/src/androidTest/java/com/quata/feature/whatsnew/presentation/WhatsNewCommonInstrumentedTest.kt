package com.quata.feature.whatsnew.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class WhatsNewCommonInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = EvidenceWhatsNewRepository()
    private val closeCount = mutableIntStateOf(0)
    private val hostGeneration = mutableIntStateOf(0)
    private val steps = mutableListOf<String>()

    @Test
    fun sharedWhatsNewRendersClosesAndDoesNotRepeatAfterSeen() {
        mountHost()
        compose.onNodeWithTag(WhatsNewRootTestTag, useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithTag("${WhatsNewPageTestTagPrefix}0", useUnmergedTree = true).fetchSemanticsNode()
        saveScreenshot("android-whats-new-page-0")

        compose.onNodeWithTag(WhatsNewNextTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("${WhatsNewPageTestTagPrefix}1", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-whats-new-page-1")

        compose.onNodeWithTag(WhatsNewNextTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { closeCount.intValue == 1 }
        steps += "whats_new_marked_seen_and_closed"

        remountHost()
        compose.waitUntil(5_000) { closeCount.intValue == 2 }
        steps += "whats_new_second_mount_closed_without_repeating"

        writeReport(
            name = "android-whats-new-common-evidence.json",
            check = "WHATS-NEW-ANDROID-COMMON-001",
            steps = steps,
            screenshots = listOf("android-whats-new-page-0.png", "android-whats-new-page-1.png"),
        )
    }

    private fun mountHost() {
        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                key(hostGeneration.intValue) {
                    WhatsNewScreenHost(
                        repository = repository,
                        installedVersionCode = 2,
                        languageTags = listOf("es-ES", "es"),
                        strings = whatsNewStrings(),
                        saveError = "No se pudo guardar.",
                        onClose = { closeCount.intValue += 1 },
                    )
                }
            }
        }
    }

    private fun remountHost() {
        compose.runOnIdle { hostGeneration.intValue += 1 }
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

    private fun writeReport(name: String, check: String, steps: List<String>, screenshots: List<String>) {
        File(evidenceDir(), name).writeText(
            JSONObject()
                .put("check", check)
                .put("status", "passed")
                .put("steps", JSONArray(steps))
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("whats-new-evidence")
            ?: File(instrumentation.targetContext.filesDir, "whats-new-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}

private class EvidenceWhatsNewRepository : WhatsNewRepository {
    private var lastSeenVersionCode: Long = 0
    private val releases = listOf(
        PendingRelease(
            releaseId = "qadata-whats-new-1",
            versionCode = 1,
            versionName = "1.0",
            localizedNote = "Primera novedad compartida para evidencia Android.",
            availableLanguageTags = setOf("es"),
        ),
        PendingRelease(
            releaseId = "qadata-whats-new-2",
            versionCode = 2,
            versionName = "1.1",
            localizedNote = "Segunda novedad compartida; al continuar queda marcada como vista.",
            availableLanguageTags = setOf("es"),
        ),
    )

    override suspend fun getPendingReleases(
        installedVersionCode: Long,
        languageTags: List<String>,
    ): Result<List<PendingRelease>> = Result.success(
        releases.filter { it.versionCode <= installedVersionCode && it.versionCode > lastSeenVersionCode },
    )

    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> =
        Result.success(releases.sortedByDescending(PendingRelease::versionCode))

    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> = Result.success(Unit)

    override suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long,
    ): Result<Unit> {
        lastSeenVersionCode = maxOf(lastSeenVersionCode, upToVersionCode)
        return Result.success(Unit)
    }
}

private fun whatsNewStrings(): WhatsNewStrings =
    WhatsNewStrings(
        title = "Novedades",
        previous = "Anterior",
        next = "Siguiente",
        continueLabel = "Continuar",
        version = { "Version $it" },
        versionHeading = { "Novedades de $it" },
    )
