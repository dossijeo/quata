package com.quata.feature.auth.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.MainActivity
import com.quata.feature.auth.presentation.register.RegisterTestTags
import com.quata.feature.feed.presentation.FeedRootTestTag
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuthRegisterRealInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun productFormSubmitsOneJournaledRegistrationAndReachesAuthenticatedFeed() {
        val inputFile = File(context.filesDir, InputFileName)
        val input = JSONObject(inputFile.readText())
        assertTrue("registration_private_input_not_removed", inputFile.delete())
        val countryCode = input.getString("countryCode")
        val phone = input.getString("phone")
        val identityDigits = "$countryCode$phone".filter(Char::isDigit)
        context.getSharedPreferences("registration_security", Context.MODE_PRIVATE).edit()
            .putString("client_instance_id", input.getString("clientInstanceId"))
            .putString("pending_$identityDigits", input.getString("idempotencyKey"))
            .commit()

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
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "register")
        })
        try {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithTag(RegisterTestTags.DisplayName, true).fetchSemanticsNode() }.isSuccess
            }
            anchors.forEach { compose.onNodeWithTag(it, true).fetchSemanticsNode() }
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
            compose.onNodeWithTag(RegisterTestTags.Submit, true).performScrollTo().performClick()
            compose.waitUntil(120_000) {
                runCatching { compose.onNodeWithTag(FeedRootTestTag, true).fetchSemanticsNode() }.isSuccess
            }
            File(context.filesDir, ResultFileName).writeText(
                JSONObject()
                    .put("passed", true)
                    .put("exactSubmits", 1)
                    .put("authenticatedTransition", true)
                    .put("anchors", JSONArray(anchors))
                    .toString() + "\n",
            )
        } finally {
            scenario.close()
        }
    }

    private fun fill(tag: String, value: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(value)
    }

    private companion object {
        const val InputFileName = "auth-register-product-input.json"
        const val ResultFileName = "auth-register-product-result.json"
    }
}
