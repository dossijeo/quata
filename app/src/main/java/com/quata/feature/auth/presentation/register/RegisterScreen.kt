package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.AuthResponsiveLayout
import com.quata.feature.auth.presentation.register.RegisterForm
import com.quata.feature.auth.presentation.register.RegisterFormStrings
import com.quata.feature.auth.presentation.register.RegisterSecretQuestion
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.registrationSecretQuestionOptions

@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterAndroidViewModel = viewModel(factory = RegisterAndroidViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val secretQuestions = remember(context) { context.registrationSecretQuestionOptions() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { if (it is RegisterEffect.Success) onRegisterSuccess() }
    }

    AuthResponsiveLayout(
        padding = padding,
        subtitle = stringResource(R.string.auth_create_account_title),
        portraitLogoSpacing = 14.dp
    ) { isLandscape ->
        RegisterForm(
            state = state,
            prefixes = prefixes,
            secretQuestions = secretQuestions.map { RegisterSecretQuestion(it.value, it.label) },
            strings = RegisterFormStrings(
                displayName = stringResource(R.string.auth_name), neighborhood = stringResource(R.string.auth_neighborhood_community),
                phone = stringResource(R.string.auth_phone), password = stringResource(R.string.auth_password), secretAnswer = stringResource(R.string.auth_secret_answer),
                searchPrefix = stringResource(R.string.profile_search_prefix), creating = stringResource(R.string.auth_creating),
                createAccount = stringResource(R.string.auth_create_account), back = stringResource(R.string.common_back),
            ),
            isLandscape = isLandscape,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
    }
}
