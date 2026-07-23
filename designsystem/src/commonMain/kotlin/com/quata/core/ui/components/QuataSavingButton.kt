package com.quata.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

@Composable
fun QuataSavingButton(
    isSaving: Boolean,
    savingText: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = modifier.fillMaxWidth().height(40.dp).compactButtonMinSize(),
        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
        shape = RoundedCornerShape(9.dp),
        contentPadding = CompactButtonContentPadding
    ) {
        Text(if (isSaving) savingText else actionText, fontWeight = FontWeight.ExtraBold)
    }
}
