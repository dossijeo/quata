package com.quata.feature.profile.data

import android.content.Context
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.profile.domain.SecretQuestionOption

fun Context.countryPrefixOptions(): List<CountryPrefix> = AuthCatalog.countryPrefixes(authCatalogLocale())

fun Context.authCatalog() = AuthCatalog.copy(authCatalogLocale())

private fun Context.authCatalogLocale(): AuthCatalogLocale =
    AuthCatalogLocale.fromLanguage(resources.configuration.locales[0]?.language)

fun Context.profileSecretQuestionOptions(): List<SecretQuestionOption> {
    val copy = authCatalog()
    return copy.secretQuestions.map { question ->
        SecretQuestionOption(
            value = question.value,
            label = if (question.value.isBlank()) copy.profileKeepCurrentSecretQuestion else question.label,
        )
    }
}

fun Context.registrationSecretQuestionOptions(): List<SecretQuestionOption> =
    authCatalog().secretQuestions.map { question -> SecretQuestionOption(question.value, question.label) }

fun Context.secretQuestionLabel(value: String): String =
    (profileSecretQuestionOptions() + registrationSecretQuestionOptions())
        .firstOrNull { it.value == value }
        ?.label
        .orEmpty()
