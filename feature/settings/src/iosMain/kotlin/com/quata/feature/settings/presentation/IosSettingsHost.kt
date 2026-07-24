package com.quata.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import platform.UIKit.UIViewController

/** Swift supplies persisted appearance state and all navigation/platform actions. */
class IosSettingsHostDependencies(
    val touchFlowEnabled: Boolean,
    val themeMode: QuataThemeMode,
    val strings: AppearanceSettingsStrings,
    val onTouchFlowEnabledChange: (Boolean) -> Unit,
    val onThemeModeChange: (QuataThemeMode) -> Unit,
    val onNavigate: (String) -> Unit,
    val onPlatformAction: (String) -> Unit,
    val onClose: () -> Unit,
)

fun QuataSettingsViewController(dependencies: IosSettingsHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme(mode = dependencies.themeMode) {
        Column {
            AppearanceSettingsSectionContent(
                touchFlowEnabled = dependencies.touchFlowEnabled,
                themeMode = dependencies.themeMode,
                strings = dependencies.strings,
                onTouchFlowEnabledChange = dependencies.onTouchFlowEnabledChange,
                onThemeModeChange = dependencies.onThemeModeChange,
            )
        }
    }
}
