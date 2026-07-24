package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/**
 * Shared composer page structure. Hosts retain media editors, permission launchers and the
 * concrete form/feedback content through slots while title, scroll and vertical rhythm stay common.
 */
@Composable
fun ComposerScreenLayoutContent(
    title: String,
    scrollState: ScrollState,
    form: @Composable ColumnScope.() -> Unit,
    feedback: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = template.colors.textPrimary,
        )
        Spacer(Modifier.height(28.dp))
        form()
        feedback()
    }
}
