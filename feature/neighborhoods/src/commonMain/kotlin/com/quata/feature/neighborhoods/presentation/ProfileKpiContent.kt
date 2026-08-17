package com.quata.feature.neighborhoods.presentation

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun ProfileKpiContent(value: Int, label: String, modifier: Modifier = Modifier, testTag: String? = null, onClick: (() -> Unit)? = null) {
    val template = quataTheme()
    val animatedValue = animateIntAsState(value, tween(650), label = "profile_kpi_$label").value
    val interactiveModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val semanticsModifier = testTag?.let { tag -> Modifier.semantics { this.testTag = tag } } ?: Modifier
    Column(
        modifier
            .then(interactiveModifier)
            .then(semanticsModifier)
            .border(1.dp, template.colors.divider, RoundedCornerShape(16.dp))
            .background(template.colors.surface, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(animatedValue.toString(), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, fontSize = 13.sp)
    }
}
