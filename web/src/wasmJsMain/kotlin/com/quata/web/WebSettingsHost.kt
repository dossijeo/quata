package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.feature.settings.presentation.AppearanceSettingsSectionContent
import com.quata.feature.settings.presentation.AppearanceSettingsStrings

/** Browser route adapter for the shared appearance controls. */
@Composable
fun WebSettingsHost(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
    )
}
