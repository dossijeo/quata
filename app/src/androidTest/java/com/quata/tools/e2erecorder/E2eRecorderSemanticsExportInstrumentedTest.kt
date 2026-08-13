package com.quata.tools.e2erecorder

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.presentation.WhatsNewContent
import com.quata.feature.whatsnew.presentation.WhatsNewStrings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class E2eRecorderSemanticsExportInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun exportsWhatsNewSemanticsForRecorder() {
        val outName = arguments.getString("quataE2eRecorderOut") ?: "android-compose-semantics.json"
        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                WhatsNewContent(
                    releases = listOf(
                        PendingRelease(
                            releaseId = "e2e-recorder-whats-new",
                            versionCode = 1,
                            versionName = "1.0",
                            localizedNote = "Ruta estable para grabacion visual del recorder.",
                            availableLanguageTags = setOf("es"),
                        ),
                    ),
                    isCompleting = false,
                    strings = WhatsNewStrings(
                        title = "Novedades",
                        previous = "Anterior",
                        next = "Siguiente",
                        continueLabel = "Continuar",
                        version = { version -> "Version $version" },
                        versionHeading = { version -> "QÜATA $version" },
                    ),
                    onComplete = {},
                    onDismiss = {},
                    padding = PaddingValues(),
                )
            }
        }

        compose.waitForIdle()
        val nodes = compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = true)
            .map { node ->
                val config = node.config
                JSONObject()
                    .put("packageName", "com.quata")
                    .put("testTag", config.getOrNull(SemanticsProperties.TestTag))
                    .put("contentDescription", JSONArray(config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()))
                    .put("text", JSONArray(config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }))
                    .put("roleName", config.getOrNull(SemanticsProperties.Role)?.stableName())
                    .put("bounds", node.boundsInRoot.toJson())
            }

        val payload = JSONObject()
            .put("source", "compose-semantics")
            .put("screen", "whats-new")
            .put("capturedAt", System.currentTimeMillis())
            .put("children", JSONArray(nodes))

        val file = File(evidenceDir(), outName)
        file.writeText(payload.toString(2) + "\n")
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("e2e-recorder")
            ?: File(instrumentation.targetContext.filesDir, "e2e-recorder"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "e2e_recorder_evidence_directory_create_failed" } }
}

private fun Rect.toJson(): JSONObject =
    JSONObject()
        .put("x", left)
        .put("y", top)
        .put("width", width)
        .put("height", height)

private fun Role.stableName(): String = when (this) {
    Role.Button -> "Button"
    Role.Checkbox -> "Checkbox"
    Role.Switch -> "Switch"
    Role.RadioButton -> "RadioButton"
    Role.Tab -> "Tab"
    Role.Image -> "Image"
    Role.DropdownList -> "DropdownList"
    Role.ValuePicker -> "ValuePicker"
    else -> toString()
}
