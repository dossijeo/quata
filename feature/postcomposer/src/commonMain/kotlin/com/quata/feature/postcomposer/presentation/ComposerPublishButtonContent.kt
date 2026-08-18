package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy
import com.quata.core.ui.components.compactButtonMinSize
import kotlinx.coroutines.delay

/** Portable publishing action with its visual progress affordance. */
const val ComposerPublishButtonTestTag = "composer-publish"

@Composable
fun ComposerPublishButtonContent(
    isLoading: Boolean,
    publishLabel: String,
    publishingLabel: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    accessibility: CriticalControlsAccessibilityCopy? = null,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        progress = 0f
        while (isLoading) {
            delay(180L)
            progress = (progress + 0.018f).coerceAtMost(0.92f)
        }
    }
    val accessibilityModifier = if (accessibility != null) {
        val control = accessibility.publish
        Modifier
            .testTag(ComposerPublishButtonTestTag)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (isLoading) disabled()
                contentDescription = "${control.name} $ComposerPublishButtonTestTag"
                stateDescription = "${control.state(isSelected = false, isEnabled = !isLoading)}; ${control.focus(focused)}"
                onClick(label = publishLabel) {
                    if (!isLoading) onSubmit()
                    !isLoading
                }
            }
    } else {
        Modifier
    }
    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = QuataOrange,
            contentColor = Color.Black,
            disabledContainerColor = Color(0xFFFFB45E),
            disabledContentColor = Color.Black,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .compactButtonMinSize()
            .then(accessibilityModifier),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .align(Alignment.CenterStart)
                        .background(Color(0xFFE86F12))
                )
            }
            Text(
                if (isLoading) publishingLabel else publishLabel,
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
