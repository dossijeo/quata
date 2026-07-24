package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.PlatformServices
import com.quata.feature.auth.presentation.AuthBrowserLoginHostContent
import com.quata.feature.auth.presentation.login.LoginFormStrings

/** Web adapter retains auth construction and browser-backed session persistence. */
@Composable
fun WebLoginHost(platformServices: PlatformServices, configuration: WebRuntimeConfiguration, onLoginSuccess: () -> Unit) {
    val repository = remember(configuration, platformServices.preferences) { WebAuthRepository(configuration, platformServices.preferences) }
    AuthBrowserLoginHostContent(repository, WebCountryPrefixes, WebLoginStrings, "Quata Web", "La recuperación de contraseña aún no está disponible en Quata Web.", "El registro aún no está disponible en Quata Web.") {
        platformServices.preferences.putString(WebSessionReadyKey, "true")
        onLoginSuccess()
    }
}
internal const val WebSessionReadyKey = "web.auth.session_ready"
private val WebCountryPrefixes = listOf(CountryPrefix("240", "+240 - Guinea Ecuatorial"), CountryPrefix("1", "+1 - United States / Canada"), CountryPrefix("34", "+34 - España"))
private val WebLoginStrings = LoginFormStrings("Teléfono", "Contraseña", "Iniciando sesión…", "Iniciar sesión", "He olvidado mi contraseña", "Crear cuenta", "Buscar prefijo", "")
