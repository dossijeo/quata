package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.QuataSecondaryButton

/** Shared empty/error presentation for the conversation root on every launcher. */
@Composable
fun ConversationsStatusContent(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    messageTag: String,
    actionTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag(messageTag),
        )
        Spacer(Modifier.height(12.dp))
        QuataSecondaryButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.testTag(actionTag),
        )
    }
}
