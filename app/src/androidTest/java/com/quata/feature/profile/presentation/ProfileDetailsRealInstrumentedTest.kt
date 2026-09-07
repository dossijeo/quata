package com.quata.feature.profile.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCacheMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ProfileDetailsRealInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedUserUpdatesAccountDetailsFromCommonProfile() = runBlocking {
        val credentialsFile = optionalArgument("quataAccountDetailsCredentialsFile")
        assumeTrue(
            "ACCOUNT-DETAILS-ANDROID-REAL-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataAccountDetailsEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue(
            "Android must hold a real Supabase-authenticated session before changing account details.",
            session?.isSupabaseAuthenticated() == true,
        )
        val profileId = session?.userId ?: error("android_account_details_session_missing")
        val original = fetchProfile(profileId)
        val marker = System.currentTimeMillis().toString().takeLast(6)
        val update = AccountDetailsSnapshot(
            displayName = "Gabrielo QA $marker",
            neighborhood = "Bata QA $marker",
            countryCode = original.countryCode.ifBlank { credentials.countryCode },
            phone = evidencePhone(original.phone.ifBlank { localPhone(original.countryCode.ifBlank { credentials.countryCode }, credentials.phone) }),
        )
        val screenshots = mutableListOf<String>()
        var remotePersisted = false
        var reloadVerified = false
        var cleanupRestored = false

        try {
            ActivityScenario.launch<MainActivity>(mainIntent()).use {
                compose.waitUntil(45_000) {
                    runCatching {
                        compose.onNodeWithTag(ProfileDetailsOpenTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
                screenshots += saveScreenshot("android-account-details-profile-opened")

                compose.onNodeWithTag(ProfileDetailsOpenTestTag, useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()
                compose.waitUntil(20_000) {
                    runCatching {
                        compose.onNodeWithTag(ProfileDetailsRootTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
                screenshots += saveScreenshot("android-account-details-form-opened")

                replaceText(ProfileDetailsNameInputTestTag, update.displayName)
                replaceText(ProfileDetailsNeighborhoodInputTestTag, update.neighborhood)
                replaceText(ProfileDetailsPhoneInputTestTag, update.phone)
                device.pressBack()
                screenshots += saveScreenshot("android-account-details-form-edited")

                compose.onAllNodesWithTag(ProfileDetailsSaveTestTag, useUnmergedTree = true)
                    .filterToOne(hasClickAction())
                    .performScrollTo()
                    .performClick()
                remotePersisted = waitForProfile(profileId, update)
                screenshots += saveScreenshot("android-account-details-saved")
            }
            ActivityScenario.launch<MainActivity>(mainIntent()).use {
                compose.waitUntil(45_000) {
                    runCatching {
                        compose.onNodeWithTag(ProfileDetailsOpenTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
                compose.onNodeWithTag(ProfileDetailsOpenTestTag, useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()
                reloadVerified = waitForVisibleDetails(update)
                screenshots += saveScreenshot("android-account-details-reloaded")
            }
        } finally {
            restoreProfile(profileId, original)
            cleanupRestored = waitForProfile(profileId, original)
            writeReport(
                profileId = profileId,
                original = original,
                update = update,
                remotePersisted = remotePersisted,
                reloadVerified = reloadVerified,
                cleanupRestored = cleanupRestored,
                screenshots = screenshots,
            )
        }

        assertTrue("android_account_details_remote_persisted", remotePersisted)
        assertTrue("android_account_details_reload_verified", reloadVerified)
        assertTrue("android_account_details_profile_restored", cleanupRestored)
        assertEquals("android_account_details_restored_name", original.displayName, fetchProfile(profileId).displayName)
    }

    private fun replaceText(testTag: String, value: String) {
        compose.onNodeWithTag(testTag, useUnmergedTree = true)
            .performScrollTo()
            .performTextReplacement(value)
    }

    private suspend fun waitForVisibleDetails(update: AccountDetailsSnapshot): Boolean {
        repeat(20) {
            val state = runCatching {
                compose.onNodeWithTag(ProfileDetailsNameInputTestTag, useUnmergedTree = true).fetchSemanticsNode()
                compose.onNodeWithTag(ProfileDetailsNeighborhoodInputTestTag, useUnmergedTree = true).fetchSemanticsNode()
                compose.onNodeWithTag(ProfileDetailsPhoneInputTestTag, useUnmergedTree = true).fetchSemanticsNode()
                true
            }.getOrDefault(false)
            if (state && fetchProfileFromUi(update)) return true
            delay(500)
        }
        return false
    }

    private fun fetchProfileFromUi(update: AccountDetailsSnapshot): Boolean =
        runCatching {
            compose.onNodeWithTag(ProfileDetailsNameInputTestTag, useUnmergedTree = true)
                .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, AnnotatedString(update.displayName)))
            compose.onNodeWithTag(ProfileDetailsNeighborhoodInputTestTag, useUnmergedTree = true)
                .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, AnnotatedString(update.neighborhood)))
            compose.onNodeWithTag(ProfileDetailsPhoneInputTestTag, useUnmergedTree = true)
                .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, AnnotatedString(update.phone)))
            true
        }.getOrDefault(false)

    private suspend fun fetchProfile(profileId: String): AccountDetailsSnapshot {
        val profile = app.container.supabaseCommunityApi
            .getProfiles(ids = listOf(profileId), cacheMode = SupabaseCacheMode.NETWORK_ONLY)
            .firstOrNull()
            ?: error("android_account_details_profile_missing")
        return profile.toAccountDetailsSnapshot()
    }

    private suspend fun waitForProfile(profileId: String, expected: AccountDetailsSnapshot): Boolean {
        repeat(30) {
            if (fetchProfile(profileId).matches(expected)) return true
            delay(1_000)
        }
        return false
    }

    private suspend fun restoreProfile(profileId: String, original: AccountDetailsSnapshot) {
        app.container.supabaseCommunityApi.updateProfile(
            profileId,
            mapOf(
                "display_name" to original.displayName,
                "nombre" to original.displayName,
                "neighborhood" to original.neighborhood,
                "barrio" to original.neighborhood,
                "country_code" to original.countryCode,
                "code" to original.countryCode,
                "phone_local" to original.phone,
                "phone" to original.phone,
                "telefono" to original.phone,
            ),
        )
    }

    private fun mainIntent(): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "profile")

    private fun saveScreenshot(name: String): String {
        val file = File(evidenceDir(), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null) {
            check(device.takeScreenshot(file)) { "android_screenshot_failed:$name" }
        } else {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                    "android_screenshot_encode_failed:$name"
                }
            }
        }
        return file.name
    }

    private fun writeReport(
        profileId: String,
        original: AccountDetailsSnapshot,
        update: AccountDetailsSnapshot,
        remotePersisted: Boolean,
        reloadVerified: Boolean,
        cleanupRestored: Boolean,
        screenshots: List<String>,
    ) {
        val passed = remotePersisted && reloadVerified && cleanupRestored
        File(evidenceDir(), "android-account-details-evidence.json").writeText(
            JSONObject()
                .put("check", "ACCOUNT-DETAILS-ANDROID-REAL-001")
                .put("status", if (passed) "passed" else "failed")
                .put("profileId", profileId)
                .put("fields", JSONArray(listOf("display_name", "neighborhood", "country_code", "phone_local")))
                .put("changedName", update.displayName != original.displayName)
                .put("changedNeighborhood", update.neighborhood != original.neighborhood)
                .put("changedPhone", update.phone != original.phone)
                .put("remotePersisted", remotePersisted)
                .put("reloadVerified", reloadVerified)
                .put("cleanup", JSONObject().put("profileRestored", cleanupRestored))
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "account-details-evidence")
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

    private fun CommunityProfile.toAccountDetailsSnapshot(): AccountDetailsSnapshot =
        AccountDetailsSnapshot(
            displayName = display_name.orEmpty(),
            neighborhood = neighborhood ?: barrio.orEmpty(),
            countryCode = country_code ?: code.orEmpty(),
            phone = phone_local ?: phone ?: telefono.orEmpty(),
        )

    private fun localPhone(countryCode: String, phone: String): String {
        val country = countryCode.filter(Char::isDigit)
        val digits = phone.filter(Char::isDigit)
        return if (digits.startsWith(country)) digits.removePrefix(country) else digits
    }

    private fun evidencePhone(originalPhone: String): String {
        val digits = originalPhone.filter(Char::isDigit)
        return when {
            digits.endsWith("607") -> digits.dropLast(3) + "609"
            digits.endsWith("608") -> digits.dropLast(3) + "606"
            digits.length > 1 -> digits.dropLast(1) + if (digits.last() == '9') '7' else '9'
            else -> "680242609"
        }
    }

    private data class AccountDetailsSnapshot(
        val displayName: String,
        val neighborhood: String,
        val countryCode: String,
        val phone: String,
    ) {
        fun matches(other: AccountDetailsSnapshot): Boolean =
            displayName == other.displayName &&
                neighborhood == other.neighborhood &&
                countryCode.filter(Char::isDigit) == other.countryCode.filter(Char::isDigit) &&
                phone.filter(Char::isDigit) == other.phone.filter(Char::isDigit)
    }

    private data class EvidenceCredentials(
        val countryCode: String,
        val phone: String,
        val password: String,
    )
}
