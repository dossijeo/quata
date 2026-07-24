package com.quata.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Honest recovery surface when a browser share payload exceeds the safe worker limits. */
@Composable
fun WebShareTargetErrorHost(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No se pudo recibir el contenido compartido. Prueba con menos archivos o archivos de hasta 25 MB.")
        Button(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) { Text("Volver a chats") }
    }
}
