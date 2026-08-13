package com.quata.feature.profile.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.assetName
import com.quata.core.ui.components.QuataLegalDocumentLinkTestTagPrefix
import com.quata.feature.settings.presentation.SettingsLegalDocumentsSectionContent
import com.quata.feature.settings.presentation.SettingsLegalDocumentsStrings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ProfileLegalDocumentsInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun accountLegalDocumentsUseSharedSectionAndResolveLocalAssets() {
        val openedLegalDocuments = mutableListOf<String>()

        compose.setContent {
            QuataTheme {
                SettingsLegalDocumentsSectionContent(
                    language = QuataLanguage.Spanish,
                    strings = SettingsLegalDocumentsStrings(title = "Documentos legales"),
                    onOpenDocument = { document ->
                        openedLegalDocuments += document.assetName(QuataLanguage.Spanish)
                    },
                )
            }
        }

        compose.onNodeWithTag("${QuataLegalDocumentLinkTestTagPrefix}privacy", useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.onNodeWithTag("${QuataLegalDocumentLinkTestTagPrefix}childsafety", useUnmergedTree = true)
            .performTouchInput { click(center) }

        check(openedLegalDocuments == listOf("privacy_es.docx", "child_safety_es.docx")) {
            "android_account_legal_document_open_mismatch:$openedLegalDocuments"
        }
        saveScreenshot("android-account-legal-documents")
        writeReport(openedLegalDocuments)
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

    private fun writeReport(openedLegalDocuments: List<String>) {
        File(evidenceDir(), "android-account-legal-documents-evidence.json").writeText(
            JSONObject()
                .put("check", "ACCOUNT-LEGAL-DOCUMENTS-ANDROID-COMMON-001")
                .put("status", "passed")
                .put("steps", JSONArray(listOf("shared_settings_legal_section_clicked_both_documents")))
                .put("screenshots", JSONArray(listOf("android-account-legal-documents.png")))
                .put("openedLegalDocuments", JSONArray(openedLegalDocuments))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("account-legal-documents-evidence")
            ?: File(instrumentation.targetContext.filesDir, "account-legal-documents-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}
