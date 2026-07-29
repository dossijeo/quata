@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.loginproof

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.quata.core.config.QuataPublicBackendConfig
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.auth.data.LoginTransport
import com.quata.feature.auth.data.RemoteLoginRepository
import com.quata.feature.auth.data.RegisterTransport
import com.quata.feature.auth.data.RegistrationCompletionAware
import com.quata.feature.auth.data.RemoteRegisterRepository
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.auth.presentation.login.LoginScreenHost
import com.quata.feature.auth.presentation.register.RegisterScreenHost
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
        val registerRepository = remember(repository) {
            RemoteRegisterRepository(
                transport = BrowserRegisterTransport(
                    supabaseUrl = QuataPublicBackendConfig.SUPABASE_URL,
                    registrationApiKey = browserWebRegistrationApiKey(),
                    registrationEnabled = browserWebRegistrationEnabled(),
                    turnstileSiteKey = browserTurnstileSiteKey(),
                ),
                loginRepository = repository,
            )
        }
        val catalog = remember { AuthCatalog.copy(AuthCatalogLocale.Spanish) }
        val prefixes = remember { AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish) }
        var destination by remember { mutableStateOf(LoginProofDestination.Login) }

        QuataTheme {
            when (destination) {
                LoginProofDestination.Login -> LoginScreenHost(
                    padding = PaddingValues(),
                    repository = repository,
                    catalog = catalog,
                    prefixes = prefixes,
                    showMockNotice = false,
                    onGoToRegister = { destination = LoginProofDestination.Register },
                    onForgotPassword = {},
                    onLoginSuccess = { browserAlert("logueado correctamente") },
                    onLoginFailure = { browserAlert("error de login") },
                )
                LoginProofDestination.Register -> RegisterScreenHost(
                    padding = PaddingValues(),
                    repository = registerRepository,
                    catalog = catalog,
                    prefixes = prefixes,
                    onBack = { destination = LoginProofDestination.Login },
                    onRegisterSuccess = { browserAlert("cuenta creada correctamente") },
                )
            }
        }
    }
}

private enum class LoginProofDestination { Login, Register }

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

/** Executes the protected registration contract; missing public browser configuration fails. */
private class BrowserRegisterTransport(
    private val supabaseUrl: String,
    private val registrationApiKey: String?,
    private val registrationEnabled: Boolean,
    private val turnstileSiteKey: String?,
) : RegisterTransport, RegistrationCompletionAware {
    override suspend fun register(request: RegisterAccountRequest): Unit = suspendCoroutine { continuation ->
        browserRegisterRequest(
            endpoint = supabaseUrl.trimEnd('/') + "/functions/v1/quata-register",
            registrationApiKey = registrationApiKey,
            registrationEnabled = registrationEnabled,
            turnstileSiteKey = turnstileSiteKey,
            displayName = request.displayName.trim(),
            neighborhood = request.neighborhood.trim(),
            countryCode = request.countryCode.filter(Char::isDigit),
            phone = request.phone.filter(Char::isDigit),
            password = request.password,
            secretQuestion = request.secretQuestion.trim(),
            secretAnswer = request.secretAnswer.trim(),
            clientInstanceId = browserClientInstanceId(),
            idempotencyKey = browserRegistrationIdempotencyKey(
                countryCode = request.countryCode.filter(Char::isDigit),
                phone = request.phone.filter(Char::isDigit),
            ),
            onSuccess = { continuation.resume(Unit) },
            onFailure = { message -> continuation.resumeWith(Result.failure(IllegalStateException(message))) },
        )
    }

    override fun onRegistrationCompleted() = browserClearPendingRegistration()
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

private fun browserTurnstileSiteKey(): String? = js(
    "globalThis.document.querySelector('meta[name=quata-turnstile-site-key]')?.content || null",
)

private fun browserWebRegistrationEnabled(): Boolean = js(
    "globalThis.document.querySelector('meta[name=quata-web-registration-enabled]')?.content === 'true'",
)

private fun browserWebRegistrationApiKey(): String? = js(
    "globalThis.document.querySelector('meta[name=quata-web-registration-api-key]')?.content || null",
)

private fun browserRegisterRequest(
    endpoint: String,
    registrationApiKey: String?,
    registrationEnabled: Boolean,
    turnstileSiteKey: String?,
    displayName: String,
    neighborhood: String,
    countryCode: String,
    phone: String,
    password: String,
    secretQuestion: String,
    secretAnswer: String,
    clientInstanceId: String,
    idempotencyKey: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      if (!registrationEnabled) { onFailure('web_registration_unavailable'); return; }
      if (!registrationApiKey) { onFailure('web_registration_api_key_missing'); return; }
      if (!turnstileSiteKey) { onFailure('turnstile_site_key_missing'); return; }
      const turnstile = globalThis.turnstile;
      if (!turnstile) { onFailure('turnstile_unavailable'); return; }
      const node = document.createElement('div');
      node.style.position = 'fixed'; node.style.left = '-10000px';
      document.body.appendChild(node);
      const fail = code => { node.remove(); onFailure(code || 'turnstile_challenge_failed'); };
      const widgetId = turnstile.render(node, {
        sitekey: turnstileSiteKey, size: 'invisible', execution: 'execute', action: 'register_web',
        callback: challengeToken => {
          node.remove();
          const body = JSON.stringify({
            version: 1, channel: 'web', display_name: displayName, neighborhood,
            country_code: countryCode, phone_local: phone, password,
            secret_question: secretQuestion, secret_answer: secretAnswer,
            client_instance_id: clientInstanceId,
            idempotency_key: idempotencyKey,
            challenge_token: challengeToken
          });
          fetch(endpoint, {
            method: 'POST', headers: { 'Content-Type': 'application/json', apikey: registrationApiKey }, body
          }).then(async response => {
            const text = await response.text();
            let accepted = false;
            try { accepted = response.ok && JSON.parse(text).status === 'accepted'; } catch (_) {}
            if (accepted) onSuccess(); else onFailure('register_http_' + response.status);
          }).catch(error => onFailure(error?.message || 'register_network_error'));
        },
        'error-callback': () => fail('turnstile_challenge_failed'),
        'expired-callback': () => fail('turnstile_challenge_expired')
      });
      turnstile.execute(widgetId);
    })()
    """,
)

private fun browserRegistrationIdempotencyKey(countryCode: String, phone: String): String = js(
    """
    (() => {
      const identity = '+' + (countryCode + phone).replace(/[^0-9]/g, '');
      const identityKey = 'web.auth.registration.identity';
      const keyKey = 'web.auth.registration.idempotency_key';
      const storedIdentity = globalThis.localStorage?.getItem(identityKey);
      const storedKey = globalThis.localStorage?.getItem(keyKey);
      if (storedIdentity === identity && storedKey && /^[A-Za-z0-9_-]{16,200}$/.test(storedKey)) return storedKey;
      const created = (globalThis.crypto?.randomUUID?.() ||
        ('q' + Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2)))
        .replace(/[^A-Za-z0-9_-]/g, '');
      globalThis.localStorage?.setItem(identityKey, identity);
      globalThis.localStorage?.setItem(keyKey, created);
      return created;
    })()
    """,
)

private fun browserClearPendingRegistration(): Unit = js(
    """
    (() => {
      globalThis.localStorage?.removeItem('web.auth.registration.identity');
      globalThis.localStorage?.removeItem('web.auth.registration.idempotency_key');
    })()
    """,
)
