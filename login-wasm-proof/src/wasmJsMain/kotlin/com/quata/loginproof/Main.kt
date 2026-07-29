@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.loginproof

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.quata.core.config.QuataPublicBackendConfig
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.auth.data.LoginTransport
import com.quata.feature.auth.data.RemoteLoginRepository
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.auth.presentation.login.LoginScreenHost
import kotlinx.browser.document
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.getElementById("quata-login-root")!!) {
        val repository = remember {
            RemoteLoginRepository(
                BrowserLoginTransport(
                    supabaseUrl = QuataPublicBackendConfig.SUPABASE_URL,
                    publishableKey = QuataPublicBackendConfig.SUPABASE_PUBLISHABLE_KEY,
                ),
            )
        }
        val catalog = remember { AuthCatalog.copy(AuthCatalogLocale.Spanish) }
        val prefixes = remember { AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish) }

        QuataTheme {
            LoginScreenHost(
                padding = PaddingValues(),
                repository = repository,
                catalog = catalog,
                prefixes = prefixes,
                showMockNotice = false,
                onGoToRegister = {},
                onForgotPassword = {},
                onLoginSuccess = { browserAlert("logueado correctamente") },
                onLoginFailure = { browserAlert("error de login") },
            )
        }
    }
}

private class BrowserLoginTransport(
    private val supabaseUrl: String,
    private val publishableKey: String,
) : LoginTransport {
    override suspend fun login(
        countryCode: String,
        phone: String,
        password: String,
    ): String = suspendCoroutine { continuation ->
        browserLoginRequest(
            endpoint = supabaseUrl.trimEnd('/') + "/functions/v1/quata-auth-bridge",
            publishableKey = publishableKey,
            countryCode = countryCode,
            phone = phone,
            password = password,
            clientInstanceId = browserClientInstanceId(),
            onSuccess = continuation::resume,
            onFailure = { message ->
                continuation.resumeWith(Result.failure(IllegalStateException(message)))
            },
        )
    }
}

private fun browserAlert(message: String): Unit =
    js("globalThis.alert(message)")

private fun browserClientInstanceId(): String = js(
    """
    (() => {
      const key = 'quata_login_proof_client_id';
      const stored = globalThis.localStorage?.getItem(key);
      if (stored) return stored;
      const created = globalThis.crypto?.randomUUID?.() ||
        (String(Date.now()) + '-' + Math.random().toString(36).slice(2));
      globalThis.localStorage?.setItem(key, created);
      return created;
    })()
    """,
)

private fun browserLoginRequest(
    endpoint: String,
    publishableKey: String,
    countryCode: String,
    phone: String,
    password: String,
    clientInstanceId: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      const body = JSON.stringify({
        action: 'web_login',
        country_code: countryCode,
        phone_local: phone,
        password,
        client_instance_id: clientInstanceId
      });
      fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: publishableKey
        },
        body
      })
        .then(async response => {
          const text = await response.text();
          if (response.ok) onSuccess(text);
          else onFailure('login_http_' + response.status);
        })
        .catch(error => onFailure(error?.message || 'login_network_error'));
    })()
    """,
)
