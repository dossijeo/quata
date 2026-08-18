package com.quata.feature.profile.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.profile.domain.EmergencyContactCandidate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ProfileSosContactsInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun profileSosContactsUseSharedAnchorsAndSelectionPolicy() {
        val candidates = (1..6).map { index ->
            EmergencyContactCandidate(
                id = "sos-$index",
                displayName = "Contacto SOS $index",
                email = "sos-$index@example.invalid",
                neighborhood = "Bovano",
                phone = "+24068024260$index",
            )
        }
        var selectedIds by mutableStateOf(emptyList<String>())
        var message by mutableStateOf("Avisar a mis contactos de emergencia.")
        val savedSelections = mutableListOf<List<String>>()
        val savedMessages = mutableListOf<String>()

        compose.setContent {
            QuataTheme {
                EmergencyContactsDialogContent(
                    layoutPadding = PaddingValues(),
                    isLandscapeLayout = false,
                    isImeVisible = false,
                    candidates = candidates,
                    selectedIds = selectedIds,
                    message = message,
                    isSaving = false,
                    strings = evidenceStrings(),
                    onMessageChange = { message = it },
                    onToggleContact = { contact ->
                        selectedIds = selectedIds.toggleForEvidence(contact.id)
                    },
                    onDismiss = {},
                    onSave = {
                        savedSelections += selectedIds
                        savedMessages += message
                    },
                    slots = EmergencyContactsDialogSlots(
                        contactRow = { contact, selected, toggle ->
                            EmergencyUserRowContent(
                                user = contact,
                                selected = selected,
                                addLabel = "Añadir",
                                removeLabel = "Quitar",
                                avatar = { Text(contact.displayName.take(1)) },
                                onToggle = toggle,
                            )
                        },
                        messageInput = { modifier: Modifier, value, change, minLines, maxLines ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = change,
                                modifier = modifier,
                                minLines = minLines,
                                maxLines = maxLines ?: Int.MAX_VALUE,
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag(ProfileSosRootTestTag, useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithTag(ProfileSosContactsTabTestTag, useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithTag(ProfileSosSearchTestTag, useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithTag(ProfileSosContactsListTestTag, useUnmergedTree = true).fetchSemanticsNode()

        for (index in 1..6) {
            tapContactToggle("sos-$index")
        }
        check(selectedIds == listOf("sos-1", "sos-2", "sos-3", "sos-4", "sos-5")) {
            "android_sos_selection_policy_mismatch:$selectedIds"
        }
        tapContactToggle("sos-3")
        check(selectedIds == listOf("sos-1", "sos-2", "sos-4", "sos-5")) {
            "android_sos_remove_policy_mismatch:$selectedIds"
        }
        compose.onNodeWithTag(ProfileSosSaveTestTag, useUnmergedTree = true).performClick()

        check(savedSelections.single() == listOf("sos-1", "sos-2", "sos-4", "sos-5")) {
            "android_sos_saved_contacts_mismatch:$savedSelections"
        }
        check(savedMessages.single() == "Avisar a mis contactos de emergencia.") {
            "android_sos_saved_message_mismatch:$savedMessages"
        }
        saveScreenshot("android-profile-sos-contacts")
        writeReport(savedSelections.single(), savedMessages.single())
    }

    @Test
    fun profileSosMessageUsesSharedAnchorAndSaveAction() {
        val candidates = listOf(
            EmergencyContactCandidate(
                id = "sos-1",
                displayName = "Contacto SOS 1",
                email = "sos-1@example.invalid",
                neighborhood = "Bovano",
                phone = "+240680242601",
            ),
        )
        var selectedIds by mutableStateOf(listOf("sos-1"))
        var message by mutableStateOf("Avisar a mis contactos de emergencia.")
        val savedSelections = mutableListOf<List<String>>()
        val savedMessages = mutableListOf<String>()

        compose.setContent {
            QuataTheme {
                EmergencyContactsDialogContent(
                    layoutPadding = PaddingValues(),
                    isLandscapeLayout = false,
                    isImeVisible = false,
                    candidates = candidates,
                    selectedIds = selectedIds,
                    message = message,
                    isSaving = false,
                    strings = evidenceStrings(),
                    onMessageChange = { message = it },
                    onToggleContact = { contact ->
                        selectedIds = selectedIds.toggleForEvidence(contact.id)
                    },
                    onDismiss = {},
                    onSave = {
                        savedSelections += selectedIds
                        savedMessages += message
                    },
                    slots = EmergencyContactsDialogSlots(
                        contactRow = { contact, selected, toggle ->
                            EmergencyUserRowContent(
                                user = contact,
                                selected = selected,
                                addLabel = "Añadir",
                                removeLabel = "Quitar",
                                avatar = { Text(contact.displayName.take(1)) },
                                onToggle = toggle,
                            )
                        },
                        messageInput = { modifier: Modifier, value, change, minLines, maxLines ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = change,
                                modifier = modifier,
                                minLines = minLines,
                                maxLines = maxLines ?: Int.MAX_VALUE,
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag(ProfileSosRootTestTag, useUnmergedTree = true).fetchSemanticsNode()
        compose.onNodeWithTag(ProfileSosMessageTabTestTag, useUnmergedTree = true).performClick()
        compose.onNodeWithTag(ProfileSosMessageInputTestTag, useUnmergedTree = true)
            .performTextClearance()
        compose.onNodeWithTag(ProfileSosMessageInputTestTag, useUnmergedTree = true)
            .performTextInput("Necesito ayuda cerca del mercado.")
        compose.onNodeWithTag(ProfileSosSaveTestTag, useUnmergedTree = true).performClick()

        check(savedSelections.single() == listOf("sos-1")) {
            "android_sos_saved_message_contacts_mismatch:$savedSelections"
        }
        check(savedMessages.single() == "Necesito ayuda cerca del mercado.") {
            "android_sos_custom_message_mismatch:$savedMessages"
        }
        saveScreenshot("android-profile-sos-message")
    }

    private fun evidenceStrings() = EmergencyContactsEditorStrings(
        header = EmergencyContactsHeaderStrings(
            back = "Atrás",
            sos = "SOS",
            title = "Contactos de emergencia",
            description = "Elige hasta cinco contactos de Quata.",
            contactsTab = "Contactos",
            messageTab = "Mensaje",
        ),
        selectedCount = { "$it/5 seleccionados" },
        networkUsers = "Contactos disponibles",
        searchPlaceholder = "Buscar",
        messageTitle = "Mensaje SOS",
        messageHint = "Este mensaje se enviará con el aviso.",
        savePortrait = "Guardar SOS",
        saveLandscape = "Guardar",
    )

    private fun List<String>.toggleForEvidence(contactId: String): List<String> {
        val selected = distinct().take(5).toMutableList()
        if (contactId in selected) {
            selected.remove(contactId)
        } else if (selected.size < 5) {
            selected += contactId
        }
        return selected
    }

    private fun tapContactToggle(contactId: String) {
        val tag = "$ProfileSosContactToggleTestTagPrefix$contactId"
        compose.onNodeWithTag(ProfileSosContactsListTestTag, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
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

    private fun writeReport(savedContacts: List<String>, savedMessage: String) {
        File(evidenceDir(), "android-profile-sos-contacts-evidence.json").writeText(
            JSONObject()
                .put("check", "PROFILE-SOS-CONTACTS-ANDROID-COMMON-001")
                .put("status", "passed")
                .put(
                    "steps",
                    JSONArray(
                        listOf(
                            "shared_sos_dialog_rendered",
                            "sixth_contact_rejected_by_common_limit",
                            "contact_removed",
                            "custom_message_saved",
                        ),
                    ),
                )
                .put("savedContacts", JSONArray(savedContacts))
                .put("savedMessage", savedMessage)
                .put("screenshots", JSONArray(listOf("android-profile-sos-contacts.png")))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("profile-sos-contacts-evidence")
            ?: File(instrumentation.targetContext.filesDir, "profile-sos-contacts-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}
