package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared composition of a community profile identity/header section. Platform actions are slots. */
@Composable
fun CommunityProfileHeaderContent(
    displayName: String,
    neighborhood: String,
    avatar: @Composable () -> Unit,
    kpis: @Composable () -> Unit,
    primaryActions: @Composable () -> Unit,
    moderationActions: @Composable () -> Unit,
    adminControls: (@Composable () -> Unit)?,
    errorMessage: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        Text(displayName, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(neighborhood, color = template.colors.textSecondary, fontSize = 16.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { avatar() }
        Spacer(Modifier.height(24.dp))
        kpis()
        Spacer(Modifier.height(20.dp))
        primaryActions()
        moderationActions()
        adminControls?.invoke()
        errorMessage?.invoke()
        Spacer(Modifier.height(18.dp))
    }
}
