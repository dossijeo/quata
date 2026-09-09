package com.quata.feature.auth.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.AuthSession
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.presentation.recovery.ForgotPasswordScreen
import com.quata.feature.auth.presentation.recovery.ForgotPasswordTestTags
import com.quata.feature.profile.data.authCatalog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Local Android wrapper check: no network, fixtures, screenshots or production sessions. */
@RunWith(AndroidJUnit4::class)
class RecoveryReturnInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun immediateSuccessReturnsOnce() = verifyReturn(deferred = false)
    @Test fun asynchronousSuccessReturnsOnce() = verifyReturn(deferred = true)
    @Test fun leavingDuringRequestDoesNotReturnLater() = verifyReturn(deferred = true, abandon = true)

    private fun verifyReturn(deferred: Boolean, abandon: Boolean = false) {
        val release = CompletableDeferred<Unit>()
        if (!deferred) release.complete(Unit)
        val resets = AtomicInteger()
        val returns = AtomicInteger()
        val visible = mutableStateOf(true)
        val repository = object : AuthRepository {
            override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String) =
                Result.success(PasswordRecoveryQuestion(secretQuestion = "madre"))
            override suspend fun resetPassword(countryCode: String, phone: String, secretAnswer: String, newPassword: String): Result<Unit> {
                assertEquals("240", countryCode)
                assertEquals("600000001", phone)
                assertEquals("synthetic-answer", secretAnswer)
                assertEquals("synthetic-password", newPassword)
                resets.incrementAndGet()
                release.await()
                return Result.success(Unit)
            }
            override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = error("unused")
            override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = error("unused")
            override suspend fun deactivateAccount(password: String): Result<Unit> = error("unused")
            override suspend fun deleteAccountData(password: String): Result<Unit> = error("unused")
            override suspend fun logout() = Unit
        }
        val label = compose.activity.authCatalog().secretQuestions.single { it.value == "madre" }.label
        compose.setContent {
            QuataTheme(mode = QuataThemeMode.Light) {
                if (visible.value) ForgotPasswordScreen(PaddingValues(0.dp), repository, onBack = {
                    assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                    returns.incrementAndGet()
                })
            }
        }
        fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
        node(ForgotPasswordTestTags.Phone).performTextReplacement("600000001")
        compose.waitUntil(10_000) {
            node(ForgotPasswordTestTags.Question).fetchSemanticsNode().config[SemanticsProperties.EditableText].text == label
        }
        node(ForgotPasswordTestTags.SecretAnswer).performTextReplacement("synthetic-answer")
        node(ForgotPasswordTestTags.NewPassword).performTextReplacement("synthetic-password")
        val submit = compose.onAllNodesWithTag(ForgotPasswordTestTags.Submit, useUnmergedTree = true).filterToOne(hasClickAction())
        submit.performScrollTo().performClick()
        compose.waitUntil(10_000) { resets.get() == 1 }
        if (deferred) {
            submit.assertIsNotEnabled()
            assertEquals(0, returns.get())
            if (abandon) {
                compose.runOnIdle { visible.value = false }
                compose.waitForIdle()
                release.complete(Unit)
                compose.waitForIdle()
                assertEquals(0, returns.get())
                return
            }
            release.complete(Unit)
        }
        compose.waitUntil(10_000) { returns.get() == 1 }
        submit.assertIsEnabled()
        assertEquals(1, resets.get())
        assertEquals(1, returns.get())
    }
}
