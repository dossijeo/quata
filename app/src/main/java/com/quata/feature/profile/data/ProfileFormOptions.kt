package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.feature.profile.domain.CountryPrefix
import com.quata.feature.profile.domain.SecretQuestionOption

fun Context.countryPrefixOptions(): List<CountryPrefix> {
    val codes = resources.getStringArray(R.array.country_prefix_codes)
    val labels = resources.getStringArray(R.array.country_prefix_labels)
    require(codes.size == labels.size) { "Country prefix resources must have the same size" }
    return codes.zip(labels) { code, label -> CountryPrefix(code, label) }
}

fun Context.profileSecretQuestionOptions(): List<SecretQuestionOption> =
    listOf(
        SecretQuestionOption("", getString(R.string.secret_question_keep_current)),
        SecretQuestionOption("madre", getString(R.string.secret_question_mother)),
        SecretQuestionOption("barrio", getString(R.string.secret_question_neighborhood)),
        SecretQuestionOption("amigo", getString(R.string.secret_question_friend)),
        SecretQuestionOption("comida", getString(R.string.secret_question_food))
    )

fun Context.registrationSecretQuestionOptions(): List<SecretQuestionOption> =
    listOf(
        SecretQuestionOption("", getString(R.string.secret_question_select)),
        SecretQuestionOption("madre", getString(R.string.secret_question_mother)),
        SecretQuestionOption("barrio", getString(R.string.secret_question_neighborhood)),
        SecretQuestionOption("amigo", getString(R.string.secret_question_friend)),
        SecretQuestionOption("comida", getString(R.string.secret_question_food))
    )

fun Context.secretQuestionLabel(value: String): String =
    (profileSecretQuestionOptions() + registrationSecretQuestionOptions())
        .firstOrNull { it.value == value }
        ?.label
        .orEmpty()
