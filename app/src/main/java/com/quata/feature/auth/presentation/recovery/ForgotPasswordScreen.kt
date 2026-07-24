package com.quata.feature.auth.presentation.recovery

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactTextFieldHeight
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.AuthScreenLayoutContent
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.secretQuestionLabel

@Composable
fun ForgotPasswordScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    viewModel: ForgotPasswordAndroidViewModel = viewModel(factory = ForgotPasswordAndroidViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val secretQuestion = remember(context, state.secretQuestion, state.isLoadingQuestion) {
        when {
            state.isLoadingQuestion -> context.getString(R.string.auth_loading_secret_question)
            state.secretQuestion.isBlank() -> context.getString(R.string.auth_secret_question_waiting)
            else -> context.secretQuestionLabel(state.secretQuestion)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is ForgotPasswordEffect.PasswordUpdated) {
                Toast.makeText(context, context.getString(R.string.auth_password_updated), Toast.LENGTH_SHORT).show()
                onBack()
            }
        }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = stringResource(R.string.auth_recover_password_title),
        portraitLogoSpacing = 14.dp
    ) { isLandscape ->
            ForgotPasswordForm(
                state = state,
                prefixes = prefixes,
                resolvedQuestion = secretQuestion,
                strings = ForgotPasswordFormStrings(
                    phone = stringResource(R.string.auth_your_phone), searchPrefix = stringResource(R.string.profile_search_prefix),
                    secretQuestion = stringResource(R.string.auth_your_secret_question), secretAnswer = stringResource(R.string.auth_your_secret_answer),
                    newPassword = stringResource(R.string.auth_new_password), saving = stringResource(R.string.common_saving),
                    updatePassword = stringResource(R.string.auth_update_password), back = stringResource(R.string.common_back),
                ),
                isLandscape = isLandscape,
                onEvent = viewModel::onEvent,
                onBack = onBack,
            )
    }
}
