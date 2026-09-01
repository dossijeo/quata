package com.quata.core.moderation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.localization.QuataLanguage
import com.quata.core.ui.components.QuataLegalDocumentLinkTestTagPrefix
import com.quata.core.ui.components.QuataLegalDocumentLinksColumnContent
import com.quata.core.ui.components.QuataUgcTermsAcceptTestTag
import com.quata.core.ui.components.QuataUgcTermsBodyTestTag
import com.quata.core.ui.components.QuataUgcTermsDialogTestTag
import com.quata.core.ui.components.QuataUgcTermsGateContent
import com.quata.core.ui.components.QuataUgcTermsLogoutTestTag
import com.quata.core.ui.components.quataUgcTermsStrings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class UgcTermsGateCommonInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sharedUgcTermsGateBlocksShowsLegalLinksAndAccepts() {
        val gateway = FixtureUgcTermsGateway()
        val openedDocuments = mutableListOf<LegalDocument>()
        val acceptedStates = mutableListOf<Boolean?>()
        var logoutRequests = 0

        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                QuataUgcTermsGateContent(
                    profileId = "android-ugc-terms-fixture",
                    gateway = gateway,
                    strings = quataUgcTermsStrings(QuataLanguage.Spanish),
                    onAcceptedStateChanged = { acceptedStates += it },
                    onLogout = { logoutRequests += 1 },
                    legalLinks = {
                        QuataLegalDocumentLinksColumnContent(
                            language = QuataLanguage.Spanish,
                            documents = listOf(LegalDocument.ChildSafety, LegalDocument.Privacy),
                            onOpenDocument = { openedDocuments += it },
                        )
                    },
                )
            }
        }

        compose.waitUntil(5_000) { gateway.remoteChecks == 1 }
        compose.onNodeWithTag(QuataUgcTermsDialogTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(QuataUgcTermsBodyTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(QuataUgcTermsLogoutTestTag, useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("android-ugc-terms-required")

        compose.onNodeWithTag("${QuataLegalDocumentLinkTestTagPrefix}childsafety", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("${QuataLegalDocumentLinkTestTagPrefix}privacy", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(LegalDocument.ChildSafety, LegalDocument.Privacy), openedDocuments)

        compose.onNodeWithTag(QuataUgcTermsAcceptTestTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(5_000) { gateway.acceptAttempts == 1 && acceptedStates.lastOrNull() == true }
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag(QuataUgcTermsDialogTestTag, useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isFailure
        }
        saveScreenshot("android-ugc-terms-accepted")

        assertEquals(0, logoutRequests)
        assertTrue(gateway.accepted)
        writeReport(gateway, openedDocuments)
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

    private fun writeReport(gateway: FixtureUgcTermsGateway, openedDocuments: List<LegalDocument>) {
        File(evidenceDir(), "android-ugc-terms-evidence.json").writeText(
            JSONObject()
                .put("check", "UGC-TERMS-ANDROID-COMMON-001")
                .put("status", "passed")
                .put("remoteChecks", gateway.remoteChecks)
                .put("acceptAttempts", gateway.acceptAttempts)
                .put("openedDocuments", JSONArray(openedDocuments.map { it.name }))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("ugc-terms-evidence")
            ?: File(instrumentation.targetContext.filesDir, "ugc-terms-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}

private class FixtureUgcTermsGateway : UgcTermsGateway {
    var accepted = false
    var remoteChecks = 0
    var acceptAttempts = 0

    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> {
        remoteChecks += 1
        return Result.success(accepted)
    }

    override suspend fun acceptTerms(version: String): Result<Unit> {
        acceptAttempts += 1
        accepted = true
        return Result.success(Unit)
    }
}
