package com.quata.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.UgcTermsGateway
import com.quata.core.moderation.flushPendingAcceptanceIfSupported
import kotlinx.coroutines.launch

data class QuataUgcTermsStrings(
    val title: String,
    val body: String,
    val accept: String,
    val accepting: String,
    val checking: String,
    val logout: String,
    val acceptError: String,
)

fun quataUgcTermsStrings(language: QuataLanguage): QuataUgcTermsStrings = when (language) {
    QuataLanguage.Spanish -> QuataUgcTermsStrings(
        title = "Normas de la comunidad",
        body = "Para participar y compartir contenido en Qüata debes aceptar nuestras normas de comunidad y seguridad infantil. No se permite contenido ilegal, abusivo, sexual, violento, de odio o de explotación. Puedes denunciar contenido y bloquear usuarios en cualquier momento.",
        accept = "Acepto",
        accepting = "Guardando...",
        checking = "Comprobando...",
        logout = "Cerrar sesión",
        acceptError = "No hemos podido guardar tu aceptación. Comprueba la conexión e inténtalo de nuevo.",
    )
    QuataLanguage.French -> QuataUgcTermsStrings(
        title = "Règles de la communauté",
        body = "Pour participer et partager du contenu sur Qüata, vous devez accepter nos règles de communauté et de sécurité des enfants. Les contenus illégaux, abusifs, sexuels, violents, haineux ou relevant de l'exploitation sont interdits. Vous pouvez signaler du contenu et bloquer des utilisateurs à tout moment.",
        accept = "J'accepte",
        accepting = "Enregistrement...",
        checking = "Vérification...",
        logout = "Se déconnecter",
        acceptError = "Impossible d'enregistrer votre acceptation. Vérifiez la connexion et réessayez.",
    )
    QuataLanguage.English -> QuataUgcTermsStrings(
        title = "Community rules",
        body = "To participate and share content on Qüata, you must accept our community and child safety standards. Illegal, abusive, sexual, violent, hateful or exploitative content is not permitted. You can report content and block users at any time.",
        accept = "I accept",
        accepting = "Saving...",
        checking = "Checking...",
        logout = "Sign out",
        acceptError = "We could not save your acceptance. Check your connection and try again.",
    )
}

@Composable
fun QuataUgcTermsGateContent(
    profileId: String?,
    gateway: UgcTermsGateway,
    strings: QuataUgcTermsStrings,
    onAcceptedStateChanged: (Boolean?) -> Unit = {},
    onLogout: () -> Unit,
    legalLinks: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var accepted by remember(profileId) { mutableStateOf<Boolean?>(null) }
    var accepting by remember(profileId) { mutableStateOf(false) }
    var errorMessage by remember(profileId) { mutableStateOf<String?>(null) }

    LaunchedEffect(profileId, gateway) {
        accepted = null
        errorMessage = null
        onAcceptedStateChanged(null)
        if (profileId != null) {
            accepted = gateway.hasAcceptedTerms().getOrDefault(false)
            onAcceptedStateChanged(accepted)
        }
    }

    LaunchedEffect(profileId, accepted, gateway) {
        if (profileId != null && accepted == true) {
            gateway.flushPendingAcceptanceIfSupported()
        }
    }

    if (profileId != null && accepted != true) {
        QuataTermsAcceptanceDialogContent(
            title = strings.title,
            body = strings.body,
            acceptLabel = strings.accept,
            acceptingLabel = if (accepted == null) strings.checking else strings.accepting,
            logoutLabel = strings.logout,
            errorMessage = errorMessage,
            isAccepting = accepting || accepted == null,
            onAccept = {
                if (!accepting) {
                    accepting = true
                    errorMessage = null
                    scope.launch {
                        gateway.acceptTerms().fold(
                            onSuccess = {
                                accepted = true
                                onAcceptedStateChanged(true)
                            },
                            onFailure = {
                                errorMessage = strings.acceptError
                            },
                        )
                        accepting = false
                    }
                }
            },
            onLogout = onLogout,
            legalLinks = legalLinks,
        )
    }
}
