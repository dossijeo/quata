package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.moderation.LegalDocument
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openWithViewerState
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import com.quata.feature.settings.presentation.SettingsAccountLifecycleActions
import com.quata.feature.settings.presentation.SettingsAccountLifecycleStrings
import com.quata.feature.settings.presentation.SettingsNotificationsStrings
import com.quata.feature.settings.presentation.SettingsScreenHost
import com.quata.feature.settings.presentation.SettingsScreenStrings
import com.quata.feature.settings.presentation.settingsLegalDocumentsStrings
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
    var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
    DisposableEffect(Unit) {
        val uninstall = installWebDocumentStatusE2eBridge("settings") {
            documentViewerState = null
        }
        onDispose { uninstall() }
    }
    val openLegalDocument: (LegalDocument) -> Unit = { document ->
        scope.launch {
            val file = webLegalDocumentFile(document, language)
            documentViewerState = documentViewerOpeningState(file)
            documentViewerState = documentOpener.openWithViewerState(file).completed
        }
        Unit
    }
    DisposableEffect(language, documentOpener) {
        val uninstall = installWebLegalDocumentsE2eBridge(
            surface = "settings",
            openPrivacy = { openLegalDocument(LegalDocument.Privacy) },
            openChildSafety = { openLegalDocument(LegalDocument.ChildSafety) },
            dismissStatus = { documentViewerState = null },
        )
        onDispose { uninstall() }
    }
    SettingsScreenHost(
        touchFlowEnabled = touchFlowEnabled,
        themeMode = themeMode,
        strings = SettingsScreenStrings(
            appearance = AppearanceSettingsStrings(
                touchFlow = "Touch Flow",
                theme = "Tema",
                system = "Sistema",
                dark = "Oscuro",
                light = "Claro",
            ),
            legalDocuments = settingsLegalDocumentsStrings(language),
            notifications = SettingsNotificationsStrings(
                title = "Notificaciones del navegador",
                enabledBody = "Las notificaciones Web están activadas para este navegador.",
                disabledBody = "Activa las notificaciones solo si quieres recibir avisos en este navegador.",
                enableLabel = "Activar notificaciones",
                disableLabel = "Desactivar notificaciones",
            ),
            accountLifecycle = accountLifecycleActions?.let {
                SettingsAccountLifecycleStrings(
                title = "Gestión de cuenta",
                description = "Puedes desactivar tu cuenta o eliminar sus datos definitivamente.",
                    deactivate = "Desactivar cuenta",
                    deleteData = "Eliminar datos de la cuenta",
                    deactivateTitle = "Desactivar cuenta",
                    deactivateBody = "Tu cuenta dejará de estar activa hasta que la reactives.",
                    deleteTitle = "Eliminar datos de la cuenta",
                    deleteBody = "Esta acción elimina tus datos y no se puede deshacer.",
                    passwordPrompt = "Confirma tu contraseña para continuar.",
                    passwordLabel = "Contraseña",
                    cancel = "Cancelar",
                    deactivateConfirm = "Desactivar",
                    deleteConfirm = "Eliminar",
                    deleteConfirmationPrompt = "Escribe ELIMINAR para confirmar.",
                    deleteConfirmationWord = "ELIMINAR",
                    deactivateSuccess = "Cuenta desactivada correctamente.",
                    deleteSuccess = "Datos eliminados correctamente.",
                    genericError = "No se pudo completar la operación.",
                )
            },
        ),
        language = language,
        onTouchFlowEnabledChange = onTouchFlowEnabledChange,
        onThemeModeChange = onThemeModeChange,
        onOpenDocument = openLegalDocument,
        modifier = modifier,
        notificationsEnabled = webPushOptedIn,
        onNotificationsEnabledChange = onWebPushOptInChange,
        accountLifecycleActions = accountLifecycleActions?.let { actions ->
            SettingsAccountLifecycleActions(
                deactivateAccount = actions::deactivateAccount,
                deleteAccountData = actions::deleteAccountData,
            )
        },
        onAccountLifecycleSuccess = onAccountLifecycleSuccess,
    )
    QuataDocumentViewerStatusContent(
        state = documentViewerState,
        strings = webDocumentViewerStatusStrings(browserWhatsNewLanguageTags()),
        onDismiss = { documentViewerState = null },
    )
}
