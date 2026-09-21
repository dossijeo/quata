package com.quata.core.moderation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.QuataApp
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.ui.components.QuataUgcTermsAcceptTestTag
import com.quata.core.ui.components.QuataUgcTermsDialogTestTag
import com.quata.core.ui.components.QuataUgcTermsGateContent
import com.quata.core.ui.components.quataUgcTermsStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UgcTermsRemoteAcceptanceInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedUserAcceptsTermsThroughProductGateAndPersistsRemotely() = runBlocking {
        val credentialsFile = optionalArgument("quataUgcTermsCredentialsFile")
        assumeTrue(
            "UGC-TERMS-ANDROID-REMOTE-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataUgcTermsRemoteEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue("Android must hold a real authenticated session.", session?.isSupabaseAuthenticated() == true)
        val profileId = session?.userId ?: error("android_ugc_terms_session_missing")
        UgcTermsAcceptanceStore(targetContext).clearUser(profileId)
        assertFalse(
            "The coordinator must remove the remote acceptance before the product gate starts.",
            app.container.supabaseCommunityApi.hasAcceptedUgcTerms(profileId, CurrentUgcTermsVersion),
        )
        assertFalse(
            "The production Android gateway must observe the prepared remote unaccepted state.",
            app.container.moderationRepository.hasAcceptedTerms().getOrThrow(),
        )

        var gateObserved = false
        var gateDismissed = false
        var remotePersisted = false
        compose.setContent {
            QuataTheme {
                QuataUgcTermsGateContent(
                    profileId = profileId,
                    gateway = app.container.moderationRepository,
                    strings = quataUgcTermsStrings(QuataLanguage.Spanish),
                    onAcceptedStateChanged = {},
                    onLogout = {},
                    legalLinks = {},
                )
            }
        }
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag(QuataUgcTermsDialogTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        compose.onNodeWithTag(QuataUgcTermsDialogTestTag, useUnmergedTree = true).assertIsDisplayed()
        gateObserved = true
        compose.onNodeWithTag(QuataUgcTermsAcceptTestTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(20_000) {
            runCatching {
                compose.onNodeWithTag(QuataUgcTermsDialogTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isFailure
        }
        gateDismissed = true

        assertTrue(
            "The same production repository path used by WorkManager must flush the pending acceptance.",
            app.container.moderationRepository.flushPendingTermsForCurrentUser(),
        )
        for (attempt in 0 until 20) {
            if (app.container.supabaseCommunityApi.hasAcceptedUgcTerms(profileId, CurrentUgcTermsVersion)) {
                remotePersisted = true
                break
            }
            delay(500)
        }

        writeReport(profileId, gateObserved, gateDismissed, remotePersisted)
        assertTrue("android_ugc_terms_gate_observed", gateObserved)
        assertTrue("android_ugc_terms_gate_dismissed", gateDismissed)
        assertTrue("android_ugc_terms_remote_persisted", remotePersisted)
    }

    private fun writeReport(profileId: String, gateObserved: Boolean, gateDismissed: Boolean, remotePersisted: Boolean) {
        File(evidenceDir(), "android-ugc-terms-remote-evidence.json").writeText(
            JSONObject()
                .put("check", "UGC-TERMS-ANDROID-REMOTE-001")
                .put("status", if (gateObserved && gateDismissed && remotePersisted) "passed" else "failed")
                .put("profileId", profileId)
                .put("termsVersion", CurrentUgcTermsVersion)
                .put("gateObserved", gateObserved)
                .put("acceptedThroughProductUi", gateDismissed)
                .put("remotePersisted", remotePersisted)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File = File(targetContext.filesDir, "ugc-terms-remote-evidence")
        .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_ugc_terms_evidence_directory_create_failed" } }

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit().putBoolean("app_links_prompt_seen", true).commit()
    }

    private fun optionalArgument(name: String): String? = arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentialsFromFile(path: String): EvidenceCredentials {
        val file = if (path.startsWith("app-internal:")) File(targetContext.filesDir, path.removePrefix("app-internal:")) else File(path)
        val json = JSONObject(file.readText())
        return EvidenceCredentials(json.getString("country_code"), json.getString("phone"), json.getString("password"))
    }

    private data class EvidenceCredentials(val countryCode: String, val phone: String, val password: String)
}
