package com.quata.feature.whatsnew.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.ui.components.QuataAboutBodyTestTag
import com.quata.core.ui.components.QuataAboutCloseTestTag
import com.quata.core.ui.components.QuataAboutDialogContent
import com.quata.core.ui.components.QuataAboutReleaseHistoryTestTag
import com.quata.core.ui.components.QuataAboutRootTestTag
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
class AboutReleaseHistoryCommonBridgeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = FixtureWhatsNewRepository()
    private val steps = mutableListOf<String>()

    @Test
    fun aboutDialogOpensSharedReleaseHistoryAndNavigatesPages() {
        var showingHistory by mutableStateOf(false)
        var dismissed by mutableStateOf(false)

        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                if (showingHistory) {
                    ReleaseHistoryContent(
                        repository = repository,
                        languageTags = listOf("es-ES"),
                        strings = fixtureStrings(),
                        onBack = {
                            showingHistory = false
                            dismissed = true
                        },
                    )
                } else {
                    QuataAboutDialogContent(
                        title = "Acerca de QUATA",
                        version = "Version 999",
                        versionDate = "2026-08-10",
                        body = "Evidencia comun de About para Android.",
                        releaseHistoryLabel = "Historial",
                        closeLabel = "Cerrar",
                        onDismiss = { dismissed = true },
                        onOpenReleaseHistory = {
                            steps += "about_callback_opened_release_history"
                            showingHistory = true
                        },
                        legalLinks = {
                            TextButton(onClick = {}) { Text("Privacidad") }
                        },
                    )
                }
            }
        }

        for (tag in listOf(QuataAboutRootTestTag, QuataAboutBodyTestTag, QuataAboutReleaseHistoryTestTag, QuataAboutCloseTestTag)) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        }
        saveScreenshot("android-about-common")

        compose.onNodeWithTag(QuataAboutReleaseHistoryTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag(ReleaseHistoryRootTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag("${ReleaseHistoryPageTestTagPrefix}0", useUnmergedTree = true).fetchSemanticsNode()
        saveScreenshot("android-about-to-release-history")

        compose.onNodeWithTag(ReleaseHistoryNextTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("${ReleaseHistoryPageTestTagPrefix}1", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-release-history-next")

        compose.onNodeWithTag(ReleaseHistoryPreviousTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("${ReleaseHistoryPageTestTagPrefix}0", useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag(ReleaseHistoryCloseTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { dismissed }
        steps += "release_history_close_returned"

        writeReport(
            name = "android-about-release-history-common-evidence.json",
            check = "ABOUT-RELEASE-HISTORY-ANDROID-COMMON-001",
            steps = steps,
            screenshots = listOf(
                "android-about-common.png",
                "android-about-to-release-history.png",
                "android-release-history-next.png",
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
        (instrumentation.targetContext.getExternalFilesDir("about-release-history-evidence")
            ?: File(instrumentation.targetContext.filesDir, "about-release-history-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}

private class FixtureWhatsNewRepository : WhatsNewRepository {
    override suspend fun getPendingReleases(
        installedVersionCode: Long,
        languageTags: List<String>,
    ): Result<List<PendingRelease>> = Result.success(emptyList())

    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> = Result.success(
        listOf(
            PendingRelease(
                releaseId = "qadata-release-history-1",
                versionCode = 1001,
                versionName = "1.0.1",
                localizedNote = "Primera pagina de evidencia comun About e Historial.",
                availableLanguageTags = setOf("es-ES"),
            ),
            PendingRelease(
                releaseId = "qadata-release-history-2",
                versionCode = 1002,
                versionName = "1.0.2",
                localizedNote = "Segunda pagina para comprobar siguiente y anterior.",
                availableLanguageTags = setOf("es-ES"),
            ),
        ),
    )

    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> = Result.success(Unit)

    override suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long,
    ): Result<Unit> = Result.success(Unit)
}

private fun fixtureStrings(): ReleaseHistoryStrings =
    ReleaseHistoryStrings(
        close = "Cerrar",
        empty = "Sin versiones.",
        error = "No se pudo cargar el historial.",
        title = "Historial de versiones",
        subtitle = "Evidencia Android comun",
        previous = "Anterior",
        next = "Siguiente",
        version = { "Version $it" },
        versionHeading = { "Novedades de $it" },
    )
