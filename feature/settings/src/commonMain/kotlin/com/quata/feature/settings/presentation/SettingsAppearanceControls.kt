package com.quata.feature.settings.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.ui.components.QuataAccountLifecycleConfirmationDialogContent
import com.quata.core.ui.components.QuataLegalDocumentLinksContent
import com.quata.core.ui.components.QuataPanel
import kotlinx.coroutines.launch

data class AppearanceSettingsStrings(val touchFlow: String, val theme: String, val system: String, val dark: String, val light: String)
data class SettingsLegalDocumentsStrings(val title: String)
data class SettingsNotificationsStrings(
    val title: String,
    val enabledBody: String,
    val disabledBody: String,
    val enableLabel: String,
    val disableLabel: String,
)
data class SettingsAccountLifecycleStrings(
    val title: String,
    val description: String,
    val deactivate: String,
    val deleteData: String,
    val deactivateTitle: String,
    val deactivateBody: String,
    val deleteTitle: String,
    val deleteBody: String,
    val passwordPrompt: String,
    val passwordLabel: String,
    val cancel: String,
    val deactivateConfirm: String,
    val deleteConfirm: String,
    val deleteConfirmationPrompt: String,
    val deleteConfirmationWord: String,
    val deactivateSuccess: String,
    val deleteSuccess: String,
    val genericError: String,
)
data class SettingsScreenStrings(
    val appearance: AppearanceSettingsStrings,
    val legalDocuments: SettingsLegalDocumentsStrings,
    val notifications: SettingsNotificationsStrings? = null,
    val accountLifecycle: SettingsAccountLifecycleStrings? = null,
    val logout: String? = null,
)
data class SettingsAccountLifecycleActions(
    val deactivateAccount: suspend (password: String) -> Result<Unit>,
    val deleteAccountData: suspend (password: String) -> Result<Unit>,
)

object SettingsScreenTestTags {
    const val Root = "settings-screen-root"
    const val Notifications = "settings-notifications-section"
    const val LegalDocuments = "settings-legal-documents-section"
    const val AccountLifecycle = "settings-account-lifecycle-section"
    const val Logout = "settings-logout"
}

fun settingsLegalDocumentsStrings(language: QuataLanguage): SettingsLegalDocumentsStrings =
    SettingsLegalDocumentsStrings(
        title = when (language) {
            QuataLanguage.Spanish -> "Documentos legales"
            QuataLanguage.French -> "Documents juridiques"
            QuataLanguage.English -> "Legal documents"
        },
    )

