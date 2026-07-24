package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared profile-details structure; navigation, fields and persistence action are host slots. */
@Composable
fun ProfileDetailsFormContent(
    title: String,
    bottomSpacing: androidx.compose.ui.unit.Dp,
    backAction: @Composable () -> Unit,
    fields: @Composable () -> Unit,
    saveAction: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            backAction()
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().padding(end = 48.dp)
            )
        }
        fields()
        Spacer(Modifier.height(bottomSpacing))
        saveAction()
    }
}
