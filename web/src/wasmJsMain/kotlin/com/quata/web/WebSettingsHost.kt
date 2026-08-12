package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.platform.DocumentOpenService
import com.quata.core.ui.components.QuataAccountLifecycleConfirmationDialogContent
import com.quata.feature.profile.presentation.ProfileAccountManagementContent
import com.quata.feature.profile.presentation.ProfileManagementAction
import com.quata.feature.settings.presentation.AppearanceSettingsSectionContent
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import com.quata.feature.settings.presentation.SettingsLegalDocumentsSectionContent
import com.quata.feature.settings.presentation.SettingsLegalDocumentsStrings
import kotlinx.coroutines.launch

interface WebAccountLifecycleActions {
    suspend fun deactivateAccount(password: String): Result<Unit>
    suspend fun deleteAccountData(password: String): Result<Unit>
}

internal class WebAuthAccountLifecycleActions(
    private val repository: WebAuthRepository,
) : WebAccountLifecycleActions {
    override suspend fun deactivateAccount(password: String): Result<Unit> = repository.deactivateAccount(password)
    override suspend fun deleteAccountData(password: String): Result<Unit> = repository.deleteAccountData(password)
}

private enum class WebAccountLifecycleAction { Deactivate, DeleteData }

/** Browser settings route: shared appearance and account-lifecycle presentation with injected work. */
@Composable
fun WebSettingsHost(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    webPushOptedIn: Boolean,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    onWebPushOptInChange: (Boolean) -> Unit,
    accountLifecycleActions: WebAccountLifecycleActions? = null,
    documentOpener: DocumentOpenService,
    onAccountLifecycleSuccess: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val language = remember { browserWhatsNewLanguageTags().toQuataLanguage() }
    var pendingAction by remember { mutableStateOf<WebAccountLifecycleAction?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AppearanceSettingsSectionContent(
            touchFlowEnabled = touchFlowEnabled,
            themeMode = themeMode,
            strings = AppearanceSettingsStrings(
                touchFlow = "Touch Flow",
                theme = "Tema",
                system = "Sistema",
                dark = "Oscuro",
                light = "Claro",
            ),
            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
            onThemeModeChange = onThemeModeChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notificaciones del navegador", style = MaterialTheme.typography.titleMedium)
            Text(
                if (webPushOptedIn) "Las notificaciones Web están activadas para este navegador."
                else "Activa las notificaciones solo si quieres recibir avisos en este navegador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WebNativeButton(
                label = if (webPushOptedIn) "Desactivar notificaciones" else "Activar notificaciones",
                enabled = true,
                onClick = { onWebPushOptInChange(!webPushOptedIn) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
        SettingsLegalDocumentsSectionContent(
            language = language,
            strings = SettingsLegalDocumentsStrings(title = "Documentos legales"),
            onOpenDocument = { document ->
                scope.launch { documentOpener.open(webLegalDocumentFile(document, language)) }
            },
        )
        accountLifecycleActions?.let { actions ->
            ProfileAccountManagementContent(
                title = "Gestión de cuenta",
                description = "Puedes desactivar tu cuenta o eliminar sus datos definitivamente.",
                descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actions = listOf(
                    ProfileManagementAction("Desactivar cuenta") {
                        pendingAction = WebAccountLifecycleAction.Deactivate
                        errorMessage = null
                    },
                    ProfileManagementAction("Eliminar datos de la cuenta") {
                        pendingAction = WebAccountLifecycleAction.DeleteData
                        errorMessage = null
                    },
                ),
                backButton = {},
            )
        }
        successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    pendingAction?.let { action -> accountLifecycleActions?.let { actions ->
        val isDeletion = action == WebAccountLifecycleAction.DeleteData
        QuataAccountLifecycleConfirmationDialogContent(
            title = if (isDeletion) "Eliminar datos de la cuenta" else "Desactivar cuenta",
            body = if (isDeletion) {
                "Esta acción elimina tus datos y no se puede deshacer."
            } else {
                "Tu cuenta dejará de estar activa hasta que la reactives."
            },
            passwordPrompt = "Confirma tu contraseña para continuar.",
            passwordLabel = "Contraseña",
            cancelLabel = "Cancelar",
            confirmLabel = if (isDeletion) "Eliminar" else "Desactivar",
            isWorking = isWorking,
            errorMessage = errorMessage,
            onDismiss = {
                if (!isWorking) pendingAction = null
                Unit
            },
            onConfirm = { password ->
                scope.launch {
                    isWorking = true
                    errorMessage = null
                    val result = if (isDeletion) actions.deleteAccountData(password) else actions.deactivateAccount(password)
                    isWorking = false
                    result.onSuccess {
                        pendingAction = null
                        successMessage = if (isDeletion) "Datos eliminados correctamente." else "Cuenta desactivada correctamente."
                        onAccountLifecycleSuccess()
                    }.onFailure { failure ->
                        errorMessage = failure.message ?: "No se pudo completar la operación."
                    }
                    Unit
                }
            },
            confirmationPrompt = if (isDeletion) "Escribe ELIMINAR para confirmar." else null,
            requiredConfirmation = if (isDeletion) "ELIMINAR" else null,
        )
    } }
}
