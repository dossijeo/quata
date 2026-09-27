package com.quata.feature.settings.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsAccountLifecycleFailureMessageTest {
    private val strings = SettingsAccountLifecycleStrings(
        title = "title",
        description = "description",
        deactivate = "deactivate",
        deleteData = "delete",
        deactivateTitle = "deactivate title",
        deactivateBody = "deactivate body",
        deleteTitle = "delete title",
        deleteBody = "delete body",
        passwordPrompt = "password prompt",
        passwordLabel = "password",
        cancel = "cancel",
        deactivateConfirm = "confirm",
        deleteConfirm = "delete confirm",
        deleteConfirmationPrompt = "type delete",
        deleteConfirmationWord = "DELETE",
        deactivateSuccess = "deactivated",
        deleteSuccess = "deleted",
        incorrectPassword = "incorrect password",
        genericError = "generic error",
    )

    @Test
    fun backendPasswordCodesMapToTheLocalizedPasswordError() {
        for (code in listOf("account_password_incorrect", "web_auth_invalid_password", "ios_auth_invalid_password")) {
            assertEquals("incorrect password", accountLifecycleFailureMessage(IllegalStateException(code), strings))
        }
    }

    @Test
    fun transportAndUnknownFailuresNeverExposeTechnicalMessages() {
        for (code in listOf("transport_offline", "web_auth_http_503", "unexpected_internal_detail")) {
            assertEquals("generic error", accountLifecycleFailureMessage(IllegalStateException(code), strings))
        }
    }
}
