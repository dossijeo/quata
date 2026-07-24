package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/**
 * Shared layout for a text post. System emoji pickers and publication actions are slots so
 * launchers, localized resources and platform policies remain in the host.
 */
@Composable
fun ComposerTextPostFormContent(
    isLandscapeLayout: Boolean,
    textValue: TextFieldValue,
    contentTitle: String,
    placeholder: String,
    wordCountText: String,
    minLines: Int,
    onTextChange: (TextFieldValue) -> Unit,
    trailingInputAction: @Composable () -> Unit,
    emojiPanel: @Composable ColumnScope.() -> Unit,
    preview: @Composable ColumnScope.() -> Unit,
    publish: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    @Composable
    fun ColumnScope.InputAndEmoji() {
        ComposerTextFormPanelContent(title = contentTitle, highlighted = true) {
            val template = quataTheme()
            OutlinedTextField(
                value = textValue,
                onValueChange = onTextChange,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                placeholder = { Text(placeholder, color = template.colors.textSecondary) },
                minLines = minLines,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = template.colors.textPrimary,
                    unfocusedTextColor = template.colors.textPrimary,
                    focusedBorderColor = template.colors.accent,
                    unfocusedBorderColor = template.colors.divider,
                    cursorColor = template.colors.accent
                ),
                trailingIcon = trailingInputAction
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(wordCountText, color = template.colors.textSecondary)
            }
        }
        emojiPanel()
    }

    Column(modifier) {
        if (isLandscapeLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    InputAndEmoji()
                    publish()
                }
                Column(Modifier.weight(1f)) { preview() }
            }
        } else {
            InputAndEmoji()
            preview()
            publish()
        }
    }
}

@Composable
private fun ComposerTextFormPanelContent(
    title: String,
    highlighted: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val template = quataTheme()
    val shape = RoundedCornerShape(24.dp)
    Surface(
        color = if (highlighted) template.colors.surfaceRaised else template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(1.dp, template.colors.divider, shape)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), color = template.colors.textPrimary.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
    Spacer(Modifier.height(18.dp))
}
