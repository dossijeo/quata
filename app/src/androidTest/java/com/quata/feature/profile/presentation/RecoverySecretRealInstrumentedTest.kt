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
    private var question = ""
    private var questionLabel = ""
    private var phase = "ready"
    private lateinit var evidence: File

    @Test(timeout = 600_000)
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
                deadline.schedule(590_000) {
                    runCatching { accepted.get()?.close() }
                    runCatching { server.close() }
                }
                try {
                server.accept().use { socket ->
                    accepted.set(socket)
                    socket.soTimeout = 240_000
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
                            JSONObject().put("id", id).put("ok", false)
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
            if (profileId.isNotEmpty() && app.container.sessionManager.currentSession()?.userId == profileId) {
                app.container.sessionManager.clearSession()
            }
        }
    }

    private suspend fun execute(action: String, args: JSONObject): Any = when (action) {
        "login" -> {
            check(phase == "ready" && app.container.sessionManager.currentSession() == null)
            profileId = args.getString("profileId")
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
            compose.waitUntil(30_000) { answerEmpty() && exists(ProfileFeedbackSuccessTestTag) }
            check(!exists(ProfileFeedbackErrorTestTag))
            capture("account-secret-saved-answer-empty")
            phase = "saved"; true
        }
        "read" -> {
            check(phase == "saved")
            val selected = compose.onNodeWithTag(ProfileDetailsSecretQuestionButtonTestTag).fetchSemanticsNode().config
                .getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == questionLabel }
            JSONObject().put("visible", exists(ProfileDetailsRootTestTag)).put("question", if (selected) question else "")
                .put("answerEmpty", answerEmpty()).put("saving", false)
                .put("failed", exists(ProfileFeedbackErrorTestTag)).put("saved", exists(ProfileFeedbackSuccessTestTag))
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
            launchProfile()
            val loginLabel = context.getString(R.string.auth_required_login)
            compose.waitUntil(30_000) { compose.onAllNodesWithText(loginLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(loginLabel).performClick()
            val forgotLabel = context.authCatalog().login.forgotPassword
            compose.waitUntil(20_000) { compose.onAllNodesWithText(forgotLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(forgotLabel).performClick()
            waitFor(ForgotPasswordTestTags.Root)
            node(ForgotPasswordTestTags.Phone).performTextReplacement(args.getString("phone"))
            compose.waitUntil(20_000) {
                runCatching { node(ForgotPasswordTestTags.Question).fetchSemanticsNode().config[SemanticsProperties.EditableText].text == questionLabel }.getOrDefault(false)
            }
            node(ForgotPasswordTestTags.SecretAnswer).performTextReplacement(args.getString("answer"))
            node(ForgotPasswordTestTags.NewPassword).performTextReplacement(args.getString("password"))
            phase = "reset_started"
            click(ForgotPasswordTestTags.Submit)
            compose.waitUntil(30_000) { !exists(ForgotPasswordTestTags.Root) && compose.onAllNodesWithText(forgotLabel).fetchSemanticsNodes().isNotEmpty() }
            capture("login-after-recovery")
            phase = "recovered"; true
        }
        "close" -> {
            activity?.close(); activity = null
            val current = app.container.sessionManager.currentSession()
            check(current == null || (profileId.isNotEmpty() && current.userId == profileId))
            if (current != null) app.container.sessionManager.clearSession()
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
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(30_000) { exists(tag) }
    private fun click(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).filterToOne(hasClickAction()).performScrollTo().performClick()
    private fun answerEmpty() = runCatching { node(ProfileDetailsSecretAnswerInputTestTag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty() }.getOrDefault(false)
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
