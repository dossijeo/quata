package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.PlatformServices
import com.quata.feature.auth.presentation.AuthBrowserLoginHostContent
import com.quata.feature.auth.presentation.login.LoginFormStrings
import com.quata.feature.auth.presentation.recovery.ForgotPasswordFormStrings
import com.quata.feature.auth.presentation.register.RegisterSecretQuestion

/** Web adapter retains auth construction and browser-backed session persistence. */
@Composable
fun WebLoginHost(platformServices: PlatformServices, configuration: WebRuntimeConfiguration, onLoginSuccess: () -> Unit) {
    val repository = remember(configuration, platformServices.preferences) { WebAuthRepository(configuration, platformServices.preferences) }
    AuthBrowserLoginHostContent(
        repository = repository,
        prefixes = WebCountryPrefixes,
        strings = WebLoginStrings,
        subtitle = "Quata Web",
        recoveryStrings = WebRecoveryStrings,
        secretQuestions = WebSecretQuestions,
        recoveryQuestionWaiting = "Introduce un teléfono registrado",
        recoveryQuestionLoading = "Cargando pregunta secreta…",
        passwordUpdatedMessage = "Contraseña actualizada. Ya puedes iniciar sesión.",
        registerUnavailableMessage = "El registro aún no está disponible en Quata Web.",
    ) {
        platformServices.preferences.putString(WebSessionReadyKey, "true")
        onLoginSuccess()
    }
}

internal const val WebSessionReadyKey = "web.auth.session_ready"

private val WebCountryPrefixes = listOf(
    CountryPrefix("240", "+240 - Guinea Ecuatorial"),
    CountryPrefix("1", "+1 - United States / Canada"),
    CountryPrefix("34", "+34 - España"),
)
private val WebLoginStrings = LoginFormStrings("Teléfono", "Contraseña", "Iniciando sesión…", "Iniciar sesión", "He olvidado mi contraseña", "Crear cuenta", "Buscar prefijo", "")
private val WebRecoveryStrings = ForgotPasswordFormStrings("Tu teléfono", "Buscar prefijo", "Tu pregunta secreta", "Tu respuesta secreta", "Nueva contraseña", "Guardando…", "Actualizar contraseña", "Volver")
private val WebSecretQuestions = listOf(
    RegisterSecretQuestion("madre", "¿Cómo se llama tu madre?"),
    RegisterSecretQuestion("barrio", "¿En qué barrio creciste?"),
    RegisterSecretQuestion("amigo", "¿Cómo se llama tu mejor amigo?"),
    RegisterSecretQuestion("comida", "¿Cuál es tu comida favorita?"),
)
