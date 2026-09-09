package com.quata.feature.profile.presentation

import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.R
import com.quata.feature.auth.presentation.recovery.ForgotPasswordTestTags
import com.quata.feature.profile.data.EmergencyContactsStore
import com.quata.feature.profile.data.authCatalog
import com.quata.feature.profile.data.profileSecretQuestionOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.Reader
import java.util.Timer
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.schedule

/** A single focal adapter. The external core owns tickets, mutations and restitution. */
@RunWith(AndroidJUnit4::class)
class RecoverySecretRealInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private var activity: ActivityScenario<MainActivity>? = null
    private var profileId = ""
    private var authUserId = ""
    private var question = ""
    private var questionLabel = ""
    private var phase = "ready"
    private lateinit var evidence: File

    /** Explicit opt-in, exact actor only; never clears an unrelated installed session. */
    @Test(timeout = 30_000)
    fun clearOwnedFixtureSession() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quataRecoveryMaintenance") == "clear-owned-session")
        profileId = args.getString("quataRecoveryProfileId").orEmpty()
        authUserId = args.getString("quataRecoveryAuthUserId").orEmpty()
        check(profileId.matches(Regex("[0-9a-f-]{36}")) && authUserId.matches(Regex("[0-9a-f-]{36}")))
        check(app.container.sessionManager.currentSession() != null)
        clearOwnedSessionDurably()
    }

    /** Invoke in a separate instrumentation process after the cleanup process terminates. */
    @Test(timeout = 30_000)
    fun verifyNoPersistedFixtureSession() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("quataRecoveryMaintenance") == "verify-empty-session")
        check(app.container.sessionManager.currentSession() == null)
        check(context.getSharedPreferences("quata_session", android.content.Context.MODE_PRIVATE).all.isEmpty())
        InstrumentationRegistry.getArguments().getString("quataRecoveryQuestion")?.let { selected ->
            check(selected.isNotBlank() && context.profileSecretQuestionOptions().count { it.value == selected } == 1) {
                "recovery_question_not_in_installed_catalog"
            }
        }
    }

    private fun clearOwnedSessionDurably() {
        val current = app.container.sessionManager.currentSession()
        check(current == null || (profileId.isNotEmpty() && authUserId.isNotEmpty() &&
            current.userId == profileId && current.authUserId == authUserId)) { "recovery_session_owner_mismatch" }
        val prefs = context.getSharedPreferences("quata_session", android.content.Context.MODE_PRIVATE)
        if (current != null) {
            app.container.sessionManager.clearSession()
        }
        check(app.container.sessionManager.currentSession() == null && prefs.all.isEmpty())
        // Also waits for a preceding logout's apply writes before process exit.
        check(prefs.edit().commit()) { "recovery_session_cleanup_not_persisted" }
    }

    @Test(timeout = 2_700_000)
    fun accountSecretRoundtripControlledByFocalCoordinator() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("quataRecoverySocket").orEmpty()
        assumeTrue(name.matches(Regex("quata-recovery-[0-9a-f-]{36}")))
        evidence = File(context.filesDir, "recovery-secret-evidence/$name")
        check(!evidence.exists() && evidence.mkdirs()) { "recovery_evidence_directory_unavailable" }
        var completed = false
        // Accept/read never run on the app UI thread. The caller supplies an owned adb forward.
        try {
            LocalServerSocket(name).use { server ->
                val accepted = AtomicReference<LocalSocket?>()
                val deadline = Timer("recovery-private-channel", true)
                deadline.schedule(2_690_000) {
                    runCatching { accepted.get()?.close() }
                    runCatching { server.close() }
                }
                try {
                server.accept().use { socket ->
                    accepted.set(socket)
                    socket.soTimeout = 660_000
                    val reader = socket.inputStream.bufferedReader()
                    val writer = socket.outputStream.bufferedWriter()
                    var expectedId = 1
                    while (true) {
                        val line = readBoundedLine(reader) ?: break
                        val request = JSONObject(line)
                        val id = request.getInt("id")
                        if (id != expectedId++) error("request_out_of_order")
                        val action = request.getString("action")
                        val reply = try {
                            JSONObject().put("id", id).put("ok", true)
                                .put("result", execute(action, request.getJSONObject("args")))
                        } catch (_: Throwable) {
                            // Never serialize a Compose tree, exception or secret in an error.
                            JSONObject().put("id", id).put("ok", false).put("phase", phase)
                        }
                        writer.write(reply.toString()); writer.newLine(); writer.flush()
                        if (action == "close" && reply.getBoolean("ok") && phase == "recovered") completed = true
                        if (action == "close" || !reply.getBoolean("ok")) break
                    }
                }
                } finally { deadline.cancel() }
            }
            check(completed) { "recovery_instrumentation_incomplete" }
        } catch (_: Throwable) {
            throw AssertionError("recovery_private_channel_stopped")
        } finally {
            activity?.close()
            if (profileId.isNotEmpty()) {
                clearOwnedSessionDurably()
            }
        }
    }

    private suspend fun execute(action: String, args: JSONObject): Any = when (action) {
        "login" -> {
            check(phase == "ready" && app.container.sessionManager.currentSession() == null)
            profileId = args.getString("profileId")
            authUserId = args.getString("authUserId")
            check(EmergencyContactsStore(context).get(profileId).isEmpty())
            phase = "login_started"
            app.container.authRepository.login(args.getString("countryCode"), args.getString("phone"), args.getString("password")).getOrThrow()
            val session = app.container.sessionManager.currentSession() ?: error("session_missing")
            check(session.userId == profileId && session.authUserId == args.getString("authUserId") && session.isSupabaseAuthenticated())
            phase = "authenticated"
            JSONObject().put("profileId", session.userId).put("accessToken", session.bearerToken)
        }
        "open" -> {
            check(phase == "authenticated")
            launchProfile()
            waitFor(ProfileDetailsOpenTestTag)
            click(ProfileDetailsOpenTestTag)
            waitFor(ProfileDetailsRootTestTag)
            check(answerEmpty())
            capture("account-before-secret")
            phase = "account"; true
        }
        "configure" -> {
            check(phase == "account" && EmergencyContactsStore(context).get(profileId).isEmpty())
            question = args.getString("question")
            questionLabel = context.profileSecretQuestionOptions().single { it.value == question && it.value.isNotBlank() }.label
            click(ProfileDetailsSecretQuestionButtonTestTag)
            click("$ProfileDetailsSecretQuestionButtonTestTag.option.$question")
            node(ProfileDetailsSecretAnswerInputTestTag).performScrollTo().performTextReplacement(args.getString("answer"))
            phase = "configured"; true
        }
        "save" -> {
            check(phase == "configured" && EmergencyContactsStore(context).get(profileId).isEmpty())
            phase = "save_started"
            click(ProfileDetailsSaveTestTag)
            // Android's onProfileSaved leaves Account for Feed. The caller reviews
            // this capture before permitting the subsequent Account read.
            compose.waitUntil(30_000) { !exists(ProfileDetailsRootTestTag) }
            check(!exists(ProfileFeedbackErrorTestTag))
            capture("after-save-navigation")
            phase = "saved"; true
        }
        "read" -> {
            check(phase == "saved")
            phase = "read_opening"
            checkCurrentActor()
            activity?.close(); activity = null
            launchProfile()
            waitFor(ProfileDetailsOpenTestTag)
            phase = "read_details"
            click(ProfileDetailsOpenTestTag)
            waitFor(ProfileDetailsRootTestTag)
            checkCurrentActor()
            phase = "read_verification"
            compose.waitUntil(30_000) { answerEmpty() && selectedQuestionMatches() }
            val selected = selectedQuestionMatches()
            capture("account-secret-saved-answer-empty")
            phase = "saved"
            JSONObject().put("visible", exists(ProfileDetailsRootTestTag)).put("question", if (selected) question else "")
                .put("answerEmpty", answerEmpty()).put("saving", false)
                .put("failed", exists(ProfileFeedbackErrorTestTag)).put("saved", phase == "saved")
        }
        "logout" -> {
            check(phase == "saved")
            activity?.close(); activity = null
            app.container.authRepository.logout()
            check(app.container.sessionManager.currentSession() == null)
            phase = "logged_out"; true
        }
        "recover" -> {
            check(phase == "logged_out" && args.getString("countryCode") == "240")
            phase = "recovery_opening"
            launchProfile()
            val loginLabel = context.getString(R.string.auth_required_login)
            compose.waitUntil(30_000) { compose.onAllNodesWithText(loginLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(loginLabel).performClick()
            phase = "recovery_login"
            val forgotLabel = context.authCatalog().login.forgotPassword
            compose.waitUntil(20_000) { compose.onAllNodesWithText(forgotLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(forgotLabel).performClick()
            phase = "recovery_form"
            waitFor(ForgotPasswordTestTags.Root)
            node(ForgotPasswordTestTags.Phone).performTextReplacement(args.getString("phone"))
            phase = "recovery_question"
            compose.waitUntil(20_000) {
                runCatching { node(ForgotPasswordTestTags.Question).fetchSemanticsNode().config[SemanticsProperties.EditableText].text == questionLabel }.getOrDefault(false)
            }
            node(ForgotPasswordTestTags.SecretAnswer).performTextReplacement(args.getString("answer"))
            node(ForgotPasswordTestTags.NewPassword).performTextReplacement(args.getString("password"))
            phase = "reset_started"
            click(ForgotPasswordTestTags.Submit)
            phase = "recovery_return"
            try {
                compose.waitUntil(30_000) { !exists(ForgotPasswordTestTags.Root) && compose.onAllNodesWithText(forgotLabel).fetchSemanticsNodes().isNotEmpty() }
            } catch (error: Throwable) {
                // Report only a fixed stage; an unknown surface may contain private inputs.
                phase = if (exists(ForgotPasswordTestTags.Root)) "recovery_still_open" else "recovery_destination_missing"
                if (phase == "recovery_still_open") {
                    val errorNodes = compose.onAllNodesWithTag(ForgotPasswordTestTags.Error, useUnmergedTree = true).fetchSemanticsNodes()
                    val errorText = errorNodes.singleOrNull()?.config?.getOrElse(SemanticsProperties.Text) { emptyList() }
                        ?.joinToString(" ") { it.text }
                    val knownErrors = mapOf(
                        R.string.error_network_timeout to "network_timeout",
                        R.string.error_network to "network",
                        R.string.error_backend_generic to "backend_generic",
                        R.string.error_backend_bad_request to "bad_request",
                        R.string.error_backend_unauthorized to "unauthorized",
                        R.string.error_backend_unavailable to "backend_unavailable"
                    )
                    val errorKind = if (errorNodes.isEmpty()) "absent" else
                        knownErrors.entries.firstOrNull { context.getString(it.key) == errorText }?.value ?: "unclassified"
                    val submitNodes = compose.onAllNodesWithTag(ForgotPasswordTestTags.Submit, useUnmergedTree = true)
                        .filter(hasClickAction()).fetchSemanticsNodes()
                    File(evidence, "recovery-state.json").writeText(JSONObject()
                        .put("errorKind", errorKind)
                        .put("submitFound", submitNodes.size == 1)
                        .put("submitEnabled", submitNodes.singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false)
                        .toString())
                }
                throw error
            }
            capture("login-after-recovery")
            phase = "recovered"; true
        }
        "close" -> {
            activity?.close(); activity = null
            clearOwnedSessionDurably()
            true
        }
        else -> error("unsupported_recovery_command")
    }

    private fun launchProfile() {
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "profile"))
    }
    private fun checkCurrentActor() {
        val current = app.container.sessionManager.currentSession()
        check(current != null && current.userId == profileId && current.authUserId == authUserId)
    }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(30_000) { exists(tag) }
    private fun click(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).filterToOne(hasClickAction()).performScrollTo().performClick()
    private fun answerEmpty() = runCatching { node(ProfileDetailsSecretAnswerInputTestTag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty() }.getOrDefault(false)
    private fun selectedQuestionMatches() = runCatching {
        compose.onNodeWithTag(ProfileDetailsSecretQuestionButtonTestTag).fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == questionLabel }
    }.getOrDefault(false)
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot_unavailable")
        try { File(evidence, "$name.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
    private fun readBoundedLine(reader: Reader): String? {
        val value = StringBuilder()
        while (value.length <= 16_384) {
            val next = reader.read()
            if (next == -1) { check(value.isEmpty()); return null }
            if (next == 10) return value.toString()
            value.append(next.toChar())
        }
        error("request_too_large")
    }
}
