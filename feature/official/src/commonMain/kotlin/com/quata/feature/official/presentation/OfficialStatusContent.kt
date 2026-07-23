package com.quata.feature.official.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon

data class OfficialStatusStrings(val empty: String, val create: String)

@Composable
fun OfficialLoadingContent(canPublish: Boolean, strings: OfficialStatusStrings, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = quataTheme().colors.surfaceAlt), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxSize().padding(bottom = 10.dp)) {}
        if (canPublish) OfficialCreateAction(strings.create, onCreate, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 16.dp))
    }
}

@Composable
fun OfficialEmptyContent(canPublish: Boolean, strings: OfficialStatusStrings, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = quataTheme().colors.surfaceAlt), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxSize().padding(bottom = 10.dp)) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(Icons.Filled.Info, null, tint = QuataOrange, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp)); Text(strings.empty, fontWeight = FontWeight.Bold)
            }
        }
        if (canPublish) OfficialCreateAction(strings.create, onCreate, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 16.dp))
    }
}

@Composable
private fun OfficialCreateAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(color = QuataOrange, contentColor = Color.White, shape = CircleShape, shadowElevation = 6.dp, border = BorderStroke(2.dp, Color.White.copy(alpha = .88f)), modifier = modifier.size(48.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) { CompactIcon(Icons.Filled.Add, label, modifier = Modifier.size(24.dp)) }
    }
}
