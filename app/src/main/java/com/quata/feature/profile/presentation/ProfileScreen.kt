package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.session.SessionManager
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton

@Composable
fun ProfileScreen(
    padding: PaddingValues,
    sessionManager: SessionManager,
    onLogout: () -> Unit
) {
    val session = sessionManager.currentSession()

    QuataScreen(padding) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Perfil", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            QuataCard {
                Column(Modifier.padding(20.dp)) {
                    AvatarLetter(session?.displayName ?: "Q")
                    Spacer(Modifier.height(16.dp))
                    Text(session?.displayName ?: "Usuario", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(session?.email ?: "Sin email", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Aquí puedes añadir edición de perfil, ajustes de privacidad y conexión a WordPress/Supabase.")
                }
            }
            QuataSecondaryButton("Ver configuración técnica", modifier = Modifier.fillMaxWidth()) { }
            QuataPrimaryButton("Cerrar sesión") {
                sessionManager.clearSession()
                onLogout()
            }
        }
    }
}
