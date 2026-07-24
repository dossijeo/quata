package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.presentation.AuthScreenLayoutContent
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.countryPrefixOptions

@Composable
fun LoginScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginAndroidViewModel = viewModel(factory = LoginAndroidViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is LoginEffect.Success) onLoginSuccess()
        }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = stringResource(R.string.auth_tagline),
        portraitLogoSpacing = 22.dp
    ) { isLandscape ->
            LoginForm(
                state = state,
                prefixes = prefixes,
                strings = LoginFormStrings(
                    phone = stringResource(R.string.auth_phone), password = stringResource(R.string.auth_password),
                    signingIn = stringResource(R.string.auth_signing_in), signIn = stringResource(R.string.auth_sign_in),
                    forgotPassword = stringResource(R.string.auth_forgot_password), createAccount = stringResource(R.string.auth_create_account),
                    searchPrefix = stringResource(R.string.profile_search_prefix), mockNotice = stringResource(R.string.auth_mock_notice),
                ),
                isLandscape = isLandscape,
                showMockNotice = AppConfig.USE_MOCK_BACKEND,
                onEvent = viewModel::onEvent,
                onForgotPassword = onForgotPassword,
                onGoToRegister = onGoToRegister,
            )
    }
}
