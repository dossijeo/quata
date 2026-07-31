package com.quata.feature.profile.data

import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSecretQuestionsTest {
    @Test
    fun profile_questions_reuse_the_exact_localized_auth_catalogue() {
        AuthCatalogLocale.entries.forEach { locale ->
            val auth = AuthCatalog.copy(locale)
            val profile = profileSecretQuestions(locale)
            assertEquals(auth.secretQuestions.map { it.value }, profile.map { it.value })
            assertEquals(auth.profileKeepCurrentSecretQuestion, profile.first().label)
            assertEquals(auth.secretQuestions.drop(1).map { it.label }, profile.drop(1).map { it.label })
        }
    }
}
