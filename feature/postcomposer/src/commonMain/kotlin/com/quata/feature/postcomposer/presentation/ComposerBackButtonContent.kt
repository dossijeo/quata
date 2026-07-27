package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy

@Composable
fun ComposerBackButtonContent(
    label: String,
    onBack: () -> Unit,
    accessibility: CriticalControlsAccessibilityCopy,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val control = accessibility.back
    Button(
        onClick = onBack,
        modifier = modifier
            .fillMaxWidth()
            .testTag("composer-back")
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = control.name
                stateDescription = "${control.state(isSelected = false, isEnabled = true)}; ${control.focus(focused)}"
            },
    ) {
        Text(label)
    }
}
