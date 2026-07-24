package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.compactButtonMinSize

/** Shared Composer action control; hosts provide system-backed icon/action through slots. */
@Composable
fun ComposerActionButtonContent(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp).compactButtonMinSize(),
        shape = RoundedCornerShape(9.dp),
        contentPadding = CompactButtonContentPadding
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.ExtraBold)
    }
}
