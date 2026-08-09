package com.quata.feature.auth.presentation

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.AuthSession
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.presentation.recovery.ForgotPasswordTestTags
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AuthRecoveryProductBridgeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sharedRecoverySurfaceHandlesQuestionResetAndReturnToLogin() {
        assumeTrue(
            "ANDROID-AUTH-RECOVERY-001 uses Compose/Espresso semantics; API 35+ emulator images removed InputManager.getInstance.",
            Build.VERSION.SDK_INT < 35,
        )
        val repository = FixtureAuthRepository()

        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                AuthProductHostContent(
                    repository = repository,
                    catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish),
                    prefixes = listOf(CountryPrefix("240", "+240 - Guinea Ecuatorial")),
                    initialDestination = AuthProductDestination.Recovery,
                    onAuthenticated = {},
                )
            }
        }

        for (tag in listOf(
            ForgotPasswordTestTags.Root,
            ForgotPasswordTestTags.CountryPrefix,
            ForgotPasswordTestTags.Phone,
            ForgotPasswordTestTags.Question,
            ForgotPasswordTestTags.SecretAnswer,
            ForgotPasswordTestTags.NewPassword,
            ForgotPasswordTestTags.Submit,
            ForgotPasswordTestTags.Back,
        )) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        }
        saveScreenshot("android-auth-recovery-mounted")

        compose.onNodeWithTag(ForgotPasswordTestTags.Phone, useUnmergedTree = true)
            .performTextInput(FixtureAuthRepository.MissingPhone)
        compose.waitUntil(5_000) { repository.missingQuestionReads == 1 }
        compose.onNodeWithTag(ForgotPasswordTestTags.Error, useUnmergedTree = true)
            .assertTextContains("No hay ninguna cuenta", substring = true)

        compose.onNodeWithTag(ForgotPasswordTestTags.Phone, useUnmergedTree = true)
            .performTextClearance()
        compose.onNodeWithTag(ForgotPasswordTestTags.Phone, useUnmergedTree = true)
            .performTextInput(FixtureAuthRepository.Phone)
        compose.waitUntil(5_000) { repository.questionReads == 1 }
        compose.onNodeWithTag(ForgotPasswordTestTags.Question, useUnmergedTree = true)
            .assertTextContains(FixtureAuthRepository.SecretQuestion, substring = true)

        compose.onNodeWithTag(ForgotPasswordTestTags.SecretAnswer, useUnmergedTree = true)
            .performTextInput(FixtureAuthRepository.SecretAnswer)
        compose.onNodeWithTag(ForgotPasswordTestTags.NewPassword, useUnmergedTree = true)
            .performTextInput(FixtureAuthRepository.NewPassword)
        compose.waitForIdle()
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag(ForgotPasswordTestTags.Submit, useUnmergedTree = true)
            .assertIsEnabled()
            .performScrollTo()
            .performTouchInput { click(center) }
        compose.waitUntil(5_000) { repository.resetAttempts == 1 }
        compose.onNodeWithTag("auth.submit", useUnmergedTree = true).fetchSemanticsNode()
        saveScreenshot("android-auth-recovery-login-return")

        assertEquals(1, repository.questionReads)
        assertEquals(1, repository.missingQuestionReads)
        assertEquals(1, repository.resets)
        writeReport(repository)
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

    private fun writeReport(repository: FixtureAuthRepository) {
        File(evidenceDir(), "android-auth-recovery-evidence.json").writeText(
            JSONObject()
                .put("check", "ANDROID-AUTH-RECOVERY-001")
                .put("status", "passed")
                .put("questionReads", repository.questionReads)
                .put("missingQuestionReads", repository.missingQuestionReads)
                .put("resets", repository.resets)
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("auth-recovery-evidence")
            ?: File(instrumentation.targetContext.filesDir, "auth-recovery-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}

private class FixtureAuthRepository : AuthRepository {
    var questionReads = 0
    var missingQuestionReads = 0
    var resetAttempts = 0
    var resets = 0

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> = Result.success(
        when (phone.filter(Char::isDigit)) {
            MissingPhone -> {
                missingQuestionReads += 1
                null
            }
            Phone -> {
                questionReads += 1
                PasswordRecoveryQuestion(secretQuestion = SecretQuestion)
            }
            else -> null
        },
    )

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> {
        resetAttempts += 1
        check(countryCode.filter(Char::isDigit) == CountryCode)
        check(phone.filter(Char::isDigit) == Phone)
        check(secretAnswer == SecretAnswer)
        check(newPassword == NewPassword)
        resets += 1
        return Result.success(Unit)
    }

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> =
        Result.failure(UnsupportedOperationException("fixture_login_not_used"))

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> =
        Result.failure(UnsupportedOperationException("fixture_register_not_used"))

    override suspend fun deactivateAccount(password: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("fixture_deactivate_not_used"))

    override suspend fun deleteAccountData(password: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("fixture_delete_not_used"))

    override suspend fun logout() = Unit

    companion object {
        const val CountryCode = "240"
        const val Phone = "600000001"
        const val MissingPhone = "699999999"
        const val SecretQuestion = "Nombre de tu primer barrio"
        const val SecretAnswer = "Bata"
        const val NewPassword = "fixture-reset-21085800"
    }
}
