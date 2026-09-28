package com.quata.feature.auth.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.QuataApp
import com.quata.R
import com.quata.core.auth.MainActivityTurnstileHost
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.navigation.AppDestinations
import com.quata.core.navigation.AppNavGraph
import com.quata.feature.auth.presentation.register.RegisterTestTags
import com.quata.feature.feed.presentation.FeedRootTestTag
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AuthRegisterRealInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun productFormSubmitsOneJournaledRegistrationAndReachesAuthenticatedFeed() {
        var stage = "private-input"
        val anchors = listOf(
            RegisterTestTags.DisplayName,
            RegisterTestTags.Neighborhood,
            RegisterTestTags.CountryPrefix,
            RegisterTestTags.PhoneInput,
            RegisterTestTags.Password,
            RegisterTestTags.SecretQuestion,
            RegisterTestTags.SecretAnswer,
            RegisterTestTags.Submit,
        )
        try {
            val inputFile = File(context.filesDir, InputFileName)
            val input = JSONObject(inputFile.readText())
            assertTrue("registration_private_input_not_removed", inputFile.delete())
            val app = ApplicationProvider.getApplicationContext<QuataApp>()
            app.container.sessionManager.clearSession()
            val countryCode = input.getString("countryCode")
            val phone = input.getString("phone")
            val identityDigits = "$countryCode$phone".filter(Char::isDigit)
            context.deleteSharedPreferences("registration_security")
            context.getSharedPreferences("registration_security", Context.MODE_PRIVATE).edit()
                .putString("client_instance_id", input.getString("clientInstanceId"))
                .putString("pending_$identityDigits", input.getString("idempotencyKey"))
                .commit()
            stage = "mount"
            val challengeOutcome = AtomicReference("pending")
            val turnstileHost = MainActivityTurnstileHost(compose.activity, challengeOutcome::set)
            app.container.registrationChallengeService.attachHost {
                challengeOutcome.compareAndSet("pending", "started")
                turnstileHost.request()
            }
            try {
                compose.setContent {
                    QuataTheme(mode = QuataThemeMode.Light) {
                        AppNavGraph(
                            container = app.container,
                            themeMode = QuataThemeMode.Light,
                            startDestinationOverride = AppDestinations.Register.route,
                        )
                    }
                }
                stage = "mount-root"
                compose.waitUntil(15_000) {
                    runCatching { compose.onNodeWithTag(RegisterTestTags.DisplayName, true).fetchSemanticsNode() }.isSuccess
                }
                anchors.forEach {
                    stage = "mount-${it.replace('.', '-')}"
                    compose.onNodeWithTag(it, true).fetchSemanticsNode()
                }
                stage = "form"
                fill(RegisterTestTags.DisplayName, input.getString("displayName"))
                fill(RegisterTestTags.Neighborhood, input.getString("neighborhood"))
                compose.onNodeWithTag(RegisterTestTags.CountryPrefix, true).performScrollTo().performClick()
                fill(RegisterTestTags.CountryPrefixSearch, countryCode)
                compose.onNodeWithTag("${RegisterTestTags.CountryPrefixOption}.$countryCode", true)
                    .performScrollTo().performClick()
                fill(RegisterTestTags.PhoneInput, phone)
                fill(RegisterTestTags.Password, input.getString("password"))
                compose.onNodeWithTag(RegisterTestTags.SecretQuestion, true).performScrollTo().performClick()
                compose.onNodeWithTag("${RegisterTestTags.SecretQuestionOption}.${input.getString("secretQuestion")}", true)
                    .performScrollTo().performClick()
                fill(RegisterTestTags.SecretAnswer, input.getString("secretAnswer"))
                stage = "submit"
                compose.onNodeWithTag(RegisterTestTags.Submit)
                    .performScrollTo()
                    .assertIsEnabled()
                    .performClick()
                stage = "challenge-dispatch"
                val challengeDispatched = runCatching {
                    compose.waitUntil(15_000) {
                        challengeOutcome.get() != "pending" || runCatching {
                            compose.onNodeWithTag(RegisterTestTags.Error, true).fetchSemanticsNode()
                        }.isSuccess
                    }
                }.isSuccess
                if (!challengeDispatched || challengeOutcome.get() == "pending") {
                    stage = "product-error-challenge-not-dispatched"
                    error("registration_product_android_challenge_not_dispatched")
                }
                stage = "authenticated-transition"
                var feedVisible = false
                var productErrorVisible = false
                val transitionObserved = runCatching {
                    compose.waitUntil(120_000) {
                        feedVisible = runCatching {
                            compose.onNodeWithTag(FeedRootTestTag, true).fetchSemanticsNode()
                        }.isSuccess
                        productErrorVisible = runCatching {
                            compose.onNodeWithTag(RegisterTestTags.Error, true).fetchSemanticsNode()
                        }.isSuccess
                        feedVisible || productErrorVisible
                    }
                }.isSuccess
                if (!transitionObserved || productErrorVisible || !feedVisible) {
                    val errorClass = if (productErrorVisible) classifyProductError() else "transition"
                    stage = "product-error-${challengeOutcome.get()}-$errorClass"
                    error("registration_product_android_product_error")
                }
            } finally {
                app.container.registrationChallengeService.detachHost()
                turnstileHost.close()
            }
            writeResult(
                JSONObject()
                    .put("passed", true)
                    .put("exactSubmits", 1)
                    .put("authenticatedTransition", true)
                    .put("anchors", JSONArray(anchors))
            )
        } catch (error: Throwable) {
            writeResult(JSONObject().put("passed", false).put("failureStage", stage))
            throw error
        } finally {
            ApplicationProvider.getApplicationContext<QuataApp>().container.sessionManager.clearSession()
        }
    }

    private fun fill(tag: String, value: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(value)
    }

    private fun classifyProductError(): String {
        val text = compose.onNodeWithTag(RegisterTestTags.Error, true)
            .fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }
        return when (text) {
            context.getString(R.string.error_backend_bad_request) -> "bad-request"
            context.getString(R.string.error_backend_unauthorized) -> "unauthorized"
            context.getString(R.string.error_backend_unavailable) -> "unavailable"
            context.getString(R.string.error_backend_generic) -> "generic"
            context.getString(R.string.error_network) -> "network"
            context.getString(R.string.error_network_timeout) -> "network-timeout"
            else -> text.takeIf { it.matches(Regex("[a-z0-9_-]{1,64}")) } ?: "other"
        }
    }

    private fun writeResult(result: JSONObject) {
        File(context.filesDir, ResultFileName).writeText(result.toString() + "\n")
    }

    private companion object {
        const val InputFileName = "auth-register-product-input.json"
        const val ResultFileName = "auth-register-product-result.json"
    }
}