@Composable
fun SettingsScreenHost(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    strings: SettingsScreenStrings,
    language: QuataLanguage,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
    notificationsEnabled: Boolean? = null,
    onNotificationsEnabledChange: ((Boolean) -> Unit)? = null,
    accountLifecycleActions: SettingsAccountLifecycleActions? = null,
    onAccountLifecycleSuccess: () -> Unit = {},
    onLogout: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<SettingsDangerousAction?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.testTag(SettingsScreenTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AppearanceSettingsSectionContent(
            touchFlowEnabled = touchFlowEnabled,
            themeMode = themeMode,
            strings = strings.appearance,
            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
            onThemeModeChange = onThemeModeChange,
        )
        if (strings.notifications != null && notificationsEnabled != null && onNotificationsEnabledChange != null) {
            SettingsNotificationsSectionContent(
                enabled = notificationsEnabled,
                strings = strings.notifications,
                onEnabledChange = onNotificationsEnabledChange,
            )
        }
        SettingsLegalDocumentsSectionContent(
            language = language,
            strings = strings.legalDocuments,
            onOpenDocument = onOpenDocument,
        )
        if (strings.accountLifecycle != null && accountLifecycleActions != null) {
            SettingsAccountLifecycleSectionContent(
                strings = strings.accountLifecycle,
                onDeactivate = {
                    pendingAction = SettingsDangerousAction.Deactivate
                    errorMessage = null
                },
                onDelete = {
                    pendingAction = SettingsDangerousAction.DeleteData
                    errorMessage = null
                },
            )
        }
        strings.logout?.let { logout ->
            onLogout?.let {
                OutlinedButton(
                    onClick = it,
                    modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTestTags.Logout),
                ) {
                    Text(logout, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    pendingAction?.let { action ->
        val lifecycleStrings = strings.accountLifecycle ?: return@let
        val lifecycleActions = accountLifecycleActions ?: return@let
        val isDeletion = action == SettingsDangerousAction.DeleteData
        QuataAccountLifecycleConfirmationDialogContent(
            title = if (isDeletion) lifecycleStrings.deleteTitle else lifecycleStrings.deactivateTitle,
            body = if (isDeletion) lifecycleStrings.deleteBody else lifecycleStrings.deactivateBody,
            passwordPrompt = lifecycleStrings.passwordPrompt,
            passwordLabel = lifecycleStrings.passwordLabel,
            cancelLabel = lifecycleStrings.cancel,
            confirmLabel = if (isDeletion) lifecycleStrings.deleteConfirm else lifecycleStrings.deactivateConfirm,
            isWorking = isWorking,
            errorMessage = errorMessage,
            onDismiss = { if (!isWorking) pendingAction = null },
            onConfirm = { password ->
                scope.launch {
                    isWorking = true
                    errorMessage = null
                    val result = if (isDeletion) {
                        lifecycleActions.deleteAccountData(password)
                    } else {
                        lifecycleActions.deactivateAccount(password)
                    }
                    isWorking = false
                    result.onSuccess {
                        pendingAction = null
                        successMessage = if (isDeletion) lifecycleStrings.deleteSuccess else lifecycleStrings.deactivateSuccess
                        onAccountLifecycleSuccess()
                    }.onFailure { failure ->
                        errorMessage = failure.message ?: lifecycleStrings.genericError
                    }
                }
            },
            confirmationPrompt = if (isDeletion) lifecycleStrings.deleteConfirmationPrompt else null,
            requiredConfirmation = if (isDeletion) lifecycleStrings.deleteConfirmationWord else null,
        )
    }
}

/** Shared settings-card shell; the host supplies only localized strings and persisted values. */
@Composable
fun AppearanceSettingsSectionContent(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    strings: AppearanceSettingsStrings,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        AppearanceSettingsControls(
            touchFlowEnabled = touchFlowEnabled,
            themeMode = themeMode,
            strings = strings,
            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
            onThemeModeChange = onThemeModeChange,
        )
    }
}

@Composable
fun AppearanceSettingsControls(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    strings: AppearanceSettingsStrings,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.touchFlow, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Switch(touchFlowEnabled, onTouchFlowEnabledChange)
        }
        Text(strings.theme, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeModeOption(strings.system, themeMode == QuataThemeMode.System, { onThemeModeChange(QuataThemeMode.System) }, Modifier.weight(1f))
            ThemeModeOption(strings.dark, themeMode == QuataThemeMode.Dark, { onThemeModeChange(QuataThemeMode.Dark) }, Modifier.weight(1f))
            ThemeModeOption(strings.light, themeMode == QuataThemeMode.Light, { onThemeModeChange(QuataThemeMode.Light) }, Modifier.weight(1f))
        }
    }
}

/** Shared legal-documents settings section; platform hosts only resolve the selected document. */
@Composable
fun SettingsLegalDocumentsSectionContent(
    language: QuataLanguage,
    strings: SettingsLegalDocumentsStrings,
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier.testTag(SettingsScreenTestTags.LegalDocuments),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.title, fontWeight = FontWeight.ExtraBold)
            QuataLegalDocumentLinksContent(
                language = language,
                onOpenDocument = onOpenDocument,
            )
        }
    }
}

@Composable
private fun SettingsNotificationsSectionContent(
    enabled: Boolean,
    strings: SettingsNotificationsStrings,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier.testTag(SettingsScreenTestTags.Notifications),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.title, fontWeight = FontWeight.ExtraBold)
            Text(
                text = if (enabled) strings.enabledBody else strings.disabledBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onEnabledChange(!enabled) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (enabled) strings.disableLabel else strings.enableLabel)
            }
        }
    }
}

@Composable
private fun SettingsAccountLifecycleSectionContent(
    strings: SettingsAccountLifecycleStrings,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier.testTag(SettingsScreenTestTags.AccountLifecycle),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.title, fontWeight = FontWeight.ExtraBold)
            Text(strings.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onDeactivate, modifier = Modifier.fillMaxWidth()) {
                Text(strings.deactivate, fontWeight = FontWeight.ExtraBold)
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(strings.deleteData, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ThemeModeOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val template = quataTheme()
    Surface(color = if (selected) template.colors.accent else template.colors.surfaceAlt, contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary, shape = RoundedCornerShape(14.dp), modifier = modifier.height(40.dp).border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = template.textSizes.caption, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private enum class SettingsDangerousAction { Deactivate, DeleteData }
